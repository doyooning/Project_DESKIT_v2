<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import ConfirmModal from '../components/ConfirmModal.vue'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import {
  filterLivesByDay,
  getDayWindow,
  parseLiveDate,
  sortLivesByStartAt,
} from '../lib/live/utils'
import { computeLifecycleStatus, getScheduledEndMs, normalizeBroadcastStatus, type BroadcastStatus } from '../lib/broadcastStatus'
import type { LiveItem } from '../lib/live/types'
import { useNow } from '../lib/live/useNow'
import { fetchBroadcastStats, fetchPublicBroadcastOverview } from '../lib/live/api'
import { getAuthUser } from '../lib/auth'
import { resolveViewerId } from '../lib/live/viewer'
import { createImageErrorHandler } from '../lib/images/productImages'

const router = useRouter()
const today = new Date()
const { now } = useNow(1000)

const NOTIFY_KEY = 'deskit_live_notifications'
const WATCH_HISTORY_CONSENT_KEY = 'deskit_live_watch_history_consent_v1'
const notifiedIds = ref<Set<string>>(new Set())
const normalizeDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate())
const toast = ref<{ message: string; variant: 'success' | 'neutral' } | null>(null)
const showWatchHistoryConsent = ref(false)
const pendingLiveId = ref<string | null>(null)
const liveItems = ref<LiveItem[]>([])
let toastTimer: ReturnType<typeof setTimeout> | null = null
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const sseSource = ref<EventSource | null>(null)
const sseConnected = ref(false)
const sseRetryCount = ref(0)
const sseRetryTimer = ref<number | null>(null)
const refreshTimer = ref<number | null>(null)
const statsTimer = ref<number | null>(null)

const hasWatchHistoryConsent = () => {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(WATCH_HISTORY_CONSENT_KEY) === 'true'
  } catch {
    return false
  }
}

const requestWatchHistoryConsent = (liveId: string) => {
  pendingLiveId.value = liveId
  showWatchHistoryConsent.value = true
}

const handleConfirmWatchHistory = () => {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(WATCH_HISTORY_CONSENT_KEY, 'true')
    }
  } catch {
    // localStorage unavailable (e.g., private mode); proceed without persistence
  }
  showWatchHistoryConsent.value = false
  if (pendingLiveId.value) {
    router.push({ name: 'live-detail', params: { id: pendingLiveId.value } }).catch(() => {})
  }
  pendingLiveId.value = null
}

const handleCancelWatchHistory = () => {
  showWatchHistoryConsent.value = false
  pendingLiveId.value = null
}

const dayWindow = computed(() => getDayWindow(today))
const selectedDay = ref(normalizeDay(dayWindow.value[3] ?? today))

const formatTime = (value: string) => {
  const time = parseLiveDate(value)
  const hours = time.getHours().toString().padStart(2, '0')
  const minutes = time.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
}

const { handleImageError } = createImageErrorHandler()

const getLifecycleStatus = (item: LiveItem): BroadcastStatus => {
  const startAtMs = parseLiveDate(item.startAt).getTime()
  const normalizedStart = Number.isNaN(startAtMs) ? undefined : startAtMs
  const endAtMs = parseLiveDate(item.endAt).getTime()
  const normalizedEnd = Number.isNaN(endAtMs) ? getScheduledEndMs(normalizedStart) : endAtMs
  return computeLifecycleStatus({
    status: normalizeBroadcastStatus(item.status),
    startAtMs: normalizedStart,
    endAtMs: normalizedEnd,
  })
}

const getStatus = (item: LiveItem) => {
  const status = getLifecycleStatus(item)
  if (status === 'ON_AIR') return 'LIVE'
  if (status === 'READY') return 'READY'
  if (status === 'RESERVED') return 'UPCOMING'
  if (status === 'STOPPED') return 'STOPPED'
  if (status === 'VOD') return 'VOD'
  return 'ENDED'
}
const getSectionStatus = (item: LiveItem) => {
  const status = getLifecycleStatus(item)
  if (status === 'RESERVED') return 'UPCOMING'
  if (status === 'VOD') return 'ENDED'
  return 'LIVE'
}
const isPastScheduledEnd = (item: LiveItem): boolean => {
  const startAtMs = parseLiveDate(item.startAt).getTime()
  const normalizedStart = Number.isNaN(startAtMs) ? undefined : startAtMs
  const scheduledEndMs = getScheduledEndMs(normalizedStart)
  if (!scheduledEndMs) return false
  return Date.now() > scheduledEndMs
}
const isRowDisabled = (item: LiveItem) => getLifecycleStatus(item) === 'RESERVED'
const getElapsedLabel = (item: LiveItem) => {
  if (getStatus(item) !== 'LIVE') return ''
  const started = parseLiveDate(item.startAt)
  if (Number.isNaN(started.getTime())) return ''
  const diffMs = Math.max(0, now.value.getTime() - started.getTime())
  const totalSeconds = Math.floor(diffMs / 1000)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const hours = Math.floor(totalSeconds / 3600)
  const pad = (value: number) => String(value).padStart(2, '0')
  return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
}
const getCountdownLabel = (item: LiveItem) => {
  const lifecycleStatus = getLifecycleStatus(item)
  if (lifecycleStatus !== 'RESERVED' && lifecycleStatus !== 'READY' && lifecycleStatus !== 'ENDED') {
    return ''
  }
  const start = parseLiveDate(item.startAt)
  const nowValue = now.value
  const diffMs = start.getTime() - nowValue.getTime()
  if (lifecycleStatus === 'READY') {
    if (diffMs <= 0) {
      return '방송 대기 중'
    }
    const totalSeconds = Math.ceil(diffMs / 1000)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}분 ${String(seconds).padStart(2, '0')}초 뒤 방송 시작`
  }
  if (lifecycleStatus === 'ENDED') {
    const startAtMs = Number.isNaN(start.getTime()) ? undefined : start.getTime()
    const rawEndAt = parseLiveDate(item.endAt).getTime()
    const endAtMs = Number.isNaN(rawEndAt) ? getScheduledEndMs(startAtMs) : rawEndAt
    if (!endAtMs) return '방송 종료'
    const remainingMs = endAtMs - nowValue.getTime()
    if (remainingMs <= 0) return '방송 종료'
    const totalSeconds = Math.ceil(remainingMs / 1000)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `종료까지 ${minutes}분 ${String(seconds).padStart(2, '0')}초`
  }
  if (diffMs <= 0) {
    return '시작 예정'
  }

  const dayMs = 86400000
  const startDay = normalizeDay(start)
  const nowDay = normalizeDay(nowValue)
  const dayDiff = Math.floor((startDay.getTime() - nowDay.getTime()) / dayMs)

  if (dayDiff >= 2) {
    return `${dayDiff}일 후 시작`
  }
  if (dayDiff === 1) {
    return '내일 시작'
  }

  const minutes = Math.floor(diffMs / 60000)
  if (minutes < 1) {
    return '곧 시작'
  }
  if (minutes < 60) {
    return `${minutes}분 후 시작`
  }
  const hours = Math.floor(minutes / 60)
  const remaining = minutes % 60
  return remaining === 0 ? `${hours}시간 후 시작` : `${hours}시간 ${remaining}분 후 시작`
}

const itemsForDay = computed(() => {
  const filtered = filterLivesByDay(liveItems.value, selectedDay.value)
  const visible = filtered.filter((item) => {
    const rawStatus = (item.status ?? '').toUpperCase()
    if (rawStatus === 'DELETED') return false
    const status = getLifecycleStatus(item)
    if (status === 'CANCELED') return false
    if (!['RESERVED', 'READY', 'ON_AIR', 'STOPPED', 'ENDED', 'VOD'].includes(status)) return false
    if (status === 'STOPPED' && isPastScheduledEnd(item)) return false
    if (status === 'VOD') {
      const visibility = (item as { isPublic?: boolean; public?: boolean }).isPublic ?? (item as { public?: boolean }).public
      if (typeof visibility === 'boolean' && !visibility) return false
    }
    return true
  })
  return sortLivesByStartAt(visible)
})

const liveItemsForDay = computed(() => itemsForDay.value.filter((item) => getSectionStatus(item) === 'LIVE'))
const upcomingItemsForDay = computed(() => itemsForDay.value.filter((item) => getSectionStatus(item) === 'UPCOMING'))
const endedItemsForDay = computed(() =>
  [...itemsForDay.value].filter((item) => getSectionStatus(item) === 'ENDED').reverse(),
)

const orderedItems = computed(() => [
  ...liveItemsForDay.value,
  ...upcomingItemsForDay.value,
  ...endedItemsForDay.value,
])

const statusWeight = (item: LiveItem) => {
  const s = getSectionStatus(item)
  if (s === 'LIVE') return 0
  if (s === 'UPCOMING') return 1
  return 2
}

const groupedByTime = computed(() => {
  const groups = new Map<string, LiveItem[]>()
  orderedItems.value.forEach((item) => {
    const key = formatTime(item.startAt)
    const bucket = groups.get(key) ?? []
    bucket.push(item)
    groups.set(key, bucket)
  })
  return Array.from(groups.entries()).map(([time, items]) => {
    const next = [...items].sort((a, b) => {
      const weight = statusWeight(a) - statusWeight(b)
      if (weight !== 0) return weight
      const ta = parseLiveDate(a.startAt).getTime()
      const tb = parseLiveDate(b.startAt).getTime()
      if (ta !== tb) return ta - tb
      return a.id.localeCompare(b.id)
    })
    return [time, next] as [string, LiveItem[]]
  })
})

const isToday = (day: Date) => {
  return (
    day.getFullYear() === today.getFullYear() &&
    day.getMonth() === today.getMonth() &&
    day.getDate() === today.getDate()
  )
}

const isSelectedDay = (day: Date) => {
  return (
    day.getFullYear() === selectedDay.value.getFullYear() &&
    day.getMonth() === selectedDay.value.getMonth() &&
    day.getDate() === selectedDay.value.getDate()
  )
}

const formatDayLabel = (day: Date) => {
  const dayNames = ['일', '월', '화', '수', '목', '금', '토']
  const label = isToday(day) ? '오늘' : dayNames[day.getDay()]
  const date = `${day.getMonth() + 1}.${day.getDate()}`

  return { label, date }
}

const getDayCount = (day: Date) => filterLivesByDay(liveItems.value, day).length

const selectDay = (day: Date) => {
  selectedDay.value = normalizeDay(day)
}

const selectToday = () => {
  selectedDay.value = normalizeDay(today)
}

const handleRowClick = (item: LiveItem) => {
  const lifecycleStatus = getLifecycleStatus(item)
  if (lifecycleStatus === 'RESERVED') {
    return
  }
  if (lifecycleStatus !== 'VOD') {
    if (!hasWatchHistoryConsent()) {
      requestWatchHistoryConsent(item.id)
      return
    }
    router.push({ name: 'live-detail', params: { id: item.id } })
    return
  }
  router.push({ name: 'vod', params: { id: item.id } })
}

const isNotified = (id: string) => notifiedIds.value.has(id)

const handleRowKeydown = (event: KeyboardEvent, item: LiveItem) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    handleRowClick(item)
  }
}

const showToast = (message: string, variant: 'success' | 'neutral') => {
  toast.value = { message, variant }
  if (toastTimer) {
    clearTimeout(toastTimer)
  }
  toastTimer = setTimeout(() => {
    toast.value = null
    toastTimer = null
  }, 2200)
}

const toggleNotify = (id: string) => {
  const next = new Set(notifiedIds.value)
  const wasNotified = next.has(id)
  if (wasNotified) {
    next.delete(id)
  } else {
    next.add(id)
  }
  notifiedIds.value = next
  showToast(wasNotified ? '알림 해제됨' : '알림 신청 완료', wasNotified ? 'neutral' : 'success')
}

onMounted(() => {
  try {
    const raw = localStorage.getItem(NOTIFY_KEY)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      notifiedIds.value = new Set(parsed.filter((id) => typeof id === 'string'))
    }
  } catch {
    notifiedIds.value = new Set()
  }
})

type BroadcastOverviewItem = {
  broadcastId: number
  title: string
  notice?: string
  thumbnailUrl?: string
  startAt?: string
  scheduledAt?: string
  endAt?: string
  liveViewerCount?: number
  viewerCount?: number
  sellerName?: string
  status?: string
}

const mapToLiveItems = (items: BroadcastOverviewItem[]) =>
  items
    .map((item) => {
      const startAt = item.startAt ?? item.scheduledAt ?? ''
      if (!startAt) return null
      const liveItem: LiveItem = {
        id: String(item.broadcastId),
        title: item.title,
        description: item.notice ?? '',
        thumbnailUrl: item.thumbnailUrl ?? '',
        startAt,
        endAt: item.endAt ?? startAt,
        viewerCount: item.liveViewerCount ?? item.viewerCount ?? 0,
        status: item.status,
        sellerName: item.sellerName ?? '',
      }
      return liveItem
    })
    .filter((item): item is LiveItem => item !== null)

const loadBroadcasts = async () => {
  try {
    const items = await fetchPublicBroadcastOverview()
    liveItems.value = mapToLiveItems(items)
  } catch {
    liveItems.value = []
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
    void loadBroadcasts()
  }, 500)
}

const handleSseEvent = (event: MessageEvent) => {
  parseSseData(event)
  switch (event.type) {
    case 'BROADCAST_READY':
    case 'BROADCAST_UPDATED':
    case 'BROADCAST_STARTED':
    case 'PRODUCT_PINNED':
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

const isStatsTarget = (item: LiveItem) => {
  const status = normalizeBroadcastStatus(item.status)
  if (status === 'ON_AIR' || status === 'READY' || status === 'ENDED' || status === 'STOPPED') return true
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

const startStatsPolling = () => {
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void updateLiveViewerCounts()
    }
  }, 5000)
}

watchEffect(() => {
  localStorage.setItem(NOTIFY_KEY, JSON.stringify(Array.from(notifiedIds.value)))
})

onBeforeUnmount(() => {
  if (toastTimer) {
    clearTimeout(toastTimer)
  }
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  sseRetryTimer.value = null
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = null
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = null
  window.removeEventListener('visibilitychange', handleSseVisibilityChange)
  window.removeEventListener('focus', handleSseVisibilityChange)
  sseSource.value?.close()
})

onMounted(() => {
  void loadBroadcasts()
  connectSse()
  startStatsPolling()
  window.addEventListener('visibilitychange', handleSseVisibilityChange)
  window.addEventListener('focus', handleSseVisibilityChange)
})
</script>

<template>
  <PageContainer>
    <ConfirmModal
      v-model="showWatchHistoryConsent"
      title="시청 기록 수집 안내"
      description="라이브 방송 입장 시 시청 기록을 수집합니다. 계속 진행하시겠습니까?"
      confirm-text="동의하고 입장하기"
      cancel-text="취소"
      @confirm="handleConfirmWatchHistory"
      @cancel="handleCancelWatchHistory"
    />
    <PageHeader title="라이브 일정" eyebrow="DESKIT LIVE" />

    <div class="date-strip">
      <button
        v-for="day in dayWindow"
        :key="day.toISOString()"
        type="button"
        class="date-pill"
        :class="{ 'date-pill--active': isSelectedDay(day) }"
        @click="selectDay(day)"
      >
        <span class="date-pill__label">{{ formatDayLabel(day).label }}</span>
        <span class="date-pill__date">{{ formatDayLabel(day).date }}</span>
        <span v-if="getDayCount(day) >= 2" class="date-pill__count">{{ getDayCount(day) }}</span>
      </button>
    </div>

    <div v-if="!orderedItems.length" class="empty-state-box">
      <p>선택한 날짜에 라이브가 없습니다.</p>
      <button type="button" class="action-btn action-btn--ghost" @click="selectToday">
        오늘 보기
      </button>
    </div>

    <div v-else class="timeline">
      <div v-for="[time, items] in groupedByTime" :key="time" class="time-group">
        <div class="time-label">{{ time }}</div>
        <div class="time-group__list">
          <article
            v-for="item in items"
            :key="item.id"
            class="live-card-row"
            :class="{
              'row--clickable': !isRowDisabled(item),
              'row--disabled': isRowDisabled(item),
            }"
            :aria-disabled="isRowDisabled(item) ? 'true' : undefined"
            :tabindex="isRowDisabled(item) ? -1 : 0"
            @click="handleRowClick(item)"
            @keydown="(e) => handleRowKeydown(e, item)"
          >
            <div class="thumb ds-thumb-frame ds-thumb-16x10">
              <img class="ds-thumb-img" :src="item.thumbnailUrl" :alt="item.title" @error="handleImageError" />
            </div>
            <div class="meta">
              <div class="meta__title-row">
                <h4 class="meta__title">{{ item.title }}</h4>
                <span
                  v-if="getStatus(item) === 'LIVE'"
                  class="status-pill status-pill--live"
                >
                  LIVE
                  <span v-if="item.viewerCount" class="status-viewers">
                    {{ item.viewerCount.toLocaleString() }}명
                  </span>
                </span>
                <span v-else-if="getStatus(item) === 'READY'" class="status-pill">대기</span>
                <span v-else-if="getStatus(item) === 'UPCOMING'" class="status-pill">예정</span>
                <span v-else-if="getStatus(item) === 'STOPPED'" class="status-pill status-pill--ended">중지됨</span>
                <span v-else-if="getStatus(item) === 'VOD'" class="status-pill status-pill--ended">
                  VOD
                  <span v-if="item.viewerCount" class="status-viewers">
                    누적 {{ item.viewerCount.toLocaleString() }}명
                  </span>
                </span>
                <span v-else class="status-pill status-pill--ended">
                  종료
                  <span v-if="item.viewerCount" class="status-viewers">
                    시청자 {{ item.viewerCount.toLocaleString() }}명
                  </span>
                </span>
                <span
                  v-if="getStatus(item) === 'LIVE'"
                  class="status-pill status-pill--sub"
                >
                  경과 {{ getElapsedLabel(item) }}
                </span>
                <span v-else-if="['UPCOMING', 'READY', 'ENDED'].includes(getStatus(item))" class="status-pill status-pill--sub">
                  {{ getCountdownLabel(item) }}
                </span>
              </div>
              <p v-if="item.sellerName" class="meta__seller">{{ item.sellerName }}</p>
              <p v-if="item.description" class="meta__desc">{{ item.description }}</p>
            </div>
            <div v-if="getStatus(item) === 'UPCOMING'" class="right-slot">
              <button
                type="button"
                :class="[
                  'action-btn',
                  isNotified(item.id) ? 'action-btn--tinted' : 'action-btn--ghost',
                ]"
                @click.stop="toggleNotify(item.id)"
              >
                {{ isNotified(item.id) ? '알림 신청됨' : '알림 신청' }}
              </button>
            </div>
          </article>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast" :class="`toast--${toast.variant}`" role="status" aria-live="polite">
      {{ toast.message }}
    </div>
  </PageContainer>
</template>

<style scoped>
.date-strip {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 6px 2px 18px;
}

.date-strip {
  display: flex;
  gap: 10px;
  width: 100%;

  /* ✅ 가운데 정렬 */
  justify-content: center;

  /* ✅ 줄바꿈 허용해서 "10일"이 한 줄로 안 들어가면 자연스럽게 다음 줄로 */
  flex-wrap: wrap;

  padding: 6px 2px 18px;
  margin: 0 auto 18px;
}

.date-pill {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 12px;
  padding: 10px 12px;
  min-width: 76px;

  display: grid;
  gap: 4px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;

  flex: 0 0 auto;
}

@media (max-width: 840px) {
  .date-strip {
    justify-content: flex-start;
    flex-wrap: nowrap;
    overflow-x: auto;
  }
}

.date-pill__label {
  font-weight: 800;
  font-size: 0.9rem;
  color: var(--text-strong);
}

.date-pill__date {
  font-size: 0.85rem;
  color: var(--text-muted);
  min-height: 1.1em;
}

.date-pill__count {
  align-self: center;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-soft);
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--surface-weak);
}

.date-pill--active {
  border-color: var(--primary-color);
  box-shadow: 0 10px 22px rgba(119, 136, 115, 0.12);
  transform: translateY(-1px);
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.time-group {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 16px;
  align-items: start;
}

.time-label {
  font-size: 1.2rem;
  font-weight: 800;
  color: var(--text-strong);
  padding-top: 8px;
}

.time-group__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.live-card-row {
  display: grid;
  grid-template-columns: 180px 1fr auto;
  gap: 18px;
  align-items: center;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--border-color);
  background: var(--surface);
}

.row--clickable {
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.row--clickable:hover {
  border-color: var(--primary-color);
  box-shadow: 0 10px 22px rgba(119, 136, 115, 0.12);
  transform: translateY(-1px);
}

.row--disabled {
  cursor: default;
  opacity: 0.66;
}

.row--disabled:hover {
  border-color: var(--border-color);
  box-shadow: none;
  transform: none;
}

.thumb {
  width: 180px;
  height: 140px;
  border-radius: 16px;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.meta__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.meta__title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 800;
  color: var(--text-strong);
  line-height: 1.35;
}

.meta__seller {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.95rem;
}

.meta__desc {
  margin: 0;
  color: var(--text-soft);
  font-size: 0.85rem;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  white-space: normal;
  line-height: 1.4;
}

.status-pill {
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--surface-weak);
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-pill--live {
  background: var(--live-color-soft);
  color: var(--live-color);
}

.status-pill--ended {
  background: var(--border-color);
  color: var(--text-muted);
}

.status-pill--sub {
  background: transparent;
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

.status-viewers {
  font-size: 0.75rem;
  font-weight: 700;
}

.right-slot {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  min-width: 132px;
}

.action-btn {
  border: none;
  background: var(--primary-color);
  color: #fff;
  font-weight: 800;
  border-radius: 12px;
  padding: 8px 14px;
  cursor: pointer;
}

.action-btn--ghost {
  background: var(--surface);
  color: var(--text-strong);
  border: 1px solid var(--border-color);
}

.action-btn--tinted {
  background: var(--primary-color);
  color: #fff;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  padding: 10px 16px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.9rem;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.12);
  z-index: 20;
}

.toast--success {
  background: var(--primary-color);
  color: #fff;
}

.toast--neutral {
  background: var(--surface);
  color: var(--text-strong);
  border: 1px solid var(--border-color);
}

.empty-state-box {
  padding: 18px;
  border: 1px dashed var(--border-color);
  border-radius: 14px;
  background: var(--surface);
  color: var(--text-muted);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

@media (max-width: 960px) {
  .time-group {
    grid-template-columns: 72px 1fr;
  }

  .live-card-row {
    grid-template-columns: 150px 1fr auto;
  }

  .thumb {
    width: 150px;
    height: 120px;
  }
}

@media (max-width: 640px) {
  .time-group {
    grid-template-columns: 1fr;
  }

  .time-label {
    padding-top: 0;
  }

  .live-card-row {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }

  .thumb {
    width: 100%;
    height: 180px;
  }

  .right-slot {
    width: 100%;
  }

  .action-btn {
    width: 100%;
  }
}
</style>
