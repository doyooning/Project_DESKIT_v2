import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { hydrateSessionUser, isAdmin, isLoggedIn, isSeller } from '../lib/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('../pages/Home.vue'),
  },
  {
    path: '/products',
    name: 'products',
    component: () => import('../pages/Products.vue'),
  },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: () => import('../pages/ProductDetail.vue'),
  },
  {
    path: '/setup',
    name: 'setup',
    component: () => import('../pages/Setup.vue'),
  },
  {
    path: '/setups/:id',
    name: 'setup-detail',
    component: () => import('../pages/SetupDetail.vue'),
  },
  {
    path: '/live',
    name: 'live',
    component: () => import('../pages/Live.vue'),
  },
  {
    path: '/live/:id',
    name: 'live-detail',
    component: () => import('../pages/LiveDetail.vue'),
  },
  {
    path: '/vod/:id',
    name: 'vod',
    component: () => import('../pages/Vod.vue'),
  },
  {
    path: '/cart',
    name: 'cart',
    component: () => import('../pages/Cart.vue'),
  },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('../pages/Checkout.vue'),
  },
  {
    path: '/payments/success',
    name: 'payment-success',
    component: () => import('../pages/PaymentSuccess.vue'),
  },
  {
    path: '/payments/fail',
    name: 'payment-fail',
    component: () => import('../pages/PaymentFail.vue'),
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../pages/Login.vue'),
  },
  {
    path: '/admin/verify',
    name: 'admin-verify',
    component: () => import('../pages/AdminVerify.vue'),
  },
  {
    path: '/signup',
    name: 'signup',
    component: () => import('../pages/Signup.vue'),
  },
  {
    path: '/signup/complete',
    name: 'register-complete',
    component: () => import('../pages/RegisterComplete.vue'),
  },
  {
    path: '/chat',
    name: 'chatbot',
    component: () => import('../pages/Chatbot.vue'),
  },
  {
    path: '/my',
    name: 'my-page',
    component: () => import('../pages/MyPage.vue'),
  },
  {
    path: '/my/settings',
    name: 'my-settings',
    component: () => import('../pages/MySettings.vue'),
  },
  {
    path: '/my/address',
    name: 'my-address',
    component: () => import('../pages/MyAddress.vue'),
  },
  {
    path: '/my/orders',
    name: 'order-history',
    component: () => import('../pages/OrderHistory.vue'),
  },
  {
    path: '/orders/:orderId/complete',
    name: 'order-complete-detail',
    component: () => import('../pages/OrderComplete.vue'),
  },
  {
    path: '/order/complete',
    name: 'order-complete-legacy',
    redirect: (to) =>
      to.query.orderId ? `/orders/${to.query.orderId}/complete` : '/cart',
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../pages/Admin.vue'),
    children: [
      {
        path: '',
        name: 'admin-dashboard',
        component: () => import('../pages/admin/live/Stats.vue'),
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('../pages/admin/AdminUsers.vue'),
      },
      {
        path: 'live',
        name: 'admin-live',
        component: () => import('../pages/admin/AdminLive.vue'),
      },
      {
        path: 'live/stats',
        name: 'admin-live-stats',
        component: () => import('../pages/admin/live/Stats.vue'),
      },
      {
        path: 'live/sanctions',
        name: 'admin-live-sanctions',
        component: () => import('../pages/admin/live/SanctionStats.vue'),
      },
      {
        path: 'live/reservations/:reservationId',
        name: 'admin-live-reservation-detail',
        component: () => import('../pages/admin/live/ReservationDetail.vue'),
      },
      {
        path: 'live/now/:liveId',
        name: 'admin-live-now-detail',
        component: () => import('../pages/admin/live/LiveDetail.vue'),
      },
      {
        path: 'live/vods/:vodId',
        name: 'admin-live-vod-detail',
        component: () => import('../pages/admin/live/VodDetail.vue'),
      },
      {
        path: 'products',
        name: 'admin-products',
        component: () => import('../pages/admin/AdminProducts.vue'),
      },
      {
        path: 'support',
        name: 'admin-support',
        component: () => import('../pages/admin/AdminSupport.vue'),
      },
      {
        path: 'my',
        name: 'admin-my',
        component: () => import('../pages/admin/AdminMyPage.vue'),
      },
    ],
  },
  {
    path: '/seller',
    name: 'seller',
    component: () => import('../pages/Seller.vue'),
    children: [
      {
        path: 'live',
        name: 'seller-live',
        component: () => import('../pages/seller/Live.vue'),
      },
      {
        path: 'live/stats',
        name: 'seller-live-stats',
        component: () => import('../pages/seller/LiveStats.vue'),
      },
      {
        path: 'products',
        name: 'seller-products',
        component: () => import('../pages/seller/Products.vue'),
      },
      {
        path: 'orders',
        name: 'seller-orders',
        component: () => import('../pages/seller/SellerOrders.vue'),
      },
      {
        path: 'orders/:orderId',
        name: 'seller-order-detail',
        component: () => import('../pages/seller/SellerOrderDetail.vue'),
      },
    ],
  },
  {
    path: '/seller/my',
    name: 'seller-my',
    component: () => import('../pages/seller/MyPage.vue'),
  },
  {
    path: '/seller/live/create',
    name: 'seller-live-create',
    component: () => import('../pages/seller/LiveCreateCue.vue'),
  },
  {
    path: '/seller/live/create/basic',
    name: 'seller-live-create-basic',
    component: () => import('../pages/seller/LiveCreateBasic.vue'),
  },
  {
    path: '/seller/live/stream/:id',
    name: 'seller-live-stream',
    component: () => import('../pages/seller/LiveStream.vue'),
  },
  {
    path: '/seller/broadcasts/reservations/:reservationId',
    name: 'seller-reservation-detail',
    component: () => import('../pages/seller/broadcasts/ReservationDetail.vue'),
  },
  {
    path: '/seller/broadcasts/vods/:vodId',
    name: 'seller-vod-detail',
    component: () => import('../pages/seller/broadcasts/VodDetail.vue'),
  },
  {
    path: '/seller/products/create',
    name: 'seller-products-create',
    component: () => import('../pages/seller/ProductCreateInfo.vue'),
  },
  {
    path: '/seller/products/create/detail',
    name: 'seller-products-create-detail',
    component: () => import('../pages/seller/ProductCreateDetail.vue'),
  },
  {
    path: '/seller/products/:id/edit',
    name: 'seller-products-edit',
    component: () => import('../pages/seller/ProductEditInfo.vue'),
  },
  {
    path: '/seller/products/:id/edit/detail',
    name: 'seller-products-edit-detail',
    component: () => import('../pages/seller/ProductEditDetail.vue'),
  },
]

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.onError((error, to) => {
  const message = String(error?.message ?? '')
  const isChunkLoadError =
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed')

  if (!isChunkLoadError) {
    return
  }

  const reloadGuardKey = 'deskit:chunk-reload'
  const alreadyReloaded = sessionStorage.getItem(reloadGuardKey) === '1'
  if (alreadyReloaded) {
    sessionStorage.removeItem(reloadGuardKey)
    return
  }

  sessionStorage.setItem(reloadGuardKey, '1')
  const separator = to.fullPath.includes('?') ? '&' : '?'
  window.location.replace(`${to.fullPath}${separator}__reload=${Date.now()}`)
})

const hasPendingAdminVerification = async (): Promise<boolean> => {
  try {
    const response = await fetch('/api/admin/auth/pending', {
      credentials: 'include',
    })
    return response.ok
  } catch {
    return false
  }
}

router.beforeEach(async (to) => {
  let loggedIn = isLoggedIn()
  const isSellerPath = to.path.startsWith('/seller')
  const isAdminPath = to.path.startsWith('/admin')
  const isAdminVerify = to.path === '/admin/verify'
  const shouldHydrateSession =
    !loggedIn &&
    (to.path === '/' ||
    (to.path.startsWith('/my') ||
      isSellerPath ||
      (isAdminPath && !isAdminVerify)))

  if (shouldHydrateSession) {
    const sessionOk = await hydrateSessionUser()
    loggedIn = isLoggedIn()
    if (!sessionOk && (to.path.startsWith('/my') || isSellerPath || (isAdminPath && !isAdminVerify))) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (isSellerPath && !loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (isSellerPath && loggedIn && !isSeller()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (isAdminVerify) {
    const pending = await hasPendingAdminVerification()
    if (pending) {
      return true
    }
    if (loggedIn && isAdmin()) {
      return { path: '/admin' }
    }
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (isAdminPath) {
    const pending = await hasPendingAdminVerification()
    if (pending) {
      return { path: '/admin/verify' }
    }
  }
  if (to.path.startsWith('/admin')) {
    if (!loggedIn || !isAdmin()) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (loggedIn && to.path === '/login') {
    return { path: isAdmin() ? '/admin' : isSeller() ? '/seller' : '/my' }
  }
  if (loggedIn && isSeller() && to.path === '/') {
    return { path: '/seller' }
  }
  if (loggedIn && isAdmin() && to.path === '/') {
    return { path: '/admin' }
  }
  return true
})
