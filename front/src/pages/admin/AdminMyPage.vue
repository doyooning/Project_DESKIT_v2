<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '../../components/PageContainer.vue'
import PageHeader from '../../components/PageHeader.vue'
import { getAuthUser, hydrateSessionUser, requestLogout } from '../../lib/auth'

type AdminInfo = {
  name: string
  email: string
  memberCategory: string
  role: string
  phone: string
  createdAt: string
}

const router = useRouter()
const user = ref<AdminInfo | null>(null)

const loadUser = () => {
  const parsed = getAuthUser()
  if (!parsed) {
    user.value = null
    return
  }
  user.value = {
    name: parsed.name || '',
    email: parsed.email || '',
    memberCategory: parsed.memberCategory || '',
    role: parsed.role || '',
    phone: parsed.phone || '',
    createdAt: parsed.createdAt || '',
  }
}

const display = computed(() => {
  const current = user.value
  return {
    name: current?.name || '관리자',
    email: current?.email || '',
    memberCategory: current?.memberCategory || '관리자',
    role: current?.role || '',
    phone: current?.phone || '',
    createdAt: current?.createdAt || '',
  }
})

const formatAdminRole = (value: string) => {
  const normalized = (value || '').trim().toUpperCase()
  if (normalized === 'ROLE_ADMIN') return '관리자'
  return value || '-'
}

const formatCreatedAt = (value: string) => {
  const normalized = (value || '').trim()
  if (!normalized) return '-'
  const parsed = new Date(normalized)
  if (Number.isNaN(parsed.getTime())) return normalized
  const yyyy = parsed.getFullYear()
  const mm = String(parsed.getMonth() + 1).padStart(2, '0')
  const dd = String(parsed.getDate()).padStart(2, '0')
  const hh = String(parsed.getHours()).padStart(2, '0')
  const mi = String(parsed.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

const handleLogout = async () => {
  const success = await requestLogout()
  if (success) {
    window.alert('로그아웃되었습니다.')
  }
  router.push('/').catch(() => {})
}

onMounted(() => {
  window.addEventListener('deskit-user-updated', loadUser)
  loadUser()
  hydrateSessionUser().then(() => {
    loadUser()
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('deskit-user-updated', loadUser)
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="관리자 마이페이지" />
    <section class="admin-card ds-surface">
      <div class="admin-meta">
        <p class="admin-name">{{ display.name }}</p>
        <p class="admin-email">{{ display.email }}</p>
      </div>
      <dl class="admin-info">
        <div class="admin-info__row">
          <dt>로그인 ID</dt>
          <dd>{{ display.email || '-' }}</dd>
        </div>
        <div class="admin-info__row">
          <dt>관리자 이름</dt>
          <dd>{{ display.name }}</dd>
        </div>
        <div class="admin-info__row">
          <dt>전화번호</dt>
          <dd>{{ display.phone || '-' }}</dd>
        </div>
        <div class="admin-info__row">
          <dt>권한</dt>
          <dd>{{ formatAdminRole(display.role) }}</dd>
        </div>
        <div class="admin-info__row">
          <dt>계정 생성일</dt>
          <dd>{{ formatCreatedAt(display.createdAt) }}</dd>
        </div>
      </dl>
      <button type="button" class="admin-logout" @click="handleLogout">로그아웃</button>
    </section>
  </PageContainer>
</template>

<style scoped>
.admin-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-name {
  margin: 0;
  color: var(--text-strong);
  font-weight: 900;
  font-size: 16px;
}

.admin-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.admin-email {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.admin-info {
  margin: 0;
  display: grid;
  gap: 10px;
}

.admin-info__row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.admin-info__row dt {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.admin-info__row dd {
  margin: 0;
  color: var(--text-strong);
  font-weight: 800;
}

.admin-logout {
  align-self: flex-start;
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 16px;
  font-weight: 800;
  cursor: pointer;
}

.admin-logout:hover,
.admin-logout:focus-visible {
  border-color: var(--text-strong);
  outline: none;
}
</style>
