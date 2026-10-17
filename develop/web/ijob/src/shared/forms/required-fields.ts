import { ref } from 'vue'
import type { Ref } from 'vue'

// Validate presence without trimming passwords or changing the server's rules.
// Once a complete form is submitted, clearing its passwords must not create new
// required errors alongside the server response.
export function useRequiredFields<K extends string>(fields: Record<K, Ref<string>>) {
  const attempted = ref(false)
  const missing = (key: K) => attempted.value && fields[key].value.length === 0
  function firstMissing(): K | undefined {
    const first = (Object.keys(fields) as K[]).find((key) => fields[key].value.length === 0)
    attempted.value = first !== undefined
    return first
  }
  return {
    missing,
    firstMissing,
    reset: () => {
      attempted.value = false
    },
  }
}
