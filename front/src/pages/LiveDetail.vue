<script setup lang="ts">
import { OpenVidu, type Session, type StreamEvent, type Subscriber } from 'openvidu-browser'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { resolveSockJsUrl } from '../lib/ws'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import ConfirmModal from '../components/ConfirmModal.vue'
import { parseLiveDate } from '../lib/live/utils'
import { useNow } from '../lib/live/useNow'
import { getAuthUser, hydrateSessionUser, normalizeDisplayName } from '../lib/auth'
import { resolveViewerId } from '../lib/live/viewer'
import { createImageErrorHandler } from '../lib/images/productImages'
// import { resolveWsBase } from '../lib/ws'
import {
  fetchBroadcastLikeStatus,
  fetchBroadcastProducts,
  fetchBroadcastStats,
  fetchChatPermission,
  fetchPublicBroadcastDetail,
  joinBroadcast,
  leaveBroadcast,
  reportBroadcast,
  toggleBroadcastLike,
  type BroadcastProductItem,
} from '../lib/live/api'
import type { LiveItem } from '../lib/live/types'
import { computeLifecycleStatus, getBroadcastStatusLabel, getScheduledEndMs, normalizeBroadcastStatus } from '../lib/broadcastStatus'

const route = useRoute()
const router = useRouter()
const { now } = useNow(1000)
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'
// const wsBase = resolveWsBase(apiBase)
const sockJsUrl = resolveSockJsUrl(apiBase)
const sseSource = ref<EventSource | null>(null)
const sseConnected = ref(false)
const sseRetryCount = ref(0)
const sseRetryTimer = ref<number | null>(null)
const statsTimer = ref<number | null>(null)
const refreshTimer = ref<number | null>(null)
const joinInFlight = ref(false)
const streamToken = ref<string | null>(null)
const viewerId = ref<string | null>(resolveViewerId(getAuthUser()))
const joinedBroadcastId = ref<number | null>(null)
const leaveRequested = ref(false)
const viewerContainerRef = ref<HTMLDivElement | null>(null)
const openviduInstance = ref<OpenVidu | null>(null)
const openviduSession = ref<Session | null>(null)
const openviduSubscriber = ref<Subscriber | null>(null)
const openviduConnected = ref(false)
const openviduConnectionId = ref<string | null>(null)

const { handleImageError } = createImageErrorHandler()

const liveId = computed(() => {
  const value = route.params.id
  return Array.isArray(value) ? value[0] : value
})

const liveItem = ref<LiveItem | null>(null)

const lifecycleStatus = computed(() => {
  if (!liveItem.value) {
    return 'RESERVED'
  }
  const startAtMs = parseLiveDate(liveItem.value.startAt).getTime()
  const endAtMs = parseLiveDate(liveItem.value.endAt).getTime()
  return computeLifecycleStatus({
    status: normalizeBroadcastStatus(liveItem.value.status),
    startAtMs: Number.isNaN(startAtMs) ? undefined : startAtMs,
    endAtMs: Number.isNaN(endAtMs) ? undefined : endAtMs,
  })
})

const scheduledEndMs = computed(() => {
  if (!liveItem.value) return undefined
  const startAtMs = parseLiveDate(liveItem.value.startAt).getTime()
  return Number.isNaN(startAtMs) ? undefined : getScheduledEndMs(startAtMs)
})

const stopConfirmOpen = ref(false)
const stopConfirmMessage = ref('')

const isChatEnabled = computed(() => lifecycleStatus.value === 'ON_AIR')
const hasChatPermission = ref(true)
const viewerSanctionType = ref<'MUTE' | 'OUT' | null>(null)
const lastSanctionMessage = ref<string | null>(null)
const isChatAvailable = computed(() => isChatEnabled.value && hasChatPermission.value)
const isProductEnabled = computed(() => {
  if (lifecycleStatus.value === 'ON_AIR') return true
  if (lifecycleStatus.value === 'ENDED') {
    return scheduledEndMs.value ? Date.now() <= scheduledEndMs.value : false
  }
  return false
})

const chatHelperMessage = computed(() => {
  if (!isLoggedIn.value) return '로그인 후 이용하실 수 있습니다.'
  if (!hasChatPermission.value) return '채팅이 금지되었습니다.'
  if (!isChatEnabled.value) return '방송 중에만 채팅을 이용할 수 있습니다.'
  return ''
})

const statusLabel = computed(() => getBroadcastStatusLabel(lifecycleStatus.value))
const statusBadgeClass = computed(() => {
  if (lifecycleStatus.value === 'ON_AIR') return 'status-badge--live'
  if (['RESERVED', 'READY'].includes(lifecycleStatus.value)) return 'status-badge--upcoming'
  return 'status-badge--ended'
})
const waitingScreenUrl = computed(() => liveItem.value?.waitScreenUrl ?? '')
const readyCountdownLabel = computed(() => {
  if (!liveItem.value || lifecycleStatus.value !== 'READY') return ''
  const startAtMs = parseLiveDate(liveItem.value.startAt).getTime()
  if (Number.isNaN(startAtMs)) return '방송 시작 대기 중'
  const diffMs = startAtMs - now.value.getTime()
  if (diffMs <= 0) return '방송 시작 대기 중'
  const totalSeconds = Math.ceil(diffMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}분 ${String(seconds).padStart(2, '0')}초 뒤 방송 시작`
})
const elapsedLabel = computed(() => {
  if (!liveItem.value?.startAt) return ''
  const started = parseLiveDate(liveItem.value.startAt)
  if (Number.isNaN(started.getTime())) return ''
  const diffMs = Math.max(0, now.value.getTime() - started.getTime())
  const totalSeconds = Math.floor(diffMs / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  if (hours > 0) {
    return `${pad(hours)}:${pad(minutes)}`
  }
  return `${pad(minutes)}:${pad(seconds)}`
})
const endedCountdownLabel = computed(() => {
  if (lifecycleStatus.value !== 'ENDED' || !scheduledEndMs.value) return ''
  const diffMs = scheduledEndMs.value - now.value.getTime()
  if (diffMs <= 0) return '방송 종료'
  const totalSeconds = Math.ceil(diffMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `종료까지 ${minutes}분 ${String(seconds).padStart(2, '0')}초`
})
const playerMessage = computed(() => {
  if (lifecycleStatus.value === 'STOPPED') {
    return '방송 운영 정책 위반으로 송출 중지되었습니다.'
  }
  if (lifecycleStatus.value === 'ENDED') {
    return '방송이 종료되었습니다.'
  }
  if (lifecycleStatus.value === 'READY') {
    return readyCountdownLabel.value || '방송 시작 대기 중'
  }
  return ''
})
const viewerExtraLabel = computed(() => {
  if (lifecycleStatus.value === 'READY') {
    return readyCountdownLabel.value || '방송 시작 대기 중'
  }
  if (['ON_AIR', 'STOPPED', 'ENDED'].includes(lifecycleStatus.value)) {
    return elapsedLabel.value ? `경과 ${elapsedLabel.value}` : ''
  }
  return ''
})
const hasSubscriberStream = computed(() => openviduConnected.value && !!openviduSubscriber.value)

const scheduledLabel = computed(() => {
  if (!liveItem.value) {
    return ''
  }
  const start = parseLiveDate(liveItem.value.startAt)
  const dayNames = ['일', '월', '화', '수', '목', '금', '토']
  const month = String(start.getMonth() + 1).padStart(2, '0')
  const date = String(start.getDate()).padStart(2, '0')
  const day = dayNames[start.getDay()]
  const hours = String(start.getHours()).padStart(2, '0')
  const minutes = String(start.getMinutes()).padStart(2, '0')
  return `${month}.${date} (${day}) ${hours}:${minutes} 예정`
})

const buildLiveItem = (detail: {
  broadcastId: number
  title: string
  notice?: string
  thumbnailUrl?: string
  waitScreenUrl?: string
  scheduledAt?: string
  startedAt?: string
  sellerName?: string
  status?: string
  stoppedReason?: string
}) => {
  const startAt = detail.startedAt ?? detail.scheduledAt ?? ''
  const startAtMs = startAt ? parseLiveDate(startAt).getTime() : NaN
  const endAtMs = Number.isNaN(startAtMs) ? undefined : getScheduledEndMs(startAtMs)
  const endAt = endAtMs ? new Date(endAtMs).toISOString() : ''
  return {
    id: String(detail.broadcastId),
    title: detail.title,
    description: detail.notice ?? '',
    thumbnailUrl: detail.thumbnailUrl ?? '',
    waitScreenUrl: detail.waitScreenUrl ?? '',
    startAt,
    endAt,
    status: detail.status,
    stoppedReason: detail.stoppedReason,
    sellerName: detail.sellerName ?? '',
  }
}

const loadDetail = async () => {
  if (!broadcastId.value) return
  try {
    const detail = await fetchPublicBroadcastDetail(broadcastId.value)
    liveItem.value = buildLiveItem(detail)
    likeCount.value = detail.totalLikes ?? 0
    if (isLoggedIn.value) {
      await loadLikeStatus()
    } else {
      isLiked.value = false
    }
  } catch {
    liveItem.value = null
  }
}

const loadLikeStatus = async () => {
  if (!broadcastId.value || !isLoggedIn.value) return
  try {
    const result = await fetchBroadcastLikeStatus(broadcastId.value)
    isLiked.value = result.liked
    likeCount.value = result.likeCount ?? likeCount.value
  } catch {
    return
  }
}

const loadStats = async () => {
  if (!broadcastId.value || !liveItem.value) return
  try {
    const stats = await fetchBroadcastStats(broadcastId.value)
    liveItem.value = {
      ...liveItem.value,
      viewerCount: stats.viewerCount ?? liveItem.value.viewerCount ?? 0,
    }
    likeCount.value = stats.likeCount ?? likeCount.value
  } catch {
    return
  }
}

const loadProducts = async () => {
  if (!broadcastId.value) {
    products.value = []
    return
  }
  try {
    products.value = await fetchBroadcastProducts(broadcastId.value)
  } catch {
    products.value = []
  }
}

const products = ref<BroadcastProductItem[]>([])
const sortedProducts = computed(() => {
  const list = products.value.slice()
  const orderMap = new Map(list.map((product, index) => [product.id, index]))
  return list.sort((a, b) => {
    if (a.isSoldOut && !b.isSoldOut) return 1
    if (!a.isSoldOut && b.isSoldOut) return -1
    if (a.isPinned && !b.isPinned) return -1
    if (!a.isPinned && b.isPinned) return 1
    return (orderMap.get(a.id) ?? 0) - (orderMap.get(b.id) ?? 0)
  })
})

const formatPrice = (price: number) => {
  return `${price.toLocaleString('ko-KR')}원`
}

const handleProductClick = (productId: string) => {
  const selected = products.value.find((product) => product.id === productId)
  if (!isProductEnabled.value || selected?.isSoldOut) return
  if (!isLoggedIn.value) {
    alert('회원만 이용할 수 있습니다. 로그인해주세요.')
    router.push({ path: '/login', query: { redirect: route.fullPath } }).catch(() => {})
    return
  }
  router.push({ name: 'product-detail', params: { id: productId } })
}

const showChat = ref(true)
const stopEntryPrompted = ref(false)
const isStopRestricted = ref(false)
const isFullscreen = ref(false)
const stageRef = ref<HTMLElement | null>(null)
const isLiked = ref(false)
const likeCount = ref(0)
const likeInFlight = ref(false)
const reportInFlight = ref(false)
const hasReported = ref(false)

const requireMemberAction = () => {
  if (!isLoggedIn.value) {
    alert('회원만 이용할 수 있습니다.')
    return false
  }
  return true
}

const toggleLike = async () => {
  if (!broadcastId.value || !requireMemberAction() || likeInFlight.value) return
  likeInFlight.value = true
  try {
    const result = await toggleBroadcastLike(broadcastId.value)
    isLiked.value = result.liked
    likeCount.value = result.likeCount
  } catch {
    return
  } finally {
    likeInFlight.value = false
  }
}

const submitReport = async () => {
  if (!broadcastId.value || !requireMemberAction() || reportInFlight.value) return
  reportInFlight.value = true
  try {
    const result = await reportBroadcast(broadcastId.value)
    hasReported.value = hasReported.value || result.reported
    if (result.reported) {
      alert('신고가 접수되었습니다.')
    } else {
      alert('이미 신고한 방송입니다.')
    }
  } catch {
    return
  } finally {
    reportInFlight.value = false
  }
}

const isSettingsOpen = ref(false)
const settingsButtonRef = ref<HTMLElement | null>(null)
const settingsPanelRef = ref<HTMLElement | null>(null)
const volume = ref(60)
const selectedQuality = ref<'auto' | '1080p' | '720p' | '480p'>('auto')
const qualityObserver = ref<MutationObserver | null>(null)

type QualityOption = {
  value: 'auto' | '1080p' | '720p' | '480p'
  label: string
  width?: number
  height?: number
}

const qualityOptions: QualityOption[] = [
  { value: 'auto', label: '자동' },
  { value: '1080p', label: '1080p', width: 1920, height: 1080 },
  { value: '720p', label: '720p', width: 1280, height: 720 },
  { value: '480p', label: '480p', width: 854, height: 480 },
]

const applySubscriberVolume = () => {
  const container = stageRef.value
  if (!container) return
  const video = container.querySelector('video') as HTMLVideoElement | null
  if (!video) return
  video.muted = false
  video.volume = Math.min(1, Math.max(0, volume.value / 100))
}

const applyVideoQuality = async (value: typeof selectedQuality.value) => {
  try {
    const container = stageRef.value
    if (!container) return
    container.dataset.quality = value
    const subscriber = openviduSubscriber.value as { setPreferredResolution?: (width: number, height: number) => void } | null
    if (subscriber?.setPreferredResolution) {
      if (value === 'auto') {
        subscriber.setPreferredResolution(0, 0)
      } else {
        const option = qualityOptions.find((item) => item.value === value)
        if (option?.width && option?.height) {
          subscriber.setPreferredResolution(option.width, option.height)
        }
      }
    }
    const video = container.querySelector('video') as HTMLVideoElement | null
    if (!video) return
    const stream = video.srcObject
    if (!(stream instanceof MediaStream)) return
    const [track] = stream.getVideoTracks()
    if (!track) return
    if (value === 'auto') {
      await track.applyConstraints({})
      return
    }
    const option = qualityOptions.find((item) => item.value === value)
    if (!option?.width || !option?.height) return
    await track.applyConstraints({
      width: { ideal: option.width },
      height: { ideal: option.height },
    })
  } catch {
    return
  }
}

const clearViewerContainer = () => {
  if (viewerContainerRef.value) {
    viewerContainerRef.value.innerHTML = ''
  }
}

const resetOpenViduState = () => {
  openviduConnected.value = false
  openviduSubscriber.value = null
  openviduSession.value = null
  openviduInstance.value = null
  openviduConnectionId.value = null
  clearViewerContainer()
}

const disconnectOpenVidu = () => {
  if (openviduSession.value) {
    try {
      if (openviduSubscriber.value) {
        openviduSession.value.unsubscribe(openviduSubscriber.value as Subscriber)
      }
      openviduSession.value.disconnect()
    } catch {
      // noop
    }
  }
  resetOpenViduState()
}

const connectSubscriber = async (token: string) => {
  if (!viewerContainerRef.value) return
  try {
    disconnectOpenVidu()
    openviduInstance.value = new OpenVidu()
    openviduSession.value = openviduInstance.value.initSession()
    openviduSession.value.on('streamCreated', (event) => {
      if (!viewerContainerRef.value || !openviduSession.value) return
      if (openviduSubscriber.value) {
        openviduSession.value.unsubscribe(openviduSubscriber.value as Subscriber)
        openviduSubscriber.value = null
        clearViewerContainer()
      }
      openviduSubscriber.value = openviduSession.value.subscribe(event.stream, viewerContainerRef.value, {
        insertMode: 'append',
      })
      applySubscriberVolume()
      void applyVideoQuality(selectedQuality.value)
    })
    openviduSession.value.on('streamDestroyed', (event: StreamEvent) => {
      event.preventDefault()
      openviduSubscriber.value = null
      clearViewerContainer()
    })
    openviduSession.value.on('sessionDisconnected', (event) => {
      if (event.reason === 'forceDisconnectByServer') {
        notifyViewerSanction('OUT')
      }
    })
    await openviduSession.value.connect(token)
    openviduConnectionId.value = openviduSession.value.connection?.connectionId ?? null
    openviduConnected.value = true
  } catch {
    disconnectOpenVidu()
  }
}

const ensureSubscriberConnected = async () => {
  if (!streamToken.value || lifecycleStatus.value !== 'ON_AIR') return
  if (openviduConnected.value) return
  await connectSubscriber(streamToken.value)
}

const toggleChat = () => {
  if (isStopRestricted.value) return
  showChat.value = !showChat.value
}

const toggleFullscreen = async () => {
  const el = stageRef.value
  if (!el) return
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
      isFullscreen.value = false
    } else if (el.requestFullscreen) {
      await el.requestFullscreen()
      isFullscreen.value = true
    }
  } catch {
    return
  }
}

const toggleSettings = () => {
  isSettingsOpen.value = !isSettingsOpen.value
}

type LiveMessageType = 'TALK' | 'ENTER' | 'EXIT' | 'PURCHASE' | 'NOTICE'

// [수정] DTO 구조를 백엔드와 맞춤
type LiveChatMessageDTO = {
  broadcastId: number
  memberEmail: string
  type: LiveMessageType
  sender: string
  content: string
  senderRole?: string
  connectionId?: string
  vodPlayTime: number
  sentAt?: number
}

type ChatMessage = {
  id: string
  user: string
  text: string
  at: Date
  kind?: 'system' | 'user'
  senderRole?: string
}

const messages = ref<ChatMessage[]>([])

const input = ref('')
const isLoggedIn = ref(true)
const chatListRef = ref<HTMLDivElement | null>(null)
const CHAT_SCROLL_THRESHOLD_PX = 120
const showScrollToBottom = ref(false)
const memberEmail = ref<string>("") // [확인] memberEmail ref
const nickname = ref(`guest_${Math.floor(Math.random() * 1000)}`)
const stompClient = ref<Client | null>(null)
let stompSubscription: StompSubscription | null = null
const isChatConnected = ref(false)
const ENTER_SENT_KEY_PREFIX = 'deskit_live_enter_sent_v1'

const getAccessToken = () => {
  return localStorage.getItem('access') || sessionStorage.getItem('access')
}

const broadcastId = computed(() => {
  if (!liveId.value) {
    return undefined
  }
  const raw = String(liveId.value)
  const numeric = Number.parseInt(raw.replace(/[^0-9]/g, ''), 10)
  return Number.isFinite(numeric) ? numeric : undefined
})

const formatChatTime = (value: Date) => {
  const hours = String(value.getHours()).padStart(2, '0')
  const minutes = String(value.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}


const formatChatUser = (message: ChatMessage) => {
  if (message.kind === 'system') {
    return message.user
  }
  if (message.senderRole) {
    if (message.senderRole === 'ROLE_ADMIN') {
      return `${message.user}(관리자)`
    }
    if (message.senderRole.startsWith('ROLE_SELLER')) {
      return `${message.user}(판매자)`
    }
    if (message.senderRole === 'ROLE_MEMBER' && message.user === nickname.value) {
      return `${message.user}(나)`
    }
    return message.user
  }
  if (message.user === nickname.value) {
    return `${message.user}(나)`
  }
  if (liveItem.value?.sellerName && message.user === liveItem.value.sellerName) {
    return `${message.user}(판매자)`
  }
  return message.user
}

const scrollToBottom = () => {
  nextTick(() => {
    if (!chatListRef.value) {
      return
    }
    chatListRef.value.scrollTop = chatListRef.value.scrollHeight
    updateChatScrollIndicator()
  })
}

const updateChatScrollIndicator = () => {
  const el = chatListRef.value
  if (!el) return
  const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  showScrollToBottom.value = distanceFromBottom > CHAT_SCROLL_THRESHOLD_PX
}

const handleChatScroll = () => {
  updateChatScrollIndicator()
}

const parseSseData = (event: MessageEvent) => {
  if (!event.data) return null
  try {
    return JSON.parse(event.data)
  } catch {
    return event.data
  }
}

const resolveProductId = (data: unknown) => {
  if (typeof data === 'number') return String(data)
  if (typeof data === 'string') return data
  if (data && typeof data === 'object') {
    const record = data as { productId?: number | string; bpId?: number | string; id?: number | string }
    if (record.productId !== undefined) return String(record.productId)
    if (record.bpId !== undefined) return String(record.bpId)
    if (record.id !== undefined) return String(record.id)
  }
  return null
}

const applyPinnedProduct = (productId: string | null) => {
  products.value = products.value.map((product) => ({
    ...product,
    isPinned: productId ? product.id === productId : false,
  }))
}

const markProductSoldOut = (productId: string | null) => {
  if (!productId) return
  products.value = products.value.map((product) =>
    product.id === productId
      ? {
        ...product,
        isSoldOut: true,
        stockQty: 0,
      }
      : product,
  )
}

const buildStopConfirmMessage = () => {
  return '방송 운영 정책 위반으로 방송이 중지되었습니다.\n방송에서 나가시겠습니까?'
}

const handleStopConfirm = () => {
  router.push({ name: 'live' }).catch(() => {})
}

const handleStopCancel = () => {
  isStopRestricted.value = true
  showChat.value = false
}

const handleStopDecision = (message: string) => {
  stopConfirmMessage.value = message
  stopConfirmOpen.value = true
}

const promptStoppedEntry = () => {
  if (stopEntryPrompted.value) return
  stopEntryPrompted.value = true
  handleStopDecision('해당 방송은 운영정책 위반으로 송출 중지되었습니다. 방송을 나가겠습니까?')
}

const scheduleRefresh = () => {
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = window.setTimeout(() => {
    void loadDetail()
    void loadStats()
    void loadProducts()
    void refreshChatPermission()
  }, 500)
}

const resolveMemberId = () => {
  const id = viewerId.value ?? resolveViewerId(getAuthUser())
  if (!id) return undefined
  const numeric = Number(id)
  if (Number.isNaN(numeric)) {
    return undefined
  }
  return numeric
}

const refreshChatPermission = async () => {
  if (!broadcastId.value) return
  try {
    const permission = await fetchChatPermission(broadcastId.value, resolveMemberId())
    hasChatPermission.value = permission
  } catch {
    hasChatPermission.value = true
  }
}

const notifyViewerSanction = (type: 'MUTE' | 'OUT', actorLabel?: string) => {
  if (viewerSanctionType.value === type) {
    return
  }
  viewerSanctionType.value = type
  const actorSuffix = actorLabel ? `${actorLabel}에 의해 ` : '관리자/판매자에 의해 '
  if (type === 'MUTE') {
    hasChatPermission.value = false
    input.value = ''
    const message = `${actorSuffix}채팅이 금지되었습니다.`
    if (lastSanctionMessage.value !== message) {
      lastSanctionMessage.value = message
      alert(message)
      appendMessage({
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        user: 'system',
        text: message,
        at: new Date(),
        kind: 'system',
      })
    }
    return
  }
  const message = `${actorSuffix}강제 퇴장되었습니다.`
  if (lastSanctionMessage.value !== message) {
    lastSanctionMessage.value = message
    alert(message)
  }
  void sendLeaveSignal()
  disconnectChat()
  disconnectOpenVidu()
  sseSource.value?.close()
  sseSource.value = null
  router.push({ name: 'live' }).catch(() => {})
}

watch([hasChatPermission, lifecycleStatus], ([nextPermission, nextStatus], [prevPermission]) => {
  if (!nextPermission) {
    input.value = ''
    if (prevPermission && viewerSanctionType.value !== 'MUTE' && nextStatus === 'ON_AIR') {
      notifyViewerSanction('MUTE')
    }
  }
})

const handleSseEvent = (event: MessageEvent) => {
  const data = parseSseData(event)
  switch (event.type) {
    case 'BROADCAST_READY':
    case 'BROADCAST_UPDATED':
    case 'BROADCAST_STARTED':
      scheduleRefresh()
      break
    case 'PRODUCT_PINNED':
      applyPinnedProduct(resolveProductId(data))
      scheduleRefresh()
      break
    case 'PRODUCT_UNPINNED':
      applyPinnedProduct(null)
      scheduleRefresh()
      break
    case 'PRODUCT_SOLD_OUT':
      markProductSoldOut(resolveProductId(data))
      scheduleRefresh()
      break
    case 'SANCTION_ALERT':
      if (typeof data === 'object' && data) {
        const sanctionType = String((data as { type?: string }).type || '').toUpperCase()
        const actorType = String((data as { actorType?: string }).actorType || '').toUpperCase()
        const actorLabel = actorType === 'ADMIN' ? '관리자' : actorType === 'SELLER' ? '판매자' : ''
        if (sanctionType === 'MUTE') {
          notifyViewerSanction('MUTE', actorLabel || undefined)
          break
        }
        if (sanctionType === 'OUT') {
          notifyViewerSanction('OUT', actorLabel || undefined)
          break
        }
      }
      alert('제재가 적용되었습니다.')
      router.push({ name: 'live' }).catch(() => {})
      break
    case 'BROADCAST_CANCELED':
      alert('방송이 자동 취소되었습니다.')
      router.push({ name: 'live' }).catch(() => {})
      break
    case 'BROADCAST_ENDED':
      alert('방송이 종료되었습니다.')
      scheduleRefresh()
      break
    case 'BROADCAST_SCHEDULED_END':
      alert('방송이 종료되었습니다.')
      router.push({ name: 'live' }).catch(() => {})
      break
    case 'BROADCAST_STOPPED':
      if (liveItem.value) {
        liveItem.value = {
          ...liveItem.value,
          status: 'STOPPED',
        }
      }
      scheduleRefresh()
      stopEntryPrompted.value = true
      handleStopDecision(buildStopConfirmMessage())
      break
    default:
      break
  }
}

const scheduleReconnect = (id: number) => {
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  const delay = Math.min(30000, 1000 * 2 ** sseRetryCount.value)
  const jitter = Math.floor(Math.random() * 500)
  sseRetryTimer.value = window.setTimeout(() => {
    connectSse(id)
  }, delay + jitter)
  sseRetryCount.value += 1
}

const connectSse = (id: number) => {
  sseSource.value?.close()
  const user = getAuthUser()
  const currentViewerId = viewerId.value ?? resolveViewerId(user)
  const query = currentViewerId ? `?viewerId=${encodeURIComponent(currentViewerId)}` : ''
  const source = new EventSource(`${apiBase}/broadcasts/${id}/subscribe${query}`)
  const events = [
    'BROADCAST_READY',
    'BROADCAST_UPDATED',
    'BROADCAST_STARTED',
    'PRODUCT_PINNED',
    'PRODUCT_UNPINNED',
    'PRODUCT_SOLD_OUT',
    'SANCTION_ALERT',
    'BROADCAST_ENDING_SOON',
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
      scheduleReconnect(id)
    }
  }
  sseSource.value = source
}

const handleSseVisibilityChange = () => {
  if (document.visibilityState !== 'visible') {
    return
  }
  if (!broadcastId.value) {
    return
  }
  if (!sseConnected.value) {
    connectSse(broadcastId.value)
    return
  }
  scheduleRefresh()
}

const startStatsPolling = () => {
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = window.setInterval(() => {
    if (document.visibilityState !== 'visible') {
      return
    }
    if (!['READY', 'ON_AIR', 'ENDED', 'STOPPED'].includes(lifecycleStatus.value)) {
      return
    }
    void loadStats()
    if (!sseConnected.value) {
      void loadProducts()
    }
  }, 5000)
}

const requestJoinToken = async () => {
  if (!broadcastId.value) return
  if (lifecycleStatus.value !== 'ON_AIR') return
  if (joinInFlight.value) return
  if (joinedBroadcastId.value === broadcastId.value) return
  joinInFlight.value = true
  try {
    streamToken.value = await joinBroadcast(broadcastId.value, viewerId.value)
    joinedBroadcastId.value = broadcastId.value
  } catch (error) {
    const code =
      (error as { code?: string } | null)?.code ||
      (error as { response?: { data?: { error?: { code?: string } } } } | null)?.response?.data?.error?.code
    if (code === 'B007') {
      alert('관리자/판매자에 의해 방송 방 입장이 금지되었습니다.')
      router.push({ name: 'live' }).catch(() => {})
    }
    return
  } finally {
    joinInFlight.value = false
  }
}

const sendLeaveSignal = async (useBeacon = false) => {
  if (!joinedBroadcastId.value || !viewerId.value || leaveRequested.value) return
  leaveRequested.value = true
  const url = `${apiBase}/broadcasts/${joinedBroadcastId.value}/leave?viewerId=${encodeURIComponent(viewerId.value)}`
  if (useBeacon && navigator.sendBeacon) {
    navigator.sendBeacon(url)
    return
  }
  await leaveBroadcast(joinedBroadcastId.value, viewerId.value).catch(() => {})
}

const handlePageHide = () => {
  void sendLeaveSignal(true)
}

const appendMessage = (message: ChatMessage) => {
  messages.value.push(message)
  scrollToBottom()
}

// [수정] 인증 정보 갱신 시 email을 저장하도록 변경
const refreshAuth = () => {
  const user = getAuthUser()
  isLoggedIn.value = user !== null
  nickname.value = normalizeDisplayName(user?.name, nickname.value)
  // memberId 관련 로직을 제거하고 email을 할당
  memberEmail.value = user?.email || ""
}

const sendSocketMessage = (type: LiveMessageType, content: string) => {
  if (!stompClient.value?.connected || !broadcastId.value) {
    return
  }
  const payload: LiveChatMessageDTO = {
    broadcastId: broadcastId.value,
    memberEmail: memberEmail.value, // [수정] memberEmail 사용
    type,
    sender: normalizeDisplayName(nickname.value, '시청자'),
    content,
    connectionId: openviduConnectionId.value ?? undefined,
    vodPlayTime: 0,
    sentAt: Date.now(),
  }
  stompClient.value.publish({
    destination: '/pub/chat/message',
    body: JSON.stringify(payload),
  })
}

const handleIncomingMessage = (payload: LiveChatMessageDTO) => {
  const kind: ChatMessage['kind'] = payload.type === 'TALK' ? 'user' : 'system'
  const user = kind === 'system' ? 'system' : normalizeDisplayName(payload.sender, '시청자')
  const sentAt = payload.sentAt ? new Date(payload.sentAt) : new Date()
  appendMessage({
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    user,
    text: payload.content ?? '',
    at: sentAt,
    kind,
    senderRole: payload.senderRole,
  })
}

const fetchRecentMessages = async () => {
  if (!broadcastId.value) {
    return
  }
  try {
    // const response = await fetch(`${wsBase}/livechats/${broadcastId.value}/recent?seconds=60`)
    const response = await fetch(`${apiBase}/livechats/${broadcastId.value}/recent?seconds=60`)
    if (!response.ok) {
      return
    }
    const recent = (await response.json()) as LiveChatMessageDTO[]
    if (!Array.isArray(recent) || recent.length === 0) {
      return
    }
    messages.value = recent
      .filter((item) => item.type === 'TALK')
      .map((item) => ({
        id: `${item.sentAt ?? Date.now()}-${Math.random().toString(16).slice(2)}`,
        user: normalizeDisplayName(item.sender, '시청자'),
        text: item.content ?? '',
        at: new Date(item.sentAt ?? Date.now()),
        kind: 'user' as const,
        senderRole: item.senderRole,
      }))
    scrollToBottom()
  } catch (error) {
    console.error('[livechat] recent fetch failed', error)
  }
}

const connectChat = () => {
  if (!broadcastId.value || stompClient.value?.active) {
    return
  }
  const client = new Client({
    webSocketFactory: () =>
      new SockJS(sockJsUrl, null, {
        transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
        withCredentials: true,
      }),
    reconnectDelay: 5000,
  })
  const access = getAccessToken()
  if (access) {
    client.connectHeaders = {
      access,
      Authorization: `Bearer ${access}`,
    }
  }

  client.onConnect = () => {
    isChatConnected.value = true
    stompSubscription?.unsubscribe()
    stompSubscription = client.subscribe(`/sub/chat/${broadcastId.value}`, (frame) => {
      try {
        const payload = JSON.parse(frame.body) as LiveChatMessageDTO
        handleIncomingMessage(payload)
      } catch (error) {
        console.error('[livechat] message parse failed', error)
      }
    })
    if (shouldSendEnterMessage()) {
      sendSocketMessage('ENTER', `${nickname.value} entered the room.`)
      markEnterMessageSent()
    }
  }

  client.onStompError = (frame) => {
    console.error('[livechat] stomp error', frame.headers, frame.body)
  }

  client.onWebSocketClose = () => {
    isChatConnected.value = false
  }

  client.onDisconnect = () => {
    isChatConnected.value = false
  }

  stompClient.value = client
  client.activate()
}

const disconnectChat = () => {
  if (stompClient.value?.connected) {
    sendSocketMessage('EXIT', `${nickname.value} left the room.`)
  }
  stompSubscription?.unsubscribe()
  stompSubscription = null
  if (stompClient.value) {
    stompClient.value.deactivate()
    stompClient.value = null
  }
  isChatConnected.value = false
}

const getEnterSentKey = () => {
  if (!broadcastId.value) {
    return null
  }
  return `${ENTER_SENT_KEY_PREFIX}:${broadcastId.value}`
}

const shouldSendEnterMessage = () => {
  const key = getEnterSentKey()
  if (!key) {
    return false
  }
  try {
    return localStorage.getItem(key) !== 'true'
  } catch {
    return true
  }
}

const markEnterMessageSent = () => {
  const key = getEnterSentKey()
  if (!key) {
    return
  }
  try {
    localStorage.setItem(key, 'true')
  } catch {
    return
  }
}

const sendMessage = () => {
  if (!isChatAvailable.value || !isChatConnected.value) {
    return
  }
  if (!isLoggedIn.value) {
    alert('회원만 이용할 수 있습니다. 로그인해주세요.')
    router.push({ path: '/login', query: { redirect: route.fullPath } }).catch(() => {})
    return
  }
  const trimmed = input.value.trim()
  if (!trimmed) {
    return
  }
  // messages.value.push({
  //   id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
  //   user: '나',
  //   text: trimmed,
  //   at: new Date(),
  //   kind: 'user',
  // })
  sendSocketMessage('TALK', trimmed)
  input.value = ''
  scrollToBottom()
}

onMounted(() => {
  scrollToBottom()
})

const WATCH_HISTORY_CONSENT_KEY = 'deskit_live_watch_history_consent_v1'
const hasWatchHistoryConsent = ref(false)
const showWatchHistoryConsent = ref(false)

const requestWatchHistoryConsent = () => {
  try {
    hasWatchHistoryConsent.value =
      typeof localStorage !== 'undefined' && localStorage.getItem(WATCH_HISTORY_CONSENT_KEY) === 'true'
  } catch {
    hasWatchHistoryConsent.value = false
  }

  if (!hasWatchHistoryConsent.value) {
    showWatchHistoryConsent.value = true
  }
}

const handleConfirmWatchHistory = () => {
  hasWatchHistoryConsent.value = true
  showWatchHistoryConsent.value = false
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(WATCH_HISTORY_CONSENT_KEY, 'true')
    }
  } catch {
    return
  }
}

const handleCancelWatchHistory = () => {
  showWatchHistoryConsent.value = false
  router.push({ name: 'live' }).catch(() => {})
}

onMounted(() => {
  requestWatchHistoryConsent()
})

const handleDocumentClick = (event: MouseEvent) => {
  if (!isSettingsOpen.value) {
    return
  }
  const target = event.target as Node | null
  if (
    settingsButtonRef.value?.contains(target) ||
    settingsPanelRef.value?.contains(target)
  ) {
    return
  }
  isSettingsOpen.value = false
}

const handleDocumentKeydown = (event: KeyboardEvent) => {
  if (!isSettingsOpen.value) {
    return
  }
  if (event.key === 'Escape') {
    isSettingsOpen.value = false
  }
}

const handleFullscreenChange = () => {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleDocumentKeydown)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onMounted(() => {
  if (!stageRef.value) return
  qualityObserver.value?.disconnect()
  qualityObserver.value = new MutationObserver(() => {
    void applyVideoQuality(selectedQuality.value)
    applySubscriberVolume()
  })
  qualityObserver.value.observe(stageRef.value, { childList: true, subtree: true })
})

onMounted(() => {
  window.addEventListener('pagehide', handlePageHide)
})

onMounted(() => {
  window.addEventListener('visibilitychange', handleSseVisibilityChange)
  window.addEventListener('focus', handleSseVisibilityChange)
})

const reconnectSseIfNeeded = (nextViewerId: string | null, previousViewerId: string | null) => {
  if (!broadcastId.value || !nextViewerId || nextViewerId === previousViewerId) {
    return
  }
  sseSource.value?.close()
  sseSource.value = null
  sseConnected.value = false
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  sseRetryTimer.value = null
  connectSse(broadcastId.value)
}

const syncViewerIdentity = () => {
  const previousViewerId = viewerId.value
  const nextViewerId = resolveViewerId(getAuthUser())
  viewerId.value = nextViewerId
  reconnectSseIfNeeded(nextViewerId, previousViewerId)
}

const handleAuthUpdate = () => {
  refreshAuth()
  syncViewerIdentity()
  void refreshChatPermission()
  void loadLikeStatus()
}

const initializeAuth = async () => {
  const previousViewerId = viewerId.value
  await hydrateSessionUser()
  refreshAuth()
  const nextViewerId = resolveViewerId(getAuthUser())
  viewerId.value = nextViewerId
  reconnectSseIfNeeded(nextViewerId, previousViewerId)
  void refreshChatPermission()
  void loadLikeStatus()
}

onMounted(() => {
  void initializeAuth()
  window.addEventListener('deskit-user-updated', handleAuthUpdate)
})

watch(
  broadcastId,
  (value, previous) => {
    if (value === previous) {
      return
    }
    if (previous && joinedBroadcastId.value) {
      void sendLeaveSignal()
    }
    leaveRequested.value = false
    joinedBroadcastId.value = null
    isLiked.value = false
    likeCount.value = 0
    hasReported.value = false
    streamToken.value = null
    void loadDetail()
    void loadProducts()
    void loadStats()
    messages.value = []
    disconnectChat()
    disconnectOpenVidu()
    sseSource.value?.close()
    sseSource.value = null
    sseConnected.value = false
    if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
    sseRetryTimer.value = null
    if (statsTimer.value) window.clearInterval(statsTimer.value)
    statsTimer.value = null
    if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
    refreshTimer.value = null
    hasChatPermission.value = true
    if (value) {
      if (isChatEnabled.value) {
        fetchRecentMessages()
        connectChat()
      }
      connectSse(value)
      startStatsPolling()
      void requestJoinToken()
      void refreshChatPermission()
    }
  },
  { immediate: true }
)

watch(
  isChatEnabled,
  (enabled) => {
    if (!broadcastId.value) return
    if (enabled) {
      fetchRecentMessages()
      connectChat()
    } else {
      disconnectChat()
    }
  },
)

watch(
  lifecycleStatus,
  () => {
    if (lifecycleStatus.value === 'STOPPED') {
      promptStoppedEntry()
    } else {
      isStopRestricted.value = false
      stopEntryPrompted.value = false
    }
    void requestJoinToken()
    if (lifecycleStatus.value === 'ON_AIR') {
      void ensureSubscriberConnected()
      return
    }
    disconnectOpenVidu()
  },
)

watch(streamToken, () => {
  if (lifecycleStatus.value === 'ON_AIR') {
    void ensureSubscriberConnected()
  }
})

watch(
  selectedQuality,
  (value) => {
    void applyVideoQuality(value)
  },
  { immediate: true },
)

watch(
  volume,
  () => {
    applySubscriberVolume()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleDocumentKeydown)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  window.removeEventListener('deskit-user-updated', handleAuthUpdate)
  window.removeEventListener('pagehide', handlePageHide)
  window.removeEventListener('visibilitychange', handleSseVisibilityChange)
  window.removeEventListener('focus', handleSseVisibilityChange)
  disconnectOpenVidu()
  qualityObserver.value?.disconnect()
  qualityObserver.value = null
  void sendLeaveSignal()
  disconnectChat()
  sseSource.value?.close()
  sseSource.value = null
  sseConnected.value = false
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  sseRetryTimer.value = null
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = null
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = null
})
</script>

<template>
  <PageContainer>
    <div v-if="stopConfirmOpen" class="stop-blocker" aria-hidden="true"></div>
    <ConfirmModal
      v-model="showWatchHistoryConsent"
      title="시청 기록 수집 안내"
      description="라이브 방송 입장 시 시청 기록이 수집됩니다. 계속 진행하시겠습니까?"
      confirm-text="동의하고 입장하기"
      cancel-text="취소"
      @confirm="handleConfirmWatchHistory"
      @cancel="handleCancelWatchHistory"
    />
    <ConfirmModal
      v-model="stopConfirmOpen"
      title="방송 송출 중지"
      :description="stopConfirmMessage"
      confirm-text="나가기"
      cancel-text="계속 보기"
      @confirm="handleStopConfirm"
      @cancel="handleStopCancel"
    />
    <PageHeader eyebrow="DESKIT LIVE" title="라이브 상세" />

    <div v-if="!liveItem" class="empty-state">
      <p>라이브를 찾을 수 없습니다.</p>
      <RouterLink to="/live" class="link-back">라이브 일정으로 돌아가기</RouterLink>
    </div>

    <section v-else class="live-detail-layout">
      <div
        class="live-detail-main"
        :class="{ 'live-detail-main--chat': showChat && !isStopRestricted }"
        :style="{
          gridTemplateColumns: showChat && !isStopRestricted ? 'minmax(0, 1.6fr) minmax(0, 0.95fr)' : 'minmax(0, 1fr)',
        }"
      >
        <section class="panel panel--player live-detail-main__primary">
          <div class="player-meta">
            <div class="status-row">
              <span class="status-badge" :class="statusBadgeClass">
                {{ statusLabel }}
              </span>
              <span v-if="liveItem.viewerCount != null" class="status-viewers">
                {{ liveItem.viewerCount.toLocaleString() }}명 시청 중
                <span v-if="viewerExtraLabel"> · {{ viewerExtraLabel }}</span>
              </span>
              <span v-else-if="lifecycleStatus === 'RESERVED'" class="status-schedule">
                {{ scheduledLabel }}
              </span>
              <span v-else-if="lifecycleStatus === 'READY'" class="status-schedule">
                {{ readyCountdownLabel || '방송 시작 대기 중' }}
              </span>
              <span v-else-if="lifecycleStatus === 'ENDED'" class="status-ended">
                {{ endedCountdownLabel || '방송 종료' }}
              </span>
              <span v-else-if="lifecycleStatus === 'STOPPED'" class="status-ended">송출 중지</span>
            </div>
            <h3 class="player-title">{{ liveItem.title }}</h3>
            <p v-if="liveItem.description" class="player-desc">{{ liveItem.description }}</p>
          </div>

          <div ref="stageRef" class="player-frame" :class="{ 'player-frame--fullscreen': isFullscreen }">
            <div v-show="hasSubscriberStream" ref="viewerContainerRef" class="player-frame__viewer"></div>
            <div v-if="['READY', 'ENDED', 'STOPPED'].includes(lifecycleStatus)" class="player-frame__placeholder">
              <img
                v-if="waitingScreenUrl && lifecycleStatus !== 'STOPPED'"
                class="player-frame__image"
                :src="waitingScreenUrl"
                alt="대기 화면"
                @error="handleImageError"
              />
              <p v-if="playerMessage && (!waitingScreenUrl || lifecycleStatus === 'STOPPED')" class="player-frame__message">
                {{ playerMessage }}
              </p>
            </div>
            <span v-else-if="!hasSubscriberStream" class="player-frame__label">LIVE 플레이어</span>
            <div v-if="!isStopRestricted" class="player-actions">
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
                <span class="icon-count">{{ likeCount.toLocaleString('ko-KR') }}</span>
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
                <span class="icon-label">신고</span>
              </div>
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
              <div class="player-settings">
                <button
                  ref="settingsButtonRef"
                  type="button"
                  class="icon-circle"
                  aria-controls="player-settings"
                  :aria-expanded="isSettingsOpen ? 'true' : 'false'"
                  aria-label="설정"
                  @click="toggleSettings"
                >
                  <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M4 6h16M4 12h16M4 18h16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                    <circle cx="9" cy="6" r="2" fill="none" stroke="currentColor" stroke-width="1.8" />
                    <circle cx="14" cy="12" r="2" fill="none" stroke="currentColor" stroke-width="1.8" />
                    <circle cx="7" cy="18" r="2" fill="none" stroke="currentColor" stroke-width="1.8" />
                  </svg>
                </button>
                <div
                  v-if="isSettingsOpen"
                  id="player-settings"
                  ref="settingsPanelRef"
                  class="settings-popover"
                >
                  <label class="settings-row">
                    <span class="settings-label">볼륨</span>
                    <input
                      class="toolbar-slider"
                      type="range"
                      min="0"
                      max="100"
                      v-model.number="volume"
                      aria-label="볼륨 조절"
                    />
                  </label>
                  <label class="settings-row">
                    <span class="settings-label">화질</span>
                    <select v-model="selectedQuality" class="settings-select" aria-label="화질">
                      <option v-for="option in qualityOptions" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </option>
                    </select>
                  </label>
                </div>
              </div>
              <button type="button" class="icon-circle" aria-label="전체 화면" @click="toggleFullscreen">
                <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
            </div>
          </div>
        </section>

        <aside v-if="showChat && !isStopRestricted" class="chat-panel ds-surface">
          <header class="chat-head">
            <div class="chat-head__title">
              <h4>실시간 채팅</h4>
              <span v-if="liveItem.viewerCount != null" class="chat-viewers">
                시청자 {{ liveItem.viewerCount.toLocaleString() }}명
              </span>
            </div>
            <button type="button" class="chat-close" aria-label="채팅 닫기" @click="toggleChat">×</button>
          </header>
          <div ref="chatListRef" class="chat-messages" @scroll="handleChatScroll">
            <div
              v-for="message in messages"
                :key="message.id"
                class="chat-message"
                :class="{ 'chat-message--system': message.kind === 'system' }"
            >
              <div class="chat-meta">
                <span class="chat-user">{{ formatChatUser(message) }}</span>
                <span class="chat-time">{{ formatChatTime(message.at) }}</span>
              </div>
              <p class="chat-text">{{ message.text }}</p>
            </div>
          </div>
          <button
            v-if="showScrollToBottom"
            type="button"
            class="chat-scroll-button"
            aria-label="아래로 이동"
            @click="scrollToBottom"
          >
            <svg class="chat-scroll-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 5v12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <path d="M7 12l5 5 5-5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <div class="chat-input">
            <input
              v-model="input"
              type="text"
              placeholder="메시지를 입력하세요."
              :disabled="!isChatConnected || !isChatAvailable"
              @keydown.enter="sendMessage"
            />
            <button
              type="button"
              class="btn primary"
              :disabled="!isChatConnected || !isChatAvailable || !input.trim()"
              @click="sendMessage"
            >
              전송
            </button>
          </div>
          <p v-if="chatHelperMessage" class="chat-helper">{{ chatHelperMessage }}</p>
        </aside>
      </div>

      <section v-if="!isStopRestricted" class="panel panel--products">
        <div class="panel__header">
          <h3 class="panel__title">라이브 상품</h3>
          <span class="panel__count">{{ products.length }}개</span>
        </div>
        <div v-if="!products.length" class="panel__empty">등록된 상품이 없습니다.</div>
        <div v-else class="product-list product-list--grid">
          <button
            v-for="product in sortedProducts"
            :key="product.id"
            type="button"
            class="product-card"
            :class="{ 'product-card--disabled': !isProductEnabled || product.isSoldOut, 'product-card--pinned': product.isPinned }"
            :disabled="!isProductEnabled || product.isSoldOut"
            @click="handleProductClick(product.id)"
          >
            <span v-if="product.isPinned" class="product-card__pin">PIN</span>
            <div class="product-card__thumb ds-thumb-frame ds-thumb-square">
              <img class="ds-thumb-img" :src="product.imageUrl" :alt="product.name" @error="handleImageError" />
            </div>
            <div class="product-card__info">
              <p class="product-card__name">{{ product.name }}</p>
              <p class="product-card__price">
                <span
                  v-if="product.originalPrice && product.originalPrice > product.price"
                  class="product-card__price--original"
                >
                  {{ formatPrice(product.originalPrice) }}
                </span>
                <span class="product-card__price--sale">{{ formatPrice(product.price) }}</span>
              </p>
              <span v-if="product.isSoldOut" class="product-card__badge">품절</span>
            </div>
          </button>
        </div>
      </section>
    </section>
  </PageContainer>
</template>

<style scoped>
.stop-blocker {
  position: fixed;
  inset: 0;
  background: var(--surface);
  z-index: 1300;
}

.live-detail-layout {
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow-x: hidden;
  --danger-color: #dc2626;
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
  position: relative;
}

.product-card--pinned {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 1px rgba(var(--primary-rgb), 0.2);
}

.product-card--disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

.product-card__pin {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(var(--primary-rgb), 0.12);
  color: var(--primary-color);
  font-size: 0.7rem;
  font-weight: 700;
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
}

.product-card__name {
  margin: 0;
  font-weight: 700;
  color: var(--text-strong);
}

.product-card__price {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.95rem;
  display: flex;
  gap: 6px;
  align-items: baseline;
}

.product-card__price--original {
  color: var(--text-soft);
  font-weight: 600;
  text-decoration: line-through;
  font-size: 0.85rem;
}

.product-card__price--sale {
  color: var(--text-strong);
  font-weight: 700;
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

.settings-popover {
  position: absolute;
  top: 0;
  right: calc(100% + 10px);
  background: var(--surface);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.12);
  min-width: 220px;
  display: grid;
  gap: 10px;
}

.settings-select {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  height: 36px;
  padding: 0 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, color 0.2s ease;
}

.settings-select:hover {
  border-color: var(--primary-color);
}

.settings-select:focus-visible,
.toolbar-slider:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.toolbar-slider {
  accent-color: var(--primary-color);
  width: 140px;
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.settings-label {
  font-weight: 800;
  color: var(--text-strong);
}

.viewer-stage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.player-frame {
  position: relative;
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
  background: #272d3b;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  max-width: min(100%, calc((100vh - 180px) * (16 / 9)));
  overflow: hidden;
}

.player-frame__viewer {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: #000;
}

.player-frame__viewer :deep(video) {
  width: 100%;
  height: 100%;
  object-fit: contain;
  transform: scaleX(-1);
}

.player-frame[data-quality='720p'] :deep(video),
.player-frame[data-quality='720p'] :deep(img) {
  filter: blur(0.3px);
}

.player-frame[data-quality='480p'] :deep(video),
.player-frame[data-quality='480p'] :deep(img) {
  filter: blur(0.6px);
  image-rendering: pixelated;
}

.player-frame--fullscreen,
.player-frame:fullscreen {
  width: min(100vw, calc(100vh * (16 / 9)));
  height: min(100vh, calc(100vw * (9 / 16)));
  max-height: 100vh;
  max-width: 100vw;
  border-radius: 0;
  background: #000;
}

.player-frame:fullscreen iframe,
.player-frame:fullscreen video,
.player-frame:fullscreen img {
  object-fit: contain;
}

.player-frame__label {
  opacity: 0.8;
}

.player-frame__placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  text-align: center;
  background: #1f2432;
}

.player-frame__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.player-frame__message {
  font-weight: 900;
  color: #ffffff;
  text-shadow: 0 3px 12px rgba(0, 0, 0, 0.45);
  max-width: min(560px, 100%);
  font-size: 1.35rem;
}

.player-actions {
  position: absolute;
  right: 14px;
  bottom: 14px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  z-index: 2;
}

.icon-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.icon-count,
.icon-label {
  font-size: 0.7rem;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
}

.player-settings {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.player-frame iframe,
.player-frame video,
.player-frame img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  border: 0;
  background: #000;
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

.icon-circle:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.icon-circle.active {
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
  max-height: min(70vh, 720px);
  overflow: hidden;
  position: relative;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chat-head__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-head h4 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.chat-viewers {
  font-size: 0.85rem;
  font-weight: 800;
  color: var(--text-soft);
  background: var(--surface-weak);
  padding: 3px 8px;
  border-radius: 999px;
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

.chat-scroll-button {
  position: absolute;
  bottom: 64px;
  left : 50%;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  transform: translateX(-50%);
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.12);
}

.chat-scroll-icon {
  width: 18px;
  height: 18px;
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

.status-ended {
  color: var(--text-soft);
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

@media (max-width: 640px) {
  .live-detail-main {
    gap: 14px;
  }

  .chat-input {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1120px) {
  .live-detail-main {
    grid-template-columns: 1fr !important;
  }

  .chat-panel {
    width: 100%;
    max-height: min(70vh, 720px);
    overflow: hidden;
    min-height: 0;
  }
}
</style>
