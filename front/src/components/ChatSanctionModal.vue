<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  modelValue: boolean
  username: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', payload: { type: string; reason: string }): void
}>()

const sanctionType = ref('')
const sanctionReason = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      sanctionType.value = ''
      sanctionReason.value = ''
    }
  },
)

const close = () => emit('update:modelValue', false)

const handleSave = () => {
  if (!sanctionType.value) {
    alert('제재 유형을 선택해주세요.')
    return
  }
  emit('save', { type: sanctionType.value, reason: sanctionReason.value })
  close()
}
</script>

<template>
  <div v-if="modelValue" class="ds-modal" role="dialog" aria-modal="true">
    <div class="ds-modal__backdrop" @click="close"></div>
    <div class="ds-modal__card ds-surface">
      <header class="ds-modal__head">
        <h3 class="ds-modal__title">채팅 관리</h3>
        <button type="button" class="ds-modal__close" aria-label="닫기" @click="close">×</button>
      </header>

      <div class="ds-modal__body">
        <label class="field">
          <span class="field__label">제재 대상</span>
          <input :value="username ?? ''" class="field__input" disabled />
        </label>

        <label class="field">
          <span class="field__label">제재 유형 <span class="required">*</span></span>
          <select v-model="sanctionType" class="field__input">
            <option value="">선택해주세요</option>
            <option value="채팅 금지">채팅 금지</option>
            <option value="강제 퇴장">강제 퇴장</option>
          </select>
        </label>

        <label class="field">
          <span class="field__label">사유 (선택)</span>
          <textarea v-model="sanctionReason" rows="3" class="field__input" placeholder="사유를 입력해주세요."></textarea>
        </label>
      </div>

      <footer class="ds-modal__actions">
        <button type="button" class="ds-btn ghost" @click="close">취소</button>
        <button type="button" class="ds-btn primary" @click="handleSave">저장</button>
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
  width: min(520px, 92vw);
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
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field__label {
  font-weight: 800;
  color: var(--text-strong);
}

.required {
  color: #ef4444;
}

.field__input {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 10px 12px;
  font-weight: 700;
  font-size: 0.95rem;
}

.field__input:focus {
  outline: 2px solid var(--primary-color);
  outline-offset: 1px;
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
