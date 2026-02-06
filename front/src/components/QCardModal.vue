<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    qCards?: string[]
    initialIndex?: number
  }>(),
  {
    qCards: () => [],
    initialIndex: 0,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:initialIndex', value: number): void
}>()

const close = () => emit('update:modelValue', false)
const currentIndex = ref(props.initialIndex ?? 0)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      currentIndex.value = props.initialIndex ?? 0
    }
  },
)

watch(
  () => props.initialIndex,
  (next) => {
    currentIndex.value = next ?? 0
  },
)

const goPrev = () => {
  if (!props.qCards.length) return
  currentIndex.value = (currentIndex.value - 1 + props.qCards.length) % props.qCards.length
  emit('update:initialIndex', currentIndex.value)
}

const goNext = () => {
  if (!props.qCards.length) return
  currentIndex.value = (currentIndex.value + 1) % props.qCards.length
  emit('update:initialIndex', currentIndex.value)
}
</script>

<template>
  <div v-if="modelValue" class="ds-modal" role="dialog" aria-modal="true">
    <div class="ds-modal__backdrop" @click="close"></div>
    <div class="ds-modal__card ds-surface">
      <header class="ds-modal__head">
        <div>
          <p class="ds-modal__eyebrow">진행 가이드</p>
          <h3 class="ds-modal__title">큐카드</h3>
        </div>
        <button type="button" class="ds-modal__close" aria-label="닫기" @click="close">×</button>
      </header>

      <div class="ds-modal__body">
        <div v-if="qCards.length > 0" class="qcard-list">
          <article class="qcard-item">
            <p class="qcard-label">질문 {{ currentIndex + 1 }}</p>
            <p class="qcard-text">{{ qCards[currentIndex] || '(내용 없음)' }}</p>
          </article>
        </div>
        <p v-else class="qcard-empty">등록된 큐카드가 없습니다.</p>
      </div>
      <footer v-if="qCards.length > 1" class="qcard-footer">
        <button type="button" class="ds-modal__close" aria-label="이전" @click="goPrev">‹</button>
        <span class="qcard-progress">{{ currentIndex + 1 }} / {{ qCards.length }}</span>
        <button type="button" class="ds-modal__close" aria-label="다음" @click="goNext">›</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.ds-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1300;
}

.ds-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}

.ds-modal__card {
  position: relative;
  width: min(640px, 92vw);
  max-height: 80vh;
  padding: 20px;
  border-radius: 16px;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ds-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.ds-modal__eyebrow {
  margin: 0 0 4px;
  color: var(--text-muted);
  font-weight: 800;
  letter-spacing: 0.04em;
}

.ds-modal__title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 900;
  color: var(--text-strong);
}

.ds-modal__close {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  width: 36px;
  height: 36px;
  border-radius: 10px;
  font-size: 1.1rem;
  font-weight: 900;
  cursor: pointer;
}

.ds-modal__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.qcard-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.qcard-item {
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-weak);
}

.qcard-label {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-weight: 800;
  font-size: 0.85rem;
}

.qcard-text {
  margin: 0;
  color: var(--text-strong);
  line-height: 1.5;
  font-weight: 800;
}

.qcard-empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  color: var(--text-muted);
  font-weight: 800;
}

.qcard-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.qcard-progress {
  font-weight: 800;
  color: var(--text-strong);
}
</style>
