<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { getLiveStatus, parseLiveDate } from '../lib/live/utils'
import { getScheduledEndMs } from '../lib/broadcastStatus'
import { useNow } from '../lib/live/useNow'
import {
  fetchBroadcastProducts,
  fetchBroadcastLikeStatus,
  fetchPublicBroadcastDetail,
  recordVodView,
  reportBroadcast,
  toggleBroadcastLike,
  type BroadcastProductItem,
} from '../lib/live/api'
import type { LiveItem } from '../lib/live/types'
import { getAuthUser } from '../lib/auth'
import { resolveViewerId } from '../lib/live/viewer'
import { createImageErrorHandler } from '../lib/images/productImages'

const route = useRoute()
const router = useRouter()
const { now } = useNow(1000)

const { handleImageError } = createImageErrorHandler()

const vodId = computed(() => {
  const value = route.params.id
  return Array.isArray(value) ? value[0] : value
})

const vodItem = ref<LiveItem | null>(null)

const status = computed(() => {
  if (!vodItem.value) {
    return undefined
  }
  return getLiveStatus(vodItem.value, now.value)
})

const statusLabel = computed(() => {
  if (status.value === 'LIVE') {
    return 'LIVE'
  }
  if (status.value === 'UPCOMING') {
    return '예정'
  }
  if (status.value === 'ENDED') {
    return '다시보기'
  }
  return 'VOD'
})

const statusBadgeClass = computed(() => {
  if (!status.value) {
    return 'status-badge--ended'
  }
  return `status-badge--${status.value.toLowerCase()}`
})

const showChat = ref(true)
const isLiked = ref(false)
const likeCount = ref(0)
const likeInFlight = ref(false)
const reportInFlight = ref(false)
const hasReported = ref(false)
const totalViews = ref<number | null>(null)
const isVodUnavailable = ref(false)
const refreshTimerId = ref<number | null>(null)
const redirectPending = ref(false)

const isLoggedIn = computed(() => Boolean(getAuthUser()))

const requireMemberAction = () => {
  if (!isLoggedIn.value) {
    alert('회원만 이용할 수 있습니다.')
    return false
  }
  return true
}

const toggleLike = async () => {
  if (!vodItem.value || likeInFlight.value || !requireMemberAction()) return
  likeInFlight.value = true
  try {
    const result = await toggleBroadcastLike(Number(vodItem.value.id))
    isLiked.value = result.liked
    likeCount.value = result.likeCount
  } catch {
    return
  } finally {
    likeInFlight.value = false
  }
}

const submitReport = async () => {
  if (!vodItem.value || reportInFlight.value || !requireMemberAction()) return
  if (hasReported.value) {
    alert('이미 신고 완료되어 1회만 신고 가능합니다.')
    return
  }
  reportInFlight.value = true
  try {
    const result = await reportBroadcast(Number(vodItem.value.id))
    hasReported.value = hasReported.value || result.reported
    if (result.reported) {
      alert('신고가 접수되었습니다.')
    } else {
      alert('이미 신고 완료되어 1회만 신고 가능합니다.')
    }
  } catch {
    return
  } finally {
    reportInFlight.value = false
  }
}

const toggleChat = () => {
  showChat.value = !showChat.value
}

const products = ref<BroadcastProductItem[]>([])

const messages = ref(
  [] as Array<{ id: string; user: string; text: string; at: Date; kind?: 'system' | 'user' }>,
)

const chatListRef = ref<HTMLDivElement | null>(null)
const chatInput = ref('')

const formatPrice = (price: number) => `${price.toLocaleString('ko-KR')}원`

const scheduledLabel = computed(() => {
  if (!vodItem.value) {
    return ''
  }
  const start = parseLiveDate(vodItem.value.startAt)
  const dayNames = ['일', '월', '화', '수', '목', '금', '토']
  const month = String(start.getMonth() + 1).padStart(2, '0')
  const date = String(start.getDate()).padStart(2, '0')
  const day = dayNames[start.getDay()]
  const hours = String(start.getHours()).padStart(2, '0')
  const minutes = String(start.getMinutes()).padStart(2, '0')
  return `${month}.${date} (${day}) ${hours}:${minutes} 예정`
})

const buildVodItem = (detail: { broadcastId: number; title: string; notice?: string; thumbnailUrl?: string; scheduledAt?: string; startedAt?: string; sellerName?: string; vodUrl?: string }) => {
  const startAt = detail.startedAt ?? detail.scheduledAt ?? ''
  const startAtMs = startAt ? parseLiveDate(startAt).getTime() : NaN
  const endAtMs = Number.isNaN(startAtMs) ? undefined : getScheduledEndMs(startAtMs)
  const endAt = endAtMs ? new Date(endAtMs).toISOString() : ''
  return {
    id: String(detail.broadcastId),
    title: detail.title,
    description: detail.notice ?? '',
    thumbnailUrl: detail.thumbnailUrl ?? '',
    startAt,
    endAt,
    vodUrl: detail.vodUrl ?? '',
    sellerName: detail.sellerName ?? '',
  }
}

const loadVodDetail = async () => {
  if (!vodId.value) return
  const numeric = Number.parseInt(String(vodId.value).replace(/[^0-9]/g, ''), 10)
  if (!Number.isFinite(numeric)) {
    vodItem.value = null
    return
  }
  try {
    const detail = await fetchPublicBroadcastDetail(numeric)
    vodItem.value = buildVodItem(detail)
    likeCount.value = detail.totalLikes ?? 0
    if (isLoggedIn.value) {
      await loadLikeStatus(numeric)
    } else {
      isLiked.value = false
    }
    hasReported.value = false
    totalViews.value = detail.totalViews ?? null
    isVodUnavailable.value = false
    await loadProducts(numeric)
    const viewerId = resolveViewerId(getAuthUser())
    void recordVodView(numeric, viewerId)
  } catch {
    vodItem.value = null
    totalViews.value = null
    if (!isVodUnavailable.value) {
      isVodUnavailable.value = true
      if (!redirectPending.value) {
        redirectPending.value = true
        alert('VOD가 삭제되어 방송 목록으로 이동합니다.')
      }
    }
  }
}

const loadLikeStatus = async (broadcastId: number) => {
  if (!isLoggedIn.value) return
  try {
    const result = await fetchBroadcastLikeStatus(broadcastId)
    isLiked.value = result.liked
    likeCount.value = result.likeCount ?? likeCount.value
  } catch {
    return
  }
}

const loadProducts = async (broadcastId?: number) => {
  const numeric = broadcastId ?? (vodItem.value ? Number.parseInt(vodItem.value.id.replace(/[^0-9]/g, ''), 10) : NaN)
  if (!Number.isFinite(numeric)) {
    products.value = []
    return
  }
  try {
    products.value = await fetchBroadcastProducts(numeric)
  } catch {
    products.value = []
  }
}

const formatSchedule = (startAt: string, endAt: string) => {
  const dayNames = ['일', '월', '화', '수', '목', '금', '토']
  const start = parseLiveDate(startAt)
  const end = parseLiveDate(endAt)
  const year = start.getFullYear()
  const month = String(start.getMonth() + 1).padStart(2, '0')
  const day = String(start.getDate()).padStart(2, '0')
  const dayLabel = dayNames[start.getDay()]
  const startHours = String(start.getHours()).padStart(2, '0')
  const startMinutes = String(start.getMinutes()).padStart(2, '0')
  const endHours = String(end.getHours()).padStart(2, '0')
  const endMinutes = String(end.getMinutes()).padStart(2, '0')
  return `${year}.${month}.${day} (${dayLabel}) ${startHours}:${startMinutes} ~ ${endHours}:${endMinutes}`
}

const isEmbedUrl = (url: string) => url.includes('youtube.com/embed') || url.includes('player.vimeo.com')

const handleProductClick = (productId: string) => {
  router.push({ name: 'product-detail', params: { id: productId } })
}

watch(
  vodId,
  () => {
    void loadVodDetail()
  },
  { immediate: true },
)

watch(
  [status, vodItem],
  ([nextStatus, nextItem]) => {
    if (nextStatus === 'LIVE' && nextItem) {
      router.replace({ name: 'live-detail', params: { id: nextItem.id } })
    }
  },
  { immediate: true },
)

const formatChatTime = (value: Date) => {
  const hours = String(value.getHours()).padStart(2, '0')
  const minutes = String(value.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

const scrollChatToBottom = () => {
  nextTick(() => {
    if (chatListRef.value) {
      chatListRef.value.scrollTop = chatListRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  scrollChatToBottom()
  refreshTimerId.value = window.setInterval(() => {
    void loadVodDetail()
  }, 30000)
})

watchEffect((onCleanup) => {
  if (redirectPending.value) {
    return
  }
  const id = window.setInterval(() => {
    void loadVodDetail()
  }, 30000)
  refreshTimerId.value = id
  onCleanup(() => {
    window.clearInterval(id)
    if (refreshTimerId.value === id) {
      refreshTimerId.value = null
    }
  })
})

watch(redirectPending, async (next) => {
  if (!next) return
  if (refreshTimerId.value !== null) {
    window.clearInterval(refreshTimerId.value)
    refreshTimerId.value = null
  }
  await nextTick()
  router.replace('/live').catch(() => {})
})

watch(showChat, (visible) => {
  if (visible) {
    scrollChatToBottom()
  }
})

watch(isLoggedIn, (next) => {
  if (!vodItem.value) return
  if (next) {
    void loadLikeStatus(Number(vodItem.value.id))
  } else {
    isLiked.value = false
  }
})
</script>

<template>
  <PageContainer>
    <PageHeader title="VOD 다시보기" eyebrow="DESKIT VOD" />

    <div v-if="!vodItem" class="empty-state">
      <p>VOD를 찾을 수 없습니다.</p>
      <RouterLink to="/live" class="link-back">라이브 일정으로 돌아가기</RouterLink>
    </div>

    <section v-else class="live-detail-layout">
      <div
        class="live-detail-main"
        :style="{
          gridTemplateColumns: showChat ? 'minmax(0, 1.6fr) minmax(0, 0.95fr)' : 'minmax(0, 1fr)',
        }"
      >
        <section class="panel panel--player live-detail-main__primary">
          <div class="player-meta">
            <div class="status-row">
              <span class="status-badge" :class="statusBadgeClass">{{ statusLabel }}</span>
              <span v-if="status === 'LIVE' && vodItem.viewerCount" class="status-viewers">
                {{ vodItem.viewerCount.toLocaleString() }}명 시청 중
              </span>
              <span v-else-if="totalViews !== null" class="status-views">
                누적 조회수 {{ totalViews.toLocaleString('ko-KR') }}회
              </span>
              <span v-else-if="status === 'UPCOMING'" class="status-schedule">
                {{ scheduledLabel }}
              </span>
            </div>
            <h3 class="player-title">{{ vodItem.title }}</h3>
            <span> {{ formatSchedule(vodItem.startAt, vodItem.endAt)}}</span>
            <p v-if="vodItem.description" class="player-desc">{{ vodItem.description }}</p>
            <p v-if="vodItem.sellerName" class="player-seller">{{ vodItem.sellerName }}</p>
          </div>

          <div class="player-frame">
            <span v-if="status === 'UPCOMING'" class="player-frame__label">아직 시작 전입니다</span>
            <span v-else-if="!vodItem.vodUrl" class="player-frame__label">VOD 준비 중</span>
            <iframe
              v-else-if="vodItem.vodUrl && isEmbedUrl(vodItem.vodUrl)"
              class="player-embed"
              :src="vodItem.vodUrl"
              title="VOD 플레이어"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowfullscreen
            />
            <video
              v-else
              class="player-video"
              :src="vodItem.vodUrl"
              controls
              controlslist="nodownload"
              @contextmenu.prevent
            />
            <div class="player-actions">
              <div class="player-reactions">
                <button
                  type="button"
                  class="icon-circle"
                  :class="{ active: showChat }"
                  aria-label="채팅 패널 토글"
                  @click="toggleChat"
                >
                  <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M3 20l1.62-3.24A2 2 0 0 1 6.42 16H20a1 1 0 0 0 1-1V5a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v15z" fill="none" stroke="currentColor" stroke-width="1.8" />
                    <path d="M7 9h10M7 12h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                  </svg>
                </button>
                <div class="icon-action">
                  <button
                    type="button"
                    class="icon-circle"
                    :class="{ active: isLiked }"
                    aria-label="좋아요"
                    :disabled="likeInFlight"
                    @click="toggleLike"
                  >
                    <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                      <path
                        v-if="isLiked"
                        d="M12.1 21.35l-1.1-1.02C5.14 15.24 2 12.39 2 8.99 2 6.42 4.02 4.5 6.58 4.5c1.54 0 3.04.74 3.92 1.91C11.38 5.24 12.88 4.5 14.42 4.5 16.98 4.5 19 6.42 19 8.99c0 3.4-3.14 6.25-8.9 11.34l-1.1 1.02z"
                        fill="currentColor"
                      />
                      <path
                        v-else
                        d="M12.1 21.35l-1.1-1.02C5.14 15.24 2 12.39 2 8.99 2 6.42 4.02 4.5 6.58 4.5c1.54 0 3.04.74 3.92 1.91C11.38 5.24 12.88 4.5 14.42 4.5 16.98 4.5 19 6.42 19 8.99c0 3.4-3.14 6.25-8.9 11.34l-1.1 1.02z"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="1.8"
                      />
                    </svg>
                  </button>
                  <span class="icon-text">{{ likeCount.toLocaleString('ko-KR') }}</span>
                </div>
                <div class="icon-action">
                  <button
                    type="button"
                    class="icon-circle"
                    aria-label="신고하기"
                    :disabled="reportInFlight || hasReported"
                    @click="submitReport"
                  >
                    <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M6 3v18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                      <path d="M6 4h11l-2 4 2 4H6z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
                    </svg>
                  </button>
                  <span class="icon-text">신고</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <aside v-if="showChat" class="chat-panel ds-surface">
          <header class="chat-head">
            <h4>채팅 기록</h4>
            <button type="button" class="chat-close" aria-label="채팅 닫기" @click="toggleChat">×</button>
          </header>
          <div ref="chatListRef" class="chat-messages">
            <div
              v-for="message in messages"
              :key="message.id"
              class="chat-message"
              :class="{ 'chat-message--system': message.kind === 'system' }"
            >
              <div class="chat-meta">
                <span class="chat-user">{{ message.user }}</span>
                <span class="chat-time">{{ formatChatTime(message.at) }}</span>
              </div>
              <p class="chat-text">{{ message.text }}</p>
            </div>
          </div>
          <div class="chat-input">
            <input
              v-model="chatInput"
              type="text"
              readonly
              placeholder="VOD에서는 채팅을 보실 수만 있어요"
            />
            <button type="button" class="btn primary" disabled>전송</button>
          </div>
<!--          <p class="chat-helper">VOD에서는 채팅 기록만 볼 수 있어요.</p>-->
        </aside>
      </div>

      <section class="panel panel--products">
        <div class="panel__header">
          <h3 class="panel__title">라이브 상품</h3>
          <span class="panel__count">{{ products.length }}개</span>
        </div>
        <div v-if="!products.length" class="panel__empty">등록된 상품이 없습니다.</div>
        <div v-else class="product-list product-list--grid">
          <button
            v-for="product in products"
            :key="product.id"
            type="button"
            class="product-card"
            :class="{ 'product-card--sold-out': product.isSoldOut }"
            @click="handleProductClick(product.id)"
          >
            <div class="product-card__thumb ds-thumb-frame ds-thumb-square">
              <img class="ds-thumb-img" :src="product.imageUrl" :alt="product.name" @error="handleImageError" />
            </div>
            <div class="product-card__info">
              <p class="product-card__name">{{ product.name }}</p>
              <p class="product-card__price">{{ formatPrice(product.price) }}</p>
              <span v-if="product.isSoldOut" class="product-card__badge">품절</span>
            </div>
          </button>
        </div>
      </section>
    </section>
  </PageContainer>
</template>

<style scoped>
.live-detail-layout {
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow-x: hidden;
}

.live-detail-main {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.live-detail-main__primary {
  height: 100%;
}

.panel {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel__title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--text-strong);
}

.panel__count {
  font-weight: 700;
  color: var(--text-soft);
}

.panel__empty {
  color: var(--text-muted);
  padding: 10px 0;
}

.panel--products {
  overflow: hidden;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-list--grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}

.product-card {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 14px;
  padding: 10px;
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 12px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.product-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 10px 22px rgba(119, 136, 115, 0.12);
  transform: translateY(-1px);
}

.product-card--sold-out {
  opacity: 0.7;
}

.product-card__thumb {
  width: 64px;
  height: 64px;
  border-radius: 10px;
}

.product-card__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.product-card__name {
  margin: 0;
  font-weight: 700;
  color: var(--text-strong);
  word-break: break-word;
}

.product-card__price {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.95rem;
}

.product-card__badge {
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--surface-weak);
  color: var(--text-muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.empty-state {
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--text-muted);
}

.link-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--primary-color);
}

.panel--player {
  gap: 16px;
}

.player-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.status-badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 800;
  font-size: 0.85rem;
  background: var(--surface-weak);
  color: var(--text-strong);
}

.status-badge--live {
  background: var(--live-color-soft);
  color: var(--live-color);
}

.status-badge--upcoming {
  background: var(--hover-bg);
  color: var(--primary-color);
}

.status-badge--ended {
  background: var(--border-color);
  color: var(--text-muted);
}

.status-viewers {
  color: var(--text-muted);
  font-weight: 700;
}

.status-schedule {
  color: var(--text-muted);
  font-weight: 700;
}

.status-views {
  color: var(--text-muted);
  font-weight: 700;
}

.player-title {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 800;
}

.player-desc {
  margin: 0;
  color: var(--text-muted);
}

.player-seller {
  margin: 0;
  font-weight: 700;
  color: var(--text-strong);
}

.player-frame {
  position: relative;
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
  background: #10131b;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  max-width: min(100%, calc((100vh - 180px) * (16 / 9)));
  overflow: hidden;
}

.player-frame__label {
  position: absolute;
  z-index: 2;
  opacity: 0.9;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.55);
}

.player-embed,
.player-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
  object-fit: contain;
  background: #0b0f18;
}

.player-actions {
  position: absolute;
  right: 16px;
  bottom: 72px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.player-reactions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.icon-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-weight: 700;
}

.icon-text {
  font-size: 0.85rem;
}

.icon-circle {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease;
}

.icon-circle.active {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: rgba(var(--primary-rgb), 0.12);
}

.player-actions .icon-action {
  color: #fff;
}

.player-actions .icon-circle {
  border-color: var(--border-color);
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
}

.player-actions .icon-circle.active {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: rgba(var(--primary-rgb), 0.12);
}

.icon {
  width: 18px;
  height: 18px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2px;
}

.chat-panel {
  width: 360px;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  align-self: stretch;
  border-radius: 16px;
  padding: 12px;
  gap: 10px;
  background: var(--surface);
  border: 1px solid var(--border-color);
  min-height: 0;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chat-head h4 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.chat-close {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-muted);
  width: 28px;
  height: 28px;
  border-radius: 999px;
  cursor: pointer;
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}

.chat-message {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-message--system .chat-user {
  color: #ef4444;
}

.chat-meta {
  display: flex;
  gap: 8px;
  font-size: 0.85rem;
  color: var(--text-muted);
  font-weight: 700;
}

.chat-user {
  color: var(--text-strong);
  font-weight: 800;
}

.chat-text {
  margin: 0;
  color: var(--text-strong);
  font-weight: 700;
  line-height: 1.4;
}

.chat-time {
  color: var(--text-muted);
}

.chat-input {
  display: flex;
  gap: 8px;
}

.chat-input input {
  flex: 1;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 8px 10px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 16px;
  font-weight: 800;
  cursor: pointer;
}

.btn.primary {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.chat-helper {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

@media (max-width: 640px) {
  .live-detail-main {
    gap: 14px;
  }

  .product-list--grid {
    grid-template-columns: 1fr;
  }

  .product-card {
    grid-template-columns: 1fr;
  }

  .product-card__thumb {
    width: 100%;
    height: 160px;
  }
}

@media (max-width: 1120px) {
  .live-detail-main {
    grid-template-columns: 1fr !important;
  }

  .chat-panel {
    width: 100%;
    height: auto !important;
  }
}
</style>
