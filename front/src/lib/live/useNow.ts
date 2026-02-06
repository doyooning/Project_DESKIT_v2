import { onBeforeUnmount, ref } from 'vue'

export const useNow = (intervalMs: number = 1000) => {
  const now = ref(new Date())
  const timerId = setInterval(() => {
    now.value = new Date()
  }, intervalMs)

  onBeforeUnmount(() => {
    clearInterval(timerId)
  })

  return { now }
}
