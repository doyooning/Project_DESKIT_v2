<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { type DbProduct } from '../lib/products-data'
import { normalizeProducts } from '../api/products-normalizer'
import { getAuthUser, normalizeDisplayName, requestLogout, requestWithdraw } from '../lib/auth'

type UserInfo = {
  name: string
  email: string
  signupType: string
  memberCategory: string
  mbti: string
  job: string
  profileUrl: string
}

const EMPTY_USER: UserInfo = {
  name: '',
  email: '',
  signupType: '',
  memberCategory: '',
  mbti: '',
  job: '',
  profileUrl: '',
}

const router = useRouter()
const user = ref<UserInfo | null>(null)
const profileImageFailed = ref(false)
const recommendedProducts = ref<DbProduct[]>([])
const apiBase = (import.meta.env.VITE_API_BASE_URL || '').trim()
const preferencePrompt =
  '지금 MBTI(직업군)를 입력하고 나에게 맞춘 데스크테리어 상품 추천을 받아보세요!'

const loadUser = () => {
  const parsed = getAuthUser()
  if (!parsed) {
    user.value = null
    return
  }
  user.value = {
    name: normalizeDisplayName(parsed.name, '회원'),
    email: parsed.email || '',
    signupType: parsed.signupType || '',
    memberCategory: parsed.memberCategory || '',
    mbti: parsed.mbti || '',
    job: parsed.job || '',
    profileUrl: parsed.profileUrl || '',
  }
  profileImageFailed.value = false
}

const hasUser = computed(() => !!user.value)
const display = computed(() => {
  const current = user.value ?? EMPTY_USER
  return {
    ...current,
    name: normalizeDisplayName(current.name, '회원'),
  }
})
const hasPreference = computed(() => {
  const mbti = display.value.mbti?.trim()
  const job = display.value.job?.trim()
  if (!mbti || mbti === 'NONE') return false
  if (!job || job === 'NONE') return false
  return true
})

const initials = computed(() => {
  const name = display.value.name || ''
  if (!name.trim()) return 'D'
  const parts = name.trim().split(/\s+/)
  const first = parts[0] ?? ''
  const second = parts[1] ?? ''
  if (!second) return first.slice(0, 2).toUpperCase()
  return `${first[0] ?? ''}${second[0] ?? ''}`.toUpperCase()
})

const profileImageUrl = computed(() => (display.value.profileUrl || '').trim())
const showProfileImage = computed(() => !!profileImageUrl.value && !profileImageFailed.value)

const handleWithdraw = async () => {
  if (!hasUser.value) {
    router.push('/login').catch(() => {})
    return
  }
  if (!window.confirm('정말 회원 탈퇴를 진행하시겠습니까?')) return

  const result = await requestWithdraw()
  if (!result.ok) {
    window.alert(result.message || '회원 탈퇴를 진행할 수 없습니다.')
    return
  }

  window.alert(result.message || '회원 탈퇴가 완료되었습니다.')
  ;['deskit-user', 'deskit-auth', 'token'].forEach((key) => localStorage.removeItem(key))
  window.dispatchEvent(new Event('deskit-user-updated'))
  router.push('/').catch(() => {})
}

const handleLogout = async () => {
  const success = await requestLogout()
  if (success) {
    window.alert('로그아웃되었습니다.')
  }
  router.push('/').catch(() => {})
}

const loadRecommendations = async () => {
  recommendedProducts.value = []
  if (!hasUser.value || !hasPreference.value) return

  try {
    const response = await fetch(`${apiBase}/recommendations/deskterior`, {
      credentials: 'include',
    })
    if (!response.ok) return
    const payload = await response.json().catch(() => [])
    if (Array.isArray(payload)) {
      recommendedProducts.value = normalizeProducts(payload)
    }
  } catch (error) {
    console.error('recommendation load failed', error)
  }
}

onMounted(() => {
  loadUser()
  loadRecommendations()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="마이페이지" />
    <div class="mypage-wrap">
      <div v-if="!hasUser" class="login-alert">
        <p>로그인이 필요합니다. 소셜 로그인 후 다시 접속해 주세요.</p>
        <button type="button" class="btn" @click="router.push('/login')">로그인하기</button>
      </div>

      <section v-if="hasUser" class="profile-banner">
        <div class="profile-top">
          <div class="avatar">
            <img
              v-if="showProfileImage"
              :src="profileImageUrl"
              :alt="`${display.name} 프로필`"
              @error="profileImageFailed = true"
            />
            <span v-else>{{ initials }}</span>
          </div>
          <div class="profile-meta">
            <div class="name-row">
              <p class="name">{{ display.name }}</p>
            </div>
            <div class="meta-row">
              <span class="meta">{{ display.email }}</span>
              <span class="meta">{{ display.signupType }}</span>
            </div>
            <div class="chip-row">
              <span class="chip">회원 분류 · {{ display.memberCategory }}</span>
              <span class="chip">MBTI · {{ display.mbti }}</span>
              <span class="chip">직업 · {{ display.job }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="quick">
        <div class="quick-head">
          <h3>내 활동</h3>
          <p class="sub">자주 찾는 메뉴를 빠르게 이동하세요.</p>
        </div>
        <div class="quick-grid">
          <RouterLink class="quick-card" to="/my/orders">
            <span class="activity-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="5" y="4" width="14" height="16" rx="2" />
                <path d="M9 8h6M9 12h6M9 16h3" />
              </svg>
            </span>
            <p class="quick-title">주문내역</p>
            <p class="quick-desc">지난 주문을 확인해요</p>
          </RouterLink>
          <RouterLink class="quick-card" to="/cart">
            <span class="activity-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M4 5h2l1 10h10l1-7H7" />
                <circle cx="9" cy="18" r="1.2" />
                <circle cx="16" cy="18" r="1.2" />
              </svg>
            </span>
            <p class="quick-title">장바구니</p>
            <p class="quick-desc">담아둔 상품을 확인해요</p>
          </RouterLink>
          <RouterLink class="quick-card" to="/my/address">
            <span class="activity-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M3 7h11v10H3zM14 10h3l3 3v4h-6z" />
                <circle cx="7.5" cy="17.5" r="1.2" />
                <circle cx="17" cy="17.5" r="1.2" />
              </svg>
            </span>
            <p class="quick-title">배송지 관리</p>
            <p class="quick-desc">배송 정보를 관리해요</p>
          </RouterLink>
          <RouterLink class="quick-card" to="/my/settings">
            <span class="activity-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <circle cx="12" cy="8" r="3" />
                <path d="M6 19c0-2.5 2.5-4 6-4s6 1.5 6 4" />
              </svg>
            </span>
            <p class="quick-title">정보 관리</p>
            <p class="quick-desc">내 정보를 관리해요</p>
          </RouterLink>
        </div>
      </section>

      <section class="recommend">
        <div class="recommend-head">
          <h3>내 추천</h3>
          <p class="sub">MBTI와 직업 기반 맞춤 추천 데스크테리어</p>
        </div>
        <p v-if="hasUser && !hasPreference" class="recommend-empty">{{ preferencePrompt }}</p>
        <div v-else class="recommend-scroll">
          <RouterLink
            v-for="item in recommendedProducts"
            :key="item.product_id"
            class="product-card"
            :to="`/products/${item.product_id}`"
          >
            <div class="thumb ds-thumb-frame ds-thumb-square">
              <img class="ds-thumb-img" :src="item.imageUrl" :alt="item.name" />
            </div>
            <div class="product-body">
              <p class="product-name">{{ item.name }}</p>
              <p class="product-desc">{{ item.short_desc }}</p>
              <p class="product-price">{{ item.price.toLocaleString('ko-KR') }}원</p>
            </div>
          </RouterLink>
        </div>
      </section>

      <section class="account">
        <div class="account-head">
          <h3>계정 관리</h3>
          <p class="sub">계정 관련 작업을 진행할 수 있습니다.</p>
        </div>
        <div class="account-actions">
          <button v-if="hasUser" type="button" class="btn ghost" @click="handleLogout">로그아웃</button>
          <button v-if="hasUser" type="button" class="btn danger" @click="handleWithdraw">회원 탈퇴하기</button>
          <button v-else type="button" class="btn" @click="router.push('/login')">로그인하기</button>
        </div>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.mypage-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 960px;
  margin: 0 auto;
}

.login-alert {
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-strong);
  font-weight: 700;
}

.profile-banner {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(17, 24, 39, 0.16), rgba(34, 197, 94, 0.2));
  border-radius: 18px;
  padding: 26px 22px;
  box-shadow: inset 0 -1px rgba(17, 24, 39, 0.08), 0 6px 20px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-banner::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.12);
}

.profile-banner::after {
  content: '';
  position: absolute;
  width: 340px;
  height: 340px;
  right: -90px;
  top: -130px;
  background: radial-gradient(circle, rgba(34, 197, 94, 0.16), transparent 72%);
  opacity: 0.55;
}

.profile-banner > * {
  position: relative;
  z-index: 1;
}

.profile-top {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.avatar {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.6);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  color: #111827;
  letter-spacing: 0.04em;
  font-size: 16px;
  overflow: hidden;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name {
  margin: 0;
  font-size: 24px;
  font-weight: 900;
  color: var(--text-strong);
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
  min-width: 0;
  max-width: 100%;
}

.meta {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.dot {
  opacity: 0.6;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
}

.chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.5);
  color: var(--text-strong);
  font-weight: 800;
  font-size: 12px;
}

.quick {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quick-head h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 900;
  color: var(--text-strong);
}

.quick-head .sub {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.quick-card {
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  border-radius: 12px;
  padding: 12px;
  text-decoration: none;
  color: inherit;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.quick-card:hover,
.quick-card:focus-visible {
  background: var(--surface);
  border-color: var(--primary-color, var(--border-color));
  outline: none;
}

.activity-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--surface);
  border: 1px solid var(--border-color);
  color: var(--text-strong);
}

.activity-icon svg {
  width: 20px;
  height: 20px;
}

.quick-title {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
}

.quick-desc {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.recommend {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recommend-head h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.recommend-head .sub {
  margin: 2px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.recommend-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 6px;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
}

.recommend-empty {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px dashed var(--border-color);
  background: var(--surface-weak);
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.recommend-scroll .product-card:last-of-type {
  margin-right: 4px;
}

.recommend-scroll::-webkit-scrollbar {
  height: 6px;
}

.recommend-scroll::-webkit-scrollbar-thumb {
  background: rgba(17, 24, 39, 0.15);
  border-radius: 999px;
}

.recommend-scroll {
  scrollbar-width: thin;
  scrollbar-color: rgba(17, 24, 39, 0.15) transparent;
}

.recommend-scroll {
  mask-image: linear-gradient(90deg, transparent 0, #000 14px, #000 calc(100% - 14px), transparent 100%);
}

.product-card {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 12px;
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  display: flex;
  flex-direction: column;
  transition: background 0.2s ease, box-shadow 0.2s ease;
  flex: 0 0 220px;
  scroll-snap-align: start;
}

.product-card:hover,
.product-card:focus-visible {
  background: var(--surface-weak);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.04);
  outline: none;
}

.thumb {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.product-body {
  padding: 8px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.product-name {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
  font-size: 13.5px;
  min-height: 32px;
}

.product-desc {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 12px;
  min-height: 28px;
}

.product-price {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
}

.account {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.account-head h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 900;
  color: var(--text-strong);
}

.account-head .sub {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.account-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  font-weight: 900;
  padding: 8px 12px;
  border-radius: 10px;
  cursor: pointer;
}

.btn:focus-visible {
  outline: 2px solid var(--primary-color, #111827);
  outline-offset: 2px;
}

.btn.danger {
  border-color: #d14343;
  color: #d14343;
  background: transparent;
  font-weight: 800;
}

.btn.danger:hover,
.btn.danger:focus-visible {
  background: #fff5f5;
}

.btn.ghost {
  border-color: var(--border-color);
  color: var(--text-strong);
  background: var(--surface);
}

@media (max-width: 640px) {
  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .account-actions {
    justify-content: flex-start;
  }
  .profile-top {
    align-items: flex-start;
  }
  .chip-row {
    gap: 6px;
  }
  .login-alert {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 480px) {
  .profile-top {
    flex-direction: column;
  }
  .profile-banner {
    padding: 20px 16px;
  }
  .name {
    font-size: 20px;
  }
  .avatar {
    width: 54px;
    height: 54px;
    font-size: 14px;
  }
  .quick-grid {
    grid-template-columns: 1fr;
  }
  .recommend-scroll {
    gap: 10px;
  }
  .product-card {
    flex-basis: 190px;
  }
}

@media (max-width: 860px) {
  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
