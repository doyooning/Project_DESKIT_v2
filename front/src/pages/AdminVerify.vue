<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { setAuthUser } from '../lib/auth'

type PendingAdmin = {
  name: string
  email: string
  phoneMasked: string
}

type VerifyResponse = {
  name: string
  email: string
  role: string
}

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const router = useRouter()
const pending = ref<PendingAdmin | null>(null)

const form = reactive({
  code: '',
  message: '',
  sentMessage: '',
})

const loadPending = async () => {
  const response = await fetch(`${apiBase}/admin/auth/pending`, {
    credentials: 'include',
  })

  if (!response.ok) {
    form.message = '관리자 인증 정보를 찾을 수 없습니다.'
    return
  }

  pending.value = (await response.json()) as PendingAdmin
}

const sendCode = async () => {
  const response = await fetch(`${apiBase}/admin/auth/send`, {
    method: 'POST',
    credentials: 'include',
  })

  if (!response.ok) {
    form.sentMessage = '인증번호 재전송에 실패했습니다.'
    return
  }

  const data = await response.json()
  form.sentMessage = `인증번호가 발송되었습니다. (개발용 코드: ${data.code})`
}

const storeAuthUser = (payload: VerifyResponse) => {
  const authUser = {
    name: payload.name ?? '관리자',
    email: payload.email ?? '',
    signupType: '관리자',
    memberCategory: '관리자',
    role: payload.role ?? '',
  }

  setAuthUser(authUser)
  // localStorage.setItem('deskit-user', JSON.stringify(authUser))
  // localStorage.setItem('deskit-auth', 'admin')
}

const verifyCode = async () => {
  if (!form.code.trim()) {
    form.message = '인증번호를 입력해주세요.'
    return
  }

  const response = await fetch(`${apiBase}/admin/auth/verify`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify({ code: form.code }),
  })

  if (!response.ok) {
    const errorText = await response.text()
    form.message = errorText || '인증에 실패했습니다.'
    return
  }

  const payload = (await response.json()) as VerifyResponse
  storeAuthUser(payload)
  form.message = ''
  await router.push('/admin')
}

const backToLogin = () => {
  router.push('/login').catch(() => {})
}

onMounted(() => {
  loadPending()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="관리자 2차 인증" />
    <div class="admin-auth-wrap">
      <section class="admin-auth-card">
        <div class="card-top">
          <p class="lead">관리자 계정 확인을 위해 인증번호를 입력해주세요.</p>
          <p v-if="pending?.phoneMasked" class="sub">
            등록된 번호 <strong>{{ pending.phoneMasked }}</strong>로 인증번호가 전송됩니다.
          </p>
        </div>

        <div v-if="pending" class="auth-body">
          <label class="field">
            <span>인증번호</span>
            <div class="field-row">
              <input v-model="form.code" type="text" placeholder="6자리 인증번호" />
              <button type="button" class="btn primary" @click="verifyCode">인증하기</button>
            </div>
          </label>

          <button type="button" class="btn ghost" @click="sendCode">인증번호 재전송</button>
        </div>

        <div v-else class="alert">
          <p>인증 세션이 없습니다. 다시 로그인해주세요.</p>
          <button type="button" class="btn" @click="backToLogin">로그인으로 이동</button>
        </div>

        <p v-if="form.sentMessage" class="message success">{{ form.sentMessage }}</p>
        <p v-if="form.message" class="message">{{ form.message }}</p>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.admin-auth-wrap {
  max-width: 560px;
  margin: 0 auto;
  padding-top: 12px;
}

.admin-auth-card {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.03);
}

.card-top {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lead {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.sub {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.sub strong {
  color: var(--text-strong);
  font-weight: 900;
}

.auth-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-weight: 700;
  color: var(--text-muted);
}

.field-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.field-row input {
  flex: 1;
  min-width: 180px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px 12px;
  font-weight: 600;
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

.btn.ghost {
  background: transparent;
}

.alert {
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  padding: 14px;
  font-weight: 700;
  color: var(--text-strong);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message {
  margin: 0;
  font-weight: 700;
  color: var(--text-muted);
}

.message.success {
  color: #15803d;
}

@media (max-width: 600px) {
  .field-row {
    flex-direction: column;
  }

  .field-row input {
    width: 100%;
  }
}
</style>

