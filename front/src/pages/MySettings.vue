<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { getMySettings, updateMySettings } from '../api/my-settings'
import { hydrateSessionUser } from '../lib/auth'

type SelectOption = {
  value: string
  label: string
}

const router = useRouter()
const isLoading = ref(false)
const isSaving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  mbti: 'NONE',
  jobCategory: 'NONE',
  marketingAgreed: false,
})

const mbtiOptions: SelectOption[] = [
  { value: 'NONE', label: '선택 안함' },
  { value: 'INTJ', label: 'INTJ' },
  { value: 'INTP', label: 'INTP' },
  { value: 'ENTJ', label: 'ENTJ' },
  { value: 'ENTP', label: 'ENTP' },
  { value: 'INFJ', label: 'INFJ' },
  { value: 'INFP', label: 'INFP' },
  { value: 'ENFJ', label: 'ENFJ' },
  { value: 'ENFP', label: 'ENFP' },
  { value: 'ISTJ', label: 'ISTJ' },
  { value: 'ISFJ', label: 'ISFJ' },
  { value: 'ESTJ', label: 'ESTJ' },
  { value: 'ESFJ', label: 'ESFJ' },
  { value: 'ISTP', label: 'ISTP' },
  { value: 'ISFP', label: 'ISFP' },
  { value: 'ESTP', label: 'ESTP' },
  { value: 'ESFP', label: 'ESFP' },
]

const jobOptions: SelectOption[] = [
  { value: 'NONE', label: '선택 안함' },
  { value: 'CREATIVE_TYPE', label: '크리에이티브' },
  { value: 'FLEXIBLE_TYPE', label: '프리랜서/유연근무' },
  { value: 'EDU_RES_TYPE', label: '교육/연구' },
  { value: 'MED_PRO_TYPE', label: '의료/전문직' },
  { value: 'ADMIN_PLAN_TYPE', label: '기획/관리' },
]

const normalizeValue = (value?: string | null) => (value && value.trim() ? value : 'NONE')

const loadSettings = async () => {
  isLoading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const response = await getMySettings()
    form.mbti = normalizeValue(response?.mbti)
    form.jobCategory = normalizeValue(response?.job_category)
    form.marketingAgreed = Boolean(response?.marketing_agreed)
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      router.push('/login').catch(() => {})
      return
    }
    errorMessage.value = '내 정보 불러오기에 실패했습니다.'
  } finally {
    isLoading.value = false
  }
}

const canSubmit = computed(() => !isLoading.value && !isSaving.value)

const handleSubmit = async () => {
  if (!canSubmit.value) return
  isSaving.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await updateMySettings({
      mbti: form.mbti,
      job_category: form.jobCategory,
      marketing_agreed: form.marketingAgreed,
    })
    await hydrateSessionUser()
    successMessage.value = '내 정보가 업데이트되었습니다.'
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      router.push('/login').catch(() => {})
      return
    }
    errorMessage.value = '내 정보 업데이트에 실패했습니다.'
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  loadSettings()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="내 정보 관리" />

    <div class="settings-wrap">
      <section class="settings-panel">
        <div class="panel-head">
          <div>
            <h3 class="panel-title">내 정보 수정</h3>
            <p class="panel-sub">MBTI, 직업군, 마케팅 알림 동의를 수정할 수 있습니다.</p>
          </div>
        </div>

        <div v-if="isLoading" class="empty-card">
          <p>내 정보를 불러오는 중입니다.</p>
        </div>

        <form v-else class="form" @submit.prevent="handleSubmit">
          <label class="field">
            <span>MBTI</span>
            <select v-model="form.mbti">
              <option v-for="option in mbtiOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label class="field">
            <span>직업군</span>
            <select v-model="form.jobCategory">
              <option v-for="option in jobOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label class="checkbox">
            <input v-model="form.marketingAgreed" type="checkbox" />
            마케팅 알림 수신에 동의합니다.
          </label>

          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
          <p v-if="successMessage" class="success">{{ successMessage }}</p>

          <div class="actions">
            <button type="submit" class="btn primary" :disabled="!canSubmit">
              {{ isSaving ? '저장 중...' : '변경사항 저장' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.settings-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 760px;
  margin: 0 auto;
}

.settings-panel {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 900;
  color: var(--text-strong);
}

.panel-sub {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.empty-card {
  border: 1px dashed var(--border-color);
  border-radius: 12px;
  padding: 14px;
  color: var(--text-muted);
  font-weight: 700;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-weight: 700;
  color: var(--text-muted);
}

.field select {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface-weak);
}

.field select:focus {
  border-color: var(--primary-color);
  background: var(--surface);
  outline: none;
}

.checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--text-strong);
}

.checkbox input {
  width: 16px;
  height: 16px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}

.btn {
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: var(--surface);
  font-weight: 900;
  cursor: pointer;
}

.btn.primary {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #fff;
}

.btn.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  margin: 0;
  font-weight: 700;
  color: var(--danger-color, #dc2626);
}

.success {
  margin: 0;
  font-weight: 700;
  color: #15803d;
}

@media (max-width: 640px) {
  .actions {
    justify-content: flex-start;
  }
}
</style>
