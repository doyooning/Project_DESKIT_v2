<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { LiveItem } from '../lib/live/types'
import { computeLifecycleStatus, getScheduledEndMs, normalizeBroadcastStatus } from '../lib/broadcastStatus'
import { parseLiveDate } from '../lib/live/utils'
import { useNow } from '../lib/live/useNow'

const props = defineProps<{
  item: LiveItem
  isActive?: boolean
}>()

const { now } = useNow(1000)
const elapsed = computed(() => {
  const started = parseLiveDate(props.item.startAt)
  const diffMs = now.value.getTime() - started.getTime()
  const hours = Math.floor(diffMs / (1000 * 60 * 60))
  const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60))
  const seconds = Math.floor((diffMs % (1000 * 60)) / 1000)

  const pad = (value: number) => value.toString().padStart(2, '0')

  if (hours > 0) {
    return `${pad(hours)}:${pad(minutes)}`
  }
  return `${pad(minutes)}:${pad(seconds)}`
})
const lifecycleStatus = computed(() => {
  const startAtMs = parseLiveDate(props.item.startAt).getTime()
  const normalizedStart = Number.isNaN(startAtMs) ? undefined : startAtMs
  const endAtMs = parseLiveDate(props.item.endAt).getTime()
  const normalizedEnd = Number.isNaN(endAtMs) ? getScheduledEndMs(normalizedStart) : endAtMs
  return computeLifecycleStatus({
    status: normalizeBroadcastStatus(props.item.status),
    startAtMs: normalizedStart,
    endAtMs: normalizedEnd,
    now: now.value.getTime(),
  })
})
const status = computed(() => {
  if (lifecycleStatus.value === 'ON_AIR') return 'LIVE'
  if (lifecycleStatus.value === 'READY') return 'READY'
  if (lifecycleStatus.value === 'RESERVED') return 'UPCOMING'
  if (lifecycleStatus.value === 'STOPPED') return 'STOPPED'
  if (lifecycleStatus.value === 'VOD') return 'VOD'
  return 'ENDED'
})
const statusBadge = computed(() => {
  if (status.value === 'LIVE') return { label: 'LIVE', class: 'badge-live' }
  if (status.value === 'READY') return { label: 'READY', class: 'badge-ready' }
  if (status.value === 'UPCOMING') return { label: '예약', class: 'badge-upcoming' }
  if (status.value === 'STOPPED') return { label: '송출 중지', class: 'badge-stopped' }
  if (status.value === 'ENDED') return { label: 'ENDED', class: 'badge-ended' }
  if (status.value === 'VOD') return { label: 'VOD', class: 'badge-vod' }
  return null
})
const buttonLabel = computed(() => {
  if (status.value === 'LIVE' || status.value === 'READY') {
    return '입장하기'
  }
  if (status.value === 'STOPPED') {
    return '방송 입장'
  }
  if (status.value === 'ENDED') {
    return '방송 입장'
  }
  if (status.value === 'VOD') {
    return 'VOD 다시보기'
  }
  return '예정'
})

const scheduledLabel = computed(() => {
  const start = parseLiveDate(props.item.startAt)
  const dayNames = ['일', '월', '화', '수', '목', '금', '토']
  const month = String(start.getMonth() + 1).padStart(2, '0')
  const date = String(start.getDate()).padStart(2, '0')
  const day = dayNames[start.getDay()]
  const hours = String(start.getHours()).padStart(2, '0')
  const minutes = String(start.getMinutes()).padStart(2, '0')

  return `${month}.${date} (${day}) ${hours}:${minutes} 예정`
})

const countdownLabel = computed(() => {
  const start = parseLiveDate(props.item.startAt)
  if (Number.isNaN(start.getTime())) return ''
  const diffMs = start.getTime() - now.value.getTime()
  if (status.value === 'READY') {
    if (diffMs <= 0) return '방송 시작 대기 중'
    const totalSeconds = Math.ceil(diffMs / 1000)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}분 ${String(seconds).padStart(2, '0')}초 뒤 방송 시작`
  }
  return ''
})

const formatDuration = (diffMs: number) => {
  if (diffMs <= 0) return ''
  const hours = Math.floor(diffMs / (1000 * 60 * 60))
  const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60))
  const seconds = Math.floor((diffMs % (1000 * 60)) / 1000)

  const pad = (value: number) => value.toString().padStart(2, '0')

  if (hours > 0) {
    return `${pad(hours)}:${pad(minutes)}`
  }
  return `${pad(minutes)}:${pad(seconds)}`
}

const totalDurationLabel = computed(() => {
  const start = parseLiveDate(props.item.startAt)
  const end = parseLiveDate(props.item.endAt)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return ''
  return formatDuration(end.getTime() - start.getTime())
})

const endedDurationLabel = computed(() => {
  const start = parseLiveDate(props.item.startAt)
  if (Number.isNaN(start.getTime())) return ''
  return formatDuration(now.value.getTime() - start.getTime())
})

const timeLabel = computed(() => {
  if (status.value === 'LIVE') {
    return `방송 시간 ${elapsed.value}`
  }
  if (status.value === 'READY') {
    return countdownLabel.value
  }
  if (status.value === 'UPCOMING') {
    return scheduledLabel.value
  }
  if (status.value === 'STOPPED') {
    return '송출 중지'
  }
  if (status.value === 'VOD') {
    return totalDurationLabel.value ? `재생 시간 ${totalDurationLabel.value}` : '재생 시간'
  }
  return endedDurationLabel.value ? `경과 ${endedDurationLabel.value}` : '방송 종료'
})

const viewerLabel = computed(() => {
  if (props.item.viewerCount == null) return ''
  if (status.value === 'READY') {
    return `시청자 ${props.item.viewerCount.toLocaleString()}명`
  }
  return ''
})

const topViewerLabel = computed(() => {
  if (props.item.viewerCount == null) return ''
  if (status.value === 'LIVE' || status.value === 'ENDED') {
    return `시청자 ${props.item.viewerCount.toLocaleString()}명`
  }
  if (status.value === 'VOD') {
    return `누적 시청자 ${props.item.viewerCount.toLocaleString()}명`
  }
  return ''
})

const isCtaDisabled = computed(() => status.value === 'UPCOMING')

const router = useRouter()

const handleWatchNow = () => {
  if (status.value === 'LIVE' || status.value === 'READY') {
    router.push({ name: 'live-detail', params: { id: props.item.id } })
    return
  }
  if (status.value === 'STOPPED') {
    router.push({ name: 'live-detail', params: { id: props.item.id } })
    return
  }
  if (status.value === 'ENDED') {
    router.push({ name: 'live-detail', params: { id: props.item.id } })
    return
  }
  if (status.value === 'VOD') {
    router.push({ name: 'vod', params: { id: props.item.id } })
  }
}
</script>

<template>
  <article class="card" :class="{ 'card--active': props.isActive }">
    <div class="media">
      <img :src="props.item.thumbnailUrl" :alt="props.item.title" />
      <div class="top-badges">
        <span v-if="statusBadge" class="badge" :class="statusBadge.class">
          {{ statusBadge.label }}
        </span>
        <span v-if="topViewerLabel" class="badge badge-viewers">
          {{ topViewerLabel }}
        </span>
      </div>
    </div>
    <div class="content">
      <div class="eyebrow-row">
        <p v-if="status === 'LIVE'" class="eyebrow">현재 방송 중</p>
      </div>
      <h3>{{ props.item.title }}</h3>
      <p class="desc">{{ props.item.description }}</p>
      <div class="info-row">
        <span v-if="timeLabel" class="info-chip">{{ timeLabel }}</span>
        <span v-if="status === 'STOPPED'" class="info-chip">경과 {{ elapsed }}</span>
        <span v-if="viewerLabel" class="info-chip">{{ viewerLabel }}</span>
      </div>
      <div class="meta-row">
        <button
          type="button"
          class="cta"
          :disabled="isCtaDisabled"
          :aria-disabled="isCtaDisabled"
          @click="handleWatchNow"
        >
          {{ buttonLabel }}
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  box-shadow: 0 18px 40px rgba(119, 136, 115, 0.12);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.2s ease;
  display: grid;
  grid-template-columns: minmax(300px, 360px) 1fr;
  column-gap: 2px;
  align-items: stretch;
  height: clamp(240px, 32vw, 290px);
}

.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 44px rgba(119, 136, 115, 0.16);
  border-color: var(--accent-color);
}

.card--active {
  border-color: var(--primary-color);
  box-shadow: 0 22px 48px rgba(119, 136, 115, 0.2);
}

.media {
  position: relative;
  background: var(--surface-weak);
  aspect-ratio: 16 / 9;
  width: 100%;
  height: 100%;
}

.media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 600ms cubic-bezier(0.22, 1, 0.36, 1);
}

.media:hover img {
  transform: scale(1.03);
}

.badge {
  position: absolute;
  border-radius: 12px;
  padding: 6px 10px;
  font-weight: 800;
  font-size: 0.85rem;
  color: #fff;
  box-shadow: 0 12px 22px rgba(119, 136, 115, 0.18);
}

.badge-live {
  background: var(--live-color);
}

.badge-ready {
  background: rgba(56, 189, 248, 0.9);
}

.badge-upcoming {
  background: rgba(139, 122, 94, 0.9);
}

.badge-stopped {
  background: rgba(239, 68, 68, 0.9);
}

.badge-ended {
  background: rgba(100, 116, 139, 0.9);
}

.badge-vod {
  background: rgba(14, 116, 144, 0.9);
}

.top-badges {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.top-badges .badge {
  position: relative;
}

.badge-viewers {
  background: rgba(47, 58, 47, 0.72);
  color: #fff;
  backdrop-filter: blur(6px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  padding: 6px 10px;
  border-radius: 12px;
  font-weight: 800;
  font-size: 0.85rem;
  max-width: calc(100% - 24px);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.eyebrow-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.eyebrow-time {
  color: var(--text-soft);
  font-weight: 800;
  white-space: nowrap;
  font-size: 0.95rem;
}

.content {
  padding: 16px 18px 18px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  word-break: keep-all;
  white-space: normal;
  max-width: 560px;
  overflow: hidden;
}

.eyebrow {
  margin: 0;
  color: var(--text-soft);
  font-weight: 800;
  letter-spacing: 0.04em;
}

h3 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.3px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.desc {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.info-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.info-chip {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--text-muted);
  background: var(--surface-weak);
  padding: 4px 10px;
  border-radius: 999px;
}

.meta-row {
  margin-top: 6px;
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  padding-top: 6px;
}

.cta {
  border: none;
  background: var(--primary-color);
  color: #fff;
  font-weight: 800;
  width: min(320px, 100%);
  height: 52px;
  border-radius: 14px;
  font-size: 1.05rem;
  cursor: pointer;
  box-shadow: 0 14px 30px rgba(119, 136, 115, 0.18);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 32px rgba(119, 136, 115, 0.22);
}

.cta:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.cta:disabled:hover {
  transform: none;
  box-shadow: none;
}

@media (max-width: 1080px) {
  .card {
    grid-template-columns: 1fr;
  }
}
</style>
