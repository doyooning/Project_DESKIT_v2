<script setup lang="ts">
import {type ComponentPublicInstance, computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import {
  type BroadcastStatus,
  computeLifecycleStatus,
  getBroadcastStatusLabel,
  getScheduledEndMs,
  normalizeBroadcastStatus,
} from '../../lib/broadcastStatus'
import {useInfiniteScroll} from '../../composables/useInfiniteScroll'
import {parseLiveDate} from '../../lib/live/utils'
import {type BroadcastCategory, fetchAdminBroadcasts, fetchBroadcastStats, fetchCategories} from '../../lib/live/api'
import { getAuthUser } from '../../lib/auth'
import { resolveViewerId } from '../../lib/live/viewer'
import { createImageErrorHandler } from '../../lib/images/productImages'

type LiveTab = 'all' | 'scheduled' | 'live' | 'vod'
type LoopKind = 'live' | 'scheduled' | 'vod'

type LiveItem = {
  id: string
  title: string
  subtitle?: string
  thumb: string
  datetime?: string
  scheduledAt?: string
  statusBadge?: string
  viewerBadge?: string
  ctaLabel?: string
  sellerName?: string
  status?: string
  viewers?: number
  likes?: number
  reports?: number
  category?: string
  startedAt?: string
  startedAtMs?: number
  startAtMs?: number
  endAtMs?: number
  lifecycleStatus?: BroadcastStatus
}

type ReservationItem = LiveItem & {
  sellerName: string
  status: string
  category: string
  startAtMs?: number
  lifecycleStatus?: BroadcastStatus
}

type AdminVodItem = LiveItem & {
  sellerName: string
  statusLabel: string
  category: string
  metrics: {
    reports: number
    likes: number
    totalRevenue: number
    maxViewers: number
  }
  startAtMs?: number
  endAtMs?: number
  visibility?: 'public' | 'private'
  lifecycleStatus?: BroadcastStatus
}

const router = useRouter()
const route = useRoute()

const activeTab = ref<LiveTab>('all')

const LIVE_SECTION_STATUSES: BroadcastStatus[] = ['READY', 'ON_AIR', 'ENDED', 'STOPPED']
const SCHEDULED_SECTION_STATUSES: BroadcastStatus[] = ['RESERVED', 'CANCELED']
const statusPriority: Record<BroadcastStatus, number> = {
  ON_AIR: 0,
  READY: 1,
  STOPPED: 2,
  ENDED: 3,
  RESERVED: 4,
  CANCELED: 5,
  VOD: 6,
}

const liveCategory = ref<string>('all')
const liveSort = ref<'reports_desc' | 'latest' | 'viewers_desc' | 'viewers_asc'>('reports_desc')
const LIVE_PAGE_SIZE = 12
const livePage = ref(1)

const scheduledStatus = ref<'all' | 'reserved' | 'canceled'>('all')
const scheduledCategory = ref<string>('all')
const scheduledSort = ref<'nearest' | 'latest' | 'oldest'>('nearest')
const SCHEDULED_PAGE_SIZE = 12
const scheduledPage = ref(1)

const vodStartDate = ref('')
const vodEndDate = ref('')
const vodVisibility = ref<'all' | 'public' | 'private'>('all')
const vodSort = ref<
  | 'latest'
  | 'oldest'
  | 'reports_desc'
  | 'likes_desc'
  | 'likes_asc'
  | 'revenue_desc'
  | 'revenue_asc'
  | 'viewers_desc'
  | 'viewers_asc'
>('latest')
const vodCategory = ref<string>('all')
const VOD_PAGE_SIZE = 12
const vodPage = ref(1)

const { handleImageError } = createImageErrorHandler()

const liveItems = ref<LiveItem[]>([])
const scheduledItems = ref<ReservationItem[]>([])
const vodItems = ref<AdminVodItem[]>([])
const categories = ref<BroadcastCategory[]>([])
const loopGap = 14
const carouselRefs = ref<Record<LoopKind, HTMLElement | null>>({
  live: null,
  scheduled: null,
  vod: null,
})
const slideWidths = ref<Record<LoopKind, number>>({
  live: 0,
  scheduled: 0,
  vod: 0,
})
const loopIndex = ref<Record<LoopKind, number>>({
  live: 0,
  scheduled: 0,
  vod: 0,
})
const loopTransition = ref<Record<LoopKind, boolean>>({
  live: true,
  scheduled: true,
  vod: true,
})
const autoTimers = ref<Record<LoopKind, number | null>>({
  live: null,
  scheduled: null,
  vod: null,
})
const loopEnabled = ref<Record<LoopKind, boolean>>({
  live: false,
  scheduled: false,
  vod: false,
})
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'
const sseSource = ref<EventSource | null>(null)
const sseConnected = ref(false)
const statsTimer = ref<number | null>(null)
const sseRetryCount = ref(0)
const sseRetryTimer = ref<number | null>(null)
const refreshTimer = ref<number | null>(null)

const visibleLive = computed(() => activeTab.value === 'all' || activeTab.value === 'live')
const visibleScheduled = computed(() => activeTab.value === 'all' || activeTab.value === 'scheduled')
const visibleVod = computed(() => activeTab.value === 'all' || activeTab.value === 'vod')

const updateTabQuery = (tab: LiveTab, replace = true) => {
  const query = { ...route.query, tab }
  const action = replace ? router.replace : router.push
  action({ query }).catch(() => {})
}

const setTab = (tab: LiveTab, replace = false) => {
  activeTab.value = tab
  updateTabQuery(tab, replace)
}

const toDateMs = (raw: string | undefined) => {
  if (!raw) return 0
  const parsed = parseLiveDate(raw).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

const toNumber = (value: unknown, fallback = 0) => {
  const parsed = Number(value)
  return Number.isNaN(parsed) ? fallback : parsed
}

const formatDateTime = (value?: string) => {
  if (!value) return ''
  const date = parseLiveDate(value)
  if (Number.isNaN(date.getTime())) return value
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${yyyy}.${mm}.${dd} ${hh}:${min}`
}

const resolveCategoryId = (categoryName: string) => {
  if (categoryName === 'all') return undefined
  return categories.value.find((category) => category.name === categoryName)?.id
}

const mapLiveSortType = () => {
  if (liveSort.value === 'reports_desc') return 'REPORT'
  if (liveSort.value === 'latest') return 'LATEST'
  if (liveSort.value === 'viewers_desc') return 'VIEWER_DESC'
  if (liveSort.value === 'viewers_asc') return 'VIEWER_ASC'
  return undefined
}

const mapScheduledSortType = () => {
  if (scheduledSort.value === 'nearest') return 'START_ASC'
  if (scheduledSort.value === 'latest') return 'LATEST'
  if (scheduledSort.value === 'oldest') return 'OLDEST'
  return undefined
}

const mapScheduledStatusFilter = () => {
  if (scheduledStatus.value === 'reserved') return 'RESERVED'
  if (scheduledStatus.value === 'canceled') return 'CANCELED'
  return undefined
}

const mapVodSortType = () => {
  if (vodSort.value === 'latest') return 'LATEST'
  if (vodSort.value === 'oldest') return 'OLDEST'
  if (vodSort.value === 'reports_desc') return 'REPORT'
  if (vodSort.value === 'likes_desc') return 'LIKE_DESC'
  if (vodSort.value === 'likes_asc') return 'LIKE_ASC'
  if (vodSort.value === 'revenue_desc') return 'SALES'
  if (vodSort.value === 'revenue_asc') return 'SALES_ASC'
  if (vodSort.value === 'viewers_desc') return 'VIEWER_DESC'
  if (vodSort.value === 'viewers_asc') return 'VIEWER_ASC'
  return undefined
}

const mapVodVisibility = () => {
  if (vodVisibility.value === 'public') return true
  if (vodVisibility.value === 'private') return false
  return undefined
}

const mapLiveItem = (item: any, kind: 'live' | 'scheduled' | 'vod'): LiveItem => {
  const startAtValue = item.startAt ?? item.scheduledAt
  const startAtMs = startAtValue ? toDateMs(startAtValue) : undefined
  const endAtMs = item.endAt ? toDateMs(item.endAt) : getScheduledEndMs(startAtMs)
  const status = normalizeBroadcastStatus(item.status)
  const rawPublic = item.isPublic ?? item.public
  const visibility = typeof rawPublic === 'boolean' ? (rawPublic ? 'public' : 'private') : 'private'
  const dateLabel = formatDateTime(startAtValue)
  const datetime = kind === 'vod' ? (dateLabel ? `업로드: ${dateLabel}` : '') : dateLabel
  const liveViewerCount = typeof item.liveViewerCount === 'number' ? item.liveViewerCount : undefined
  const viewerBadge = liveViewerCount ? `${liveViewerCount}명 시청 중` : undefined

  return {
    id: String(item.broadcastId),
    title: item.title ?? '',
    subtitle: item.categoryName ?? '',
    thumb: item.thumbnailUrl ?? '',
    datetime,
    ctaLabel: kind === 'vod' ? '상세보기' : undefined,
    sellerName: item.sellerName ?? '',
    status,
    startedAt: item.startAt,
    scheduledAt: item.scheduledAt ?? item.startAt,
    viewers: toNumber(liveViewerCount ?? item.viewerCount),
    likes: toNumber(item.totalLikes),
    reports: toNumber(item.reportCount),
    category: item.categoryName ?? '기타',
    startAtMs: Number.isNaN(startAtMs ?? NaN) ? undefined : startAtMs,
    endAtMs: Number.isNaN(endAtMs ?? NaN) ? undefined : endAtMs,
    lifecycleStatus: status,
    statusBadge: kind === 'vod' ? (visibility === 'private' ? '비공개' : 'VOD') : undefined,
    viewerBadge,
  }
}

const mapReservationItem = (item: any): ReservationItem => {
  const base = mapLiveItem(item, 'scheduled')
  return {
    ...base,
    sellerName: base.sellerName ?? '',
    status: base.status ?? 'RESERVED',
    category: base.category ?? '기타',
  }
}

const mapVodItem = (item: any): AdminVodItem => {
  const base = mapLiveItem(item, 'vod')
  const rawPublic = item.isPublic ?? item.public
  const visibility = typeof rawPublic === 'boolean' ? (rawPublic ? 'public' : 'private') : 'private'
  return {
    ...base,
    sellerName: base.sellerName ?? '',
    statusLabel: visibility === 'private' ? '비공개' : 'VOD',
    category: base.category ?? '기타',
    visibility,
    metrics: {
      reports: toNumber(item.reportCount),
      likes: toNumber(item.totalLikes),
      totalRevenue: toNumber(item.totalSales),
      maxViewers: toNumber(item.viewerCount),
    },
  }
}

const withLifecycleStatus = <T extends LiveItem>(item: T): T & { startAtMs?: number; endAtMs?: number; lifecycleStatus?: BroadcastStatus } => {
  const startAtMs = item.startAtMs ?? item.startedAtMs ?? toDateMs(item.scheduledAt ?? item.startedAt ?? item.datetime)
  const endAtMs = getScheduledEndMs(startAtMs, item.endAtMs)
  const lifecycleStatus = computeLifecycleStatus({
    status: item.lifecycleStatus ?? item.status,
    startAtMs,
    endAtMs,
  })
  return {
    ...item,
    startAtMs,
    endAtMs,
    lifecycleStatus,
  }
}

const getLifecycleStatus = (item: LiveItem): BroadcastStatus => normalizeBroadcastStatus(item.lifecycleStatus ?? item.status)
const formatStatusLabel = (status?: BroadcastStatus | string | null) => getBroadcastStatusLabel(status)

const isPastScheduledEnd = (item: LiveItem): boolean => {
  const endAtMs = getScheduledEndMs(item.startAtMs, item.endAtMs)
  if (!endAtMs) return false
  return Date.now() > endAtMs
}

const loadAdminData = async () => {
  try {
    const liveCategoryId = resolveCategoryId(liveCategory.value)
    const scheduledCategoryId = resolveCategoryId(scheduledCategory.value)
    const vodCategoryId = resolveCategoryId(vodCategory.value)
    const [liveList, scheduledList, vodList] = await Promise.all([
      fetchAdminBroadcasts({
        tab: 'LIVE',
        size: 200,
        sortType: mapLiveSortType(),
        categoryId: liveCategoryId,
      }),
      fetchAdminBroadcasts({
        tab: 'RESERVED',
        size: 200,
        sortType: mapScheduledSortType(),
        statusFilter: mapScheduledStatusFilter(),
        categoryId: scheduledCategoryId,
      }),
      fetchAdminBroadcasts({
        tab: 'VOD',
        size: 200,
        sortType: mapVodSortType(),
        categoryId: vodCategoryId,
        isPublic: mapVodVisibility(),
        startDate: vodStartDate.value || undefined,
        endDate: vodEndDate.value || undefined,
      }),
    ])
    liveItems.value = liveList.map((item) => mapLiveItem(item, 'live'))
    scheduledItems.value = scheduledList.map((item) => mapReservationItem(item))
    vodItems.value = vodList.map((item) => mapVodItem(item))
  } catch {
    liveItems.value = []
    scheduledItems.value = []
    vodItems.value = []
  }
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
    void loadAdminData()
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
    sseConnected.value = true
    sseRetryCount.value = 0
    scheduleRefresh()
  }
  source.onerror = () => {
    sseConnected.value = false
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
  if (!sseConnected.value) {
    connectSse()
    return
  }
  scheduleRefresh()
}

const startStatsPolling = () => {
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void updateLiveViewerCounts()
    }
  }, 5000)
}

const isStatsTarget = (item: LiveItem) => {
  const status = normalizeBroadcastStatus(item.status)
  if (status === 'ON_AIR' || status === 'READY' || status === 'ENDED' || status === 'STOPPED') return true
  const startAtMs = item.startAtMs
  const endAtMs = item.endAtMs ?? getScheduledEndMs(startAtMs)
  if (!startAtMs || !endAtMs) return false
  const now = Date.now()
  return now >= startAtMs && now <= endAtMs
}

const updateLiveViewerCounts = async () => {
  const targets = [...liveItems.value, ...scheduledItems.value]
    .filter((item) => isStatsTarget(item))
    .filter((item, index, list) => list.findIndex((candidate) => candidate.id === item.id) === index)
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
  const applyStats = <T extends LiveItem>(items: T[]): T[] =>
    items.map((item): T => {
      const stats = statsMap.get(item.id)
      if (!stats) return item
      const viewers = stats.viewerCount ?? item.viewers ?? 0
      return {
        ...item,
        viewers,
        viewerBadge: `${viewers}명 시청 중`,
        likes: stats.likeCount ?? item.likes ?? 0,
        reports: stats.reportCount ?? item.reports ?? 0,
      } as T
    })
  liveItems.value = applyStats(liveItems.value)
  scheduledItems.value = applyStats(scheduledItems.value)
}

const loadCategories = async () => {
  try {
    categories.value = await fetchCategories()
  } catch {
    categories.value = []
  }
}

const liveItemsWithStatus = computed(() => liveItems.value.map(withLifecycleStatus))
const scheduledItemsWithStatus = computed(() => scheduledItems.value.map(withLifecycleStatus))
const vodItemsWithStatus = computed(() => vodItems.value.map(withLifecycleStatus))

const liveDisplayItems = computed(() => {
  const byId = new Map<string, LiveItem>()
  ;[...scheduledItemsWithStatus.value, ...liveItemsWithStatus.value].forEach((item) => {
    const status = getLifecycleStatus(item)
    if (!LIVE_SECTION_STATUSES.includes(status)) return
    if (status === 'STOPPED' && isPastScheduledEnd(item)) return
    byId.set(item.id, { ...item, lifecycleStatus: status })
  })
  return Array.from(byId.values())
})

const stoppedVodItems = computed<AdminVodItem[]>(() => {
  const sources = [...liveItemsWithStatus.value, ...scheduledItemsWithStatus.value]
  return sources
    .filter((item) => getLifecycleStatus(item) === 'STOPPED' && isPastScheduledEnd(item))
    .map((item) => ({
      ...item,
      sellerName: item.sellerName ?? '',
      statusLabel: 'STOPPED',
      category: item.category ?? '기타',
      lifecycleStatus: 'STOPPED',
      visibility: 'public',
      datetime: item.datetime ?? (item.startedAt ? `업로드: ${item.startedAt}` : ''),
      metrics: (item as AdminVodItem).metrics ?? {
        likes: item.likes ?? 0,
        reports: item.reports ?? 0,
        totalRevenue: 0,
        maxViewers: item.viewers ?? 0,
      },
      startAtMs: item.startAtMs,
      endAtMs: item.endAtMs,
    }))
})

const vodDisplayItems = computed(() => [...vodItemsWithStatus.value, ...stoppedVodItems.value])

const filteredLive = computed(() => {
  let filtered = [...liveDisplayItems.value]
  if (liveCategory.value !== 'all') {
    filtered = filtered.filter((item) => item.category === liveCategory.value)
  }
  filtered.sort((a, b) => {
    const aStatus = getLifecycleStatus(a)
    const bStatus = getLifecycleStatus(b)
    if (statusPriority[aStatus] !== statusPriority[bStatus]) {
      return statusPriority[aStatus] - statusPriority[bStatus]
    }
    if (liveSort.value === 'reports_desc') return (b.reports ?? 0) - (a.reports ?? 0)
    if (liveSort.value === 'latest') return toDateMs(b.startedAt) - toDateMs(a.startedAt)
    if (liveSort.value === 'viewers_desc') return (b.viewers ?? 0) - (a.viewers ?? 0)
    if (liveSort.value === 'viewers_asc') return (a.viewers ?? 0) - (b.viewers ?? 0)
    return 0
  })
  return filtered
})

const filteredScheduled = computed(() => {
  const base = scheduledItemsWithStatus.value.filter((item) =>
    SCHEDULED_SECTION_STATUSES.includes(getLifecycleStatus(item)),
  )
  const matchesCategory = scheduledCategory.value === 'all' ? base : base.filter((item) => item.category === scheduledCategory.value)
  const reserved = matchesCategory.filter((item) => getLifecycleStatus(item) === 'RESERVED')
  const canceled = matchesCategory.filter((item) => getLifecycleStatus(item) === 'CANCELED')

  const sortScheduled = (items: ReservationItem[]) =>
    items.slice().sort((a, b) => {
      const aDate = a.startAtMs ?? toDateMs(a.datetime)
      const bDate = b.startAtMs ?? toDateMs(b.datetime)
      if (scheduledSort.value === 'latest') return bDate - aDate
      if (scheduledSort.value === 'oldest') return aDate - bDate
      return aDate - bDate
    })

  if (scheduledStatus.value === 'canceled') return sortScheduled(canceled)
  if (scheduledStatus.value === 'reserved') return sortScheduled(reserved)
  return [...sortScheduled(reserved), ...sortScheduled(canceled)]
})

const filteredVods = computed(() => {
  const startMs = vodStartDate.value ? Date.parse(`${vodStartDate.value}T00:00:00`) : null
  const endMs = vodEndDate.value ? Date.parse(`${vodEndDate.value}T23:59:59`) : null

  let filtered = [...vodDisplayItems.value].filter((item) => {
    const dateMs = item.startAtMs ?? toDateMs(item.startedAt)
    if (startMs && dateMs < startMs) return false
    if (endMs && dateMs > endMs) return false
    if (vodVisibility.value !== 'all' && vodVisibility.value !== item.visibility) return false
    return !(vodCategory.value !== 'all' && item.category !== vodCategory.value);
  })

  const sortVod = (items: AdminVodItem[]) =>
    items.slice().sort((a, b) => {
      if (vodSort.value === 'latest') return (b.startAtMs ?? 0) - (a.startAtMs ?? 0)
      if (vodSort.value === 'oldest') return (a.startAtMs ?? 0) - (b.startAtMs ?? 0)
      if (vodSort.value === 'reports_desc') return (b.metrics.reports ?? 0) - (a.metrics.reports ?? 0)
      if (vodSort.value === 'likes_desc') return (b.metrics.likes ?? 0) - (a.metrics.likes ?? 0)
      if (vodSort.value === 'likes_asc') return (a.metrics.likes ?? 0) - (b.metrics.likes ?? 0)
      if (vodSort.value === 'revenue_desc') return (b.metrics.totalRevenue ?? 0) - (a.metrics.totalRevenue ?? 0)
      if (vodSort.value === 'revenue_asc') return (a.metrics.totalRevenue ?? 0) - (b.metrics.totalRevenue ?? 0)
      if (vodSort.value === 'viewers_desc') return (b.metrics.maxViewers ?? 0) - (a.metrics.maxViewers ?? 0)
      if (vodSort.value === 'viewers_asc') return (a.metrics.maxViewers ?? 0) - (b.metrics.maxViewers ?? 0)
      return 0
    })

  const vodOnly = filtered.filter((item) => getLifecycleStatus(item) === 'VOD')
  const stoppedOnly = filtered.filter((item) => getLifecycleStatus(item) === 'STOPPED')

  filtered = [...sortVod(vodOnly), ...sortVod(stoppedOnly)]

  return filtered
})

const visibleLiveGridItems = computed(() => filteredLive.value.slice(0, LIVE_PAGE_SIZE * livePage.value))
const visibleScheduledItems = computed(() => filteredScheduled.value.slice(0, SCHEDULED_PAGE_SIZE * scheduledPage.value))
const visibleVodItems = computed(() => filteredVods.value.slice(0, VOD_PAGE_SIZE * vodPage.value))

const liveSentinelRef = ref<HTMLElement | null>(null)
const scheduledSentinelRef = ref<HTMLElement | null>(null)
const vodSentinelRef = ref<HTMLElement | null>(null)

const { sentinelRef: liveObserverRef } = useInfiniteScroll({
  canLoadMore: () => filteredLive.value.length > visibleLiveGridItems.value.length,
  loadMore: () => {
    livePage.value += 1
  },
  enabled: () => activeTab.value === 'live',
})

const { sentinelRef: scheduledObserverRef } = useInfiniteScroll({
  canLoadMore: () => filteredScheduled.value.length > visibleScheduledItems.value.length,
  loadMore: () => {
    scheduledPage.value += 1
  },
  enabled: () => activeTab.value === 'scheduled',
})

const { sentinelRef: vodObserverRef } = useInfiniteScroll({
  canLoadMore: () => filteredVods.value.length > visibleVodItems.value.length,
  loadMore: () => {
    vodPage.value += 1
  },
  enabled: () => activeTab.value === 'vod',
})

watch(liveSentinelRef, (value) => {
  liveObserverRef.value = value
}, { immediate: true })

watch(scheduledSentinelRef, (value) => {
  scheduledObserverRef.value = value
}, { immediate: true })

watch(vodSentinelRef, (value) => {
  vodObserverRef.value = value
}, { immediate: true })

const categoryOptions = computed(() => categories.value)

const liveSummary = computed<LiveItem[]>(() =>
  liveDisplayItems.value
    .filter((item) => LIVE_SECTION_STATUSES.includes(getLifecycleStatus(item)))
    .slice()
    .sort((a, b) => (b.viewers ?? 0) - (a.viewers ?? 0))
    .slice(0, 5),
)
const scheduledSummary = computed<ReservationItem[]>(() =>
  scheduledItemsWithStatus.value
    .filter((item) => getLifecycleStatus(item) === 'RESERVED')
    .slice()
    .sort((a, b) => (a.startAtMs ?? toDateMs(a.datetime)) - (b.startAtMs ?? toDateMs(b.datetime)))
    .slice(0, 5),
)
const vodSummary = computed<AdminVodItem[]>(() =>
  vodItemsWithStatus.value
    .filter((item) => getLifecycleStatus(item) === 'VOD')
    .slice()
    .sort((a, b) => (b.startAtMs ?? 0) - (a.startAtMs ?? 0))
    .slice(0, 5),
)

const buildLoopItems = <T>(items: T[], enableLoop: boolean): T[] => {
  if (!items.length) return []
  if (!enableLoop || items.length === 1) return items
  return items
}

const liveLoopItems = computed<LiveItem[]>(() => buildLoopItems(liveSummary.value, loopEnabled.value.live))
const scheduledLoopItems = computed<ReservationItem[]>(() => buildLoopItems(scheduledSummary.value, loopEnabled.value.scheduled))
const vodLoopItems = computed<AdminVodItem[]>(() => buildLoopItems(vodSummary.value, loopEnabled.value.vod))

const openReservationDetail = (id: string) => {
  if (!id) return
  router.push({ path: `/admin/live/reservations/${id}`, query: { tab: activeTab.value } }).catch(() => {})
}

const openLiveDetail = (id: string) => {
  if (!id) return
  router.push({ path: `/admin/live/now/${id}`, query: { tab: activeTab.value } }).catch(() => {})
}

const openVodDetail = (id: string) => {
  if (!id) return
  router.push({ path: `/admin/live/vods/${id}`, query: { tab: activeTab.value } }).catch(() => {})
}

const formatDDay = (item: { startAtMs?: number }) => {
  if (!item.startAtMs) return ''
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const start = new Date(item.startAtMs)
  start.setHours(0, 0, 0, 0)
  const diffDays = Math.round((start.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
  if (diffDays === 0) return 'D-Day'
  if (diffDays > 0) return `D-${diffDays}`
  return `D+${Math.abs(diffDays)}`
}

const refreshTabFromQuery = () => {
  const tab = route.query.tab
  if (tab === 'scheduled' || tab === 'live' || tab === 'vod' || tab === 'all') {
    activeTab.value = tab
    return
  }
  setTab('all', true)
}

const setCarouselRef = (kind: LoopKind) => (el: Element | ComponentPublicInstance | null) => {
  carouselRefs.value[kind] = el && typeof el === 'object' && '$el' in el ? ((el as ComponentPublicInstance).$el as HTMLElement | null) : ((el as HTMLElement) || null)
  nextTick(() => updateSlideWidth(kind))
}

const updateSlideWidth = (kind: LoopKind) => {
  const root = carouselRefs.value[kind]
  if (!root) return
  const card = root.querySelector<HTMLElement>('.live-card')
  slideWidths.value[kind] = card?.offsetWidth ?? 280
}

const isCarouselOverflowing = (kind: LoopKind) => {
  const root = carouselRefs.value[kind]
  if (!root) return false
  const viewport = root.parentElement
  if (!viewport || viewport.clientWidth === 0) return false
  const itemCount = baseItemsFor(kind).length
  if (itemCount <= 1) return false
  const cardWidth = slideWidths.value[kind] || root.querySelector<HTMLElement>('.live-card')?.offsetWidth || 0
  if (!cardWidth) return false
  const totalWidth = (cardWidth * itemCount) + (loopGap * (itemCount - 1))
  return totalWidth > viewport.clientWidth
}

const getTrackStyle = (kind: LoopKind) => {
  const width = (slideWidths.value[kind] || 280) + loopGap
  const translate = loopIndex.value[kind] * width
  return {
    transform: `translateX(-${translate}px)`,
    transition: loopTransition.value[kind] ? 'transform 0.6s ease' : 'none',
  }
}

const loopItemsFor = (kind: LoopKind) => {
  if (kind === 'live') return liveLoopItems.value
  if (kind === 'scheduled') return scheduledLoopItems.value
  return vodLoopItems.value
}

const baseItemsFor = (kind: LoopKind) => {
  if (kind === 'live') return liveSummary.value
  if (kind === 'scheduled') return scheduledSummary.value
  return vodSummary.value
}

const getBaseLoopIndex = (kind: LoopKind) => {
  const items = loopItemsFor(kind)
  if (!items.length) return 0
  return 0
}

const handleLoopTransitionEnd = (kind: LoopKind) => {
  if (!loopEnabled.value[kind]) return
  if (!isCarouselOverflowing(kind)) return
}

const stepCarousel = (kind: LoopKind, delta: -1 | 1) => {
  const items = loopItemsFor(kind)
  if (items.length <= 1) return
  if (!loopEnabled.value[kind]) {
    const nextIndex = Math.min(Math.max(loopIndex.value[kind] + delta, 0), items.length - 1)
    loopTransition.value[kind] = true
    loopIndex.value[kind] = nextIndex
    return
  }
  if (!isCarouselOverflowing(kind)) {
    loopIndex.value[kind] = getBaseLoopIndex(kind)
    return
  }
  const nextIndex = loopIndex.value[kind] + delta
  const lastIndex = items.length - 1
  if (nextIndex > lastIndex) {
    loopTransition.value[kind] = false
    loopIndex.value[kind] = 0
    requestAnimationFrame(() => {
      loopTransition.value[kind] = true
    })
    restartAutoLoop(kind)
    return
  }
  if (nextIndex < 0) {
    loopTransition.value[kind] = false
    loopIndex.value[kind] = lastIndex
    requestAnimationFrame(() => {
      loopTransition.value[kind] = true
    })
    restartAutoLoop(kind)
    return
  }
  loopTransition.value[kind] = true
  loopIndex.value[kind] = nextIndex
  restartAutoLoop(kind)
}

const startAutoLoop = (kind: LoopKind) => {
  stopAutoLoop(kind)
  if (!loopEnabled.value[kind]) {
    loopIndex.value[kind] = getBaseLoopIndex(kind)
    return
  }
  if (!isCarouselOverflowing(kind)) {
    loopIndex.value[kind] = getBaseLoopIndex(kind)
    return
  }
  autoTimers.value[kind] = window.setInterval(() => {
    if (!isCarouselOverflowing(kind)) {
      stopAutoLoop(kind)
      loopIndex.value[kind] = getBaseLoopIndex(kind)
      return
    }
    stepCarousel(kind, 1)
  }, 3200)
}

const stopAutoLoop = (kind: LoopKind) => {
  const timer = autoTimers.value[kind]
  if (timer) window.clearInterval(timer)
  autoTimers.value[kind] = null
}

const restartAutoLoop = (kind: LoopKind) => {
  stopAutoLoop(kind)
  startAutoLoop(kind)
}

const resetLoop = (kind: LoopKind) => {
  loopIndex.value[kind] = getBaseLoopIndex(kind)
  loopTransition.value[kind] = true
  nextTick(() => {
    updateSlideWidth(kind)
    if (loopEnabled.value[kind] && isCarouselOverflowing(kind)) {
      startAutoLoop(kind)
    } else {
      stopAutoLoop(kind)
      loopIndex.value[kind] = getBaseLoopIndex(kind)
    }
  })
}

const resetAllLoops = () => {
  resetLoop('live')
  resetLoop('scheduled')
  resetLoop('vod')
}

const updateLoopEnabled = () => {
  const enable = activeTab.value === 'all'
  loopEnabled.value = {
    live: enable,
    scheduled: enable,
    vod: enable,
  }
}

const handleResize = () => {
  updateSlideWidth('live')
  updateSlideWidth('scheduled')
  updateSlideWidth('vod')
  restartAutoLoop('live')
  restartAutoLoop('scheduled')
  restartAutoLoop('vod')
}

watch(
  () => route.query.tab,
  () => refreshTabFromQuery(),
)

watch(
  () => activeTab.value,
  () => {
    updateLoopEnabled()
    resetAllLoops()
  },
  { immediate: true },
)

watch([liveCategory, liveSort], () => {
  livePage.value = 1
  resetLoop('live')
  void loadAdminData()
})

watch([scheduledStatus, scheduledCategory, scheduledSort], () => {
  scheduledPage.value = 1
  void loadAdminData()
})

watch([vodStartDate, vodEndDate, vodVisibility, vodCategory, vodSort], () => {
  vodPage.value = 1
  void loadAdminData()
})

watch(
  () => liveSummary.value,
  () => resetLoop('live'),
  { deep: true },
)

watch(
  () => scheduledSummary.value,
  () => resetLoop('scheduled'),
  { deep: true },
)

watch(
  () => vodSummary.value,
  () => resetLoop('vod'),
  { deep: true },
)

onMounted(() => {
  refreshTabFromQuery()
  void loadCategories()
  loadAdminData()
  connectSse()
  startStatsPolling()
  nextTick(() => {
    resetAllLoops()
    handleResize()
  })
  window.addEventListener('resize', handleResize)
  window.addEventListener('visibilitychange', handleSseVisibilityChange)
  window.addEventListener('focus', handleSseVisibilityChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('visibilitychange', handleSseVisibilityChange)
  window.removeEventListener('focus', handleSseVisibilityChange)
  stopAutoLoop('live')
  stopAutoLoop('scheduled')
  stopAutoLoop('vod')
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  sseRetryTimer.value = null
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = null
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = null
  sseSource.value?.close()
})
</script>

<template>
  <div>
    <PageHeader eyebrow="DESKIT" title="방송관리" />

    <header class="live-header">
      <div class="live-header__spacer" aria-hidden="true"></div>

      <div class="live-tabs" role="tablist" aria-label="방송 상태">
        <button
          type="button"
          class="live-tab"
          :class="{ 'live-tab--active': activeTab === 'all' }"
          @click="setTab('all')"
        >
          전체
        </button>
        <button
          type="button"
          class="live-tab"
          :class="{ 'live-tab--active': activeTab === 'scheduled' }"
          @click="setTab('scheduled')"
        >
          예약
        </button>
        <button
          type="button"
          class="live-tab"
          :class="{ 'live-tab--active': activeTab === 'live' }"
          @click="setTab('live')"
        >
          방송 중
        </button>
        <button
          type="button"
          class="live-tab"
          :class="{ 'live-tab--active': activeTab === 'vod' }"
          @click="setTab('vod')"
        >
          VOD
        </button>
      </div>

      <div class="live-header__right"></div>
    </header>

    <section v-if="visibleLive" class="live-section">
      <div class="live-section__head">
        <div class="live-section__title">
          <h3>방송 중</h3>
        </div>
        <div class="live-section__controls">
          <p v-if="activeTab === 'live'" class="ds-section-sub">현재 진행 중인 라이브 방송입니다.</p>
          <button v-else class="link-more" type="button" @click="setTab('live')">+ 더보기</button>
        </div>
      </div>

      <div v-if="activeTab === 'live'" class="control-stack">
        <div class="filter-row">
          <label class="inline-filter">
            <span>카테고리</span>
            <select v-model="liveCategory">
              <option value="all">모든 카테고리</option>
              <option v-for="category in categoryOptions" :key="category.id" :value="category.name">{{ category.name }}</option>
            </select>
          </label>
          <label class="inline-filter">
            <span>정렬</span>
            <select v-model="liveSort">
              <option value="reports_desc">신고가 많은 순</option>
              <option value="latest">최신순</option>
              <option value="viewers_desc">시청자가 많은 순</option>
              <option value="viewers_asc">시청자가 적은 순</option>
            </select>
          </label>
        </div>
      </div>

      <div
        v-if="activeTab === 'live'"
        class="live-grid"
        :class="{ 'live-grid--empty': !visibleLiveGridItems.length }"
        aria-label="방송 중 목록"
      >
        <template v-if="visibleLiveGridItems.length">
          <article
            v-for="item in visibleLiveGridItems"
            :key="item.id"
            class="live-card ds-surface live-card--clickable"
            @click="openLiveDetail(item.id)"
          >
            <div class="live-thumb">
              <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" @error="handleImageError" />
              <div class="live-badges">
                <span class="badge badge--live">{{ formatStatusLabel(getLifecycleStatus(item)) }}</span>
                <span class="badge badge--viewer">시청자 {{ item.viewers }}명</span>
              </div>
            </div>
            <div class="live-body">
              <div class="live-meta">
                <p class="live-title">{{ item.title }}</p>
                <p class="live-date">시작: {{ item.startedAt }}</p>
                <p class="live-seller">{{ item.sellerName }}</p>
                <p class="live-viewers">신고 {{ item.reports ?? 0 }}건 · 좋아요 {{ item.likes }}</p>
              </div>
            </div>
          </article>

          <div
            v-if="filteredLive.length > visibleLiveGridItems.length"
            ref="liveSentinelRef"
            class="scroll-sentinel"
            aria-hidden="true"
          ></div>
        </template>

        <p v-else class="empty-section">진행 중인 방송이 없습니다. 라이브 시작 시 목록이 표시됩니다.</p>
      </div>

      <div v-else class="carousel-wrap">
        <button
          type="button"
          class="carousel-btn carousel-btn--left"
          aria-label="방송 중 왼쪽 이동"
          @click="stepCarousel('live', -1)"
        >
          ‹
        </button>

        <div class="live-carousel live-carousel--loop">
          <div
            class="live-carousel__track"
            :class="{ 'live-carousel__track--empty': !liveLoopItems.length }"
            :style="getTrackStyle('live')"
            :ref="setCarouselRef('live')"
            aria-label="방송 중 목록"
            @transitionend="handleLoopTransitionEnd('live')"
          >
            <template v-if="liveLoopItems.length">
              <article
                v-for="(item, idx) in liveLoopItems"
                :key="`${item.id}-${idx}`"
                class="live-card ds-surface live-card--clickable"
                @click="openLiveDetail(item.id)"
              >
                <div class="live-thumb">
                  <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" @error="handleImageError" />
                  <div class="live-badges">
                    <span class="badge badge--live">{{ formatStatusLabel(getLifecycleStatus(item)) }}</span>
                    <span class="badge badge--viewer">시청자 {{ item.viewers }}명</span>
                  </div>
                </div>
                <div class="live-body">
                  <div class="live-meta">
                    <p class="live-title">{{ item.title }}</p>
                    <p class="live-date">시작: {{ item.startedAt }}</p>
                    <p class="live-seller">{{ item.sellerName }}</p>
                    <p class="live-viewers">신고 {{ item.reports ?? 0 }}건 · 좋아요 {{ item.likes }}</p>
                  </div>
                </div>
              </article>
            </template>

            <p v-else class="empty-section live-carousel__empty">
              진행 중인 방송이 없습니다. 라이브 시작 시 목록이 표시됩니다.
            </p>
          </div>
        </div>

        <button
          type="button"
          class="carousel-btn carousel-btn--right"
          aria-label="방송 중 오른쪽 이동"
          @click="stepCarousel('live', 1)"
        >
          ›
        </button>
      </div>
    </section>

    <section v-if="visibleScheduled" class="live-section">
      <div class="live-section__head">
        <div class="live-section__title">
          <h3>예약된 방송</h3>
        </div>
        <div class="live-section__controls">
          <p v-if="activeTab === 'scheduled'" class="ds-section-sub">예정된 라이브 스케줄을 관리하세요.</p>
          <button
            v-else
            class="link-more"
            type="button"
            @click="setTab('scheduled')"
          >
            + 더보기
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'scheduled'" class="control-stack">
        <div class="filter-row">
          <label class="inline-filter">
            <span>상태</span>
            <select v-model="scheduledStatus">
              <option value="all">전체</option>
              <option value="reserved">예약중</option>
              <option value="canceled">취소됨</option>
            </select>
          </label>
          <label class="inline-filter">
            <span>카테고리</span>
            <select v-model="scheduledCategory">
              <option value="all">모든 카테고리</option>
              <option v-for="category in categoryOptions" :key="category.id" :value="category.name">{{ category.name }}</option>
            </select>
          </label>
          <label class="inline-filter">
            <span>정렬</span>
            <select v-model="scheduledSort">
              <option value="nearest">방송 시간이 가까운 순</option>
              <option value="latest">최신순</option>
              <option value="oldest">오래된 순</option>
            </select>
          </label>
        </div>
      </div>

      <div
        v-if="activeTab === 'scheduled'"
        class="scheduled-grid"
        :class="{ 'scheduled-grid--empty': !visibleScheduledItems.length }"
        aria-label="예약 방송 목록"
      >
        <template v-if="visibleScheduledItems.length">
          <article
            v-for="item in visibleScheduledItems"
            :key="item.id"
            class="live-card ds-surface live-card--clickable"
            @click="openReservationDetail(item.id)"
          >
              <div class="live-thumb">
                <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" @error="handleImageError" />
                <div class="live-badges">
                  <span
                    class="badge badge--scheduled"
                    :class="{ 'badge--cancelled': getLifecycleStatus(item) === 'CANCELED' }"
                  >
                    {{ formatStatusLabel(getLifecycleStatus(item)) }}
                  </span>
                  <span class="badge badge--viewer">{{ formatDDay(item) }}</span>
                </div>
              </div>
            <div class="live-body">
              <div class="live-meta">
                <p class="live-title">{{ item.title }}</p>
                <p class="live-date">{{ item.datetime }}</p>
                <p class="live-seller">{{ item.sellerName }}</p>
                <p class="live-viewers">{{ item.category }}</p>
              </div>
            </div>
          </article>

          <div
            v-if="filteredScheduled.length > visibleScheduledItems.length"
            ref="scheduledSentinelRef"
            class="scroll-sentinel"
            aria-hidden="true"
          ></div>
        </template>

        <p v-else class="empty-section">예약된 방송이 없습니다.</p>
      </div>

      <div v-else class="carousel-wrap">
        <button
          type="button"
          class="carousel-btn carousel-btn--left"
          aria-label="예약 방송 왼쪽 이동"
          @click="stepCarousel('scheduled', -1)"
        >
          ‹
        </button>

        <div class="live-carousel live-carousel--loop">
          <div
            class="live-carousel__track"
            :class="{ 'live-carousel__track--empty': !scheduledLoopItems.length }"
            :style="getTrackStyle('scheduled')"
            :ref="setCarouselRef('scheduled')"
            aria-label="예약 방송 목록"
            @transitionend="handleLoopTransitionEnd('scheduled')"
          >
            <template v-if="scheduledLoopItems.length">
              <article
                v-for="(item, idx) in scheduledLoopItems"
                :key="`${item.id}-${idx}`"
                class="live-card ds-surface live-card--clickable"
                @click="openReservationDetail(item.id)"
              >
                <div class="live-thumb">
                  <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" @error="handleImageError" />
                  <div class="live-badges">
                    <span
                      class="badge badge--scheduled"
                      :class="{ 'badge--cancelled': getLifecycleStatus(item) === 'CANCELED' }"
                    >
                      {{ formatStatusLabel(getLifecycleStatus(item)) }}
                    </span>
                    <span class="badge badge--viewer">{{ formatDDay(item) }}</span>
                  </div>
                </div>
                <div class="live-body">
                  <div class="live-meta">
                    <p class="live-title">{{ item.title }}</p>
                    <p class="live-date">{{ item.datetime }}</p>
                    <p class="live-seller">{{ item.sellerName }}</p>
                    <p class="live-viewers">{{ item.category }}</p>
                  </div>
                </div>
              </article>
            </template>

            <p v-else class="empty-section live-carousel__empty">
              예약된 방송이 없습니다. 예약 데이터를 불러오면 자동으로 표시됩니다.
            </p>
          </div>
        </div>

        <button
          type="button"
          class="carousel-btn carousel-btn--right"
          aria-label="예약 방송 오른쪽 이동"
          @click="stepCarousel('scheduled', 1)"
        >
          ›
        </button>
      </div>
    </section>

    <section v-if="visibleVod" class="live-section">
      <div class="live-section__head">
        <div class="live-section__title">
          <h3>VOD</h3>
        </div>
        <div class="live-section__controls">
          <p v-if="activeTab === 'vod'" class="ds-section-sub">저장된 다시보기 콘텐츠를 확인합니다.</p>
          <button
            v-else
            class="link-more"
            type="button"
            @click="setTab('vod')"
          >
            + 더보기
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'vod'" class="control-stack">
        <div class="filter-row vod-filter-row">
          <label class="inline-filter">
            <span>날짜 시작</span>
            <input v-model="vodStartDate" type="date" />
          </label>
          <label class="inline-filter">
            <span>날짜 종료</span>
            <input v-model="vodEndDate" type="date" />
          </label>
          <label class="inline-filter">
            <span>공개 여부</span>
            <select v-model="vodVisibility">
              <option value="all">전체</option>
              <option value="public">공개</option>
              <option value="private">비공개</option>
            </select>
          </label>
          <label class="inline-filter">
            <span>카테고리</span>
            <select v-model="vodCategory">
              <option value="all">모든 카테고리</option>
              <option v-for="category in categoryOptions" :key="category.id" :value="category.name">{{ category.name }}</option>
            </select>
          </label>
          <label class="inline-filter">
            <span>정렬</span>
            <select v-model="vodSort">
              <option value="latest">최신순</option>
              <option value="reports_desc">신고 건수가 많은 순</option>
              <option value="oldest">오래된 순</option>
              <option value="likes_desc">좋아요가 높은 순</option>
              <option value="likes_asc">좋아요가 낮은 순</option>
              <option value="revenue_desc">매출액이 높은 순</option>
              <option value="revenue_asc">매출액이 낮은 순</option>
              <option value="viewers_desc">총 시청자 수가 높은 순</option>
              <option value="viewers_asc">총 시청자 수가 낮은 순</option>
            </select>
          </label>
        </div>
      </div>

      <div
        v-if="activeTab === 'vod'"
        class="vod-grid"
        :class="{ 'vod-grid--empty': !visibleVodItems.length }"
        aria-label="VOD 목록"
      >
        <template v-if="visibleVodItems.length">
          <article
            v-for="item in visibleVodItems"
            :key="item.id"
            class="live-card ds-surface live-card--clickable"
            @click="openVodDetail(item.id)"
          >
            <div class="live-thumb">
              <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" @error="handleImageError" />
              <div class="live-badges">
                <span class="badge badge--vod">{{ item.statusLabel }}</span>
                <span class="badge badge--viewer">신고 {{ item.metrics.reports }}</span>
              </div>
            </div>
            <div class="live-body">
              <div class="live-meta">
                <p class="live-title">{{ item.title }}</p>
                <p class="live-date">{{ item.datetime }}</p>
                <p class="live-seller">{{ item.sellerName }}</p>
                <p class="live-viewers">좋아요 {{ item.metrics.likes }} · 시청 {{ item.metrics.maxViewers }}</p>
              </div>
            </div>
          </article>

          <div
            v-if="filteredVods.length > visibleVodItems.length"
            ref="vodSentinelRef"
            class="scroll-sentinel"
            aria-hidden="true"
          ></div>
        </template>

        <p v-else class="empty-section">등록된 VOD가 없습니다.</p>
      </div>

      <div v-else class="carousel-wrap">
        <button
          type="button"
          class="carousel-btn carousel-btn--left"
          aria-label="VOD 왼쪽 이동"
          @click="stepCarousel('vod', -1)"
        >
          ‹
        </button>

        <div class="live-carousel live-carousel--loop">
          <div
            class="live-carousel__track"
            :class="{ 'live-carousel__track--empty': !vodLoopItems.length }"
            :style="getTrackStyle('vod')"
            :ref="setCarouselRef('vod')"
            aria-label="VOD 목록"
            @transitionend="handleLoopTransitionEnd('vod')"
          >
            <template v-if="vodLoopItems.length">
              <article
                v-for="(item, idx) in vodLoopItems"
                :key="`${item.id}-${idx}`"
                class="live-card ds-surface live-card--clickable"
                @click="openVodDetail(item.id)"
              >
                <div class="live-thumb">
                  <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" @error="handleImageError" />
                  <div class="live-badges">
                    <span class="badge badge--vod">{{ item.statusLabel }}</span>
                    <span class="badge badge--viewer">신고 {{ item.metrics.reports }}</span>
                  </div>
                </div>
                <div class="live-body">
                  <div class="live-meta">
                    <p class="live-title">{{ item.title }}</p>
                    <p class="live-date">{{ item.datetime }}</p>
                    <p class="live-seller">{{ item.sellerName }}</p>
                    <p class="live-viewers">좋아요 {{ item.metrics.likes }} · 시청 {{ item.metrics.maxViewers }}</p>
                  </div>
                </div>
              </article>
            </template>

            <p v-else class="empty-section live-carousel__empty">
              등록된 VOD가 없습니다. 영상이 업로드되면 자동으로 표시됩니다.
            </p>
          </div>
        </div>

        <button
          type="button"
          class="carousel-btn carousel-btn--right"
          aria-label="VOD 오른쪽 이동"
          @click="stepCarousel('vod', 1)"
        >
          ›
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.live-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.live-header__spacer {
  min-height: 1px;
}

.live-header__right {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.inline-filter {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 140px;
  font-weight: 800;
  color: var(--text-strong);
}

.inline-filter select,
.inline-filter input {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 10px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.filter-row {
  display: flex;
  width: 100%;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface);
}

.live-tabs {
  display: inline-flex;
  gap: 10px;
  justify-content: center;
}

.live-tab {
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.live-tab:hover {
  border-color: var(--primary-color);
  box-shadow: 0 8px 18px rgba(var(--primary-rgb), 0.12);
  transform: translateY(-1px);
}

.live-tab--active {
  background: var(--surface-weak);
  border-color: var(--primary-color);
}

.live-section {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.live-section:first-of-type {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.live-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.live-section__head h3 {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 900;
  color: var(--text-strong);
}

.live-section__controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.link-more {
  border: none;
  background: transparent;
  color: var(--primary-color);
  font-weight: 900;
  cursor: pointer;
  padding: 4px 6px;
}

.control-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  width: 100%;
  margin-bottom: 12px;
}

.vod-filter-row {
  align-items: flex-end;
}

.live-grid,
.scheduled-grid,
.vod-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.scheduled-grid--empty,
.vod-grid--empty,
.live-grid--empty {
  display: flex;
  justify-content: center;
  align-items: center;
}

.empty-section {
  text-align: center;
  color: var(--text-muted);
  font-weight: 800;
  padding: 18px 12px;
}

.live-carousel__empty {
  width: 100%;
}

.carousel-wrap {
  position: relative;
  padding: 0 8px;
}

.carousel-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--border-color);
  background: #fff;
  font-weight: 900;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
}

.carousel-btn--left {
  left: -6px;
}

.carousel-btn--right {
  right: -6px;
}

.live-carousel {
  overflow: hidden;
  padding: 10px 6px;
}

.live-carousel__track {
  display: flex;
  gap: 14px;
  will-change: transform;
}

.live-carousel__track--empty {
  justify-content: center;
  width: 100%;
}

.live-carousel__track .live-card {
  flex: 0 0 clamp(260px, 26vw, 320px);
  min-width: 260px;
}

.live-card {
  scroll-snap-align: start;
  border-radius: 14px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 260px;
}

.live-card--clickable {
  cursor: pointer;
}

.live-thumb {
  position: relative;
  height: 170px;
  background: var(--surface-weak);
}

.live-thumb__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.live-badges {
  position: absolute;
  top: 10px;
  left: 10px;
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  padding: 8px 10px;
  font-weight: 900;
  line-height: 1;
  font-size: 0.9rem;
}

.badge--viewer {
  background: rgba(15, 23, 42, 0.85);
  color: #fff;
}

.badge--live {
  background: #ef4444;
  color: #fff;
}

.badge--scheduled {
  background: rgba(15, 23, 42, 0.8);
  color: #fff;
}

.badge--cancelled {
  background: var(--surface-weak);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

.badge--vod {
  background: rgba(15, 23, 42, 0.8);
  color: #fff;
}

.live-body {
  padding: 14px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex: 1;
}

.live-meta {
  min-width: 0;
}

.live-title {
  margin: 0 0 8px;
  font-weight: 900;
  color: var(--text-strong);
  font-size: 1.02rem;
}

.live-date {
  margin: 0 0 6px;
  color: var(--text-soft);
  font-weight: 700;
  font-size: 0.95rem;
}

.live-seller {
  margin: 0;
  color: var(--text-muted);
  font-weight: 800;
  font-size: 0.9rem;
}

.live-viewers {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.live-card--empty {
  justify-content: center;
  padding: 18px;
}

.live-card__title {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
}

.live-card__meta {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-weight: 700;
}

.live-more {
  grid-column: 1 / -1;
  padding: 12px;
  border-radius: 12px;
  border: 1px dashed var(--border-color);
  background: var(--surface);
  font-weight: 900;
  cursor: pointer;
}

.scroll-sentinel {
  width: 100%;
  height: 1px;
  grid-column: 1 / -1;
}

@media (max-width: 1200px) {
  .live-grid,
  .scheduled-grid,
  .vod-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .live-header {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .live-grid,
  .scheduled-grid,
  .vod-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .carousel-wrap {
    padding: 0;
  }

  .live-grid,
  .scheduled-grid,
  .vod-grid {
    grid-template-columns: 1fr;
  }

  .live-section__controls {
    align-items: flex-start;
  }
}
</style>
