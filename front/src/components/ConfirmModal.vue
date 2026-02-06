<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{
  modelValue: boolean
  title: string
  description: string
  confirmText?: string
  cancelText?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const confirmLabel = computed(() => props.confirmText ?? '확인')
const cancelLabel = computed(() => props.cancelText ?? '취소')

const close = () => emit('update:modelValue', false)
const handleCancel = () => {
  emit('cancel')
  close()
}
const handleConfirm = () => {
  emit('confirm')
  close()
}
</script>

<template>
  <div v-if="modelValue" class="ds-modal" role="dialog" aria-modal="true">
    <div class="ds-modal__backdrop" @click="handleCancel"></div>
    <div class="ds-modal__card ds-surface">
      <header class="ds-modal__head">
        <h3 class="ds-modal__title">{{ title }}</h3>
        <button type="button" class="ds-modal__close" aria-label="닫기" @click="handleCancel">×</button>
      </header>
      <p class="ds-modal__description">
        {{ description }}
      </p>
      <footer class="ds-modal__actions">
        <button type="button" class="ds-btn ghost" @click="handleCancel">{{ cancelLabel }}</button>
        <button type="button" class="ds-btn primary" @click="handleConfirm">{{ confirmLabel }}</button>
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
  z-index: 1400;
}

.ds-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}

.ds-modal__card {
  position: relative;
  width: min(480px, 90vw);
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

.ds-modal__title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.ds-modal__description {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.5;
  font-weight: 700;
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

.ds-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.ds-btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 10px 16px;
  font-weight: 800;
  cursor: pointer;
}

.ds-btn.primary {
  background: var(--primary-color);
  color: #fff;
  border-color: transparent;
}

.ds-btn.ghost {
  background: transparent;
  color: var(--text-muted);
}
</style>
