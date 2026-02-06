<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import DeviceSetupModal from '../../components/DeviceSetupModal.vue'
import { useInfiniteScroll } from '../../composables/useInfiniteScroll'
import {
  computeLifecycleStatus,
  getBroadcastStatusLabel,
  getScheduledEndMs,
  hasReachedStartTime,
  normalizeBroadcastStatus,
  type BroadcastStatus,
} from '../../lib/broadcastStatus'
import { parseLiveDate } from '../../lib/live/utils'
import { useNow } from '../../lib/live/useNow'
import {
  fetchBroadcastStats,
  fetchCategories,
  fetchMediaConfig,
  fetchSellerBroadcastDetail,
  fetchSellerBroadcastReport,
  fetchSellerBroadcasts,
  type BroadcastCategory,
} from '../../lib/live/api'
import { getAuthUser } from '../../lib/auth'
import { resolveViewerId } from '../../lib/live/viewer'
import {
  clearDraft,
  clearDraftRestoreDecision,
  loadDraft,
  clearWorkingDraft,
  setDraftRestoreDecision,
} from '../../composables/useLiveCreateDraft'

const router = useRouter()
const route = useRoute()
const { now } = useNow(1000)

type LiveTab = 'all' | 'scheduled' | 'live' | 'vod'
type CarouselKind = 'live' | 'scheduled' | 'vod'
type LoopKind = 'scheduled' | 'vod'

type LiveItem = {
  id: string
  title: string
  subtitle: string
  thumb: string
  datetime: string
  statusBadge?: string
  viewerBadge?: string
  ctaLabel: string
  likes?: number
  viewers?: number
  reports?: number
  visibility?: string | boolean
  createdAt?: string
  category?: string
  status?: string
  lifecycleStatus?: BroadcastStatus
  startAtMs?: number
  revenue?: number
  endAtMs?: number
}

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

const scheduledStatus = ref<'all' | 'reserved' | 'canceled'>('all')
const scheduledCategory = ref<string>('all')
const scheduledSort = ref<'nearest' | 'latest' | 'oldest'>('nearest')
const SCHEDULED_PAGE_SIZE = 12
const scheduledPage = ref(1)

const vodStartDate = ref('')
const vodEndDate = ref('')
const vodVisibility = ref<'all' | 'public' | 'private'>('all')
const vodSort = ref<'latest' | 'oldest' | 'likes_desc' | 'likes_asc' | 'viewers_desc' | 'viewers_asc' | 'revenue_desc' | 'revenue_asc'>(
  'latest',
)
const vodCategory = ref<string>('all')
const VOD_PAGE_SIZE = 12
const vodPage = ref(1)

const showDeviceModal = ref(false)
const selectedScheduled = ref<LiveItem | null>(null)

const liveItems = ref<LiveItem[]>([])
const liveProducts = ref<
  Array<{
    id: string
    title: string
    optionLabel: string
    status: string
    priceOriginal: number
    priceSale: number
    soldCount: number
    stockTotal: number
    pinned: boolean
    thumb: string
  }>
>([])
const sortedLiveProducts = computed(() => {
  const items = [...liveProducts.value]
  return items.sort((a, b) => {
    const aSoldOut = a.status === '품절'
    const bSoldOut = b.status === '품절'
    if (aSoldOut !== bSoldOut) return aSoldOut ? 1 : -1
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    return 0
  })
})

const liveStats = ref<{ status: string; viewers: string; likes: string; revenue: string; hasData: boolean } | null>(null)
const displayLiveStats = computed(() => liveStats.value ?? {
  status: '-',
  viewers: '-',
  likes: '-',
  revenue: '-',
  hasData: false,
})

const scheduledItems = ref<LiveItem[]>([])
const vodItems = ref<LiveItem[]>([])
const categories = ref<BroadcastCategory[]>([])

const loopGap = 14
const carouselRefs = ref<Record<LoopKind, HTMLElement | null>>({
  scheduled: null,
  vod: null,
})
const slideWidths = ref<Record<LoopKind, number>>({
  scheduled: 0,
  vod: 0,
})
const loopIndex = ref<Record<LoopKind, number>>({
  scheduled: 0,
  vod: 0,
})
const loopTransition = ref<Record<LoopKind, boolean>>({
  scheduled: true,
  vod: true,
})
const autoTimers = ref<Record<LoopKind, number | null>>({
  scheduled: null,
  vod: null,
})
const loopEnabled = ref<Record<LoopKind, boolean>>({
  scheduled: false,
  vod: false,
})
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const sseSource = ref<EventSource | null>(null)
const sseConnected = ref(false)
const sseRetryCount = ref(0)
const sseRetryTimer = ref<number | null>(null)
const refreshTimer = ref<number | null>(null)
const statsTimer = ref<number | null>(null)

const toDateMs = (item: LiveItem) => {
  const raw = item.createdAt || item.datetime || ''
  const parsed = Date.parse(raw.replace(/\./g, '-').replace(' ', 'T'))
  return Number.isNaN(parsed) ? 0 : parsed
}

const getLikes = (item: LiveItem) => (typeof item.likes === 'number' ? item.likes : 0)
const getViewers = (item: LiveItem) => (typeof item.viewers === 'number' ? item.viewers : 0)
const getVisibility = (item: LiveItem): 'public' | 'private' => {
  if (typeof item.visibility === 'boolean') return item.visibility ? 'public' : 'private'
  if (typeof item.visibility === 'string') {
    if (item.visibility === 'public' || item.visibility === '공개') return 'public'
    if (item.visibility === 'private' || item.visibility === '비공개') return 'private'
  }
  const rawPublic = (item as any)?.isPublic ?? (item as any)?.public
  if (typeof rawPublic === 'boolean') return rawPublic ? 'public' : 'private'
  return 'public'
}

const formatDDay = (item: LiveItem) => {
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

const withLifecycleStatus = (item: LiveItem): LiveItem => {
  const startAtMs = item.startAtMs ?? toDateMs(item)
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

const isPastScheduledEnd = (item: LiveItem): boolean => {
  const scheduledEndMs = getScheduledEndMs(item.startAtMs)
  if (!scheduledEndMs) return false
  return Date.now() > scheduledEndMs
}

const formatDateLabel = (ms?: number, prefix: string = '업로드'): string => {
  if (!ms) return ''
  const d = new Date(ms)
  const date = `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return `${prefix}: ${date} ${time}`
}

const resolveCategoryId = (categoryName: string) => {
  if (categoryName === 'all') return undefined
  return categories.value.find((category) => category.name === categoryName)?.id
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
  if (vodSort.value === 'likes_desc') return 'LIKE_DESC'
  if (vodSort.value === 'likes_asc') return 'LIKE_ASC'
  if (vodSort.value === 'viewers_desc') return 'VIEWER_DESC'
  if (vodSort.value === 'viewers_asc') return 'VIEWER_ASC'
  if (vodSort.value === 'revenue_desc') return 'SALES'
  if (vodSort.value === 'revenue_asc') return 'SALES_ASC'
  return undefined
}

const mapVodVisibility = () => {
  if (vodVisibility.value === 'public') return true
  if (vodVisibility.value === 'private') return false
  return undefined
}

const getLifecycleStatus = (item: LiveItem): BroadcastStatus => normalizeBroadcastStatus(item.lifecycleStatus ?? item.status)

const getLiveCtaLabel = (item: LiveItem): string => {
  const status = getLifecycleStatus(item)
  if (status === 'READY') {
    return hasReachedStartTime(item.startAtMs) ? '방송 시작' : '방송 입장'
  }
  if (status === 'ON_AIR') return '방송 입장'
  if (status === 'ENDED') return '방송 확인'
  if (status === 'STOPPED') return isPastScheduledEnd(item) ? '중단됨' : '방송 확인'
  return item.ctaLabel ?? '방송 입장'
}

const formatElapsed = (startAtMs?: number) => {
  if (!startAtMs) return ''
  const diffMs = Math.max(0, now.value.getTime() - startAtMs)
  const totalSeconds = Math.floor(diffMs / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
}

const formatStartTime = (startAtMs?: number) => {
  if (!startAtMs) return ''
  const date = new Date(startAtMs)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

const parseBroadcastId = (value?: string) => {
  const numeric = Number.parseInt(String(value ?? '').replace(/[^0-9]/g, ''), 10)
  return Number.isFinite(numeric) ? numeric : null
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

const parseAmount = (value: unknown) => {
  if (typeof value === 'number') return value
  if (typeof value === 'string') {
    const parsed = Number.parseFloat(value.replace(/,/g, ''))
    return Number.isNaN(parsed) ? 0 : parsed
  }
  return 0
}

const resolveRouteId = (item: LiveItem) => {
  const parsed = parseBroadcastId(item.id)
  return parsed ? String(parsed) : item.id
}

const mapBroadcastItem = (item: any, kind: 'live' | 'scheduled' | 'vod'): LiveItem => {
  const startAtValue = item.startAt ?? item.scheduledAt
  const startAtMs = startAtValue ? parseLiveDate(startAtValue).getTime() : undefined
  const endAtMs = item.endAt ? parseLiveDate(item.endAt).getTime() : getScheduledEndMs(startAtMs)
  const status = normalizeBroadcastStatus(item.status)
  const rawPublic = item.isPublic ?? item.public
  const visibility = typeof rawPublic === 'boolean' ? (rawPublic ? 'public' : 'private') : 'private'
  const dateLabel = formatDateTime(startAtValue)
  const datetime = kind === 'vod' ? (dateLabel ? `업로드: ${dateLabel}` : '') : dateLabel
  const viewers = typeof item.liveViewerCount === 'number'
    ? item.liveViewerCount
    : (typeof item.viewerCount === 'number' ? item.viewerCount : 0)
  const viewerBadge = typeof viewers === 'number' ? `${viewers}명 시청 중` : undefined

  return {
    id: String(item.broadcastId),
    title: item.title ?? '',
    subtitle: item.categoryName ?? '',
    thumb: item.thumbnailUrl ?? '',
    datetime,
    ctaLabel: kind === 'vod' ? '상세보기' : '방송 입장',
    category: item.categoryName ?? '기타',
    status,
    lifecycleStatus: status,
    startAtMs: Number.isNaN(startAtMs) ? undefined : startAtMs,
    endAtMs: Number.isNaN(endAtMs ?? NaN) ? undefined : endAtMs,
    viewers,
    likes: item.totalLikes ?? 0,
    reports: item.reportCount ?? 0,
    revenue: parseAmount(item.totalSales),
    visibility,
    statusBadge: kind === 'vod' ? (visibility === 'private' ? '비공개' : 'VOD') : undefined,
    viewerBadge,
  }
}

const liveItemsWithStatus = computed(() => liveItems.value.map(withLifecycleStatus))
const scheduledWithStatus = computed(() => scheduledItems.value.map(withLifecycleStatus))

const liveStageItems = computed(() => {
  const merged = [...scheduledWithStatus.value, ...liveItemsWithStatus.value]
  const byId = new Map<string, LiveItem>()
  merged.forEach((item) => {
    const lifecycleStatus = normalizeBroadcastStatus(item.lifecycleStatus ?? item.status)
    if (!LIVE_SECTION_STATUSES.includes(lifecycleStatus)) return
    if (lifecycleStatus === 'STOPPED' && isPastScheduledEnd(item)) return
    byId.set(item.id, { ...item, lifecycleStatus })
  })
  return Array.from(byId.values())
})

const liveItemsSorted = computed(() => {
  const sorted = [...liveStageItems.value]
  sorted.sort((a, b) => {
    const aStatus = normalizeBroadcastStatus(a.lifecycleStatus ?? a.status)
    const bStatus = normalizeBroadcastStatus(b.lifecycleStatus ?? b.status)
    if (statusPriority[aStatus] !== statusPriority[bStatus]) {
      return statusPriority[aStatus] - statusPriority[bStatus]
    }
    return (b.viewers ?? 0) - (a.viewers ?? 0)
  })
  return sorted
})

const currentLive = computed(() => liveItemsSorted.value[0] ?? null)
const showLiveStats = computed(() => Boolean(currentLive.value && liveStats.value?.hasData))
const showLiveProducts = computed(() => Boolean(currentLive.value && liveProducts.value.length))
const formatStatusLabel = (status?: BroadcastStatus | string | null) => getBroadcastStatusLabel(status)

const loadSellerData = async () => {
  try {
    const scheduledCategoryId = resolveCategoryId(scheduledCategory.value)
    const vodCategoryId = resolveCategoryId(vodCategory.value)
    const [liveList, scheduledList, vodList] = await Promise.all([
      fetchSellerBroadcasts({ tab: 'LIVE', size: 200 }),
      fetchSellerBroadcasts({
        tab: 'RESERVED',
        size: 200,
        sortType: mapScheduledSortType(),
        statusFilter: mapScheduledStatusFilter(),
        categoryId: scheduledCategoryId,
      }),
      fetchSellerBroadcasts({
        tab: 'VOD',
        size: 200,
        sortType: mapVodSortType(),
        categoryId: vodCategoryId,
        isPublic: mapVodVisibility(),
        startDate: vodStartDate.value || undefined,
        endDate: vodEndDate.value || undefined,
      }),
    ])
    liveItems.value = liveList.map((item) => mapBroadcastItem(item, 'live'))
    scheduledItems.value = scheduledList.map((item) => mapBroadcastItem(item, 'scheduled'))
    vodItems.value = vodList.map((item) => mapBroadcastItem(item, 'vod'))
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
    void loadSellerData()
  }, 500)
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
  const applyStats = (items: LiveItem[]) =>
    items.map((item) => {
      const stats = statsMap.get(item.id)
      if (!stats) return item
      const viewers = stats.viewerCount ?? item.viewers ?? 0
      return {
        ...item,
        viewers,
        viewerBadge: `${viewers}명 시청 중`,
        likes: stats.likeCount ?? item.likes ?? 0,
        reports: stats.reportCount ?? item.reports ?? 0,
      }
    })
  liveItems.value = applyStats(liveItems.value)
  scheduledItems.value = applyStats(scheduledItems.value)
}

const startStatsPolling = () => {
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = window.setInterval(() => {
    if (document.visibilityState !== 'visible') {
      return
    }
    void updateLiveViewerCounts()
    const current = currentLive.value
    if (current) {
      void loadCurrentLiveDetails(current)
    }
  }, 5000)
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

const loadCategories = async () => {
  try {
    categories.value = await fetchCategories()
  } catch {
    categories.value = []
  }
}

const vodItemsWithStatus = computed(() => vodItems.value.map(withLifecycleStatus))

const stoppedVodItems = computed<LiveItem[]>(() => {
  const sources = [...liveItemsWithStatus.value, ...scheduledWithStatus.value]
  return sources
    .filter(
      (item) =>
        normalizeBroadcastStatus(item.lifecycleStatus ?? item.status) === 'STOPPED' &&
        isPastScheduledEnd(item),
    )
    .map((item) => ({
      ...item,
      status: 'STOPPED',
      lifecycleStatus: 'STOPPED' as BroadcastStatus,
      statusBadge: 'STOPPED',
      visibility: item.visibility ?? 'public',
      datetime: item.datetime || formatDateLabel(item.startAtMs, '종료'),
    }))
})

const combinedVodItems = computed<LiveItem[]>(() => [...vodItemsWithStatus.value, ...stoppedVodItems.value])

const filteredVodItems = computed(() => {
  const startMs = vodStartDate.value ? Date.parse(`${vodStartDate.value}T00:00:00`) : null
  const endMs = vodEndDate.value ? Date.parse(`${vodEndDate.value}T23:59:59`) : null

  let filtered = combinedVodItems.value.filter((item) => {
    const dateMs = item.startAtMs ?? toDateMs(item)
    if (startMs && dateMs < startMs) return false
    if (endMs && dateMs > endMs) return false
    const visibility = getVisibility(item)
    if (vodVisibility.value !== 'all' && vodVisibility.value !== visibility) return false
    return !(vodCategory.value !== 'all' && item.category !== vodCategory.value);

  })

  const sortVodList = (items: LiveItem[]) =>
    items.slice().sort((a, b) => {
      if (vodSort.value === 'latest') return toDateMs(b) - toDateMs(a)
      if (vodSort.value === 'oldest') return toDateMs(a) - toDateMs(b)
      if (vodSort.value === 'likes_desc') return getLikes(b) - getLikes(a)
      if (vodSort.value === 'likes_asc') return getLikes(a) - getLikes(b)
      if (vodSort.value === 'viewers_desc') return getViewers(b) - getViewers(a)
      if (vodSort.value === 'viewers_asc') return getViewers(a) - getViewers(b)
      if (vodSort.value === 'revenue_desc') return (b.revenue ?? 0) - (a.revenue ?? 0)
      if (vodSort.value === 'revenue_asc') return (a.revenue ?? 0) - (b.revenue ?? 0)
      return 0
    })

  const vodOnly = filtered.filter((item) => getLifecycleStatus(item) === 'VOD')
  const stoppedOnly = filtered.filter((item) => getLifecycleStatus(item) === 'STOPPED')

  return [...sortVodList(vodOnly), ...sortVodList(stoppedOnly)]
})

const filteredScheduledItems = computed(() => {
  const base = scheduledWithStatus.value.filter((item) =>
    SCHEDULED_SECTION_STATUSES.includes(normalizeBroadcastStatus(item.lifecycleStatus ?? item.status)),
  )

  const matchesCategory =
    scheduledCategory.value === 'all' ? base : base.filter((item) => item.category === scheduledCategory.value)

  const reserved = matchesCategory.filter((item) => normalizeBroadcastStatus(item.lifecycleStatus ?? item.status) === 'RESERVED')
  const canceled = matchesCategory.filter((item) => normalizeBroadcastStatus(item.lifecycleStatus ?? item.status) === 'CANCELED')

  const sortScheduled = (items: LiveItem[]) =>
    items.slice().sort((a, b) => {
      const aDate = a.startAtMs ?? toDateMs(a)
      const bDate = b.startAtMs ?? toDateMs(b)
      if (scheduledSort.value === 'latest') return bDate - aDate
      if (scheduledSort.value === 'oldest') return aDate - bDate
      return aDate - bDate
    })

  if (scheduledStatus.value === 'canceled') return sortScheduled(canceled)
  if (scheduledStatus.value === 'reserved') return sortScheduled(reserved)
  return [...sortScheduled(reserved), ...sortScheduled(canceled)]
})

const categoryOptions = computed(() => categories.value)

const scheduledSummary = computed(() =>
  scheduledWithStatus.value
    .filter((item) => normalizeBroadcastStatus(item.lifecycleStatus ?? item.status) === 'RESERVED')
    .slice()
    .sort((a, b) => (a.startAtMs ?? toDateMs(a)) - (b.startAtMs ?? toDateMs(b)))
    .slice(0, 5),
)

const vodSummary = computed(() =>
  vodItemsWithStatus.value
    .filter((item) => getLifecycleStatus(item) === 'VOD')
    .slice()
    .sort((a, b) => (b.startAtMs ?? toDateMs(b)) - (a.startAtMs ?? toDateMs(a)))
    .slice(0, 5),
)

const visibleScheduledItems = computed(() => filteredScheduledItems.value.slice(0, SCHEDULED_PAGE_SIZE * scheduledPage.value))
const visibleVodItems = computed(() => filteredVodItems.value.slice(0, VOD_PAGE_SIZE * vodPage.value))

const buildLoopItems = (items: LiveItem[], enableLoop: boolean): LiveItem[] => {
  if (!items.length) return []
  if (!enableLoop || items.length === 1) return items
  return items
}

const scheduledLoopItems = computed<LiveItem[]>(() => buildLoopItems(scheduledSummary.value, loopEnabled.value.scheduled))
const vodLoopItems = computed<LiveItem[]>(() => buildLoopItems(vodSummary.value, loopEnabled.value.vod))

const visibleLive = computed(() => activeTab.value === 'all' || activeTab.value === 'live')
const visibleScheduled = computed(() => activeTab.value === 'all' || activeTab.value === 'scheduled')
const visibleVod = computed(() => activeTab.value === 'all' || activeTab.value === 'vod')

const scheduledSentinelRef = ref<HTMLElement | null>(null)
const vodSentinelRef = ref<HTMLElement | null>(null)

const { sentinelRef: scheduledObserverRef } = useInfiniteScroll({
  canLoadMore: () => filteredScheduledItems.value.length > visibleScheduledItems.value.length,
  loadMore: () => {
    scheduledPage.value += 1
  },
  enabled: () => activeTab.value === 'scheduled',
})

const { sentinelRef: vodObserverRef } = useInfiniteScroll({
  canLoadMore: () => filteredVodItems.value.length > visibleVodItems.value.length,
  loadMore: () => {
    vodPage.value += 1
  },
  enabled: () => activeTab.value === 'vod',
})
void scheduledSentinelRef
void vodSentinelRef

watch(scheduledSentinelRef, (value) => {
  scheduledObserverRef.value = value
}, { immediate: true })

watch(vodSentinelRef, (value) => {
  vodObserverRef.value = value
}, { immediate: true })

const loopItemsFor = (kind: LoopKind) => (kind === 'scheduled' ? scheduledLoopItems.value : vodLoopItems.value)
const baseItemsFor = (kind: LoopKind) => (kind === 'scheduled' ? scheduledSummary.value : vodSummary.value)

const getBaseLoopIndex = (kind: LoopKind) => {
  const items = loopItemsFor(kind)
  if (!items.length) return 0
  return 0
}

const setCarouselRef = (kind: LoopKind) => (el: Element | ComponentPublicInstance | null) => {
  const target =
    el && typeof el === 'object' && '$el' in el ? ((el as ComponentPublicInstance).$el as HTMLElement | null) : ((el as HTMLElement) || null)
  carouselRefs.value[kind] = target
  nextTick(() => updateSlideWidth(kind))
}

const updateSlideWidth = (kind: LoopKind) => {
  const root = carouselRefs.value[kind]
  if (!root) return
  const card = root.querySelector<HTMLElement>('.live-card')
  slideWidths.value[kind] = (card?.offsetWidth ?? 280)
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
  resetLoop('scheduled')
  resetLoop('vod')
}

const updateLoopEnabled = () => {
  const enable = activeTab.value === 'all'
  loopEnabled.value = {
    scheduled: enable,
    vod: enable,
  }
}

const handleResize = () => {
  updateSlideWidth('scheduled')
  updateSlideWidth('vod')
  restartAutoLoop('scheduled')
  restartAutoLoop('vod')
}

const updateTabQuery = (tab: LiveTab, replace = true) => {
  const query = { ...route.query, tab }
  const action = replace ? router.replace : router.push
  action({ query }).catch(() => {})
}

const setTab = (tab: LiveTab, replace = false) => {
  activeTab.value = tab
  updateTabQuery(tab, replace)
}

const handleCreate = () => {
  const savedDraft = loadDraft()
  clearWorkingDraft()
  if (savedDraft) {
    const shouldRestore = window.confirm('이전에 작성 중인 내용을 불러올까요?')
    if (shouldRestore) {
      setDraftRestoreDecision('accepted')
    } else {
      setDraftRestoreDecision('declined')
      clearDraft()
    }
  } else {
    clearDraftRestoreDecision()
  }
  router.push('/seller/live/create').catch(() => {})
}

const syncTabFromRoute = () => {
  const tab = route.query.tab
  if (tab === 'scheduled' || tab === 'live' || tab === 'vod' || tab === 'all') {
    activeTab.value = tab
    return
  }
  setTab('all', true)
}

watch(
  () => route.query.tab,
  () => {
    syncTabFromRoute()
  },
)

watch(
  () => activeTab.value,
  () => {
    updateLoopEnabled()
    resetAllLoops()
  },
  { immediate: true },
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

watch(
  () => [scheduledStatus.value, scheduledCategory.value, scheduledSort.value],
  () => {
    scheduledPage.value = 1
    void loadSellerData()
  },
)

watch(
  () => [vodStartDate.value, vodEndDate.value, vodVisibility.value, vodCategory.value, vodSort.value],
  () => {
    vodPage.value = 1
    void loadSellerData()
  },
)

watch(
  () => currentLive.value,
  (value) => {
    void loadCurrentLiveDetails(value)
  },
  { immediate: true },
)

const handleCta = async (kind: CarouselKind, item: LiveItem) => {
  if (kind === 'live') {
    const lifecycleStatus = getLifecycleStatus(item)
    if (lifecycleStatus === 'READY' && hasReachedStartTime(item.startAtMs)) {
      const id = parseBroadcastId(item.id)
      if (id) {
        try {
          const config = await fetchMediaConfig(id)
          if (config) {
            router.push({ path: `/seller/live/stream/${resolveRouteId(item)}`, query: { tab: activeTab.value } }).catch(() => {})
            return
          }
        } catch {
          // ignore
        }
      }
      selectedScheduled.value = item
      showDeviceModal.value = true
      return
    }
    router.push({ path: `/seller/live/stream/${resolveRouteId(item)}`, query: { tab: activeTab.value } }).catch(() => {})
    return
  }
  if (kind === 'scheduled') {
    router.push({ path: `/seller/broadcasts/reservations/${resolveRouteId(item)}`, query: { tab: activeTab.value } }).catch(() => {})
    return
  }
  router.push({ path: `/seller/broadcasts/vods/${resolveRouteId(item)}`, query: { tab: activeTab.value } }).catch(() => {})
}

const handleDeviceStart = () => {
  const target = selectedScheduled.value
  if (!target) return
  router.push({ path: `/seller/live/stream/${resolveRouteId(target)}`, query: { tab: activeTab.value } }).catch(() => {})
}

const openReservationDetail = (item: LiveItem) => {
  router.push({ path: `/seller/broadcasts/reservations/${resolveRouteId(item)}`, query: { tab: activeTab.value } }).catch(() => {})
}

const openVodDetail = (item: LiveItem) => {
  router.push({ path: `/seller/broadcasts/vods/${resolveRouteId(item)}`, query: { tab: activeTab.value } }).catch(() => {})
}

async function loadCurrentLiveDetails(item: LiveItem | null) {
  if (!item) {
    liveStats.value = null
    liveProducts.value = []
    return
  }
  const id = parseBroadcastId(item.id)
  if (!id) {
    liveStats.value = null
    liveProducts.value = []
    return
  }
  try {
    const [stats, report, detail] = await Promise.all([
      fetchBroadcastStats(id),
      fetchSellerBroadcastReport(id).catch(() => null),
      fetchSellerBroadcastDetail(id),
    ])
    const revenue = report ? parseAmount(report.totalSales) : 0
    const hasData = stats.viewerCount > 0 || stats.likeCount > 0 || revenue > 0
    liveStats.value = hasData
      ? {
        status: getLifecycleStatus(item),
        viewers: `${stats.viewerCount.toLocaleString()}명`,
        likes: stats.likeCount.toLocaleString(),
        revenue: `₩${Math.round(revenue).toLocaleString()}`,
        hasData,
      }
      : null
    liveProducts.value = (detail.products ?? []).map((product) => {
      const totalQty = product.bpQuantity ?? product.stockQty ?? 0
      const stockQty = product.stockQty ?? totalQty
      const soldCount = Math.max(0, totalQty - stockQty)
      return {
        id: String(product.productId),
        title: product.name,
        optionLabel: product.name,
        status: product.status === 'SOLDOUT' ? '품절' : '판매중',
        priceOriginal: product.originalPrice,
        priceSale: product.bpPrice,
        soldCount,
        stockTotal: stockQty,
        pinned: product.pinned,
        thumb: product.imageUrl ?? '',
      }
    })
  } catch {
    liveStats.value = null
    liveProducts.value = []
  }
}

onMounted(() => {
  void loadCategories()
  loadSellerData()
  syncTabFromRoute()
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
  stopAutoLoop('scheduled')
  stopAutoLoop('vod')
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('visibilitychange', handleSseVisibilityChange)
  window.removeEventListener('focus', handleSseVisibilityChange)
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

      <div class="live-header__right">
        <button type="button" class="live-create-btn" @click="handleCreate">방송 등록</button>
      </div>
    </header>

    <section v-if="visibleLive" class="live-section">
      <div class="live-section__head">
        <div class="live-section__title">
          <h3>방송 중</h3>
        </div>
        <div class="live-section__desc">
          <p v-if="activeTab !== 'all'" class="ds-section-sub">현재 진행 중인 라이브 방송입니다.</p>
          <button v-else class="link-more" type="button" @click="setTab('live')">+ 더보기</button>
        </div>
      </div>

      <div
        v-if="activeTab === 'live'"
        class="live-livecolumn"
        :class="{ 'live-livecolumn--empty': !currentLive }"
      >
        <article v-if="currentLive" class="live-feature ds-surface live-pane">
          <div class="live-feature__layout">
            <div class="live-feature__thumb">
              <img :src="currentLive.thumb" :alt="currentLive.title" loading="lazy" />
              <span class="badge badge--live live-feature__badge">
                {{ currentLive.statusBadge ?? formatStatusLabel(getLifecycleStatus(currentLive)) }}
              </span>
            </div>
            <div class="live-feature__info">
              <div>
                <div class="live-feature__title-row">
                  <h4>{{ currentLive.title }}</h4>
                </div>
                <p class="live-feature__seller">{{ currentLive.subtitle }}</p>
                <div class="live-feature__meta">
                  <span v-if="currentLive.viewerBadge" class="meta-pill">{{ currentLive.viewerBadge }}</span>
                  <span v-if="currentLive.startAtMs" class="meta-pill">경과 시간 · {{ formatElapsed(currentLive.startAtMs) }}</span>
                  <span v-if="currentLive.startAtMs" class="meta-pill">시작 · {{ formatStartTime(currentLive.startAtMs) }}</span>
                </div>
              </div>
              <button type="button" class="live-feature__cta" @click="handleCta('live', currentLive!)">
                {{ getLiveCtaLabel(currentLive!) }}
              </button>
            </div>
          </div>
        </article>
        <p v-else class="section-empty live-pane">현재 진행 중인 방송이 없습니다.</p>

        <section v-if="showLiveStats" class="live-stats live-stats--stacked live-pane">
          <div class="live-stats__head">
            <h4>실시간 통계</h4>
            <span class="live-stats__badge">
              <span class="live-stats__dot"></span>
              실시간 업데이트 중
            </span>
          </div>
          <div class="live-stats-grid">
            <article class="live-stat-card ds-surface">
              <p class="stat-label">방송 상태</p>
              <p class="stat-value">{{ displayLiveStats.status }}</p>
              <p class="stat-sub">정상 송출 중</p>
            </article>
            <article class="live-stat-card ds-surface">
              <p class="stat-label">동시 접속자 수</p>
              <p class="stat-value">{{ displayLiveStats.viewers }}</p>
              <p class="stat-sub">실시간 기준</p>
            </article>
            <article class="live-stat-card ds-surface">
              <p class="stat-label">좋아요 수</p>
              <p class="stat-value">{{ displayLiveStats.likes }}</p>
              <p class="stat-sub">최근 5분</p>
            </article>
            <article class="live-stat-card ds-surface">
              <p class="stat-label">현재 매출</p>
              <p class="stat-value">{{ displayLiveStats.revenue }}</p>
              <p class="stat-sub">실시간 집계</p>
            </article>
          </div>
        </section>

        <article v-if="showLiveProducts" class="live-products ds-surface live-pane">
          <div class="live-products__head">
            <div>
              <h4>판매 상품</h4>
              <p class="ds-section-sub">라이브에 등록된 상품이에요.</p>
            </div>
            <span class="live-products__count">{{ liveProducts.length }}개</span>
          </div>
          <div class="live-products__list">
            <div v-for="item in sortedLiveProducts" :key="item.id" class="product-row">
              <div class="product-thumb">
                <img :src="item.thumb" :alt="item.title" loading="lazy" />
                <span v-if="item.pinned" class="product-pin">PIN</span>
              </div>
              <div class="product-meta">
                <p class="product-title">{{ item.title }}</p>
                <p class="product-option">{{ item.optionLabel }}</p>
                <div class="product-badges">
                  <span class="product-status" :class="{ 'is-soldout': item.status === '품절' }">{{ item.status }}</span>
                </div>
              </div>
              <div class="product-right">
                <p class="product-price">
                  <span class="product-sale">{{ item.priceSale.toLocaleString('ko-KR') }}원</span>
                  <span class="product-origin">{{ item.priceOriginal.toLocaleString('ko-KR') }}원</span>
                </p>
                <p class="product-stock">판매 {{ item.soldCount }} · 재고 {{ item.stockTotal }}</p>
              </div>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="live-feature-wrap">
        <article v-if="currentLive" class="live-feature ds-surface">
          <div class="live-feature__layout">
            <div class="live-feature__thumb">
              <img :src="currentLive.thumb" :alt="currentLive.title" loading="lazy" />
              <span class="badge badge--live live-feature__badge">
                {{ currentLive.statusBadge ?? formatStatusLabel(getLifecycleStatus(currentLive)) }}
              </span>
            </div>
            <div class="live-feature__info">
              <div>
                <div class="live-feature__title-row">
                  <h4>{{ currentLive.title }}</h4>
                </div>
                <p class="live-feature__seller">{{ currentLive.subtitle }}</p>
                <div class="live-feature__meta">
                  <span v-if="currentLive.viewerBadge" class="meta-pill">{{ currentLive.viewerBadge }}</span>
                  <span v-if="currentLive.startAtMs" class="meta-pill">경과 시간 · {{ formatElapsed(currentLive.startAtMs) }}</span>
                  <span v-if="currentLive.startAtMs" class="meta-pill">시작 · {{ formatStartTime(currentLive.startAtMs) }}</span>
                </div>
              </div>
              <button type="button" class="live-feature__cta" @click="handleCta('live', currentLive!)">
                {{ getLiveCtaLabel(currentLive!) }}
              </button>
            </div>
          </div>
        </article>
        <p v-else class="section-empty">등록된 방송이 없습니다. 새 방송을 등록해보세요.</p>
      </div>
    </section>

    <section v-if="visibleScheduled" class="live-section">
      <div class="live-section__head">
        <div class="live-section__title">
          <h3>예약된 방송</h3>
        </div>
        <div class="live-section__desc">
          <p v-if="activeTab !== 'all'" class="ds-section-sub">예정된 라이브 스케줄을 관리하세요.</p>
          <button v-else class="link-more" type="button" @click="setTab('scheduled')">+ 더보기</button>
        </div>
      </div>

      <div v-if="activeTab === 'scheduled'" class="filter-bar">
        <label class="filter-field">
          <span class="filter-label">상태</span>
          <select v-model="scheduledStatus">
            <option value="all">전체</option>
            <option value="reserved">예약 중</option>
            <option value="canceled">취소됨</option>
          </select>
        </label>
        <label class="filter-field">
          <span class="filter-label">카테고리</span>
          <select v-model="scheduledCategory">
            <option value="all">전체</option>
            <option v-for="category in categoryOptions" :key="category.id" :value="category.name">
              {{ category.name }}
            </option>
          </select>
        </label>
        <label class="filter-field">
          <span class="filter-label">정렬</span>
          <select v-model="scheduledSort">
            <option value="nearest">방송 시간이 가까운 순</option>
            <option value="latest">최신 순</option>
            <option value="oldest">오래된 순</option>
          </select>
        </label>
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
            @click="openReservationDetail(item)"
          >
            <div class="live-thumb">
              <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" />
              <div class="live-badges">
                <span v-if="formatDDay(item)" class="badge badge--dday">{{ formatDDay(item) }}</span>
                <span
                  class="badge badge--scheduled"
                  :class="{ 'badge--cancelled': getLifecycleStatus(item) === 'CANCELED' }"
                >{{ formatStatusLabel(getLifecycleStatus(item)) }}</span>
              </div>
            </div>
            <div class="live-body">
              <div class="live-meta">
                <p class="live-title">{{ item.title }}</p>
                <p class="live-date">{{ item.datetime }}</p>
                <p class="live-seller">{{ item.category }}</p>
              </div>
            </div>
          </article>

          <div
            v-if="filteredScheduledItems.length > visibleScheduledItems.length"
            ref="scheduledSentinelRef"
            class="scroll-sentinel"
            aria-hidden="true"
          ></div>
        </template>

        <p v-else class="section-empty">등록된 방송이 없습니다. 예약 방송을 추가해보세요.</p>
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
                @click="openReservationDetail(item)"
              >
                <div class="live-thumb">
                  <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" />
                  <div class="live-badges">
                    <span v-if="formatDDay(item)" class="badge badge--dday">{{ formatDDay(item) }}</span>
                    <span
                      class="badge badge--scheduled"
                      :class="{ 'badge--cancelled': getLifecycleStatus(item) === 'CANCELED' }"
                    >{{ formatStatusLabel(getLifecycleStatus(item)) }}</span>
                  </div>
                </div>
                <div class="live-body">
                  <div class="live-meta">
                    <p class="live-title">{{ item.title }}</p>
                    <p class="live-date">{{ item.datetime }}</p>
                    <p class="live-seller">{{ item.category }}</p>
                  </div>
                </div>
              </article>
            </template>

            <p v-else class="section-empty live-carousel__empty">
              등록된 방송이 없습니다. 예약 방송을 추가해보세요.
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
        <div class="live-section__desc">
          <p v-if="activeTab !== 'all'" class="ds-section-sub">저장된 다시보기 콘텐츠를 확인합니다.</p>
          <button v-else class="link-more" type="button" @click="setTab('vod')">+ 더보기</button>
        </div>
      </div>

      <div v-if="activeTab === 'vod'" class="vod-filters">
        <label class="filter-field">
          <span class="filter-label">시작일</span>
          <input v-model="vodStartDate" type="date" />
        </label>
        <label class="filter-field">
          <span class="filter-label">종료일</span>
          <input v-model="vodEndDate" type="date" />
        </label>
        <label class="filter-field">
          <span class="filter-label">공개 여부</span>
          <select v-model="vodVisibility">
            <option value="all">전체</option>
            <option value="public">공개</option>
            <option value="private">비공개</option>
          </select>
        </label>
        <label class="filter-field">
          <span class="filter-label">카테고리</span>
          <select v-model="vodCategory">
            <option value="all">전체</option>
            <option v-for="category in categoryOptions" :key="category.id" :value="category.name">{{ category.name }}</option>
          </select>
        </label>
        <label class="filter-field">
          <span class="filter-label">정렬</span>
          <select v-model="vodSort">
            <option value="latest">최신 순</option>
            <option value="oldest">오래된 순</option>
            <option value="likes_desc">좋아요 높은 순</option>
            <option value="likes_asc">좋아요 낮은 순</option>
            <option value="viewers_desc">시청자 수 높은 순</option>
            <option value="viewers_asc">시청자 수 낮은 순</option>
            <option value="revenue_desc">매출 높은 순</option>
            <option value="revenue_asc">매출 낮은 순</option>
          </select>
        </label>
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
              @click="openVodDetail(item)"
            >
            <div class="live-thumb">
              <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" />
              <div class="live-badges">
              <span class="badge badge--vod">
                {{ item.statusBadge ?? formatStatusLabel(getLifecycleStatus(item) ?? 'VOD') }}
              </span>
              </div>
            </div>
            <div class="live-body">
              <div class="live-meta">
                <p class="live-title">{{ item.title }}</p>
                <p class="live-date">{{ item.datetime }}</p>
                <p class="live-seller">{{ item.category }}</p>
              </div>
            </div>
          </article>

          <div
            v-if="filteredVodItems.length > visibleVodItems.length"
            ref="vodSentinelRef"
            class="scroll-sentinel"
            aria-hidden="true"
          ></div>
        </template>

        <p v-else class="section-empty">등록된 VOD가 없습니다. 방송이 종료되면 자동 등록됩니다.</p>
      </div>

      <div v-else class="carousel-wrap">
        <button type="button" class="carousel-btn carousel-btn--left" aria-label="VOD 왼쪽 이동" @click="stepCarousel('vod', -1)">
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
                @click="openVodDetail(item)"
              >
                <div class="live-thumb">
                  <img class="live-thumb__img" :src="item.thumb" :alt="item.title" loading="lazy" />
                  <div class="live-badges">
                    <span class="badge badge--vod">
                      {{ item.statusBadge ?? formatStatusLabel(getLifecycleStatus(item) ?? 'VOD') }}
                    </span>
                  </div>
                </div>
                <div class="live-body">
                  <div class="live-meta">
                    <p class="live-title">{{ item.title }}</p>
                    <p class="live-date">{{ item.datetime }}</p>
                    <p class="live-seller">{{ item.category }}</p>
                  </div>
                </div>
              </article>
            </template>

            <p v-else class="section-empty live-carousel__empty">
              등록된 VOD가 없습니다. 방송이 종료되면 자동 등록됩니다.
            </p>
          </div>
        </div>

        <button type="button" class="carousel-btn carousel-btn--right" aria-label="VOD 오른쪽 이동" @click="stepCarousel('vod', 1)">
          ›
        </button>
      </div>
    </section>

    <DeviceSetupModal
      v-model="showDeviceModal"
      :broadcast-title="selectedScheduled?.title"
      @start="handleDeviceStart"
    />
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
  gap: 10px;
}

.inline-filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  color: var(--text-strong);
}

.inline-filter select {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 10px;
  font-weight: 700;
  color: var(--text-strong);
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

.live-create-btn {
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-strong);
  border-radius: 10px;
  padding: 10px 14px;
  font-weight: 900;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.live-create-btn:hover {
  border-color: var(--primary-color);
  box-shadow: 0 8px 18px rgba(var(--primary-rgb), 0.12);
  transform: translateY(-1px);
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
}

.live-section__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.live-section__desc {
  display: flex;
  align-items: center;
  gap: 10px;
}

.live-section__head h3 {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 900;
  color: var(--text-strong);
}

.link-more {
  border: none;
  background: transparent;
  color: var(--primary-color);
  font-weight: 900;
  cursor: pointer;
  padding: 4px 6px;
}

.vod-filters,
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface);
  margin-bottom: 12px;
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 140px;
}

.filter-label {
  font-weight: 800;
  color: var(--text-strong);
  font-size: 0.85rem;
}

.filter-field input,
.filter-field select {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 10px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.live-feature-wrap {
  display: block;
  width: 100%;
}

.live-livecolumn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.live-pane {
  width: 100%;
  max-width: 1080px;
}

.live-feature {
  width: 100%;
  border-radius: 14px;
  padding: 14px;
}

.live-feature__layout {
  display: flex;
  gap: 14px;
  align-items: stretch;
}

.live-feature__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.live-feature__title-row h4 {
  margin: 0;
  font-size: 1.4rem;
  font-weight: 900;
  color: var(--text-strong);
}

.live-feature__seller {
  margin: 0;
  color: var(--text-muted);
  font-weight: 800;
}

.live-feature__thumb {
  position: relative;
  flex: 0 0 320px;
  width: 320px;
  max-width: 420px;
  min-width: 280px;
  aspect-ratio: 16 / 9;
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface-weak);
}

.live-feature__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.live-feature__badge {
  position: absolute;
  top: 10px;
  left: 10px;
}

.live-feature__info {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.live-feature__meta {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  background: rgba(15, 23, 42, 0.06);
  border-radius: 999px;
  padding: 8px 10px;
  font-weight: 800;
  color: var(--text-muted);
}

.live-feature__cta {
  align-self: flex-start;
  border-radius: 10px;
  padding: 10px 14px;
  background: var(--primary-color);
  color: #fff;
  font-weight: 900;
  border: none;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.live-feature__cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(var(--primary-rgb), 0.2);
}

.live-feature--empty {
  align-items: center;
  text-align: center;
}

.live-feature--empty .live-feature__layout {
  display: block;
}

.live-livegrid {
  display: grid;
  grid-template-columns: 2fr 1.2fr;
  gap: 12px;
}

.live-products {
  padding: 14px;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.live-products__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.live-products__count {
  font-weight: 900;
  color: var(--text-muted);
}

.live-products__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 10px;
  align-items: center;
}

.product-thumb {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface-weak);
}

.product-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.product-pin {
  position: absolute;
  top: 6px;
  left: 6px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
  padding: 4px 6px;
  font-size: 0.75rem;
  font-weight: 900;
  color: var(--text-strong);
}

.product-meta {
  min-width: 0;
}

.product-title {
  margin: 0 0 4px;
  font-weight: 900;
  color: var(--text-strong);
}

.product-option {
  margin: 0 0 8px;
  color: var(--text-muted);
  font-weight: 700;
}

.product-badges {
  display: flex;
  gap: 8px;
}

.product-status {
  display: inline-flex;
  align-items: center;
  border-radius: 8px;
  padding: 6px 8px;
  background: rgba(15, 23, 42, 0.06);
  font-weight: 800;
  color: var(--text-strong);
}

.product-status.is-soldout {
  background: #fee2e2;
  color: #991b1b;
}

.product-right {
  text-align: right;
}

.product-price {
  margin: 0 0 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
}

.product-sale {
  font-weight: 900;
  color: var(--text-strong);
}

.product-origin {
  color: var(--text-muted);
  font-weight: 700;
  text-decoration: line-through;
}

.product-stock {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.live-stats {
  margin-top: 14px;
}

.live-stats--stacked {
  width: 100%;
  max-width: 1080px;
}

.live-stats__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.live-stats__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
  border-radius: 999px;
  padding: 6px 10px;
  font-weight: 900;
}

.live-stats__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  display: inline-block;
  animation: pulse 1.4s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.1);
    opacity: 0.65;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.live-stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.live-stat-card {
  padding: 14px;
  border-radius: 12px;
}

.stat-label {
  margin: 0 0 8px;
  font-weight: 800;
  color: var(--text-muted);
}

.stat-value {
  margin: 0 0 4px;
  font-weight: 900;
  font-size: 1.4rem;
  color: var(--text-strong);
}

.stat-sub {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
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

.live-carousel::-webkit-scrollbar {
  height: 0;
}

.live-carousel::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 999px;
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

.badge--dday {
  background: rgba(15, 23, 42, 0.9);
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

.live-livegrid {
  display: grid;
  grid-template-columns: 2fr 1.2fr;
  gap: 12px;
}

.live-livegrid--empty:not(.live-livegrid--has-data) {
  grid-template-columns: 1fr;
  justify-items: center;
}

.section-empty {
  text-align: center;
  color: var(--text-muted);
  font-weight: 800;
  padding: 18px 12px;
}

.live-carousel__empty {
  width: 100%;
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

.live-cta {
  border-radius: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  background: #fff;
  font-weight: 900;
  cursor: pointer;
}

.live-cta--ghost {
  background: var(--surface);
}

.live-grid,
.scheduled-grid,
.vod-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.scheduled-grid--empty,
.vod-grid--empty {
  display: flex;
  justify-content: center;
  align-items: center;
}

.live-carousel__track--empty {
  justify-content: center;
  width: 100%;
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

  .live-livegrid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .live-header {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .live-feature__layout {
    flex-direction: column;
  }

  .live-feature__thumb {
    flex: 0 0 auto;
    width: 100%;
    max-width: none;
    min-width: 0;
  }

  .live-feature__info {
    align-items: flex-start;
  }

  .live-carousel {
    padding: 10px 10px;
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
}
</style>
