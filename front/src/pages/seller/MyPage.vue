<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '../../components/PageContainer.vue'
import PageHeader from '../../components/PageHeader.vue'
import { getAuthUser, normalizeDisplayName, requestLogout, requestWithdraw } from '../../lib/auth'

const router = useRouter()
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'

type Manager = {
  id: number
  name: string
  email: string
  role: string
  status: string
}

type SellerMyPagePayload = {
  companyName?: string
  companyGrade?: string
  gradeExpiredAt?: string
  managers?: Manager[]
}

type UserInfo = {
  name: string
  email: string
  signupType: string
  memberCategory: string
  sellerRole: string
  profileUrl: string
}

const user = ref<UserInfo | null>(null)
const profileImageFailed = ref(false)

const loadUser = () => {
  const parsed = getAuthUser()
  if (!parsed) {
    user.value = null
    profileImageFailed.value = false
    return
  }
  user.value = {
    name: normalizeDisplayName(parsed.name, '판매자'),
    email: parsed.email || '',
    signupType: parsed.signupType || '',
    memberCategory: parsed.memberCategory || '',
    sellerRole: parsed.sellerRole || '',
    profileUrl: parsed.profileUrl || '',
  }
  profileImageFailed.value = false
}
const display = computed(() => {
  const current = user.value
  const fallbackSignupType = current ? '소셜 회원' : ''
  return {
    name: normalizeDisplayName(current?.name, '판매자'),
    email: current?.email || '',
    signupType: current?.signupType || fallbackSignupType,
    memberCategory: current?.memberCategory || '판매자',
    sellerRole: current?.sellerRole || '대표자',
    profileUrl: current?.profileUrl || '',
  }
})

const isManager = computed(() => display.value.sellerRole === '매니저')

const handleLogout = async () => {
  const success = await requestLogout()
  if (success) {
    window.alert('로그아웃되었습니다.')
  }
  router.push('/').catch(() => {})
}

/*
const managers = ref([
  {
    id: 'manager-1',
    name: '김지우',
    email: 'jiwoo.manager@test.com',
    role: '매니저',
  },
  {
    id: 'manager-2',
    name: '박서준',
    email: 'seojun.manager@test.com',
    role: '부매니저',
  },
])
*/
const managers = ref<Manager[]>([])
const companyName = ref('')
const companyGrade = ref('')
const gradeExpiredAt = ref('')

const showManagerModal = ref(false)
const showConfirmModal = ref(false)
const showSent = ref(false)
const showWithdrawModal = ref(false)
const managerEmail = ref('')
const pendingEmail = ref('')
const inviteError = ref('')
const inviteSending = ref(false)
const withdrawProcessing = ref(false)
const emailInputRef = ref<HTMLInputElement | null>(null)
const isEmailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(managerEmail.value.trim()))

const buildAuthHeaders = (): Record<string, string> => {
  const access = localStorage.getItem('access') || sessionStorage.getItem('access')
  if (!access) return {}
  return { Authorization: `Bearer ${access}` }
}

const loadSellerMyPage = async () => {
  try {
    const response = await fetch(`${apiBase}/seller/mypage`, {
      credentials: 'include',
      headers: buildAuthHeaders(),
    })
    if (!response.ok) {
      managers.value = []
      companyName.value = ''
      companyGrade.value = ''
      gradeExpiredAt.value = ''
      return
    }
    const payload = (await response.json().catch(() => null)) as SellerMyPagePayload | null
    managers.value = Array.isArray(payload?.managers) ? payload?.managers ?? [] : []
    companyName.value = typeof payload?.companyName === 'string' ? payload?.companyName ?? '' : ''
    companyGrade.value = typeof payload?.companyGrade === 'string' ? payload?.companyGrade ?? '' : ''
    gradeExpiredAt.value = typeof payload?.gradeExpiredAt === 'string' ? payload?.gradeExpiredAt ?? '' : ''
  } catch (error) {
    console.error('failed to load my page', error)
    managers.value = []
    companyName.value = ''
    companyGrade.value = ''
    gradeExpiredAt.value = ''
  }
}

const formatCompanyGrade = (value: string) => {
  if (value === 'A') return '공식 파트너'
  if (value === 'B') return '인증 판매자'
  if (value === 'C') return '신규 판매자'
  return '-'
}

const formatManagerRole = (role: string) => {
  const normalized = (role || '').trim().toUpperCase()
  if (normalized === 'ROLE_SELLER_MANAGER') return '매니저'
  if (normalized === 'ROLE_SELLER_OWNER') return '대표자'
  return role
}

const formatManagerStatus = (status: string) => {
  const normalized = (status || '').trim().toUpperCase()
  if (normalized === 'ACTIVE') return '활성'
  if (normalized === 'PENDING') return '대기'
  if (normalized === 'INACTIVE') return '비활성'
  return status
}

const openManagerModal = () => {
  showManagerModal.value = true
  managerEmail.value = ''
  pendingEmail.value = ''
  showSent.value = false
  inviteError.value = ''
  nextTick(() => {
    emailInputRef.value?.focus()
  })
}

const closeManagerModal = () => {
  showManagerModal.value = false
  managerEmail.value = ''
  pendingEmail.value = ''
  showConfirmModal.value = false
  showSent.value = false
  inviteError.value = ''
  inviteSending.value = false
}

const openWithdrawModal = () => {
  showWithdrawModal.value = true
}

const closeWithdrawModal = () => {
  showWithdrawModal.value = false
  withdrawProcessing.value = false
}

const confirmWithdraw = async () => {
  withdrawProcessing.value = true
  const result = await requestWithdraw()
  withdrawProcessing.value = false
  if (result.ok) {
    window.alert('탈퇴가 완료되었습니다.')
    localStorage.removeItem('access')
    sessionStorage.removeItem('access')
    localStorage.removeItem('deskit-user')
    localStorage.removeItem('deskit-auth')
    closeWithdrawModal()
    window.location.href = '/'
    return
  }
  window.alert(result.message || '탈퇴에 실패했습니다.')
}

const openConfirm = () => {
  if (!isEmailValid.value) return
  pendingEmail.value = managerEmail.value.trim()
  showConfirmModal.value = true
}

const closeConfirm = () => {
  showConfirmModal.value = false
}

const confirmSend = async () => {
  if (!pendingEmail.value) return
  inviteSending.value = true
  inviteError.value = ''
  try {
    const response = await fetch(`${apiBase}/invitations`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...buildAuthHeaders(),
      },
      credentials: 'include',
      body: JSON.stringify({ email: pendingEmail.value }),
    })
    if (!response.ok) {
      const errorText = await response.text()
      inviteError.value = errorText || '초대 링크 전송에 실패했습니다.'
      return
    }
    showConfirmModal.value = false
    showSent.value = true
  } catch (error) {
    console.error('invite send failed', error)
    inviteError.value = '초대 링크 전송에 실패했습니다.'
  } finally {
    inviteSending.value = false
  }
}

const handleModalClose = () => {
  if (showSent.value) {
    closeManagerModal()
    return
  }
  if (showConfirmModal.value) {
    closeConfirm()
    return
  }
  closeManagerModal()
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Escape') return
  if (showWithdrawModal.value) {
    closeWithdrawModal()
    return
  }
  if (showManagerModal.value) {
    if (showSent.value) {
      closeManagerModal()
      return
    }
    if (showConfirmModal.value) {
      closeConfirm()
      return
    }
    closeManagerModal()
  }
}

const profileImageUrl = computed(() => (display.value.profileUrl || '').trim())
const showProfileImage = computed(() => !!profileImageUrl.value && !profileImageFailed.value)

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('deskit-user-updated', loadUser)
  loadSellerMyPage()
  loadUser()
  profileImageFailed.value = false
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('deskit-user-updated', loadUser)
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="판매자 마이페이지" />
    <section class="seller-card ds-surface">
      <div class="seller-card__top">
        <div class="seller-avatar">
          <img
            v-if="showProfileImage"
            :src="profileImageUrl"
            :alt="`${display.name} 프로필`"
            @error="profileImageFailed = true"
          />
          <span v-else aria-hidden="true">{{ display.name.slice(0, 2).toUpperCase() }}</span>
        </div>
        <div class="seller-meta">
          <p class="seller-name">{{ display.name }}</p>
          <p class="seller-email">{{ display.email }}</p>
        </div>
      </div>
      <dl class="seller-info">
        <div class="seller-info__row">
          <dt>가입 유형</dt>
          <dd>{{ display.signupType }}</dd>
        </div>
        <div class="seller-info__row">
          <dt>회원 분류</dt>
          <dd>{{ display.memberCategory }}</dd>
        </div>
        <div class="seller-info__row">
          <dt>판매자 역할</dt>
          <dd>{{ display.sellerRole }}</dd>
        </div>
        <div class="seller-info__row">
          <dt>사업자명</dt>
          <dd>{{ companyName || '-' }}</dd>
        </div>
        <div class="seller-info__row">
          <dt>배정 그룹</dt>
          <dd>{{ formatCompanyGrade(companyGrade) }}</dd>
        </div>
        <div class="seller-info__row">
          <dt>그룹 만료일</dt>
          <dd>{{ gradeExpiredAt || '-' }}</dd>
        </div>
      </dl>
      <button type="button" class="seller-logout" @click="handleLogout">로그아웃</button>
    </section>
    <section v-if="!isManager" class="manager-card ds-surface">
      <div class="manager-head">
        <button type="button" class="manager-add" @click="openManagerModal">매니저 등록</button>
      </div>
      <div class="manager-body">
        <div class="manager-list">
          <p class="manager-title">등록된 매니저</p>
          <ul v-if="managers.length" class="manager-items">
            <li v-for="manager in managers" :key="manager.id" class="manager-item">
              <div class="manager-meta">
                <span class="manager-name">{{ manager.name }}</span>
                <span class="manager-role">{{ formatManagerRole(manager.role) }}</span>
              </div>
              <div class="manager-meta secondary">
                <span class="manager-email">{{ manager.email }}</span>
                <span class="manager-status">{{ formatManagerStatus(manager.status) }}</span>
              </div>
            </li>
          </ul>
          <p v-else class="manager-empty">등록된 매니저가 없습니다.</p>
        </div>
      </div>
    </section>
    <section v-else class="withdraw-card ds-surface">
      <div class="withdraw-head">
        <h3>회원 탈퇴</h3>
        <p>매니저 계정 탈퇴는 되돌릴 수 없습니다.</p>
      </div>
      <button type="button" class="btn danger withdraw-action" @click="openWithdrawModal">
        회원 탈퇴
      </button>
    </section>

    <div v-if="showManagerModal" class="manager-modal" role="dialog" aria-modal="true" aria-label="매니저 등록">
      <div class="manager-modal__backdrop" @click="handleModalClose"></div>
      <div class="manager-modal__card ds-surface">
        <div class="manager-modal__head">
          <div>
            <h3>매니저 등록</h3>
            <p>매니저 이메일로 로그인 링크를 보내드립니다.</p>
          </div>
          <button type="button" class="modal-close" @click="handleModalClose" aria-label="닫기">닫기</button>
        </div>
        <form v-if="!showConfirmModal && !showSent" class="manager-form" @submit.prevent="openConfirm">
          <label class="field">
            <span class="field__label">매니저 이메일</span>
            <input
              ref="emailInputRef"
              v-model="managerEmail"
              class="field-input"
              :class="{ 'field-input--error': managerEmail.trim() && !isEmailValid }"
              type="email"
              placeholder="manager@company.com"
              autocomplete="email"
              required
            />
          </label>
          <p class="manager-error" :class="{ 'is-visible': managerEmail.trim() && !isEmailValid }">
            이메일 형식을 확인해 주세요.
          </p>
          <div class="manager-actions">
            <button type="button" class="btn ghost" @click="closeManagerModal">취소</button>
            <button type="submit" class="btn primary" :disabled="!isEmailValid || inviteSending">
              로그인 링크 보내기
            </button>
          </div>
        </form>
        <div v-else-if="showConfirmModal" class="confirm-body">
          <p class="confirm-title">다음 이메일로 로그인 링크를 보낼까요?</p>
          <p class="confirm-email">{{ pendingEmail }}</p>
          <div class="manager-actions">
            <button type="button" class="btn ghost" @click="closeConfirm">뒤로</button>
            <button type="button" class="btn primary" :disabled="inviteSending" @click="confirmSend">
              보내기
            </button>
          </div>
          <p v-if="inviteError" class="manager-error is-visible">{{ inviteError }}</p>
        </div>
        <div v-else class="sent-body">
          <p class="sent-title">로그인 링크를 보냈습니다.</p>
          <p class="sent-email">{{ pendingEmail }}</p>
          <div class="manager-actions">
            <button type="button" class="btn primary" @click="closeManagerModal">확인</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showWithdrawModal" class="manager-modal" role="dialog" aria-modal="true" aria-label="회원 탈퇴">
      <div class="manager-modal__backdrop" @click="closeWithdrawModal"></div>
      <div class="manager-modal__card ds-surface">
        <div class="manager-modal__head">
          <div>
            <h3>회원 탈퇴</h3>
            <p>탈퇴 전 아래 내용을 확인해 주세요.</p>
          </div>
          <button type="button" class="modal-close" @click="closeWithdrawModal" aria-label="닫기">닫기</button>
        </div>
        <div class="withdraw-body">
          <p>
            회원 탈퇴 전, 다음 내용을 확인해주세요.
            <br />
            대표 판매자에게 탈퇴 사유를 미리 공지해주세요.
            <br />
            탈퇴 시 일부 데이터는 복원이 불가할 수 있어요.
            <br />
            탈퇴 후 재가입시 대표 판매자로부터 이메일 초대를 다시 받아 가입해야 해요.
            <br />
            회원 탈퇴를 진행하시겠습니까?
          </p>
          <div class="manager-actions">
            <button type="button" class="btn ghost" @click="closeWithdrawModal">취소</button>
            <button type="button" class="btn danger" :disabled="withdrawProcessing" @click="confirmWithdraw">
              확인
            </button>
          </div>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.manager-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 18px;
}

.manager-head {
  display: flex;
  justify-content: flex-end;
}

.manager-add {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 16px;
  font-weight: 800;
  cursor: pointer;
}

.manager-body {
  display: flex;
}

.manager-list {
  min-width: 0;
}

.manager-title {
  margin: 0 0 10px;
  font-weight: 800;
  color: var(--text-strong);
}

.manager-items {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 10px;
}

.manager-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface);
}

.manager-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  color: var(--text-strong);
}

.manager-meta.secondary {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
  font-weight: 700;
}

.manager-name {
  font-weight: 900;
}

.manager-role {
  font-size: 0.85rem;
  color: var(--text-muted);
  font-weight: 700;
}

.manager-email {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.manager-status {
  border: 1px solid var(--border-color);
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 800;
  color: var(--text-strong);
  background: var(--surface-weak);
}

.manager-empty {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.manager-modal {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
  padding: 24px;
  pointer-events: auto;
}

.manager-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  z-index: 0;
}

.manager-modal__card {
  position: relative;
  z-index: 1;
  pointer-events: auto;
  width: min(520px, 100%);
  border-radius: 16px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
}

.withdraw-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 18px;
}

.withdraw-head h3 {
  margin: 0 0 6px;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.withdraw-head p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.withdraw-action {
  align-self: center;
  width: 33%;
  min-width: 160px;
  max-width: 240px;
}

.withdraw-body p {
  margin: 0;
  color: var(--text-strong);
  font-weight: 700;
  line-height: 1.6;
}

.manager-modal__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.manager-modal__head h3 {
  margin: 0 0 6px;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.manager-modal__head p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.modal-close {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 6px 10px;
  font-weight: 900;
  cursor: pointer;
}

.modal-close:hover,
.modal-close:focus-visible {
  border-color: var(--text-strong);
  outline: none;
}

.social-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.social-btn {
  width: 100%;
  border-radius: 12px;
  padding: 12px;
  border: 1px solid var(--border-color);
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 900;
  cursor: pointer;
  background: var(--surface);
}

.brand-ico {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
}

.social-btn.kakao {
  background: #fee500;
  color: #1f2937;
  border-color: transparent;
}

.social-btn.kakao .brand-ico {
  background: rgba(15, 23, 42, 0.08);
}

.social-btn.naver {
  background: #2db400;
  color: #fff;
  border-color: transparent;
}

.social-btn.naver .brand-ico {
  background: rgba(255, 255, 255, 0.2);
}

.social-btn.google {
  background: #fff;
  color: var(--text-strong);
}

.social-btn.google .brand-ico {
  background: var(--surface-weak);
}

.seller-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.seller-card__top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.seller-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: var(--surface-weak);
  color: var(--text-strong);
  font-weight: 900;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.seller-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.seller-name {
  margin: 0;
  color: var(--text-strong);
  font-weight: 900;
  font-size: 16px;
}

.seller-email {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.seller-info {
  margin: 0;
  display: grid;
  gap: 10px;
}

.seller-info__row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.seller-info__row dt {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.seller-info__row dd {
  margin: 0;
  color: var(--text-strong);
  font-weight: 800;
}

.seller-logout {
  align-self: flex-start;
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 16px;
  font-weight: 800;
  cursor: pointer;
}

.seller-logout:hover,
.seller-logout:focus-visible {
  border-color: var(--text-strong);
  outline: none;
}

.manager-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.field-input {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
  width: 100%;
}

.field-input::placeholder {
  color: var(--text-muted);
}

.field-input--error {
  border-color: rgba(239, 68, 68, 0.8);
}

.manager-error {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 700;
  color: #ef4444;
  min-height: 18px;
  visibility: hidden;
}

.manager-error.is-visible {
  visibility: visible;
}

.manager-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-body,
.sent-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 6px;
}

.confirm-title,
.sent-title {
  margin: 0;
  font-weight: 800;
  color: var(--text-strong);
}

.confirm-email,
.sent-email {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
  word-break: break-all;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-weak);
}

.btn {
  border-radius: 999px;
  padding: 10px 16px;
  font-weight: 800;
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  cursor: pointer;
}

.btn.primary {
  background: var(--primary-color);
  color: #fff;
  border-color: transparent;
}

.btn.danger {
  background: #ef4444;
  color: #fff;
  border-color: transparent;
}

.btn.ghost {
  border-color: var(--border-color);
  color: var(--text-muted);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
