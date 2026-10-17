import { onBeforeUnmount, ref, watch, type Ref } from 'vue'

/** Observe the scroll viewport and release its observer when replaced or unmounted. */
export function useTableOverflow(tableScroll: Ref<HTMLElement | null>) {
  const tableOverflow = ref(false)
  let observer: ResizeObserver | undefined
  watch(tableScroll, (element) => {
    observer?.disconnect()
    tableOverflow.value = false
    if (!element) return
    const update = () => {
      tableOverflow.value = element.scrollWidth > element.clientWidth + 1
    }
    observer = new ResizeObserver(update)
    observer.observe(element)
    update()
  })
  onBeforeUnmount(() => observer?.disconnect())
  return tableOverflow
}
