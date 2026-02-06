<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'
import {getAuthUser, hydrateSessionUser, isAdmin, isLoggedIn as checkLoggedIn, isSeller, requestLogout} from '../lib/auth'

const route = useRoute()
const router = useRouter()
const isScrolled = ref(false)
const isMenuOpen = ref(false)
const headerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const isLoggedIn = ref(false)
const memberCategory = ref<string | null>(null)

const navLinksBase = [
  {label: '상품', to: '/products'},
  {label: '셋업', to: '/setup'},
  {label: '라이브', to: '/live'},
]
const showInquiryTab = computed(
  () => isLoggedIn.value && !isSeller() && !isAdmin() && memberCategory.value !== 'ROLE_GUEST',
)
const navLinks = computed(() =>
  showInquiryTab.value
    ? [...navLinksBase, {label: '문의', to: '/chat'}]
    : navLinksBase,
)

const sellerTabs = [
  {
    label: '방송관리',
    to: '/seller/live',
    children: [
      {label: '방송 목록', to: '/seller/live'},
      {label: '방송 통계', to: '/seller/live/stats'},
    ],
  },
  {label: '상품관리', to: '/seller/products'},
  {label: '주문관리', to: '/seller/orders'},
]

const adminTabs = [
  {label: '회원관리', to: '/admin/users'},
  {
    label: '방송관리',
    to: '/admin/live',
    children: [
      {label: '방송 목록', to: '/admin/live'},
      {label: '방송 통계', to: '/admin/live/stats'},
      {label: '제재 통계', to: '/admin/live/sanctions'},
    ],
  },
  {label: '상품관리', to: '/admin/products'},
  {label: '고객센터', to: '/admin/support'},
]

const refreshAuth = () => {
  isLoggedIn.value = checkLoggedIn()
  memberCategory.value = getAuthUser()?.memberCategory ?? null
}

const sellerMode = computed(() => isLoggedIn.value && isSeller())
const adminLoggedIn = computed(() => isLoggedIn.value && isAdmin())
const isAdminView = computed(() => adminLoggedIn.value || route.path.startsWith('/admin'))
const isSellerView = computed(() => !isAdminView.value && (sellerMode.value || route.path.startsWith('/seller')))
const showBaseNav = computed(() => !isSellerView.value && !isAdminView.value)
const showCart = computed(
  () => isLoggedIn.value && !!memberCategory.value && memberCategory.value !== 'ROLE_GUEST',
)

const actionLinks = computed(() => {
  if (sellerMode.value) {
    return [{label: '마이페이지', to: '/seller/my', icon: 'user'}]
  }
  return isLoggedIn.value
    ? [
      ...(showCart.value ? [{label: '장바구니', to: '/cart', icon: 'cart'}] : []),
      {label: '마이페이지', to: '/my', icon: 'user'},
    ]
    : [
      {label: '로그인', to: '/login', icon: 'user'},
    ]
})

const handleScroll = () => {
  isScrolled.value = window.scrollY > 8
}

const handleDocumentClick = (event: MouseEvent) => {
  if (!isMenuOpen.value) return
  const path = event.composedPath()
  if (panelRef.value && path.includes(panelRef.value)) return
  if (headerRef.value && path.includes(headerRef.value)) return
  closeMenu()
}

const hydrateAuth = async () => {
  if (!checkLoggedIn()) {
    await hydrateSessionUser()
  }
  refreshAuth()
}

onMounted(() => {
  handleScroll()
  hydrateAuth()
  window.addEventListener('scroll', handleScroll, {passive: true})
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('click', handleDocumentClick)
  window.addEventListener('deskit-user-updated', refreshAuth)
  window.addEventListener('storage', refreshAuth)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('click', handleDocumentClick)
  window.removeEventListener('deskit-user-updated', refreshAuth)
  window.removeEventListener('storage', refreshAuth)
})

const isLiveActive = computed(() => route.path.startsWith('/live'))
const activeSellerPath = computed(() => {
  const match = sellerTabs.find((tab) => route.path.startsWith(tab.to))
  return match?.to ?? ''
})
const activeAdminPath = computed(() => {
  const match = adminTabs.find((tab) => route.path.startsWith(tab.to))
  return match?.to ?? ''
})

const showSellerMenu = ref(false)
const showAdminMenu = ref(false)

const closeMenus = () => {
  showSellerMenu.value = false
  showAdminMenu.value = false
}

const logoTo = computed(() => (isSellerView.value ? '/seller' : isAdminView.value ? '/admin' : '/'))

const closeMenu = () => {
  isMenuOpen.value = false
  closeMenus()
}

const toggleMenu = () => {
  isMenuOpen.value = !isMenuOpen.value
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    closeMenu()
  }
}

watch(
    () => route.fullPath,
    () => {
      closeMenu()
    },
)

watch(isMenuOpen, (open) => {
  const body = document.body
  if (open) {
    body.classList.add('no-scroll')
  } else {
    body.classList.remove('no-scroll')
  }
})

const searchQuery = ref('')

const submitSearch = () => {
  const q = searchQuery.value.trim()
  router.push({path: '/products', query: q ? {q} : undefined})
  closeMenu()
}

const handleLogout = async () => {
  const success = await requestLogout()
  if (success) {
    window.alert('로그아웃되었습니다.')
  }
  refreshAuth()
  router.push('/').catch(() => {})
}
</script>

<template>
  <header ref="headerRef" class="header" :class="{ 'header--scrolled': isScrolled }">
    <div class="container">
      <div class="left">
        <RouterLink :to="logoTo" class="brand">
          <img class="brand__logo" src="/DESKIT.png" alt="DESKIT"/>
        </RouterLink>

        <nav v-if="showBaseNav" class="nav">
          <RouterLink
              v-for="item in navLinks"
              :key="item.to"
              :to="item.to"
              :class="[
              'nav-link',
              {
                'nav-link--live': item.to === '/live',
                'nav-link--live-active': item.to === '/live' && isLiveActive,
              },
            ]"
              active-class="nav-link--active"
          >
            {{ item.label }}
            <span v-if="item.to === '/live'" class="live-pill">LIVE</span>
          </RouterLink>
        </nav>
        <div v-else class="seller-nav">
          <nav v-if="isSellerView" class="nav seller-tabs" aria-label="판매자 대시보드 탭">
            <div
              v-for="tab in sellerTabs"
              :key="tab.to"
              class="nav-link nav-link--dropdown"
              :class="{ 'nav-link--active': activeSellerPath === tab.to }"
              @mouseenter="tab.children ? (showSellerMenu = true) : null"
              @mouseleave="tab.children ? (showSellerMenu = false) : null"
            >
              <RouterLink :to="tab.to" :aria-current="activeSellerPath === tab.to ? 'page' : undefined">
                {{ tab.label }}
              </RouterLink>
              <div v-if="tab.children" class="dropdown" :class="{ 'dropdown--open': showSellerMenu }">
                <RouterLink v-for="child in tab.children" :key="child.to" :to="child.to" class="dropdown__item">
                  {{ child.label }}
                </RouterLink>
              </div>
            </div>
          </nav>
          <nav v-else class="nav seller-tabs" aria-label="관리자 대시보드 탭">
            <div
              v-for="tab in adminTabs"
              :key="tab.to"
              class="nav-link nav-link--dropdown"
              :class="{ 'nav-link--active': activeAdminPath === tab.to }"
              @mouseenter="tab.children ? (showAdminMenu = true) : null"
              @mouseleave="tab.children ? (showAdminMenu = false) : null"
            >
              <RouterLink :to="tab.to" :aria-current="activeAdminPath === tab.to ? 'page' : undefined">
                {{ tab.label }}
              </RouterLink>
              <div v-if="tab.children" class="dropdown" :class="{ 'dropdown--open': showAdminMenu }">
                <RouterLink v-for="child in tab.children" :key="child.to" :to="child.to" class="dropdown__item">
                  {{ child.label }}
                </RouterLink>
              </div>
            </div>
          </nav>
        </div>
      </div>

      <div class="right right-wrap">
        <form v-if="showBaseNav" class="search search--desktop" @submit.prevent="submitSearch">
          <svg class="search__icon" width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path
                d="M11 4a7 7 0 015.657 11.045l3.149 3.148-1.414 1.414-3.148-3.149A7 7 0 1111 4z"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
            />
          </svg>
          <input v-model="searchQuery" class="search__input" type="search" placeholder="검색어를 입력하세요"/>
        </form>
        <div class="actions">
          <template v-if="adminLoggedIn">
            <RouterLink to="/admin/my" class="action-link">
              <span>마이페이지</span>
            </RouterLink>
          </template>
          <RouterLink
              v-for="action in actionLinks"
              :key="action.to"
              :to="action.to"
              class="action-link"
              v-if="!adminLoggedIn"
          >
            <svg v-if="action.icon === 'cart'" width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path
                  d="M6 6h15l-1.5 9h-12L5 4H3"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linecap="round"
                  stroke-linejoin="round"
              />
              <circle cx="9" cy="19" r="1.2" fill="currentColor"/>
              <circle cx="17" cy="19" r="1.2" fill="currentColor"/>
            </svg>
            <svg v-else-if="action.icon === 'user'" width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path
                  d="M12 12a4 4 0 100-8 4 4 0 000 8z"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linecap="round"
                  stroke-linejoin="round"
              />
              <path
                  d="M5 20c.6-3 3.5-5 7-5s6.4 2 7 5"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linecap="round"
                  stroke-linejoin="round"
              />
            </svg>
            <span>{{ action.label }}</span>
          </RouterLink>
          <button v-if="isLoggedIn" type="button" class="logout-btn" @click="handleLogout">
            로그아웃
          </button>
          <button
              type="button"
              class="icon-btn menu-btn"
              aria-label="메뉴"
              :aria-expanded="isMenuOpen"
              aria-controls="mobile-menu"
              @click.stop="toggleMenu"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <transition name="fade">
      <div v-if="isMenuOpen" class="overlay" @click="closeMenu"/>
    </transition>
    <transition name="slide-down">
      <div
          v-if="isMenuOpen"
          id="mobile-menu"
          class="mobile-menu"
          role="dialog"
          aria-label="모바일 메뉴"
          ref="panelRef"
      >
        <form v-if="showBaseNav" class="search search--mobile" @submit.prevent="submitSearch">
          <svg class="search__icon" width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path
                d="M11 4a7 7 0 015.657 11.045l3.149 3.148-1.414 1.414-3.148-3.149A7 7 0 1111 4z"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
            />
          </svg>
          <input
              v-model="searchQuery"
              class="search__input"
              type="search"
              placeholder="검색어를 입력하세요"
          />
        </form>
        <div class="mobile-menu__header">
          <span class="mobile-menu__title">메뉴</span>
          <button type="button" class="icon-btn" aria-label="메뉴 닫기" @click="closeMenu">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        <nav v-if="showBaseNav" class="mobile-menu__nav">
          <RouterLink
              v-for="item in navLinks"
              :key="item.to"
              :to="item.to"
              class="mobile-menu__link"
              @click="closeMenu"
          >
            {{ item.label }}
            <span v-if="item.to === '/live'" class="live-pill live-pill--inline">LIVE</span>
          </RouterLink>
        </nav>
        <nav v-else class="mobile-menu__nav">
          <template v-if="isSellerView">
            <div v-for="tab in sellerTabs" :key="`seller-${tab.to}`" class="mobile-menu__group">
              <RouterLink :to="tab.to" class="mobile-menu__link" @click="closeMenu">
                {{ tab.label }}
              </RouterLink>
              <div v-if="tab.children" class="mobile-menu__subnav">
                <RouterLink
                  v-for="child in tab.children"
                  :key="`seller-${tab.to}-${child.to}`"
                  :to="child.to"
                  class="mobile-menu__sublink"
                  @click="closeMenu"
                >
                  {{ child.label }}
                </RouterLink>
              </div>
            </div>
          </template>
          <template v-else>
            <div v-for="tab in adminTabs" :key="`admin-${tab.to}`" class="mobile-menu__group">
              <RouterLink :to="tab.to" class="mobile-menu__link" @click="closeMenu">
                {{ tab.label }}
              </RouterLink>
              <div v-if="tab.children" class="mobile-menu__subnav">
                <RouterLink
                  v-for="child in tab.children"
                  :key="`admin-${tab.to}-${child.to}`"
                  :to="child.to"
                  class="mobile-menu__sublink"
                  @click="closeMenu"
                >
                  {{ child.label }}
                </RouterLink>
              </div>
            </div>
          </template>
        </nav>
        <div class="mobile-menu__actions">
          <RouterLink
              v-for="action in actionLinks"
              :key="action.to"
              :to="action.to"
              class="mobile-menu__link"
              v-if="!adminLoggedIn"
              @click="closeMenu"
          >
            {{ action.label }}
          </RouterLink>
          <RouterLink v-if="adminLoggedIn" to="/admin/my" class="mobile-menu__link" @click="closeMenu">
            마이페이지
          </RouterLink>
          <RouterLink v-if="isLoggedIn && !sellerMode && !adminLoggedIn" to="/my/orders" class="mobile-menu__link" @click="closeMenu">
            주문내역
          </RouterLink>
          <button
              v-if="isLoggedIn"
              type="button"
              class="mobile-menu__link logout-btn--mobile"
              @click="
                handleLogout();
                closeMenu();
              "
          >
            로그아웃
          </button>
        </div>
      </div>
    </transition>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
  --header-height: 66px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 10px 20px;
  flex-wrap: nowrap;
}

.header--scrolled {
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
  border-color: rgba(15, 23, 42, 0.06);
}

.left {
  display: flex;
  align-items: center;
  gap: 32px;
  flex-shrink: 1;
  min-width: 0;
}

.brand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 4px;
}

.brand__logo {
  height: 32px;
  width: auto;
  display: block;
  transition: opacity 0.18s ease;
}

.brand:hover .brand__logo {
  opacity: 0.85;
}

.nav {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.seller-nav {
  min-width: 0;
}

.seller-nav__title {
  display: inline-flex;
  align-items: center;
  font-weight: 800;
  color: var(--text-strong);
  font-size: 0.95rem;
  letter-spacing: -0.01em;
  margin-bottom: 4px;
}

.seller-tabs {
  flex-wrap: wrap;
}

.nav-link {
  padding: 8px 12px;
  border-radius: 10px;
  font-weight: 700;
  color: var(--text-muted);
  position: relative;
  transition: color 0.22s ease, background 0.22s ease, opacity 0.22s ease;
}

.nav-link:hover {
  color: var(--text-strong);
  background: var(--hover-bg);
}

.nav-link--active {
  color: var(--primary-color);
  background: var(--hover-bg);
}

.nav-link::after {
  content: '';
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 6px;
  height: 2px;
  border-radius: 999px;
  background: var(--primary-color);
  transform: scaleX(0);
  transform-origin: center;
  opacity: 0;
  transition: transform 0.22s ease, opacity 0.22s ease;
}

.nav-link--active::after,
.nav-link:hover::after {
  transform: scaleX(1);
  opacity: 1;
}

.nav-link--live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.nav-link--live .live-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 6px;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #fff;
  background: linear-gradient(135deg, var(--live-color), var(--live-color));
  box-shadow: 0 6px 14px rgba(229, 72, 77, 0.2);
}

.nav-link--live-active {
  color: var(--primary-color);
  background: var(--hover-bg);
  box-shadow: 0 10px 24px rgba(119, 136, 115, 0.14);
}

.nav-link--dropdown {
  position: relative;
}

.dropdown {
  position: absolute;
  top: 110%;
  left: 0;
  min-width: 140px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-card);
  opacity: 0;
  pointer-events: none;
  transform: translateY(4px);
  transition: opacity 0.12s ease, transform 0.12s ease;
  z-index: 30;
}

.dropdown--open {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.dropdown__item {
  display: block;
  padding: 10px 12px;
  color: var(--text-strong);
  font-weight: 800;
}

.dropdown__item:hover {
  background: var(--surface-weak);
}

.right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 1;
  justify-content: flex-end;
  min-width: 0;
}

.right-wrap {
  margin-right: 24px;
  max-width: 640px;
}

.search {
  position: relative;
  width: clamp(260px, 30vw, 360px);
  flex: 0 1 auto;
}

.search__input {
  width: 100%;
  padding: 9px 12px 9px 34px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: #fff;
  font-weight: 600;
  color: var(--text-strong);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.search__input::placeholder {
  color: var(--text-soft);
}

.search__input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 10px 24px rgba(119, 136, 115, 0.14);
}

.search__icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-soft);
  pointer-events: none;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-link {
  padding: 8px 10px;
  border-radius: 10px;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
  white-space: nowrap;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.action-link:hover {
  color: var(--text-strong);
  background: var(--surface-weak);
  transform: translateY(-1px);
}

.logout-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-weight: 800;
  font-size: 13px;
  white-space: nowrap;
  line-height: 1;
  padding: 6px 8px;
  cursor: pointer;
}

.logout-btn:hover,
.logout-btn:focus-visible {
  color: var(--text-strong);
  text-decoration: underline;
  outline: none;
}

.icon-btn {
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-muted);
  padding: 8px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s ease, background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.icon-btn:hover {
  color: var(--text-strong);
  background: var(--surface-weak);
  border-color: var(--primary-color);
  transform: translateY(-1px);
}

.search-btn,
.menu-btn {
  display: none;
}

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  z-index: 9;
}

.mobile-menu {
  position: fixed;
  top: var(--header-height, 64px);
  left: 0;
  right: 0;
  background: #fff;
  border-bottom-left-radius: 12px;
  border-bottom-right-radius: 12px;
  box-shadow: 0 18px 48px rgba(119, 136, 115, 0.16);
  z-index: 11;
  padding: 12px 16px 16px;
  max-height: calc(100vh - var(--header-height, 64px));
  overflow-y: auto;
}

.mobile-menu form {
  margin-bottom: 10px;
}

.search--mobile {
  width: 100%;
}

.search--mobile .search__input {
  min-height: 44px;
}

.mobile-menu__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.mobile-menu__title {
  font-weight: 800;
  color: var(--text-strong);
}

.mobile-menu__nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mobile-menu__group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mobile-menu__subnav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 12px;
  border-left: 1px solid var(--border-color);
  padding-left: 8px;
}

.mobile-menu__sublink {
  padding: 8px 10px;
  border-radius: 10px;
  color: var(--text-muted);
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  min-height: 38px;
  transition: background 0.2s ease, color 0.2s ease;
}

.mobile-menu__sublink:hover {
  background: var(--surface-weak);
  color: var(--text-strong);
}

.mobile-menu__actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
}

.logout-btn--mobile {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-weight: 800;
  text-align: left;
  padding: 12px 10px;
  border-radius: 10px;
}

.logout-btn--mobile:hover,
.logout-btn--mobile:focus-visible {
  color: var(--text-strong);
  background: var(--surface-weak);
  outline: none;
}

.mobile-menu__link {
  padding: 12px 10px;
  border-radius: 10px;
  color: var(--primary-color);
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  transition: background 0.2s ease, color 0.2s ease;
  min-height: 44px;
}

.mobile-menu__link:hover {
  background: var(--surface-weak);
  color: var(--primary-color);
}

.mobile-menu__actions .mobile-menu__link {
  color: var(--text-soft);
  font-weight: 700;
  font-size: 0.95rem;
  background: transparent;
}

.mobile-menu__actions .mobile-menu__link:hover {
  color: var(--primary-color);
}

.live-pill--inline {
  padding: 4px 6px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}

:global(body.no-scroll) {
  overflow: hidden;
}

@media (max-width: 1023px) {
  .container {
    gap: 12px;
  }

  .search {
    width: clamp(200px, 24vw, 320px);
  }
}

@media (max-width: 899px) {
  .header {
    --header-height: 60px;
  }

  .container {
    padding: 10px 14px;
    gap: 10px;
  }

  .brand__logo {
    height: 28px;
  }

  .nav {
    display: none;
  }


  .search--desktop {
    display: none;
  }

  .menu-btn {
    display: inline-flex;
  }

  .actions {
    gap: 6px;
  }

  .action-link {
    display: none;
  }

  .search--mobile .search__input {
    width: 100%;
  }
}
</style>
