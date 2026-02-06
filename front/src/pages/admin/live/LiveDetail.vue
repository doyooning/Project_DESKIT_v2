<script setup lang="ts">
import { OpenVidu, type Session, type StreamEvent, type Subscriber } from 'openvidu-browser'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { resolveSockJsUrl } from '../../../lib/ws'

import {
  fetchAdminBroadcastDetail,
  fetchBroadcastProducts,
  fetchBroadcastStats,
  joinBroadcast,
  leaveBroadcast,
  sanctionAdminViewer,
  stopAdminBroadcast,
} from '../../../lib/live/api'
import { parseLiveDate } from '../../../lib/live/utils'
import { useNow } from '../../../lib/live/useNow'
import { computeLifecycleStatus, getBroadcastStatusLabel, getScheduledEndMs, normalizeBroadcastStatus } from '../../../lib/broadcastStatus'
import { getAuthUser } from '../../../lib/auth'
import { createImageErrorHandler } from '../../../lib/images/productImages'
// import { resolveWsBase } from '../../../lib/ws'
import { resolveViewerId } from '../../../lib/live/viewer'

const route = useRoute()
const router = useRouter()
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
// const wsBase = resolveWsBase(apiBase)
const sockJsUrl = resolveSockJsUrl(apiBase)

// --- Types ---
type AdminDetail = {
  id: string
  title: string
  sellerName: string
  startedAt: string
  scheduledAt?: string
  status: string
  waitScreenUrl?: string
  stoppedReason?: string
  viewers: number
  reports: number
  likes: number
  elapsed: string
}

type LiveMessageType = 'TALK' | 'ENTER' | 'EXIT' | 'PURCHASE' | 'NOTICE'

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

type ChatMessageUI = {
  id: string
  user: string
  text: string
  time: string
  kind?: 'system' | 'user'
  senderRole?: string
  memberLoginId?: string
  connectionId?: string
}

// --- State ---
const liveId = computed(() => (typeof route.params.liveId === 'string' ? route.params.liveId : ''))
// 웹소켓 통신을 위한 숫자형 ID 변환
const broadcastId = computed(() => {
  if (!liveId.value) return undefined
  const numeric = Number.parseInt(liveId.value.replace(/[^0-9]/g, ''), 10)
  return Number.isFinite(numeric) ? numeric : undefined
})

const detail = ref<AdminDetail | null>(null)
const { now } = useNow(1000)

const stageRef = ref<HTMLDivElement | null>(null)
const isFullscreen = ref(false)
const isSettingsOpen = ref(false)
const settingsButtonRef = ref<HTMLElement | null>(null)
const settingsPanelRef = ref<HTMLElement | null>(null)
const volume = ref(60)
const selectedQuality = ref<'auto' | '1080p' | '720p' | '480p'>('auto')
const qualityObserver = ref<MutationObserver | null>(null)
const showStopModal = ref(false)
const stopReason = ref('')
const stopDetail = ref('')
const error = ref('')
const showChat = ref(true)
const stopEntryPrompted = ref(false)
const isStopRestricted = ref(false)
const chatText = ref('')
const chatListRef = ref<HTMLDivElement | null>(null)
const CHAT_SCROLL_THRESHOLD_PX = 120
const showScrollToBottom = ref(false)

// Chat State (Stomp)
const chatMessages = ref<ChatMessageUI[]>([])
const stompClient = ref<Client | null>(null)
let stompSubscription: StompSubscription | null = null
const isChatConnected = ref(false)
const memberEmail = ref("")
const nickname = ref("관리자")

// Moderation State
const showModerationModal = ref(false)
const moderationTarget = ref<{ user: string; memberLoginId?: string; connectionId?: string } | null>(null)
const moderationType = ref('')
const moderationReason = ref('')
const moderatedUsers = ref<Record<string, { type: string; reason: string; at: string }>>({})

const activePane = ref<'monitor' | 'products'>('monitor')
const liveProducts = ref<
    Array<{
      id: string
      name: string
      option: string
      price: string
      sale: string
      status: string
      thumb: string
      sold: number
      stock: number
      isPinned: boolean
    }>
>([])

// SSE & Polling State
const sseSource = ref<EventSource | null>(null)
const sseConnected = ref(false)
const sseRetryCount = ref(0)
const sseRetryTimer = ref<number | null>(null)
const statsTimer = ref<number | null>(null)
const refreshTimer = ref<number | null>(null)

// OpenVidu State
const joinInFlight = ref(false)
const streamToken = ref<string | null>(null)
const viewerId = ref<string | null>(resolveViewerId(getAuthUser()))
const joinedBroadcastId = ref<number | null>(null)
const joinedViewerId = ref<string | null>(null)
const leaveRequested = ref(false)
const viewerContainerRef = ref<HTMLDivElement | null>(null)
const openviduInstance = ref<OpenVidu | null>(null)
const openviduSession = ref<Session | null>(null)
const openviduSubscriber = shallowRef<Subscriber | null>(null)
const openviduConnected = ref(false)
const { handleImageError } = createImageErrorHandler()

const reasonOptions = [
  '음란물',
  '폭력',
  '국가기밀 누설',
  '불쾌감/공포심/불안감 조성',
  '비방',
  '취급 불가 상품 판매',
  '사이트 운영정책에 맞지 않는 상품',
  '기타',
]

// --- Utils ---
const getAccessToken = () => localStorage.getItem('access') || sessionStorage.getItem('access')

const refreshAuth = () => {
  const user = getAuthUser()
  if (user) {
    memberEmail.value = user.email || ""
    nickname.value = user.name || "관리자"
  }
}

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

const goBack = () => {
  router.back()
}

const goToList = () => {
  router.push('/admin/live?tab=live').catch(() => {})
}

const formatElapsed = (startAt?: string) => {
  if (!startAt) return '00:00:00'
  const started = parseLiveDate(startAt)
  if (Number.isNaN(started.getTime())) return '00:00:00'
  const diff = Math.max(0, Date.now() - started.getTime())
  const totalSeconds = Math.floor(diff / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

// 채팅 사용자 표시 포맷팅 (판매자/관리자 구분)
const formatChatUser = (message: ChatMessageUI) => {
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
  }
  if (message.user === nickname.value) {
    return `${message.user}(관리자)`
  }
  if (detail.value?.sellerName && message.user === detail.value.sellerName) {
    return `${message.user}(판매자)`
  }
  return message.user
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

const scrollChatToBottom = () => {
  const el = chatListRef.value
  if (!el) return
  el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  showScrollToBottom.value = false
}

const mapLiveProduct = (item: {
  id: string
  name: string
  price: number
  originalPrice?: number
  isSoldOut: boolean
  isPinned?: boolean
  imageUrl?: string
  totalQty?: number
  stockQty?: number
}) => {
  const totalQty = item.totalQty ?? item.stockQty ?? 0
  const stockQty = item.stockQty ?? totalQty
  const sold = Math.max(0, totalQty - stockQty)
  const originalPrice = item.originalPrice && item.originalPrice > item.price ? item.originalPrice : item.price
  return {
    id: item.id,
    name: item.name,
    option: item.name,
    price: `₩${originalPrice.toLocaleString('ko-KR')}`,
    sale: `₩${item.price.toLocaleString('ko-KR')}`,
    status: item.isSoldOut ? '품절' : '판매중',
    thumb: item.imageUrl ?? '',
    sold,
    stock: stockQty,
    isPinned: item.isPinned ?? false,
  }
}

const sortedLiveProducts = computed(() => {
  const list = [...liveProducts.value]
  const orderMap = new Map(list.map((product, index) => [product.id, index]))
  return list.sort((a, b) => {
    const aSoldOut = a.status === '품절'
    const bSoldOut = b.status === '품절'
    if (aSoldOut !== bSoldOut) return aSoldOut ? 1 : -1
    if (a.isPinned !== b.isPinned) return a.isPinned ? -1 : 1
    return (orderMap.get(a.id) ?? 0) - (orderMap.get(b.id) ?? 0)
  })
})

// --- Chat Logic (Stomp/WebSocket) ---

// 수신 메시지 처리
const handleIncomingMessage = (payload: LiveChatMessageDTO) => {
  const sentAt = payload.sentAt ? new Date(payload.sentAt) : new Date()
  const displayHour = sentAt.getHours() % 12 || 12
  const timeStr = `${sentAt.getHours() >= 12 ? '오후' : '오전'} ${displayHour}:${String(sentAt.getMinutes()).padStart(2, '0')}`

  chatMessages.value.push({
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    user: payload.type === 'TALK' ? (payload.sender || '알 수 없음') : 'SYSTEM',
    text: payload.content || '',
    time: timeStr,
    kind: payload.type === 'TALK' ? 'user' : 'system',
    senderRole: payload.senderRole,
    memberLoginId: payload.memberEmail,
    connectionId: payload.connectionId,
  })

  nextTick(() => {
    scrollChatToBottom()
  })
}

// 최근 채팅 내역 조회
const fetchRecentMessages = async () => {
  if (!broadcastId.value) return
  try {
    // const response = await fetch(`${wsBase}/livechats/${broadcastId.value}/recent?seconds=300`)
    const response = await fetch(`${apiBase}/livechats/${broadcastId.value}/recent?seconds=60`)
    if (!response.ok) return
    const recent = (await response.json()) as LiveChatMessageDTO[]
    if (!Array.isArray(recent)) return

    chatMessages.value = recent
        .filter((item) => item.type === 'TALK')
        .map((item) => {
          const at = new Date(item.sentAt ?? Date.now())
          const displayHour = at.getHours() % 12 || 12
          const timeStr = `${at.getHours() >= 12 ? '오후' : '오전'} ${displayHour}:${String(at.getMinutes()).padStart(2, '0')}`

          return {
            id: `${item.sentAt ?? Date.now()}-${Math.random().toString(16).slice(2)}`,
            user: item.sender || 'unknown',
            text: item.content ?? '',
            time: timeStr,
            kind: 'user',
            senderRole: item.senderRole,
            memberLoginId: item.memberEmail,
            connectionId: item.connectionId,
          }
        })
    nextTick(() => {
      scrollChatToBottom()
    })
  } catch (error) {
    console.error('[admin chat] recent fetch failed', error)
  }
}

// WebSocket 연결
const connectChat = () => {
  if (!broadcastId.value || stompClient.value?.active) return
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
    client.connectHeaders = { access, Authorization: `Bearer ${access}` }
  }

  client.onConnect = () => {
    isChatConnected.value = true
    stompSubscription?.unsubscribe()
    // 채널 구독
    stompSubscription = client.subscribe(`/sub/chat/${broadcastId.value}`, (frame) => {
      try {
        handleIncomingMessage(JSON.parse(frame.body))
      } catch (error) {
        console.error('[admin chat] message parse failed', error)
      }
    })
  }

  client.onWebSocketClose = () => { isChatConnected.value = false }
  client.onDisconnect = () => { isChatConnected.value = false }
  stompClient.value = client
  client.activate()
}

const disconnectChat = () => {
  stompSubscription?.unsubscribe()
  stompSubscription = null
  stompClient.value?.deactivate()
  stompClient.value = null
  isChatConnected.value = false
}

const sendChat = () => {
  if (!isInteractive.value) return
  if (!chatText.value.trim() || !isChatConnected.value || !broadcastId.value) return

  const payload: LiveChatMessageDTO = {
    broadcastId: broadcastId.value,
    memberEmail: memberEmail.value,
    type: 'TALK',
    sender: nickname.value,
    content: chatText.value.trim(),
    vodPlayTime: 0,
    sentAt: Date.now(),
    senderRole: 'ROLE_ADMIN' // 관리자 권한 명시
  }

  stompClient.value?.publish({
    destination: '/pub/chat/message',
    body: JSON.stringify(payload),
  })
  chatText.value = ''
}

// --- Data Loading ---

const loadDetail = async () => {
  if (!liveId.value) {
    detail.value = null
    liveProducts.value = []
    chatMessages.value = []
    return
  }
  const idValue = Number(liveId.value)
  if (Number.isNaN(idValue)) {
    detail.value = null
    return
  }

  try {
    // 채팅은 별도의 fetchRecentMessages로 처리하므로 제거
    const [detailResponse, statsResponse, productsResponse] = await Promise.all([
      fetchAdminBroadcastDetail(idValue),
      fetchBroadcastStats(idValue).catch(() => null),
      fetchBroadcastProducts(idValue).catch(() => []),
    ])

    const viewers = statsResponse?.viewerCount ?? detailResponse.totalViews ?? 0
    const likes = statsResponse?.likeCount ?? detailResponse.totalLikes ?? 0
    const reports = statsResponse?.reportCount ?? detailResponse.totalReports ?? 0
    const startedAt = detailResponse.startedAt ?? detailResponse.scheduledAt ?? ''
    const scheduledAt = detailResponse.scheduledAt ?? ''

    detail.value = {
      id: String(detailResponse.broadcastId),
      title: detailResponse.title,
      sellerName: detailResponse.sellerName ?? '',
      startedAt,
      scheduledAt,
      status: detailResponse.status ?? '',
      waitScreenUrl: detailResponse.waitScreenUrl ?? '',
      stoppedReason: detailResponse.stoppedReason ?? '',
      viewers,
      reports,
      likes,
      elapsed: formatElapsed(startedAt),
    }

    liveProducts.value = productsResponse.map((item) => mapLiveProduct(item))
  } catch {
    detail.value = null
    liveProducts.value = []
  }
}

const refreshStats = async (broadcastId: number) => {
  if (!detail.value) return
  try {
    const stats = await fetchBroadcastStats(broadcastId)
    detail.value = {
      ...detail.value,
      viewers: stats.viewerCount ?? detail.value.viewers,
      likes: stats.likeCount ?? detail.value.likes,
      reports: stats.reportCount ?? detail.value.reports,
    }
  } catch {
    return
  }
}

const refreshProducts = async (broadcastId: number) => {
  try {
    const products = await fetchBroadcastProducts(broadcastId)
    liveProducts.value = products.map((item) => mapLiveProduct(item))
  } catch {
    return
  }
}

const refreshDetail = async (broadcastId: number) => {
  try {
    const detailResponse = await fetchAdminBroadcastDetail(broadcastId)
    if (!detail.value) return
    const startedAt = detailResponse.startedAt ?? detailResponse.scheduledAt ?? ''
    const scheduledAt = detailResponse.scheduledAt ?? ''
    detail.value = {
      ...detail.value,
      title: detailResponse.title,
      sellerName: detailResponse.sellerName ?? detail.value.sellerName,
      startedAt,
      scheduledAt,
      status: detailResponse.status ?? detail.value.status,
      waitScreenUrl: detailResponse.waitScreenUrl ?? detail.value.waitScreenUrl,
      stoppedReason: detailResponse.stoppedReason ?? detail.value.stoppedReason,
      elapsed: formatElapsed(startedAt),
    }
  } catch {
    return
  }
}

// --- Lifecycle Status ---
const lifecycleStatus = computed(() => {
  if (!detail.value) return 'RESERVED'
  const baseTime = detail.value.startedAt || detail.value.scheduledAt
  const startAtMs = baseTime ? parseLiveDate(baseTime).getTime() : NaN
  const endAtMs = Number.isNaN(startAtMs) ? undefined : getScheduledEndMs(startAtMs)
  return computeLifecycleStatus({
    status: normalizeBroadcastStatus(detail.value.status),
    startAtMs: Number.isNaN(startAtMs) ? undefined : startAtMs,
    endAtMs,
  })
})

const statusLabel = computed(() => getBroadcastStatusLabel(lifecycleStatus.value))
const isInteractive = computed(() => lifecycleStatus.value === 'ON_AIR')
const canForceStop = computed(() => ['READY', 'ON_AIR', 'ENDED'].includes(lifecycleStatus.value))
const isReadOnly = computed(() => lifecycleStatus.value !== 'ON_AIR')
const waitingScreenUrl = computed(() => detail.value?.waitScreenUrl ?? '')

const readyCountdownLabel = computed(() => {
  if (lifecycleStatus.value !== 'READY') return ''
  const baseTime = detail.value?.scheduledAt ?? detail.value?.startedAt
  if (!baseTime) return '방송 시작 대기 중'
  const startAtMs = parseLiveDate(baseTime).getTime()
  if (Number.isNaN(startAtMs)) return '방송 시작 대기 중'
  const diffMs = startAtMs - now.value.getTime()
  if (diffMs <= 0) return '방송 시작 대기 중'
  const totalSeconds = Math.ceil(diffMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}분 ${String(seconds).padStart(2, '0')}초 뒤 방송 시작`
})

const endedCountdownLabel = computed(() => {
  if (lifecycleStatus.value !== 'ENDED') return ''
  const baseTime = detail.value?.scheduledAt ?? detail.value?.startedAt
  if (!baseTime) return '방송 종료'
  const startAtMs = parseLiveDate(baseTime).getTime()
  if (Number.isNaN(startAtMs)) return '방송 종료'
  const scheduledEndMs = getScheduledEndMs(startAtMs)
  if (!scheduledEndMs) return '방송 종료'
  const diffMs = scheduledEndMs - now.value.getTime()
  if (diffMs <= 0) return '방송 종료'
  const totalSeconds = Math.ceil(diffMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `종료까지 ${minutes}분 ${String(seconds).padStart(2, '0')}초`
})

const elapsedLabel = computed(() => {
  if (!detail.value?.startedAt) return '00:00:00'
  now.value
  return formatElapsed(detail.value.startedAt)
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

// --- OpenVidu Logic ---
const hasSubscriberStream = computed(() => openviduConnected.value && !!openviduSubscriber.value)

const getPlayerFrame = () => stageRef.value?.querySelector<HTMLElement>('.player-frame') ?? null

const applySubscriberVolume = () => {
  const container = stageRef.value
  if (!container) return
  const video = container.querySelector('video') as HTMLVideoElement | null
  if (!video) return
  video.muted = false
  video.volume = Math.min(1, Math.max(0, volume.value / 100))
}

const applyVideoQuality = (value: typeof selectedQuality.value) => {
  const frame = getPlayerFrame()
  if (!frame) return
  frame.dataset.quality = value
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
  clearViewerContainer()
}

const disconnectOpenVidu = () => {
  if (openviduSession.value) {
    try {
      if (openviduSubscriber.value) {
        openviduSession.value.unsubscribe(openviduSubscriber.value)
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
        openviduSession.value.unsubscribe(openviduSubscriber.value)
        openviduSubscriber.value = null
        clearViewerContainer()
      }
      openviduSubscriber.value = openviduSession.value.subscribe(event.stream, viewerContainerRef.value, {
        insertMode: 'append',
      })
      applySubscriberVolume()
      applyVideoQuality(selectedQuality.value)
    })
    openviduSession.value.on('streamDestroyed', (event: StreamEvent) => {
      event.preventDefault()
      openviduSubscriber.value = null
      clearViewerContainer()
    })
    await openviduSession.value.connect(token)
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

const requestJoinToken = async () => {
  if (!detail.value) return
  if (lifecycleStatus.value !== 'ON_AIR') return
  if (joinInFlight.value) return
  if (joinedBroadcastId.value === Number(detail.value.id)) return
  if (!viewerId.value) {
    viewerId.value = resolveViewerId(getAuthUser())
  }
  if (!viewerId.value) return
  joinInFlight.value = true
  try {
    streamToken.value = await joinBroadcast(Number(detail.value.id), viewerId.value)
    joinedBroadcastId.value = Number(detail.value.id)
    joinedViewerId.value = viewerId.value
  } catch {
    return
  } finally {
    joinInFlight.value = false
  }
}

const sendLeaveSignal = async (useBeacon = false) => {
  const leavingViewerId = joinedViewerId.value ?? viewerId.value
  if (!joinedBroadcastId.value || !leavingViewerId || leaveRequested.value) return
  leaveRequested.value = true
  const url = `${apiBase}/broadcasts/${joinedBroadcastId.value}/leave?viewerId=${encodeURIComponent(leavingViewerId)}`
  if (useBeacon && navigator.sendBeacon) {
    navigator.sendBeacon(url)
    return
  }
  await leaveBroadcast(joinedBroadcastId.value, leavingViewerId).catch(() => {})
}

// --- SSE Logic ---

const handlePageHide = () => {
  void sendLeaveSignal(true)
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
  liveProducts.value = liveProducts.value.map((product) => ({
    ...product,
    isPinned: productId ? product.id === productId : false,
  }))
}

const markProductSoldOut = (productId: string | null) => {
  if (!productId) return
  liveProducts.value = liveProducts.value.map((product) =>
    product.id === productId
      ? {
        ...product,
        status: '품절',
      }
      : product,
  )
}

const buildStopConfirmMessage = () => {
  return '방송 운영 정책 위반으로 방송이 중지되었습니다.\n방송에서 나가겠습니까?'
}

const handleStopDecision = (message: string) => {
  const ok = window.confirm(message)
  if (ok) {
    goToList()
    return
  }
  isStopRestricted.value = true
  showChat.value = false
  activePane.value = 'monitor'
}

const promptStoppedEntry = () => {
  if (stopEntryPrompted.value) return
  stopEntryPrompted.value = true
  handleStopDecision('해당 방송은 운영정책 위반으로 송출 중지되었습니다. 방송을 나가겠습니까?')
}

const scheduleRefresh = (broadcastId: number) => {
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = window.setTimeout(() => {
    void refreshDetail(broadcastId)
    void refreshStats(broadcastId)
    void refreshProducts(broadcastId)
  }, 500)
}

const handleSseEvent = (event: MessageEvent) => {
  const idValue = Number(liveId.value)
  if (Number.isNaN(idValue)) return
  const data = parseSseData(event)
  switch (event.type) {
    case 'BROADCAST_READY':
    case 'BROADCAST_UPDATED':
    case 'BROADCAST_STARTED':
      scheduleRefresh(idValue)
      break
    case 'PRODUCT_PINNED':
      applyPinnedProduct(resolveProductId(data))
      scheduleRefresh(idValue)
      break
    case 'PRODUCT_UNPINNED':
      applyPinnedProduct(null)
      scheduleRefresh(idValue)
      break
    case 'PRODUCT_SOLD_OUT':
      markProductSoldOut(resolveProductId(data))
      scheduleRefresh(idValue)
      break
    case 'SANCTION_UPDATED':
      scheduleRefresh(idValue)
      break
    case 'BROADCAST_CANCELED':
      alert('방송이 자동 취소되었습니다.')
      goToList()
      break
    case 'BROADCAST_ENDED':
      alert('방송이 종료되었습니다.')
      void refreshDetail(idValue)
      break
    case 'BROADCAST_SCHEDULED_END':
      alert('방송이 종료되었습니다.')
      goToList()
      break
    case 'BROADCAST_STOPPED':
      if (detail.value) {
        detail.value.status = 'STOPPED'
      }
      scheduleRefresh(idValue)
      stopEntryPrompted.value = true
      handleStopDecision(buildStopConfirmMessage())
      break
    default:
      break
  }
}

const scheduleReconnect = (broadcastId: number) => {
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  const delay = Math.min(30000, 1000 * 2 ** sseRetryCount.value)
  const jitter = Math.floor(Math.random() * 500)
  sseRetryTimer.value = window.setTimeout(() => {
    connectSse(broadcastId)
  }, delay + jitter)
  sseRetryCount.value += 1
}

const connectSse = (broadcastId: number) => {
  if (sseSource.value) {
    sseSource.value.close()
  }
  const source = new EventSource(`${apiBase}/broadcasts/${broadcastId}/subscribe`)
  const events = [
    'BROADCAST_READY',
    'BROADCAST_UPDATED',
    'BROADCAST_STARTED',
    'PRODUCT_PINNED',
    'PRODUCT_UNPINNED',
    'PRODUCT_SOLD_OUT',
    'SANCTION_UPDATED',
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
    scheduleRefresh(broadcastId)
  }
  source.onerror = () => {
    sseConnected.value = false
    source.close()
    if (document.visibilityState === 'visible') {
      scheduleReconnect(broadcastId)
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
  scheduleRefresh(broadcastId.value)
}

const startStatsPolling = (broadcastId: number) => {
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = window.setInterval(() => {
    if (document.visibilityState !== 'visible') {
      return
    }
    if (!['READY', 'ON_AIR', 'ENDED', 'STOPPED'].includes(lifecycleStatus.value)) {
      return
    }
    void refreshStats(broadcastId)
    void refreshProducts(broadcastId)
  }, 5000)
}

// --- Interaction Logic ---

const openStopConfirm = () => {
  if (!detail.value || detail.value.status === 'STOPPED' || !canForceStop.value) return
  showStopModal.value = true
  error.value = ''
}

const closeStopModal = () => {
  showStopModal.value = false
  stopReason.value = ''
  stopDetail.value = ''
  error.value = ''
}

const handleStopSave = () => {
  if (!detail.value) return
  if (!stopReason.value) {
    error.value = '유형을 선택해주세요.'
    return
  }
  if (stopReason.value === '기타' && !stopDetail.value.trim()) {
    error.value = '중지 사유를 입력해주세요.'
    return
  }
  const ok = window.confirm('방송 송출을 중지하시겠습니까?')
  if (!ok) return
  const reason = stopReason.value === '기타' ? stopDetail.value.trim() : stopReason.value
  stopAdminBroadcast(Number(detail.value.id), reason)
      .then(() => {
        if (detail.value) {
          detail.value.status = 'STOPPED'
          detail.value.stoppedReason = reason
        }
        goToList()
      })
      .catch(() => {})
      .finally(() => {
        showStopModal.value = false
      })
}

const syncFullscreen = () => {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

const modalHostTarget = computed(() => (isFullscreen.value && stageRef.value ? stageRef.value : 'body'))

const toggleFullscreen = async () => {
  const el = stageRef.value
  if (!el) return
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
      return
    }
    if (el.requestFullscreen) {
      await el.requestFullscreen()
    }
  } catch {
    return
  }
}

const toggleSettings = () => {
  isSettingsOpen.value = !isSettingsOpen.value
}

const handleDocumentClick = (event: MouseEvent) => {
  if (!isSettingsOpen.value) return
  const target = event.target as Node | null
  if (settingsButtonRef.value?.contains(target) || settingsPanelRef.value?.contains(target)) {
    return
  }
  isSettingsOpen.value = false
}

const handleDocumentKeydown = (event: KeyboardEvent) => {
  if (!isSettingsOpen.value) return
  if (event.key === 'Escape') {
    isSettingsOpen.value = false
  }
}

const toggleChat = () => {
  if (isStopRestricted.value) return
  showChat.value = !showChat.value
}

const closeChat = () => {
  showChat.value = false
}

// Moderation
const openModeration = (msg: { user: string; kind?: string; memberLoginId?: string; connectionId?: string }) => {
  if (!isInteractive.value) return
  if (msg.user === 'SYSTEM' || msg.kind === 'system' || msg.user === '관리자') return
  moderationTarget.value = { user: msg.user, memberLoginId: msg.memberLoginId, connectionId: msg.connectionId }
  moderationType.value = ''
  moderationReason.value = ''
  showModerationModal.value = true
}

const closeModeration = () => {
  showModerationModal.value = false
  moderationTarget.value = null
  moderationType.value = ''
  moderationReason.value = ''
}

const saveModeration = async () => {
  if (!moderationType.value) {
    window.alert('제재 유형을 선택해주세요.')
    return
  }
  const confirmModeration = window.confirm('입력한 내용으로 시청자를 제재하시겠습니까?')
  if (!confirmModeration) return
  const target = moderationTarget.value
  if (!target) return
  if (!broadcastId.value) return
  if (!target.memberLoginId) {
    window.alert('로그인된 시청자만 제재할 수 있습니다.')
    return
  }
  const sanctionType = moderationType.value === '채팅 금지' ? 'MUTE' : 'OUT'
  try {
    await sanctionAdminViewer(broadcastId.value, {
      memberLoginId: target.memberLoginId,
      status: sanctionType,
      reason: moderationReason.value.trim(),
      connectionId: target.connectionId,
    })
  } catch (error) {
    const message = (error as { message?: string } | null)?.message ?? '제재 처리에 실패했습니다.'
    window.alert(message)
    return
  }
  const now = new Date()
  const at = `${now.getHours()}시 ${String(now.getMinutes()).padStart(2, '0')}분`
  moderatedUsers.value = {
    ...moderatedUsers.value,
    [target.user]: { type: moderationType.value, reason: moderationReason.value.trim(), at },
  }

  // 로컬 시스템 메시지 추가
  chatMessages.value.push({
    id: `sys-${Date.now()}`,
    user: 'SYSTEM',
    text: `관리자가 ${target.user}님을 '${moderationType.value}' 처리했습니다. (사유: ${moderationReason.value.trim()})`,
    time: at,
    kind: 'system'
  })

  closeModeration()
  nextTick(() => {
    scrollChatToBottom()
  })
}

// --- Hooks ---

onMounted(() => {
  refreshAuth()
  viewerId.value = resolveViewerId(getAuthUser())
  window.addEventListener('pagehide', handlePageHide)
  window.addEventListener('visibilitychange', handleSseVisibilityChange)
  window.addEventListener('focus', handleSseVisibilityChange)
  document.addEventListener('fullscreenchange', syncFullscreen)
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen)
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleDocumentKeydown)
  window.removeEventListener('pagehide', handlePageHide)
  window.removeEventListener('visibilitychange', handleSseVisibilityChange)
  window.removeEventListener('focus', handleSseVisibilityChange)
  void sendLeaveSignal()
  sseSource.value?.close()
  sseSource.value = null
  sseConnected.value = false
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  sseRetryTimer.value = null
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = null
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = null
  disconnectOpenVidu()
  disconnectChat()
  qualityObserver.value?.disconnect()
  qualityObserver.value = null
})

onMounted(() => {
  viewerId.value = resolveViewerId(getAuthUser())
  window.addEventListener('pagehide', handlePageHide)
})

onMounted(() => {
  if (!stageRef.value) return
  qualityObserver.value?.disconnect()
  qualityObserver.value = new MutationObserver(() => {
    applyVideoQuality(selectedQuality.value)
    applySubscriberVolume()
  })
  qualityObserver.value.observe(stageRef.value, { childList: true, subtree: true })
})

watch(
    liveId,
    (value) => {
      if (joinedBroadcastId.value) {
        void sendLeaveSignal()
      }
      leaveRequested.value = false
      joinedBroadcastId.value = null
      joinedViewerId.value = null
      streamToken.value = null
      disconnectOpenVidu()

      // Chat disconnect
      chatMessages.value = []
      disconnectChat()

      loadDetail()
      const idValue = Number(value)
      if (!Number.isNaN(idValue)) {
        connectSse(idValue)
        startStatsPolling(idValue)
        void requestJoinToken()

        // Chat connect & Fetch recent
        fetchRecentMessages()
        connectChat()
      } else {
        sseSource.value?.close()
        sseSource.value = null
        sseConnected.value = false
        if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
        sseRetryTimer.value = null
        if (statsTimer.value) window.clearInterval(statsTimer.value)
        statsTimer.value = null
        if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
        refreshTimer.value = null
      }
    },
    { immediate: true },
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
    applyVideoQuality(value)
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
</script>

<template>
  <div v-if="detail" class="live-detail">
    <header class="detail-header">
      <button type="button" class="back-link" @click="goBack">← 뒤로 가기</button>
      <div class="header-actions">
        <button type="button" class="btn" @click="goToList">목록으로</button>
        <button
            type="button"
            class="btn danger"
            :disabled="detail.status === 'STOPPED' || !canForceStop"
            @click="openStopConfirm"
        >
          {{ detail.status === 'STOPPED' ? '송출 중지됨' : '방송 송출 중지' }}
        </button>
      </div>
    </header>

    <h2 class="page-title">방송 모니터링</h2>

    <section class="detail-card ds-surface meta-card">
      <div class="detail-meta">
        <h3>{{ detail.title }}</h3>
        <p><span>판매자</span>{{ detail.sellerName }}</p>
        <p><span>방송 시작</span>{{ detail.startedAt }}</p>
        <p v-if="detail.scheduledAt"><span>예약 시간</span>{{ detail.scheduledAt }}</p>
        <p><span>시청자 수</span>{{ detail.viewers }}명</p>
        <p><span>신고 건수</span>{{ detail.reports ?? 0 }}건</p>
        <p><span>상태</span>{{ statusLabel }}</p>
        <p v-if="lifecycleStatus === 'READY'"><span>카운트다운</span>{{ readyCountdownLabel }}</p>
        <p v-if="lifecycleStatus === 'ENDED'"><span>종료까지</span>{{ endedCountdownLabel }}</p>
      </div>
    </section>

    <section class="player-card">
      <div class="player-tabs">
        <div class="tab-list" role="tablist" aria-label="모니터링 패널">
          <button
              type="button"
              class="tab"
              :class="{ 'tab--active': activePane === 'monitor' }"
              role="tab"
              aria-controls="monitor-pane"
              :aria-selected="activePane === 'monitor'"
              @click="activePane = 'monitor'"
          >
            모니터링
          </button>
          <button
              v-if="!isStopRestricted"
              type="button"
              class="tab"
              :class="{ 'tab--active': activePane === 'products' }"
              role="tab"
              aria-controls="products-pane"
              :aria-selected="activePane === 'products'"
              @click="activePane = 'products'"
          >
            상품
          </button>
        </div>

        <div v-show="activePane === 'monitor'" id="monitor-pane">
          <div ref="stageRef" class="monitor-stage" :class="{ 'monitor-stage--chat': showChat && !isStopRestricted }">
            <div class="player-wrap">
              <div class="player-frame" :class="{ 'player-frame--fullscreen': isFullscreen }">
                <div v-show="hasSubscriberStream" ref="viewerContainerRef" class="player-frame__viewer"></div>
                <div class="player-overlay">
                  <div class="overlay-item">⏱ {{ elapsedLabel }}</div>
                  <div class="overlay-item">👥 {{ detail.viewers }}명</div>
                  <div class="overlay-item">❤ {{ detail.likes }}</div>
                  <div class="overlay-item">🚩 {{ detail.reports ?? 0 }}건</div>
                </div>
                <div v-if="!isStopRestricted" class="overlay-actions">
                  <button type="button" class="icon-circle" :class="{ active: showChat }" @click="toggleChat" :title="showChat ? '채팅 닫기' : '채팅 보기'">
                    <svg aria-hidden="true" class="icon" viewBox="0 0 24 24" focusable="false">
                      <path d="M3 20l1.62-3.24A2 2 0 0 1 6.42 16H20a1 1 0 0 0 1-1V5a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v15z" stroke="currentColor" stroke-width="1.7" />
                      <path d="M7 9h10M7 12h6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                    </svg>
                  </button>
                  <div class="player-settings">
                    <button
                      ref="settingsButtonRef"
                      type="button"
                      class="icon-circle"
                      :class="{ active: isSettingsOpen }"
                      aria-controls="admin-player-settings"
                      :aria-expanded="isSettingsOpen ? 'true' : 'false'"
                      aria-label="설정"
                      @click="toggleSettings"
                    >
                      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-linecap="round" stroke-width="1.7" />
                        <circle cx="9" cy="6" r="2" stroke="currentColor" stroke-width="1.7" />
                        <circle cx="14" cy="12" r="2" stroke="currentColor" stroke-width="1.7" />
                        <circle cx="7" cy="18" r="2" stroke="currentColor" stroke-width="1.7" />
                      </svg>
                    </button>
                    <div
                      v-if="isSettingsOpen"
                      id="admin-player-settings"
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
                  <button type="button" class="icon-circle ghost" :class="{ active: isFullscreen }" @click="toggleFullscreen" :title="isFullscreen ? '전체화면 종료' : '전체화면'">
                    <svg v-if="!isFullscreen" aria-hidden="true" class="icon" viewBox="0 0 24 24" focusable="false">
                      <path d="M4 9V4h5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                      <path d="M20 9V4h-5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                      <path d="M4 15v5h5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                      <path d="M20 15v5h-5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                    <svg v-else aria-hidden="true" class="icon" viewBox="0 0 24 24" focusable="false">
                      <path d="M9 5H5v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                      <path d="M15 19h4v-4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                      <path d="M9 19H5v-4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                      <path d="M15 5h4v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </button>
                </div>
                <div v-if="isReadOnly" class="player-placeholder">
                  <img
                      v-if="waitingScreenUrl && lifecycleStatus !== 'STOPPED'"
                      class="player-placeholder__image"
                      :src="waitingScreenUrl"
                      alt="대기 화면"
                      @error="handleImageError"
                  />
                  <p
                    v-if="playerMessage && (!waitingScreenUrl || lifecycleStatus === 'STOPPED')"
                    class="player-placeholder__message"
                  >
                    {{ playerMessage }}
                  </p>
                </div>
                <div v-else-if="!hasSubscriberStream" class="player-label">송출 화면</div>
              </div>
            </div>

            <aside v-if="showChat && !isStopRestricted" class="chat-panel ds-surface">
              <header class="chat-head">
                <h4>실시간 채팅 ({{ isChatConnected ? '연결됨' : '연결 중...' }})</h4>
                <button type="button" class="chat-close" @click="closeChat">×</button>
              </header>
              <div ref="chatListRef" class="chat-messages" @scroll="handleChatScroll">
                <div
                    v-for="msg in chatMessages"
                    :key="msg.id"
                    class="chat-message"
                    :class="{ 'chat-message--system': msg.kind === 'system', 'chat-message--muted': moderatedUsers[msg.user] }"
                    @contextmenu.prevent="openModeration(msg)"
                >
                  <div class="chat-meta">
                    <span class="chat-user">{{ formatChatUser(msg) }}</span>
                    <span class="chat-time">{{ msg.time }}</span>
                    <span v-if="msg.kind !== 'system' && moderatedUsers[msg.user]" class="chat-badge">{{ moderatedUsers[msg.user]?.type }}</span>
                  </div>
                  <p class="chat-text">{{ msg.text }}</p>
                </div>
              </div>
              <button
                v-if="showScrollToBottom"
                type="button"
                class="chat-scroll-button"
                aria-label="아래로 이동"
                @click="scrollChatToBottom"
              >
                <svg class="chat-scroll-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 5v12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                  <path d="M7 12l5 5 5-5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
              <div class="chat-input">
                <input v-model="chatText" type="text" placeholder="메시지를 입력하세요" :disabled="isReadOnly || !isChatConnected" @keydown.enter="sendChat" />
                <button type="button" class="btn primary" :disabled="isReadOnly || !isChatConnected" @click="sendChat">전송</button>
              </div>
              <p v-if="isReadOnly" class="chat-helper">방송 중에만 채팅을 이용할 수 있습니다.</p>
            </aside>
          </div>
        </div>

        <div v-if="!isStopRestricted" v-show="activePane === 'products'" id="products-pane" class="products-pane ds-surface" :class="{ 'products-pane--readonly': isReadOnly }">
          <header class="products-head">
            <div>
              <h4>상품 정보</h4>
              <p class="ds-section-sub">방송에 연결된 상품 현황을 확인하세요.</p>
            </div>
            <span class="pill">총 {{ liveProducts.length }}개</span>
          </header>
          <div class="product-list">
            <article
                v-for="product in sortedLiveProducts"
                :key="product.id"
                class="product-row"
                :class="{ 'product-row--pinned': product.isPinned, 'product-row--soldout': product.status === '품절' }"
            >
              <span v-if="product.isPinned" class="product-pin">PIN</span>
              <div class="product-thumb">
                <img :src="product.thumb" :alt="product.name" loading="lazy" @error="handleImageError" />
              </div>
              <div class="product-meta">
                <p class="product-name">{{ product.name }}</p>
                <p class="product-option">{{ product.option }}</p>
                <p class="product-price">
                  <span class="product-sale">{{ product.sale }}</span>
                  <span class="product-origin">{{ product.price }}</span>
                </p>
                <p class="product-stats">판매 {{ product.sold }} · 재고 {{ product.stock }}</p>
              </div>
              <span class="product-status" :class="{ 'is-soldout': product.status === '품절' }">{{ product.status }}</span>
            </article>
          </div>
        </div>
      </div>
    </section>

    <Teleport :to="modalHostTarget">
      <div v-if="showStopModal" class="stop-modal">
        <div class="stop-modal__backdrop" @click="closeStopModal"></div>
        <div class="stop-modal__card ds-surface">
          <header class="stop-modal__head">
            <h3>방송 송출 중지</h3>
            <button type="button" class="close-btn" @click="closeStopModal">×</button>
          </header>
          <div class="stop-modal__body">
            <label class="field">
              <span class="field__label">유형</span>
              <select v-model="stopReason" class="field-input">
                <option value="">선택해주세요</option>
                <option v-for="option in reasonOptions" :key="option" :value="option">{{ option }}</option>
              </select>
            </label>
            <label v-if="stopReason === '기타'" class="field">
              <span class="field__label">중지 사유(기타 선택 시)</span>
              <textarea v-model="stopDetail" class="field-input" rows="4" placeholder="사유를 입력해주세요."></textarea>
            </label>
            <p v-if="error" class="error">{{ error }}</p>
          </div>
          <div class="stop-modal__actions">
            <button type="button" class="btn ghost" @click="closeStopModal">취소</button>
            <button type="button" class="btn primary" @click="handleStopSave">저장</button>
          </div>
        </div>
      </div>

      <div v-if="showModerationModal" class="moderation-modal">
        <div class="moderation-modal__backdrop" @click="closeModeration"></div>
        <div class="moderation-modal__card ds-surface">
          <header class="moderation-modal__head">
            <h3>채팅 관리</h3>
            <button type="button" class="close-btn" @click="closeModeration">×</button>
          </header>
          <div class="moderation-modal__body">
            <p class="moderation-target">대상: {{ moderationTarget?.user }}</p>
            <label class="field">
              <span class="field__label">제재 유형</span>
              <select v-model="moderationType" class="field-input">
                <option value="">선택해주세요</option>
                <option value="채팅 금지">채팅 금지</option>
                <option value="강제 퇴장">강제 퇴장</option>
              </select>
            </label>
            <label class="field">
              <span class="field__label">제재 사유</span>
              <textarea v-model="moderationReason" class="field-input" rows="4" placeholder="사유를 입력해주세요."></textarea>
            </label>
          </div>
          <div class="moderation-modal__actions">
            <button type="button" class="btn ghost" @click="closeModeration">취소</button>
            <button type="button" class="btn primary" @click="saveModeration">저장</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.live-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.back-link {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-weight: 800;
  cursor: pointer;
  padding: 6px 0;
}

.page-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 900;
  color: var(--text-strong);
}

.detail-card {
  padding: 18px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.meta-card {
  padding: 14px 18px;
}

.detail-meta h3 {
  margin: 0 0 8px;
  font-size: 1.2rem;
  font-weight: 900;
  color: var(--text-strong);
}

.detail-meta p {
  margin: 4px 0;
  color: var(--text-muted);
  font-weight: 700;
}

.detail-meta span {
  display: inline-block;
  min-width: 120px;
  color: var(--text-strong);
  font-weight: 800;
  margin-right: 6px;
}

.player-card {
  width: 100%;
}

.monitor-stage {
  display: flex;
  gap: 16px;
  align-items: center;
  position: relative;
  width: 100%;
  margin: 0 auto;
}

.player-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.player-frame {
  position: relative;
  width: min(100%, calc((100vh - 120px) * (16 / 9)));
  height: auto;
  max-height: calc(100vh - 120px);
  aspect-ratio: 16 / 9;
  background: #0b0f1a;
  border-radius: 18px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
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

.player-frame--fullscreen {
  max-height: none;
  max-width: none;
  height: min(100vh, calc(100vw * (9 / 16)));
  width: min(100vw, calc(100vh * (16 / 9)));
  border-radius: 0;
  background: #000;
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

.player-frame[data-quality='720p'] :deep(video),
.player-frame[data-quality='720p'] :deep(img) {
  filter: blur(0.3px);
}

.player-frame[data-quality='480p'] :deep(video),
.player-frame[data-quality='480p'] :deep(img) {
  filter: blur(0.6px);
  image-rendering: pixelated;
}

.player-label {
  color: rgba(255, 255, 255, 0.6);
  font-weight: 800;
  letter-spacing: 0.08em;
}

.player-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  text-align: center;
  background: rgba(6, 10, 18, 0.92);
  z-index: 1;
}

.player-placeholder__image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.player-placeholder__message {
  color: #ffffff;
  font-weight: 900;
  text-shadow: 0 3px 12px rgba(0, 0, 0, 0.45);
  max-width: min(520px, 100%);
  font-size: 1.35rem;
}

.player-overlay {
  position: absolute;
  top: 14px;
  right: 14px;
  display: grid;
  gap: 8px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  padding: 10px 12px;
  border-radius: 12px;
  font-weight: 800;
  font-size: 0.9rem;
  z-index: 2;
}

.overlay-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.chat-toggle {
  position: absolute;
  right: 14px;
  bottom: 14px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  border-radius: 999px;
  padding: 8px 12px;
  font-weight: 800;
  cursor: pointer;
}

.overlay-actions {
  position: absolute;
  right: 14px;
  bottom: 14px;
  display: inline-flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
  z-index: 2;
}

.player-settings {
  position: relative;
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
}

.icon-circle.ghost {
  background: rgba(255, 255, 255, 0.16);
  color: #0f172a;
  border-color: rgba(255, 255, 255, 0.4);
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
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  padding: 12px;
  gap: 10px;
  max-height: min(70vh, 720px);
  overflow: hidden;
  min-height: 0;
  position: relative;
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

.chat-message--muted .chat-text {
  color: var(--text-muted);
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

.chat-badge {
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--surface-weak);
  color: var(--text-muted);
  font-weight: 800;
  font-size: 0.75rem;
}

.chat-input {
  display: flex;
  gap: 8px;
}

.monitor-stage--chat .player-wrap {
  margin-right: 372px;
}

.monitor-stage:fullscreen {
  height: 100vh;
  max-height: 100vh;
  align-items: center;
  justify-content: center;
}

.monitor-stage:fullscreen .player-wrap {
  height: 100vh;
  max-height: 100vh;
  display: flex;
  justify-content: center;
}

.monitor-stage:fullscreen .player-frame {
  max-height: 100vh;
  max-width: none;
  height: min(100vh, calc(100vw * (9 / 16)));
  width: min(100vw, calc(100vh * (16 / 9)));
  border-radius: 0;
  background: #000;
}

.monitor-stage:fullscreen.monitor-stage--chat .player-frame {
  width: min(max(320px, calc(100vw - 380px)), calc(100vh * (16 / 9)));
  height: min(100vh, max(200px, calc((100vw - 380px) * (9 / 16))));
}

.monitor-stage--chat .chat-panel {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 360px;
  height: auto;
  overflow: hidden;
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

.btn.danger {
  border-color: #ef4444;
  color: #ef4444;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.stop-modal {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
}

.stop-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
}

.stop-modal__card {
  position: relative;
  width: min(520px, 92vw);
  border-radius: 16px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  z-index: 1;
}

.stop-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.stop-modal__head h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.close-btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-muted);
  width: 32px;
  height: 32px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
}

.stop-modal__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field__label {
  font-weight: 800;
  color: var(--text-strong);
}

.field-input {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.stop-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn.ghost {
  border-color: var(--border-color);
  color: var(--text-muted);
  background: transparent;
}

.error {
  margin: 0;
  color: #ef4444;
  font-weight: 700;
}

.moderation-modal {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
}

.moderation-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
}

.moderation-modal__card {
  position: relative;
  width: min(520px, calc(100vw - 32px));
  border-radius: 16px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  z-index: 1;
}

.moderation-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.moderation-modal__head h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.moderation-modal__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.moderation-target {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.moderation-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* Monitoring tabs & products */
.player-tabs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tab-list {
  display: inline-flex;
  background: rgba(15, 23, 42, 0.08);
  padding: 4px;
  border-radius: 12px;
  gap: 6px;
  width: fit-content;
}

.tab {
  border: none;
  padding: 8px 14px;
  border-radius: 10px;
  background: transparent;
  color: var(--text-muted);
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.tab--active {
  background: var(--surface);
  color: var(--text-strong);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.06);
}

.products-pane {
  border-radius: 16px;
  padding: 16px;
  background: var(--surface);
  border: 1px solid var(--border-color);
}

.products-pane--readonly {
  opacity: 0.6;
}

.chat-helper {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 0.9rem;
  font-weight: 700;
}

.products-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.products-head h4 {
  margin: 0;
  color: var(--text-strong);
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--surface-weak);
  border-radius: 999px;
  padding: 6px 12px;
  font-weight: 800;
  color: var(--text-muted);
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-row {
  display: grid;
  grid-template-columns: 120px 1fr 100px;
  gap: 12px;
  align-items: center;
  background: var(--surface-weak);
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  position: relative;
}

.product-row--pinned {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 1px rgba(var(--primary-rgb), 0.2);
}

.product-row--soldout {
  opacity: 0.65;
}

.product-pin {
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

.product-thumb img {
  width: 120px;
  height: 80px;
  object-fit: cover;
  border-radius: 10px;
}

.product-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-name {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
}

.product-option {
  margin: 0;
  color: var(--text-muted);
}

.product-price {
  margin: 0;
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.product-sale {
  font-weight: 900;
  color: #f59e0b;
}

.product-origin {
  color: var(--text-soft);
  text-decoration: line-through;
}

.product-stats {
  margin: 0;
  color: var(--text-muted);
}

.product-status {
  justify-self: end;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
  font-weight: 800;
}

.product-status.is-soldout {
  background: rgba(248, 113, 113, 0.15);
  color: #ef4444;
}

.monitor-stage {
  --stacked-max-width: 1040px;
}

@media (max-width: 1200px) {
  .monitor-stage {
    flex-direction: column;
    align-items: center;
  }

  .monitor-stage--chat .player-wrap {
    margin-right: 0;
  }

  .player-wrap {
    width: 100%;
    max-width: var(--stacked-max-width);
  }

  .monitor-stage--chat .chat-panel {
    position: static;
    inset: auto;
    width: 100%;
    max-width: var(--stacked-max-width);
    max-height: 40vh;
    box-shadow: none;
  }

  .chat-messages {
    max-height: 28vh;
  }
}

@media (max-width: 900px) {
  .player-frame {
    min-height: 46vh;
  }

  .monitor-stage {
    align-items: stretch;
  }

  .player-wrap,
  .chat-panel {
    max-width: none;
  }

  .chat-panel {
    max-height: 48vh;
  }

  .chat-messages {
    max-height: 36vh;
  }
}
</style>
