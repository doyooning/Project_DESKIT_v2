<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../../components/PageContainer.vue'
import PageHeader from '../../components/PageHeader.vue'
import {
  buildDraftFromReservation,
  clearDraft,
  createDefaultQuestions,
  createEmptyDraft,
  getDraftRestoreDecision,
  loadDraft,
  saveDraft,
  saveWorkingDraft,
  clearWorkingDraft,
  type LiveCreateDraft,
  clearDraftRestoreDecision,
  setDraftRestoreDecision,
} from '../../composables/useLiveCreateDraft'

const router = useRouter()
const route = useRoute()

const maxQuestions = 10
const draft = ref<LiveCreateDraft>(createEmptyDraft())
const error = ref('')

const reservationId = computed(() => (typeof route.query.reservationId === 'string' ? route.query.reservationId : ''))
const isEditMode = computed(() => route.query.mode === 'edit' && !!reservationId.value)

const createQuestion = () => ({
  id: `q-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  text: '',
})

const syncDraft = () => {
  saveWorkingDraft({
    ...draft.value,
    questions: draft.value.questions.map((q) => ({ ...q, text: q.text.trim() })),
  })
}

const restoreDraft = async () => {
  const saved = loadDraft()
  if (!isEditMode.value && saved && (!saved.reservationId || saved.reservationId === reservationId.value)) {
    const decision = getDraftRestoreDecision()
    if (decision === 'accepted') {
      draft.value = { ...draft.value, ...saved }
    } else if (decision === 'declined') {
      clearDraft()
    } else {
      const shouldRestore = window.confirm('이전에 작성 중인 내용을 불러올까요?')
      if (shouldRestore) {
        setDraftRestoreDecision('accepted')
        draft.value = { ...draft.value, ...saved }
      } else {
        setDraftRestoreDecision('declined')
        clearDraft()
      }
    }
  }

  if (isEditMode.value) {
    draft.value = { ...draft.value, ...(await buildDraftFromReservation(reservationId.value)) }
  }

  if (!draft.value.questions.length) {
    draft.value.questions = createDefaultQuestions()
  }

  syncDraft()
}

const addQuestion = () => {
  if (draft.value.questions.length >= maxQuestions) return
  draft.value.questions.push(createQuestion())
}

const removeQuestion = (id: string) => {
  if (draft.value.questions.length <= 1) return
  draft.value.questions = draft.value.questions.filter((item) => item.id !== id)
}

const isQuestionValid = (text: string) => !!text.trim()

const goNext = () => {
  const trimmed = draft.value.questions.map((q) => ({ ...q, text: q.text.trim() }))
  const filled = trimmed.filter((q) => q.text.length > 0)

  if (filled.length === 0 && draft.value.questions.length <= 1) {
    draft.value.questions = []
    error.value = ''
    syncDraft()
    router.push({ path: '/seller/live/create/basic', query: route.query }).catch(() => {})
    return
  }

  const hasInvalid = filled.length !== trimmed.length
  if (hasInvalid) {
    error.value = '모든 질문을 입력해주세요.'
    return
  }
  draft.value.questions = filled
  error.value = ''
  syncDraft()
  router.push({ path: '/seller/live/create/basic', query: route.query }).catch(() => {})
}

const cancel = () => {
  const ok = window.confirm('작성 중인 내용을 취소하시겠어요?')
  if (!ok) return
  saveDraft(draft.value)
  clearDraftRestoreDecision()
  clearWorkingDraft()
  const redirect = isEditMode.value && reservationId.value
    ? `/seller/broadcasts/reservations/${reservationId.value}`
    : '/seller/live?tab=scheduled'
  router.push(redirect).catch(() => {})
}

onMounted(() => {
  restoreDraft()
})

watch(
  draft,
  () => {
    syncDraft()
  },
  { deep: true },
)
</script>

<template>
  <PageContainer>
    <PageHeader :eyebrow="isEditMode ? 'DESKIT' : 'DESKIT'" :title="isEditMode ? '예약 수정 - 큐 카드 편집' : '방송 등록 - 큐 카드 작성'" />
    <section class="create-card ds-surface">
      <div class="step-meta">
        <span class="step-indicator">1 / 2 단계</span>
        <button type="button" class="btn ghost" @click="router.back()">이전</button>
      </div>
      <div class="section-head">
        <h3>큐 카드 질문</h3>
        <span class="count-pill">{{ draft.questions.length }}/{{ maxQuestions }}</span>
      </div>
      <div class="question-list">
        <div v-for="(item, index) in draft.questions" :key="item.id" class="question-card" :class="{ invalid: draft.questions.length > 1 && !isQuestionValid(item.text) }">
          <div class="question-head">
            <span class="question-title">질문 {{ index + 1 }}</span>
            <button type="button" class="btn ghost" :disabled="draft.questions.length <= 1" @click="removeQuestion(item.id)">
              삭제
            </button>
          </div>
          <textarea
            v-model="item.text"
            rows="3"
            placeholder="질문을 입력하세요."
            @input="error = ''"
          ></textarea>
          <p v-if="draft.questions.length > 1 && !isQuestionValid(item.text)" class="inline-error">질문을 입력해주세요.</p>
        </div>
      </div>
      <div class="question-actions">
        <button type="button" class="btn" :disabled="draft.questions.length >= maxQuestions" @click="addQuestion">
          + 질문 추가
        </button>
        <span v-if="draft.questions.length >= maxQuestions" class="hint">최대 10개까지 추가할 수 있어요.</span>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="actions">
        <div class="step-hint">모든 질문을 입력하면 다음 단계로 이동할 수 있습니다.</div>
        <div class="action-buttons">
          <button type="button" class="btn" @click="cancel">취소</button>
          <button type="button" class="btn primary" @click="goNext">다음 단계</button>
        </div>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.create-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.step-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.step-indicator {
  color: var(--text-muted);
  font-weight: 800;
  font-size: 0.95rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field__label {
  font-weight: 800;
  color: var(--text-strong);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.count-pill {
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  color: var(--text-strong);
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 800;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 12px;
  background: var(--surface);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-card.invalid {
  border-color: #ef4444;
}

.inline-error {
  margin: 0;
  color: #ef4444;
  font-weight: 700;
  font-size: 0.9rem;
}

.question-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.question-title {
  font-weight: 800;
  color: var(--text-strong);
}

.question-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hint {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.error {
  margin: 0;
  color: #ef4444;
  font-weight: 800;
}

input,
textarea {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-buttons {
  display: flex;
  gap: 10px;
}

.btn {
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 900;
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  cursor: pointer;
}

.btn.ghost {
  border-color: var(--border-color);
  color: var(--text-muted);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn.primary {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.step-hint {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}
</style>
