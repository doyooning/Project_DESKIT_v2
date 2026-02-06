<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'

type RegisterCompleteState = {
  memberType?: string
  message?: string
}

const router = useRouter()
const route = useRoute()

const memberType = ref('GENERAL')
const message = ref('회원가입이 완료되었습니다.')

const isSeller = computed(() => {
  const normalized = memberType.value.trim().toUpperCase()
  return normalized === 'SELLER' || normalized === 'ROLE_SELLER' || normalized === '판매자'
})

const headline = computed(() => (isSeller.value ? '판매자 가입 완료' : '회원가입 완료'))
const description = computed(() =>
  isSeller.value ? '로그인 후 서비스를 이용해주세요.' : 'DESKIT의 다양한 서비스를 이용해보세요.',
)

const hydrateMessage = () => {
  const stored = sessionStorage.getItem('registerComplete')
  if (stored) {
    try {
      const payload = JSON.parse(stored) as RegisterCompleteState
      if (payload.memberType) memberType.value = payload.memberType
      if (payload.message) message.value = payload.message
    } catch (error) {
      console.warn('failed to parse registerComplete state', error)
    }
  }

  if (typeof route.query.type === 'string' && route.query.type.trim()) {
    memberType.value = route.query.type
  }
  if (typeof route.query.message === 'string' && route.query.message.trim()) {
    message.value = route.query.message
  }

  if (isSeller.value && !message.value.trim()) {
    message.value = '회원가입이 완료되었습니다.'
  }
}

const goHome = () => {
  router.push('/').catch(() => {})
}

onMounted(() => {
  hydrateMessage()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" :title="headline" />

    <section class="complete-card ds-surface">
      <div class="complete-icon" aria-hidden="true">OK</div>
      <div class="complete-body">
        <h2>{{ message }}</h2>
        <p>{{ description }}</p>
      </div>
      <div class="complete-actions">
        <button type="button" class="btn primary" @click="goHome">메인 페이지로 이동</button>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.complete-card {
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 20px;
  display: grid;
  gap: 16px;
  justify-items: center;
  text-align: center;
}

.complete-icon {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
  display: grid;
  place-items: center;
  font-size: 28px;
}

.complete-body h2 {
  margin: 0 0 6px;
  font-size: 1.2rem;
  font-weight: 900;
  color: var(--text-strong);
}

.complete-body p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.complete-actions {
  display: flex;
  justify-content: center;
}

.btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  padding: 10px 16px;
  border-radius: 12px;
  font-weight: 900;
  cursor: pointer;
}

.btn.primary {
  background: var(--primary-color);
  border-color: transparent;
  color: #fff;
}
</style>

