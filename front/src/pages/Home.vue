<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { type ProductItem, type SetupItem } from '../lib/home-data'
import { listPopularProducts, listPopularSetups } from '../api/home'
import LiveCarousel from '../components/LiveCarousel.vue'
import SetupCarousel from '../components/SetupCarousel.vue'
import ProductCarousel from '../components/ProductCarousel.vue'
import PageContainer from '../components/PageContainer.vue'
import { fetchBroadcastStats, fetchPublicBroadcastOverview } from '../lib/live/api'
import { getScheduledEndMs, normalizeBroadcastStatus } from '../lib/broadcastStatus'
import type { LiveItem } from '../lib/live/types'
import { parseLiveDate } from '../lib/live/utils'
import { getAuthUser } from '../lib/auth'
import { resolveViewerId } from '../lib/live/viewer'

const liveItems = ref<LiveItem[]>([])
const popularProducts = ref<ProductItem[]>([])
const popularSetups = ref<SetupItem[]>([])
const popularProductsLoading = ref(true)
const popularSetupsLoading = ref(true)
const popularProductsError = ref(false)
const popularSetupsError = ref(false)
let liveRefreshTimer: number | null = null
const sseSource = ref<EventSource | null>(null)
const sseRetryTimer = ref<number | null>(null)
const sseRetryCount = ref(0)
const refreshTimer = ref<number | null>(null)
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'

const buildProductItems = (items: Awaited<ReturnType<typeof listPopularProducts>>) =>
  items.map((item) => ({
    id: String(item.product_id),
    name: item.name ?? '',
    imageUrl: item.thumbnail_url || '/placeholder-product.jpg',
    price: Number(item.price ?? 0),
    tags: { space: [], tone: [], situation: [], mood: [] },
    isSoldOut: false,
  }))

const buildSetupItems = (items: Awaited<ReturnType<typeof listPopularSetups>>) =>
  items.map((item) => {
    const raw = item as any
    return {
      id: String(raw.setup_id ?? raw.setupId ?? raw.id ?? ''),
      title: raw.setup_name ?? raw.setupName ?? raw.name ?? raw.title ?? '',
      description: raw.short_desc ?? raw.shortDesc ?? raw.description ?? '',
      imageUrl: raw.setup_image_url ?? raw.setupImageUrl ?? raw.image_url ?? raw.imageUrl ?? '/placeholder-setup.jpg',
    }
  })

const loadPopulars = async () => {
  popularProductsLoading.value = true
  popularSetupsLoading.value = true
  popularProductsError.value = false
  popularSetupsError.value = false

  const [productsResult, setupsResult] = await Promise.allSettled([
    listPopularProducts(),
    listPopularSetups(),
  ])

  if (productsResult.status === 'fulfilled') {
    popularProducts.value = buildProductItems(productsResult.value)
  } else {
    popularProductsError.value = true
  }
  if (setupsResult.status === 'fulfilled') {
    popularSetups.value = buildSetupItems(setupsResult.value)
  } else {
    popularSetupsError.value = true
  }

  popularProductsLoading.value = false
  popularSetupsLoading.value = false
}

const mapToLiveItems = (items: Array<{ broadcastId: number; title: string; notice?: string; thumbnailUrl?: string; startAt?: string; endAt?: string; liveViewerCount?: number; viewerCount?: number; sellerName?: string; status?: string; totalLikes?: number; reportCount?: number }>) =>
  items
    .filter((item) => item.startAt)
    .map((item) => ({
      id: String(item.broadcastId),
      title: item.title,
      description: item.notice ?? '',
      thumbnailUrl: item.thumbnailUrl ?? '',
      startAt: item.startAt ?? '',
      endAt: item.endAt ?? item.startAt ?? '',
      status: item.status,
      viewerCount: item.liveViewerCount ?? item.viewerCount ?? 0,
      likeCount: item.totalLikes ?? 0,
      reportCount: item.reportCount ?? 0,
      sellerName: item.sellerName ?? '',
    }))

const loadLiveItems = async () => {
  try {
    const items = await fetchPublicBroadcastOverview()
    liveItems.value = mapToLiveItems(items).slice(0, 8)
  } catch {
    liveItems.value = []
  }
}

const isStatsTarget = (item: LiveItem) => {
  const status = normalizeBroadcastStatus(item.status)
  if (status === 'ON_AIR' || status === 'READY' || status === 'VOD') return true
  const startAtMs = parseLiveDate(item.startAt).getTime()
  if (Number.isNaN(startAtMs)) return false
  const endAtMs = parseLiveDate(item.endAt).getTime()
  const normalizedEnd = Number.isNaN(endAtMs) ? getScheduledEndMs(startAtMs) : endAtMs
  if (!normalizedEnd) return false
  const now = Date.now()
  return now >= startAtMs && now <= normalizedEnd
}

const updateLiveViewerCounts = async () => {
  const targets = liveItems.value.filter((item) => isStatsTarget(item))
  if (!targets.length) return
  const updates = await Promise.allSettled(
    targets.map(async (item) => ({
      id: item.id,
      stats: await fetchBroadcastStats(Number(item.id)),
    })),
  )
  const statsMap = new Map<string, { viewerCount: number; likeCount: number; reportCount: number }>()
  updates.forEach((result) => {
    if (result.status === 'fulfilled') {
      statsMap.set(result.value.id, {
        viewerCount: result.value.stats.viewerCount ?? 0,
        likeCount: result.value.stats.likeCount ?? 0,
        reportCount: result.value.stats.reportCount ?? 0,
      })
    }
  })
  if (!statsMap.size) return
  liveItems.value = liveItems.value.map((item) => {
    const stats = statsMap.get(item.id)
    if (!stats) return item
    return {
      ...item,
      viewerCount: stats.viewerCount ?? item.viewerCount ?? 0,
      likeCount: stats.likeCount ?? item.likeCount ?? 0,
      reportCount: stats.reportCount ?? item.reportCount ?? 0,
    }
  })
}

const parseSseData = (event: MessageEvent) => {
  if (!event.data) return null
  try {
    return JSON.parse(event.data)
  } catch {
    return event.data
  }
}

const scheduleRefresh = () => {
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = window.setTimeout(() => {
    void loadLiveItems()
    void updateLiveViewerCounts()
  }, 500)
}

const handleSseEvent = (event: MessageEvent) => {
  parseSseData(event)
  switch (event.type) {
    case 'BROADCAST_READY':
    case 'BROADCAST_UPDATED':
    case 'BROADCAST_STARTED':
    case 'PRODUCT_PINNED':
    case 'PRODUCT_UNPINNED':
    case 'PRODUCT_SOLD_OUT':
    case 'SANCTION_UPDATED':
    case 'BROADCAST_CANCELED':
    case 'BROADCAST_ENDED':
    case 'BROADCAST_SCHEDULED_END':
    case 'BROADCAST_STOPPED':
      scheduleRefresh()
      break
    default:
      break
  }
}

const scheduleReconnect = () => {
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  const delay = Math.min(30000, 1000 * 2 ** sseRetryCount.value)
  const jitter = Math.floor(Math.random() * 500)
  sseRetryTimer.value = window.setTimeout(() => {
    connectSse()
  }, delay + jitter)
  sseRetryCount.value += 1
}

const connectSse = () => {
  sseSource.value?.close()
  const user = getAuthUser()
  const viewerId = resolveViewerId(user)
  const query = viewerId ? `?viewerId=${encodeURIComponent(viewerId)}` : ''
  const source = new EventSource(`${apiBase}/broadcasts/subscribe/all${query}`)
  const events = [
    'BROADCAST_READY',
    'BROADCAST_UPDATED',
    'BROADCAST_STARTED',
    'PRODUCT_PINNED',
    'PRODUCT_UNPINNED',
    'PRODUCT_SOLD_OUT',
    'SANCTION_UPDATED',
    'BROADCAST_CANCELED',
    'BROADCAST_ENDED',
    'BROADCAST_SCHEDULED_END',
    'BROADCAST_STOPPED',
  ]
  events.forEach((name) => source.addEventListener(name, handleSseEvent))
  source.onopen = () => {
    sseRetryCount.value = 0
    scheduleRefresh()
  }
  source.onerror = () => {
    source.close()
    if (document.visibilityState === 'visible') {
      scheduleReconnect()
    }
  }
  sseSource.value = source
}

const handleSseVisibilityChange = () => {
  if (document.visibilityState !== 'visible') {
    return
  }
  connectSse()
}

onMounted(() => {
  loadPopulars()
  loadLiveItems()
  connectSse()
  window.addEventListener('visibilitychange', handleSseVisibilityChange)
  window.addEventListener('focus', handleSseVisibilityChange)
  liveRefreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void updateLiveViewerCounts()
    }
  }, 5000)
})

onBeforeUnmount(() => {
  if (liveRefreshTimer) window.clearInterval(liveRefreshTimer)
  liveRefreshTimer = null
  sseSource.value?.close()
  sseSource.value = null
  window.removeEventListener('visibilitychange', handleSseVisibilityChange)
  window.removeEventListener('focus', handleSseVisibilityChange)
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  sseRetryTimer.value = null
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = null
})
</script>

<template>
  <div class="home">
    <!-- Hero: full-bleed (NOT inside PageContainer) -->
    <section class="hero">
      <div class="hero__inner ds-container">
        <p class="hero__eyebrow">DESKIT</p>
        <h1 class="hero__title">나만의 데스크를 완성하는 방법</h1>
        <p class="hero__lede">라이브 · 셋업 · 아이템을 한 번에 둘러보세요.</p>
      </div>
      <div class="hero__glow" aria-hidden="true"></div>
    </section>

    <!-- Content: standardized 1200 layout -->
    <PageContainer>
      <div class="ds-stack-lg">
        <section>
          <div class="ds-section-head">
            <h2 class="section-title">라이브 방송</h2>
            <p class="ds-section-sub">지금 진행 중인 라이브를 만나보세요.</p>
          </div>
          <LiveCarousel :items="liveItems" />
        </section>

        <section>
          <div class="ds-section-head">
            <h2 class="section-title">인기 셋업</h2>
            <p class="ds-section-sub">다양한 스타일의 데스크 셋업을 둘러보세요.</p>
          </div>
          <p v-if="popularSetupsLoading" class="ds-section-sub">로딩중...</p>
          <p v-else-if="popularSetupsError" class="ds-section-sub">
            인기 셋업을 불러오지 못했습니다.
          </p>
          <p v-else-if="!popularSetups.length" class="ds-section-sub">준비중입니다.</p>
          <SetupCarousel v-else :items="popularSetups" />
        </section>

        <section>
          <div class="ds-section-head">
            <h2 class="section-title">인기 상품</h2>
            <p class="ds-section-sub">데스크 완성을 위한 추천 아이템.</p>
          </div>
          <p v-if="popularProductsLoading" class="ds-section-sub">로딩중...</p>
          <p v-else-if="popularProductsError" class="ds-section-sub">
            인기 상품을 불러오지 못했습니다.
          </p>
          <p v-else-if="!popularProducts.length" class="ds-section-sub">준비중입니다.</p>
          <ProductCarousel v-else :items="popularProducts" />
        </section>
      </div>
    </PageContainer>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
}

/* Full-bleed hero (premium gradient + more breathing room) */
.hero {
  position: relative;
  overflow: hidden;
  padding: 56px 0 44px;
  margin-bottom: 8px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  background:
    radial-gradient(1200px 520px at 20% -20%, rgba(34, 197, 94, 0.18), transparent 60%),
    radial-gradient(900px 420px at 110% 10%, rgba(17, 24, 39, 0.10), transparent 55%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.75), rgba(248, 249, 245, 0.85));
}

.hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.28), rgba(255, 255, 255, 0.68));
}

.hero__inner {
  position: relative;
  z-index: 1;
}

.hero__glow {
  position: absolute;
  width: 640px;
  height: 640px;
  right: -220px;
  top: -320px;
  background: radial-gradient(circle, rgba(34, 197, 94, 0.16), transparent 62%);
  opacity: 0.65;
  filter: blur(2px);
  pointer-events: none;
}

.hero__eyebrow {
  margin: 0;
  font-weight: 900;
  letter-spacing: 0.12em;
  font-size: 12px;
  color: var(--text-muted);
}

.hero__title {
  margin: 10px 0 0;
  font-weight: 950;
  letter-spacing: -0.6px;
  color: var(--text-strong);
  font-size: 28px;
  line-height: 1.2;
}

.hero__lede {
  margin: 10px 0 0;
  color: var(--text-muted);
  font-weight: 700;
}

@media (max-width: 640px) {
  .hero {
    padding: 44px 0 34px;
  }

  .hero__title {
    font-size: 22px;
  }
}
</style>
