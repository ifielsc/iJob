import { parse, stringify, isSafeNumber, LosslessNumber } from 'lossless-json'
import type { AppLocale } from '../preferences/preferences'
import type { ApiResponse, Token } from './contracts'
import { ApiError } from './errors'

interface Context {
  locale: () => AppLocale
  token: () => Token | null
  revision: () => number
  onAccessError: (error: ApiError) => void
  fetch?: typeof fetch
  timeoutMs?: number
}
export function createApiClient(context: Context) {
  return async function request<T>(
    path: string,
    options: {
      method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
      body?: unknown
      lossless?: boolean
      signal?: AbortSignal
      anonymous?: boolean
      idempotencyKey?: string
      localForbidden?: boolean
    } = {},
  ): Promise<T> {
    if (!/^\/api\/[a-zA-Z0-9/_?=&.%+-]+$/.test(path) || path.includes('..'))
      throw new ApiError('protocol')
    const generation = context.revision()
    const headers = new Headers({ Accept: 'application/json', 'Accept-Language': context.locale() })
    const token = options.anonymous ? null : context.token()
    if (token) headers.set(token.tokenName, token.tokenValue)
    if (options.body !== undefined) headers.set('Content-Type', 'application/json')
    if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey)
    const timeout = new AbortController()
    const timer = setTimeout(() => timeout.abort(), context.timeoutMs ?? 15_000)
    const signal = options.signal
      ? AbortSignal.any([options.signal, timeout.signal])
      : timeout.signal
    try {
      const response = await (context.fetch ?? fetch)(path, {
        method: options.method ?? 'GET',
        headers,
        body:
          options.body === undefined
            ? undefined
            : options.lossless
              ? stringify(options.body)
              : JSON.stringify(options.body),
        signal,
        credentials: 'omit',
        redirect: 'error',
        cache: 'no-store',
      })
      let envelope: Partial<ApiResponse<T>> | undefined
      if (response.headers.get('content-type')?.includes('application/json')) {
        let raw: unknown
        try {
          raw = options.lossless
            ? parse(await response.text(), undefined, {
                parseNumber: (n) => (isSafeNumber(n) ? Number(n) : new LosslessNumber(n)),
              })
            : await response.json()
        } catch {
          throw new ApiError('protocol')
        }
        if (raw && typeof raw === 'object' && 'success' in raw && typeof raw.success === 'boolean')
          envelope = raw as ApiResponse<T>
      }
      if (!response.ok || envelope?.success === false) {
        const status = response.ok ? (envelope?.code ?? response.status) : response.status
        const kind =
          status === 401
            ? 'unauthorized'
            : status === 403
              ? envelope?.errorCode === 'IJOB-SECURITY-0405'
                ? 'passwordRequired'
                : 'forbidden'
              : status === 409
                ? 'conflict'
                : 'generic'
        const error = new ApiError(kind, status, envelope)
        if (
          !options.anonymous &&
          generation === context.revision() &&
          [401, 403].includes(status) &&
          !(options.localForbidden && kind === 'forbidden')
        )
          context.onAccessError(error)
        throw error
      }
      if (!envelope || envelope.success !== true) throw new ApiError('protocol')
      // Do not expose a response belonging to an identity which has since changed.
      if (generation !== context.revision())
        throw new DOMException('Identity changed', 'AbortError')
      return envelope.data as T
    } catch (error) {
      if (error instanceof ApiError) throw error
      if (options.signal?.aborted) throw error
      if (error instanceof DOMException && error.name === 'AbortError' && !timeout.signal.aborted)
        throw error
      throw new ApiError(timeout.signal.aborted ? 'timeout' : 'network')
    } finally {
      clearTimeout(timer)
    }
  }
}
