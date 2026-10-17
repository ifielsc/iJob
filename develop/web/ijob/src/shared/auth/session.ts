import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import type { Token } from '../api/contracts'

export const useSession = defineStore('session', () => {
  const token = shallowRef<Token | null>(null)
  const revision = ref(0)
  function replace(value: Token | null) {
    if (
      value &&
      (!/^[a-zA-Z0-9-]+$/.test(value.tokenName) ||
        /^(cookie|authorization|host|origin|accept-language|content-type)$/i.test(
          value.tokenName,
        ) ||
        !value.tokenValue ||
        /[\r\n]/.test(value.tokenValue))
    )
      throw new Error('Invalid token contract')
    token.value = value ? { ...value } : null
    revision.value++
  }
  // Credentials intentionally live only in memory; never in preference/local storage or URLs.
  return { token, revision, replace }
})
