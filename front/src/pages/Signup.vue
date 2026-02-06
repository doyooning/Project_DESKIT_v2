<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { logout } from '../lib/auth'

type PendingSignup = {
  username?: string
  name: string
  email: string
}

type JobOption = {
  value: string
  label: string
}

type MbtiOption = {
  value: string
  label: string
}

type AgreementKey =
  | 'serviceTerms'
  | 'privacyPolicy'
  | 'privacyConsignment'
  | 'ageOver14'
  | 'marketing'

type Policy = {
  key: AgreementKey
  title: string
  required: boolean
  content: string
}

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const oauthBase = import.meta.env.VITE_OAUTH_BASE_URL || window.location.origin
const pending = ref<PendingSignup | null>(null)
const signupToken = ref('')
const inviteToken = ref('')
const inviteError = ref('')
const router = useRouter()

const form = reactive({
  phoneNumber: '',
  verificationCode: '',
  memberType: 'GENERAL',
  mbti: 'NONE',
  jobCategory: 'NONE',
  businessNumber: '',
  companyName: '',
  description: '',
  planFileBase64: '',
  planFileName: '',
  message: '',
  isVerified: false,
})

const normalizePhoneDigits = (value: string) => value.replace(/\D/g, '').slice(0, 11)
const normalizeBusinessDigits = (value: string) => value.replace(/\D/g, '').slice(0, 10)

const formatPhoneNumber = (digits: string) => {
  if (digits.length <= 3) {
    return digits
  }
  if (digits.length <= 7) {
    return `${digits.slice(0, 3)}-${digits.slice(3)}`
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
}

const phoneDigits = computed(() => normalizePhoneDigits(form.phoneNumber))
const businessDigits = computed(() => normalizeBusinessDigits(form.businessNumber))

const handlePhoneInput = (event: Event) => {
  const input = event.target as HTMLInputElement
  const digits = normalizePhoneDigits(input.value)
  form.phoneNumber = formatPhoneNumber(digits)
}

const formatBusinessNumber = (digits: string) => {
  if (digits.length <= 3) {
    return digits
  }
  if (digits.length <= 5) {
    return `${digits.slice(0, 3)}-${digits.slice(3)}`
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`
}

const handleBusinessInput = (event: Event) => {
  const input = event.target as HTMLInputElement
  const digits = normalizeBusinessDigits(input.value)
  form.businessNumber = formatBusinessNumber(digits)
}

const agreements = reactive<Record<AgreementKey, boolean>>({
  serviceTerms: false,
  privacyPolicy: false,
  privacyConsignment: false,
  ageOver14: false,
  marketing: false,
})

const policies: Policy[] = [
  {
    key: 'serviceTerms',
    title: '서비스 이용약관에 동의합니다.',
    required: true,
    content: `
## 서비스 이용약관
### 제1조 (목적)
이 약관은 “DESKIT”(이하 “회사”)이 제공하는 데스크테리어 관련 라이브 커머스 플랫폼 서비스(이하 “서비스”)의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.

### 제2조 (약관의 효력 및 변경)
1. 본 약관은 서비스 화면에 게시하거나 기타의 방법으로 공지함으로써 효력이 발생합니다.
2. 회사는 관련 법령을 위배하지 않는 범위에서 약관을 변경할 수 있으며, 변경 시 서비스 내 공지 또는 이메일 등을 통해 사전에 안내합니다.
3. 이용자가 변경된 약관에 동의하지 않을 경우, 이용자는 서비스 이용을 중단하고 탈퇴할 수 있습니다.

### 제3조 (회원가입 및 이용계약)
1. 회원가입은 회사가 정한 가입 양식에 따라 정보를 제공하고 이용자가 약관에 동의함으로써 체결됩니다.
2. 회원은 본 약관 및 관련 정책을 준수해야 합니다.
3. 본 서비스는 만 14세 이상부터 회원가입이 가능합니다.

### 제4조 (회원의 권리와 의무)
1. 회원은 계정 정보를 안전하게 관리할 의무가 있습니다.
2. 회원은 타인의 권리를 침해하거나 불법적인 목적으로 서비스를 이용할 수 없습니다.
3. 회사는 회원이 본 약관을 위반할 경우 서비스 이용을 제한할 수 있습니다.

### 제5조 (서비스의 제공 및 변경)
1. 회사는 안정적인 서비스를 제공하기 위해 노력하며, 서비스 내용은 사전 공지 후 변경될 수 있습니다.
2. 회사는 정기 점검, 긴급 장애, 법령상 사유 등으로 서비스 제공을 일시 중단할 수 있습니다.

### 제6조 (계약 해지 및 이용 제한)
1. 회원은 언제든지 탈퇴를 요청할 수 있으며, 회사는 관련 법령에 따라 처리합니다.
2. 회사는 회원이 본 약관을 위반할 경우 사전 통지 후 이용 제한 또는 계약 해지를 할 수 있습니다.

### 제7조 (면책조항)
1. 회사는 천재지변, 시스템 장애, 회원 과실 등 불가항력적 사유로 인한 서비스 중단이나 손해에 대해 책임을 지지 않습니다.
2. 회사는 회원 간 거래, 콘텐츠 게시 등에서 발생하는 민·형사상 문제에 대해 책임을 지지 않습니다.

### 제8조 (분쟁 해결)
1. 서비스 이용과 관련하여 발생한 분쟁은 회사와 이용자 간 합의로 해결하며, 합의가 되지 않을 경우 대한민국 법을 적용합니다.
2. 소송이 제기될 경우 회사 본사 소재지를 관할하는 법원을 관할 법원으로 합니다.
    `,
  },
  {
    key: 'privacyPolicy',
    title: '개인정보 처리방침에 동의합니다.',
    required: true,
    content: `
## 개인정보 처리 방침
### 제1조 (목적)
이 방침은 회사가 서비스 이용약관, 개인정보처리방침 등 이용 약관을 개정할 경우 이용자에게 고지하고, 투명하게 정보를 처리하기 위해 규정합니다.

### 제2조 (개정 공지)
1. 약관 개정 시, 회사는 변경 내용과 시행일을 서비스 내 공지, 이메일, 푸시 알림 등으로 안내합니다.
2. 이용자는 공지일 기준 7일 이내에 이의를 제기할 수 있으며, 이의가 없는 경우 개정에 동의한 것으로 간주됩니다.

### 제3조 (개정의 효력 발생)
1. 회사가 공지한 시행일로부터 효력이 발생합니다.
2. 이용자가 개정된 약관에 동의하지 않을 경우 서비스 이용을 중단하고 탈퇴할 수 있습니다.
    `,
  },
  {
    key: 'privacyConsignment',
    title: '개인정보 처리 위탁 약관에 동의합니다.',
    required: true,
    content: `
## 개인정보처리 위탁 약관
### 제1조 (목적)
본 약관은 회사가 서비스 제공과 관련하여 이용자의 개인정보를 안전하게 처리하고, 일부 업무를 외부 업체에 위탁할 경우 필요한 사항을 규정함을 목적으로 합니다.

### 제2조 (위탁 업무의 범위)
회사는 서비스 운영, 결제 처리, 고객 상담 등 일부 업무를 외부 전문 업체에 위탁할 수 있습니다.

### 제3조 (위탁 업체와 보호 조치)
1. 회사는 개인정보 보호법 등 관련 법령에 따라 위탁 업체와 계약을 체결하고, 개인정보 보호를 위한 기술적·관리적 조치를 취합니다.
2. 위탁 업체는 위탁 받은 목적 외에 개인정보를 처리할 수 없습니다.

### 제4조 (이용자의 권리와 조치)
이용자는 위탁업체의 개인정보 처리와 관련하여 회사에 열람, 정정, 삭제 등을 요청할 수 있으며, 회사는 관련 요청을 신속히 처리합니다.
    `,
  },
  {
    key: 'ageOver14',
    title: '만 14세 이상입니다.',
    required: true,
    content: '',
  },
  {
    key: 'marketing',
    title: '마케팅 및 알림 정보 제공에 동의합니다.',
    required: false,
    content: `
## 마케팅 정보 제공 동의
### 제1조 (목적)
회사는 이용자에게 맞춤형 서비스, 이벤트, 프로모션 정보를 제공하기 위해 마케팅 정보 수신 동의를 받습니다.

### 제2조 (동의 범위)
이용자는 이메일, SMS, 앱 푸시 알림 등을 통해 마케팅 정보를 받을 수 있으며, 선택적으로 동의할 수 있습니다.

### 제3조 (동의 철회)
1. 이용자는 언제든지 마케팅 정보 수신 동의를 철회할 수 있습니다.
2. 철회 시 즉시 마케팅 정보 수신이 중단되며, 서비스 이용에는 영향을 미치지 않습니다.

### 제4조 (보관 및 이용)
회사는 동의 받은 마케팅 정보만 사용하며, 관련 법령에 따라 안전하게 보관합니다.
    `,
  },
]

const mbtiOptions: MbtiOption[] = [
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

const jobOptions: JobOption[] = [
  { value: 'NONE', label: '선택 안함' },
  { value: 'CREATIVE_TYPE', label: '크리에이티브' },
  { value: 'FLEXIBLE_TYPE', label: '프리랜서/유연근무' },
  { value: 'EDU_RES_TYPE', label: '교육/연구' },
  { value: 'MED_PRO_TYPE', label: '의료/전문직' },
  { value: 'ADMIN_PLAN_TYPE', label: '기획/관리' },
]

const isInviteSignup = computed(() => !!inviteToken.value)

const requiredAgreed = computed(
  () =>
    agreements.serviceTerms &&
    agreements.privacyPolicy &&
    agreements.privacyConsignment &&
    agreements.ageOver14,
)

const allRequiredAgreement = computed({
  get: () => requiredAgreed.value,
  set: (value: boolean) => {
    agreements.serviceTerms = value
    agreements.privacyPolicy = value
    agreements.privacyConsignment = value
    agreements.ageOver14 = value
  },
})

const activePolicy = ref<Policy | null>(null)

const openPolicy = (policy: Policy) => {
  activePolicy.value = policy
}

const closePolicy = () => {
  activePolicy.value = null
}

const escapeHtml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const renderMarkdown = (value: string) => {
  if (!value) {
    return ''
  }

  const lines = value.replace(/\r/g, '').split('\n')
  const htmlParts: string[] = []
  let inOrderedList = false
  let paragraphParts: string[] = []

  const closeList = () => {
    if (inOrderedList) {
      htmlParts.push('</ol>')
      inOrderedList = false
    }
  }

  const flushParagraph = () => {
    if (paragraphParts.length) {
      htmlParts.push(`<p>${paragraphParts.join(' ')}</p>`)
      paragraphParts = []
    }
  }

  for (const line of lines) {
    const trimmed = line.trim()

    if (!trimmed) {
      flushParagraph()
      closeList()
      continue
    }

    if (trimmed.startsWith('### ')) {
      flushParagraph()
      closeList()
      htmlParts.push(`<h3>${escapeHtml(trimmed.slice(4))}</h3>`)
      continue
    }

    if (trimmed.startsWith('## ')) {
      flushParagraph()
      closeList()
      htmlParts.push(`<h2>${escapeHtml(trimmed.slice(3))}</h2>`)
      continue
    }

    const listMatch = trimmed.match(/^\d+\.\s+(.*)$/)
    if (listMatch) {
      flushParagraph()
      if (!inOrderedList) {
        htmlParts.push('<ol>')
        inOrderedList = true
      }
      const itemText = listMatch[1] ?? ''
      htmlParts.push(`<li>${escapeHtml(itemText)}</li>`)
      continue
    }

    paragraphParts.push(escapeHtml(trimmed))
  }

  flushParagraph()
  closeList()

  return htmlParts.join('\n')
}

const activePolicyHtml = computed(() =>
  activePolicy.value ? renderMarkdown(activePolicy.value.content) : '',
)

const loadPending = async () => {
  if (!signupToken.value) {
    form.message = '로그인이 필요합니다.'
    return
  }

  const response = await fetch(`${apiBase}/signup/social/pending`, {
    headers: { Authorization: `Bearer ${signupToken.value}` },
    credentials: 'include',
  })

  if (!response.ok) {
    form.message = '가입 정보를 불러오지 못했습니다.'
    return
  }

  pending.value = (await response.json()) as PendingSignup
}

const sendCode = async () => {
  if (!signupToken.value) {
    form.message = '로그인이 필요합니다.'
    return
  }

  const digits = phoneDigits.value
  if (digits.length !== 11) {
    form.message = '전화번호를 11자리로 입력해주세요.'
    return
  }

  const response = await fetch(`${apiBase}/signup/social/phone/send`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${signupToken.value}`,
    },
    credentials: 'include',
    body: JSON.stringify({ phoneNumber: digits }),
  })

  if (!response.ok) {
    form.message = '인증번호 전송에 실패했습니다.'
    return
  }

  const data = await response.json()
  form.message = `인증번호가 발송되었습니다. (개발용 코드: ${data.code})`
}

const verifyCode = async () => {
  if (!signupToken.value) {
    form.message = '로그인이 필요합니다.'
    return
  }

  const digits = phoneDigits.value
  if (digits.length !== 11) {
    form.message = '전화번호를 11자리로 입력해주세요.'
    return
  }

  const response = await fetch(`${apiBase}/signup/social/phone/verify`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${signupToken.value}`,
    },
    credentials: 'include',
    body: JSON.stringify({
      phoneNumber: digits,
      code: form.verificationCode,
    }),
  })

  if (!response.ok) {
    form.message = '인증에 실패했습니다.'
    form.isVerified = false
    return
  }

  form.isVerified = true
  form.message = '전화번호 인증이 완료되었습니다.'
}

const submitSignup = async () => {
  if (!signupToken.value) {
    form.message = '로그인이 필요합니다.'
    return
  }

  if (!requiredAgreed.value) {
    form.message = '필수 약관에 동의해주세요.'
    return
  }

  const digits = phoneDigits.value
  if (digits.length !== 11) {
    form.message = '전화번호를 11자리로 입력해주세요.'
    return
  }

  if (form.memberType === 'SELLER') {
    if (businessDigits.value.length !== 10) {
      form.message = '사업자등록번호를 10자리로 입력해주세요.'
      return
    }
    if (form.companyName.trim().length === 0) {
      form.message = '사업자명을 입력해주세요.'
      return
    }
  }

  const response = await fetch(`${apiBase}/signup/social/complete`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${signupToken.value}`,
    },
    credentials: 'include',
    body: JSON.stringify({
      memberType: form.memberType,
      phoneNumber: digits,
      mbti: form.mbti,
      jobCategory: form.jobCategory,
      businessNumber: form.memberType === 'SELLER' ? businessDigits.value : form.businessNumber,
      companyName: form.companyName,
      description: form.description,
      planFileBase64: form.planFileBase64,
      inviteToken: inviteToken.value,
      isAgreed: agreements.marketing,
    }),
  })

  if (!response.ok) {
    const errorText = await response.text()
    form.message = errorText || '회원가입에 실패했습니다.'
    return
  }

  await response.text()
  const completionMessage = '회원가입이 완료되었습니다.'

  sessionStorage.setItem(
    'registerComplete',
    JSON.stringify({
      memberType: form.memberType,
      message: completionMessage,
    }),
  )
  sessionStorage.removeItem('signupToken')
  sessionStorage.removeItem('inviteToken')
  logout()
  router.push('/signup/complete').catch(() => {})
}

const handlePlanFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    form.planFileBase64 = ''
    form.planFileName = ''
    return
  }

  form.planFileName = file.name
  const reader = new FileReader()
  reader.onload = () => {
    form.planFileBase64 = typeof reader.result === 'string' ? reader.result : ''
  }
  reader.readAsDataURL(file)
}

const initializeToken = () => {
  const params = new URLSearchParams(window.location.search)
  const token = params.get('token')
  if (token) {
    signupToken.value = token
    sessionStorage.setItem('signupToken', token)
    window.history.replaceState({}, '', '/signup')
    return
  }

  const storedToken = sessionStorage.getItem('signupToken')
  if (storedToken) {
    signupToken.value = storedToken
  }
}

const initializeInviteToken = () => {
  const params = new URLSearchParams(window.location.search)
  const token = params.get('invite')
  if (token) {
    inviteToken.value = token
    sessionStorage.setItem('inviteToken', token)
    window.history.replaceState({}, '', '/signup')
    form.memberType = 'SELLER'
    return
  }

  const storedToken = sessionStorage.getItem('inviteToken')
  if (storedToken) {
    inviteToken.value = storedToken
    form.memberType = 'SELLER'
  }
}

const validateInviteToken = async () => {
  if (!inviteToken.value) {
    return
  }

  const response = await fetch(
    `${apiBase}/invitations/validate?token=${encodeURIComponent(inviteToken.value)}`,
    { credentials: 'include' },
  )

  if (!response.ok) {
    const errorText = await response.text()
    inviteError.value = errorText || '초대 토큰이 유효하지 않습니다.'
  }
}

const startLogin = (provider: 'naver' | 'google' | 'kakao') => {
  window.location.href = `${oauthBase}/oauth2/authorization/${provider}`
}

onMounted(() => {
  initializeInviteToken()
  validateInviteToken()
  initializeToken()
  loadPending()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="회원가입" />
    <div class="signup-wrap">
      <section class="signup-card">
        <div v-if="inviteError" class="alert alert-error">
          {{ inviteError }}
        </div>

        <div v-if="!signupToken" class="alert">
          <p>회원가입을 진행하려면 소셜 로그인이 필요합니다.</p>
          <div class="social-row">
            <button type="button" class="btn" @click="startLogin('naver')">네이버 로그인</button>
            <button type="button" class="btn" @click="startLogin('google')">구글 로그인</button>
            <button type="button" class="btn" @click="startLogin('kakao')">카카오 로그인</button>
          </div>
        </div>

        <div v-else class="signup-body">
          <div v-if="pending" class="pending">
            <p>이름: <strong>{{ pending.name }}</strong></p>
            <p>이메일: <strong>{{ pending.email }}</strong></p>
          </div>

          <div class="section">
            <h3>전화번호 인증</h3>
            <div class="field-row">
              <input
                v-model="form.phoneNumber"
                type="text"
                inputmode="numeric"
                autocomplete="tel"
                maxlength="13"
                placeholder="전화번호"
                @input="handlePhoneInput"
              />
              <button type="button" class="btn" @click="sendCode">인증번호 받기</button>
            </div>
            <div class="field-row">
              <input v-model="form.verificationCode" type="text" placeholder="인증번호" />
              <button type="button" class="btn" @click="verifyCode">인증하기</button>
            </div>
            <p v-if="form.isVerified" class="success">인증 완료</p>
          </div>

          <div class="section">
            <h3>회원 유형</h3>
            <select v-model="form.memberType" :disabled="isInviteSignup">
              <option value="GENERAL">일반 회원</option>
              <option value="SELLER">판매자</option>
            </select>
            <p v-if="isInviteSignup" class="hint">초대받은 판매자는 판매자 유형으로만 가입할 수 있습니다.</p>
          </div>

          <div v-if="form.memberType === 'GENERAL'" class="section">
            <h3>추가 정보</h3>
            <label class="field">
              <span>MBTI</span>
              <select v-model="form.mbti">
                <option v-for="option in mbtiOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
            <label class="field">
              <span>직업</span>
              <select v-model="form.jobCategory">
                <option v-for="option in jobOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
          </div>

          <div v-else-if="form.memberType === 'SELLER'" class="section">
            <h3>판매자 정보</h3>
            <label class="field">
              <span>사업자등록번호</span>
              <input
                v-model="form.businessNumber"
                type="text"
                inputmode="numeric"
                maxlength="12"
                placeholder="사업자등록번호"
                @input="handleBusinessInput"
              />
            </label>
            <label class="field">
              <span>사업자명</span>
              <input v-model="form.companyName" type="text" placeholder="사업자명" />
            </label>
            <template v-if="!isInviteSignup">
              <label class="field">
                <span>사업 설명</span>
                <textarea v-model="form.description" placeholder="사업 설명 (선택)"></textarea>
              </label>
              <label class="field">
                <span>사업계획서</span>
                <input type="file" @change="handlePlanFileChange" />
                <p v-if="form.planFileName" class="file-name">선택된 파일: {{ form.planFileName }}</p>
              </label>
            </template>
            <p v-else class="hint">초대받은 판매자는 사업 설명과 사업계획서를 제출하지 않습니다.</p>
          </div>

          <div class="section">
            <h3>약관 동의</h3>
            <label class="checkbox">
              <input type="checkbox" v-model="allRequiredAgreement" />
              필수 약관 전체 동의
            </label>
            <div class="terms-list">
              <div v-for="policy in policies" :key="policy.key" class="terms-item">
                <label class="terms-checkbox">
                  <input type="checkbox" v-model="agreements[policy.key]" />
                  <span class="terms-title">{{ policy.title }}</span>
                  <span class="terms-badge" :class="{ required: policy.required }">
                    {{ policy.required ? '필수' : '선택' }}
                  </span>
                </label>
                <button
                  v-if="policy.content.length"
                  type="button"
                  class="terms-link"
                  @click="openPolicy(policy)"
                >
                  약관 상세 보기
                </button>
              </div>
            </div>
          </div>

          <button type="button" class="btn primary" @click="submitSignup">회원가입 완료</button>
        </div>

        <p v-if="form.message" class="message">{{ form.message }}</p>
      </section>
    </div>

    <div v-if="activePolicy" class="modal">
      <div class="modal-backdrop" @click="closePolicy"></div>
      <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="policy-title">
        <div class="modal-header">
          <div>
            <p class="modal-label">{{ activePolicy.required ? '필수' : '선택' }}</p>
            <h4 id="policy-title" class="modal-title">{{ activePolicy.title }}</h4>
          </div>
          <button type="button" class="modal-close" @click="closePolicy">닫기</button>
        </div>
        <div class="modal-body">
          <div class="modal-text" v-html="activePolicyHtml"></div>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.signup-wrap {
  max-width: 680px;
  margin: 0 auto;
  font-family: 'Pretendard', 'Noto Sans KR', 'Apple SD Gothic Neo', 'Malgun Gothic',
    'Nanum Gothic', 'Segoe UI', sans-serif;
}

.signup-card {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.alert {
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  padding: 14px;
  font-weight: 700;
  color: var(--text-strong);
}

.alert-error {
  border-color: #e36a6a;
  background: #fff5f5;
  color: #b42318;
}

.social-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.signup-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pending {
  border-radius: 12px;
  padding: 12px 14px;
  background: rgba(34, 197, 94, 0.08);
  border: 1px solid rgba(34, 197, 94, 0.2);
  font-weight: 700;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.field-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-weight: 700;
  color: var(--text-muted);
}

.field input,
.field select,
.field textarea,
.field-row input,
.field-row select,
.field-row textarea,
.section select {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px 12px;
  font-weight: 600;
  min-width: 220px;
}

.field textarea {
  min-height: 90px;
  resize: vertical;
}

.hint {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin: 0;
}

.success {
  font-weight: 800;
  color: #15803d;
}

.file-name {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--text-muted);
}

.terms-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 0.9rem;
}

.terms-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: none;
  border-radius: 0;
  padding: 0;
  background: transparent;
}

.terms-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--text-muted);
}

.terms-title {
  color: var(--text-strong);
}

.terms-badge {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 0.75rem;
  border: 1px solid var(--border-color);
  color: var(--text-muted);
}

.terms-badge.required {
  border-color: rgba(37, 99, 235, 0.4);
  color: #1d4ed8;
  background: rgba(37, 99, 235, 0.1);
}

.terms-link {
  border: none;
  background: transparent;
  color: var(--primary-color);
  font-weight: 800;
  cursor: pointer;
  padding: 0;
}

.terms-link:hover {
  text-decoration: underline;
}

.modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
}

.modal-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
}

.modal-card {
  position: relative;
  z-index: 1;
  background: var(--surface);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 18px;
  width: min(90vw, 520px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.modal-label {
  margin: 0 0 4px;
  font-size: 0.75rem;
  font-weight: 800;
  color: var(--text-muted);
}

.modal-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.modal-close {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-weak);
  padding: 6px 12px;
  font-weight: 800;
  cursor: pointer;
}

.modal-body {
  max-height: 50vh;
  overflow: auto;
  padding-right: 6px;
}


.modal-text {
  line-height: 1.6;
  font-weight: 600;
  color: var(--text-muted);
}

.modal-text h2 {
  margin: 0 0 10px;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.modal-text h3 {
  margin: 14px 0 8px;
  font-size: 0.95rem;
  font-weight: 800;
  color: var(--text-strong);
}

.modal-text p {
  margin: 0 0 10px;
}

.modal-text ol {
  margin: 0 0 10px 18px;
  padding: 0;
}

.modal-text li {
  margin-bottom: 6px;
}

.modal-text *:last-child {
  margin-bottom: 0;
}
.btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  padding: 10px 14px;
  border-radius: 10px;
  font-weight: 800;
  cursor: pointer;
}

.btn.primary {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #fff;
}

.message {
  margin: 0;
  font-weight: 700;
  color: var(--text-muted);
}

@media (max-width: 600px) {
  .field-row {
    flex-direction: column;
  }

  .field input,
  .field select,
  .field textarea,
  .field-row input,
  .field-row select,
  .field-row textarea,
  .section select {
    width: 100%;
    min-width: 0;
  }

  .terms-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>


