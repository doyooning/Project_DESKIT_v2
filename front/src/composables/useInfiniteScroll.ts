import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

type UseInfiniteScrollOptions = {
  canLoadMore: () => boolean
  loadMore: () => void
  enabled?: () => boolean
  root?: () => Element | null | undefined
  rootMargin?: string
  threshold?: number | number[]
}

export const useInfiniteScroll = (options: UseInfiniteScrollOptions) => {
  const sentinelRef = ref<HTMLElement | null>(null)
  const observer = ref<IntersectionObserver | null>(null)
  const isLoading = ref(false)

  const cleanup = () => {
    if (observer.value) {
      observer.value.disconnect()
    }
    observer.value = null
  }

  const initObserver = () => {
    cleanup()
    if (options.enabled && !options.enabled()) return
    if (!options.canLoadMore()) return
    if (!sentinelRef.value) return

    observer.value = new IntersectionObserver(
      (entries) => {
        const isVisible = entries.some((entry) => entry.isIntersecting)
        if (isVisible && options.canLoadMore() && !isLoading.value) {
          isLoading.value = true
          options.loadMore()
          void nextTick(() => {
            isLoading.value = false
          })
        }
      },
      {
        root: options.root ? options.root() : null,
        rootMargin: options.rootMargin ?? '200px 0px',
        threshold: options.threshold ?? 0.1,
      },
    )

    observer.value.observe(sentinelRef.value)
  }

  onMounted(() => {
    void nextTick(initObserver)
  })

  onBeforeUnmount(() => {
    cleanup()
  })

  watch(
    [sentinelRef as Ref<HTMLElement | null>, () => (options.enabled ? options.enabled() : true), () => options.canLoadMore()],
    () => {
      void nextTick(initObserver)
    },
  )

  return {
    sentinelRef,
  }
}
