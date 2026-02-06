<script setup lang="ts">
import { OpenVidu, type Publisher, type Session, type StreamEvent } from 'openvidu-browser'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Client, type StompSubscription } from '@stomp/stompjs'
// import SockJS from 'sockjs-client/dist/sockjs'
import BasicInfoEditModal from '../../components/BasicInfoEditModal.vue'
import ChatSanctionModal from '../../components/ChatSanctionModal.vue'
import ConfirmModal from '../../components/ConfirmModal.vue'
import DeviceSetupModal from '../../components/DeviceSetupModal.vue'
import PageContainer from '../../components/PageContainer.vue'
import QCardModal from '../../components/QCardModal.vue'
import {
  fetchBroadcastStats,
  fetchMediaConfig,
  fetchRecentLiveChats,
  fetchSellerBroadcastDetail,
  joinBroadcast,
  leaveBroadcast,
  sanctionSellerViewer,
  startSellerBroadcast,
  startSellerRecording,
  endSellerBroadcast,
  pinSellerBroadcastProduct,
  unpinSellerBroadcastProduct,
  saveMediaConfig,
  updateBroadcast,
  type MediaConfig,
  type BroadcastDetailResponse,
} from '../../lib/live/api'
import { parseLiveDate } from '../../lib/live/utils'
import { useNow } from '../../lib/live/useNow'
import { getAuthUser } from '../../lib/auth'
import { resolveViewerId } from '../../lib/live/viewer'
import { computeLifecycleStatus, getScheduledEndMs, normalizeBroadcastStatus, type BroadcastStatus } from '../../lib/broadcastStatus'
import { createImageErrorHandler, resolveProductImageUrlFromRaw } from '../../lib/images/productImages'
// import { resolveWsBase } from '../../lib/ws'
import SockJS from 'sockjs-client/dist/sockjs'
import { resolveSockJsUrl } from '../../lib/ws'

type StreamProduct = {
  id: string
  title: string
  option: string
  status: string
  price: string
  sale: string
  sold: number
  stock: number
  thumb: string
  pinned?: boolean
}

type StreamChat = {
  id: string
  name: string
  message: string
  time?: string
  senderRole?: string
  memberLoginId?: string
  connectionId?: string
}

type StreamData = {
  title: string
  datetime: string
  category: string
  notice: string
  products: StreamProduct[]
  chat: StreamChat[]
  qCards: string[]
  thumbnail?: string
  waitingScreen?: string
}

type EditableBroadcastInfo = {
  title: string
  category: string
  notice?: string
  thumbnail?: string
  waitingScreen?: string
}

const defaultNotice = ''
const { handleImageError } = createImageErrorHandler()

const route = useRoute()
const router = useRouter()

const showProducts = ref(true)
const showChat = ref(true)
const stopEntryPrompted = ref(false)
const isStopRestricted = ref(false)
const showSettings = ref(false)
const viewerCount = ref(0)
const likeCount = ref(0)
const monitorRef = ref<HTMLElement | null>(null)
const streamGridRef = ref<HTMLElement | null>(null)
const streamCenterRef = ref<HTMLElement | null>(null)
const publisherContainerRef = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const modalHostTarget = computed(() => (isFullscreen.value && monitorRef.value ? monitorRef.value : 'body'))
const micEnabled = ref(true)
const videoEnabled = ref(true)
const volume = ref(43)
const selectedMic = ref('기본 마이크')
const selectedCamera = ref('기본 카메라')
const micInputLevel = ref<number>(0)
const micStream = ref<MediaStream | null>(null)
const micAudioContext = ref<AudioContext | null>(null)
const micAnalyser = ref<AnalyserNode | null>(null)
const micMeterFrame = ref<number | null>(null)
const chatText = ref('')
const chatListRef = ref<HTMLElement | null>(null)
const CHAT_SCROLL_THRESHOLD_PX = 120
const showScrollToBottom = ref(false)
let gridObserver: ResizeObserver | null = null
const availableMics = ref<Array<{ id: string; label: string }>>([])
const availableCameras = ref<Array<{ id: string; label: string }>>([])

const showQCards = ref(false)
const showBasicInfo = ref(false)
const showSanctionModal = ref(false)
const showDeviceModal = ref(false)
const hasOpenedDeviceModal = ref(false)
const isLoadingStream = ref(true)
const qCardIndex = ref(0)
const handleFullscreenChange = () => {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

const gridWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 0)
const gridHeight = computed(() => (gridWidth.value ? (gridWidth.value * 9) / 16 : null))
const isStackedLayout = computed(() => gridWidth.value <= 960)

const confirmState = reactive({
  open: false,
  title: '',
  description: '',
  confirmText: '확인',
  cancelText: '취소',
})
const confirmAction = ref<() => void>(() => {})
const confirmCancelAction = ref<() => void>(() => {})

const pinnedProductId = ref<string | null>(null)
const sanctionTarget = ref<{ loginId: string; connectionId?: string } | null>(null)
const sanctionedUsers = ref<Record<string, { type: string; reason: string }>>({})
const broadcastInfo = ref<(EditableBroadcastInfo & { qCards: string[] }) | null>(null)
const latestDetail = ref<BroadcastDetailResponse | null>(null)
const stream = ref<StreamData | null>(null)
const chatMessages = ref<StreamChat[]>([])
const streamStatus = ref<BroadcastStatus>('RESERVED')
const { now } = useNow(1000)
const scheduleStartAtMs = ref<number | null>(null)
const scheduleEndAtMs = ref<number | null>(null)
const sseSource = ref<EventSource | null>(null)
const sseConnected = ref(false)
const sseRetryCount = ref(0)
const sseRetryTimer = ref<number | null>(null)
const statsTimer = ref<number | null>(null)
const refreshTimer = ref<number | null>(null)
const startTimer = ref<number | null>(null)
const startRetryTimer = ref<number | null>(null)
const startRetryCount = ref(0)
const MAX_START_RETRIES = 3
const START_RETRY_DELAY_MS = 1500
const PUBLISHER_READY_TIMEOUT_MS = 8000
const PUBLISHER_READY_POLL_MS = 200
const publisherToken = ref<string | null>(null)
const publisherTokenInFlight = ref(false)
const openviduInstance = shallowRef<OpenVidu | null>(null)
const openviduSession = shallowRef<Session | null>(null)
const openviduPublisher = shallowRef<Publisher | null>(null)
const openviduConnected = ref(false)
let publisherRestartTimer: number | null = null
const joinInFlight = ref(false)
const startRequested = ref(false)
const recordingStartRequested = ref(false)
const endRequested = ref(false)
const endRequestTimer = ref<number | null>(null)
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
// const wsBase = resolveWsBase(apiBase)
const sockJsUrl = resolveSockJsUrl(apiBase)
const viewerId = ref<string | null>(resolveViewerId(getAuthUser()))
const joinedBroadcastId = ref<number | null>(null)
const joinedViewerId = ref<string | null>(null)
const leaveRequested = ref(false)
const mediaConfigReady = ref(false)
const hasSavedMediaConfig = ref(false)
let mediaSaveTimer: number | null = null
const stopConfirmOpen = ref(false)
const stopConfirmMessage = ref('')

const streamId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' && id.trim() ? id : null
})

const broadcastId = computed(() => {
  if (!streamId.value) {
    return undefined
  }
  const raw = String(streamId.value)
  const numeric = Number.parseInt(raw.replace(/[^0-9]/g, ''), 10)
  return Number.isFinite(numeric) ? numeric : undefined
})

const formatElapsed = (startedAt?: string) => {
  if (!startedAt) return '00:00:00'
  const started = parseLiveDate(startedAt)
  if (Number.isNaN(started.getTime())) return '00:00:00'
  const diff = Math.max(0, Date.now() - started.getTime())
  const totalSeconds = Math.floor(diff / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

const formatScheduleWindow = (scheduledAt?: string, startedAt?: string) => {
  const baseRaw = scheduledAt ?? startedAt
  if (!baseRaw) return ''
  const base = parseLiveDate(baseRaw)
  if (Number.isNaN(base.getTime())) return ''
  const end = new Date(base.getTime() + 30 * 60 * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  const dateLabel = `${base.getFullYear()}.${pad(base.getMonth() + 1)}.${pad(base.getDate())}`
  return `${dateLabel} ${pad(base.getHours())}:${pad(base.getMinutes())} - ${pad(end.getHours())}:${pad(end.getMinutes())}`
}

const formatChatTime = (timestamp: number = Date.now()) => {
  const date = new Date(timestamp)
  const hours = date.getHours()
  const displayHour = hours % 12 || 12
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours >= 12 ? '오후' : '오전'} ${displayHour}:${minutes}`
}

const isChatConnected = ref(false)
const stompClient = ref<Client | null>(null)
let stompSubscription: StompSubscription | null = null

const isLoggedIn = ref(true)
const nickname = ref('판매자')
const memberEmail = ref('seller@deskit.com')
const ENTER_SENT_KEY_PREFIX = 'deskit_live_enter_sent_v1'

// const getAccessToken = () => localStorage.getItem('access') || sessionStorage.getItem('access')

const refreshAuth = () => {
  const user = getAuthUser()
  isLoggedIn.value = user !== null
  if (user?.name) {
    nickname.value = user.name
  }
  memberEmail.value = user?.email || memberEmail.value || 'seller@deskit.com'
}

const handleAuthUpdate = () => {
  refreshAuth()
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

const appendMessage = (payload: LiveChatMessageDTO) => {
  const isSystem = payload.type !== 'TALK'
  chatMessages.value.push({
    id: `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    name: isSystem ? 'SYSTEM' : payload.sender || 'unknown',
    message: payload.content ?? '',
    senderRole: payload.senderRole,
    memberLoginId: payload.memberEmail,
    connectionId: payload.connectionId,
    time: payload.sentAt
      ? new Date(payload.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      : formatChatTime(),
  })
  scrollChatToBottom()
}

const formatChatUser = (item: StreamChat) => {
  if (item.name === 'SYSTEM') {
    return item.name
  }
  if (item.senderRole) {
    if (item.senderRole === 'ROLE_ADMIN') {
      return `${item.name}(관리자)`
    }
    if (item.senderRole.startsWith('ROLE_SELLER')) {
      return `${item.name}(판매자)`
    }
    if (item.senderRole === 'ROLE_MEMBER' && item.name === nickname.value) {
      return `${item.name}(나)`
    }
  }
  return item.name === nickname.value ? `${item.name}(판매자)` : item.name
}

const sendSocketMessage = (type: LiveMessageType, content: string) => {
  if (!stompClient.value?.connected || !broadcastId.value) return

  const payload: LiveChatMessageDTO = {
    broadcastId: broadcastId.value,
    memberEmail: memberEmail.value,
    type,
    sender: nickname.value,
    content,
    vodPlayTime: 0,
    sentAt: Date.now(),
  }

  stompClient.value.publish({
    destination: '/pub/chat/message',
    body: JSON.stringify(payload),
  })
}

const connectChat = () => {
  if (!broadcastId.value || stompClient.value?.active) return

  // [추가] 현재 프로토콜(http/https)에 따라 ws/wss 결정 및 주소 생성
  // const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  // const host = window.location.host // 예: ssg.deskit.o-r.kr
  // const brokerURL = `wss://ssg.deskit.o-r.kr/ws`

  // const client = new Client({
  //   webSocketFactory: () =>
  //       new SockJS(`/ws`, null, {
  //         transports: ['websocket'],
  //         withCredentials: true,
  //       }),
  //   reconnectDelay: 5000,
  //   debug: () => {},
  // })

  // const client = new Client({
  //   // [수정] SockJS Factory 대신 표준 WebSocket URL 사용
  //   brokerURL: brokerURL,
  //
  //   // [설정] 순수 WebSocket 사용 시 헤더 설정 (일부 브라우저 제한 있을 수 있음)
  //   connectHeaders: {
  //     access: getAccessToken() || '',
  //     Authorization: `Bearer ${getAccessToken() || ''}`,
  //   },
  //
  //   reconnectDelay: 5000,
  //   debug: (msg) => console.log('[stomp]', msg),
  // })


  // const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  // const brokerURL = `${protocol}//${window.location.host}/ws`

  const client = new Client({
    webSocketFactory: () =>
      new SockJS(sockJsUrl, null, {
        transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
        withCredentials: true,
      }),
    reconnectDelay: 5000,
    debug: (msg) => console.log('[stomp]', msg),
    // connectHeaders는 일단 빼도 됨 (쿠키로 갈 거라 handshake엔 필요 없음)
  })

  // console.log('[ws] broadcastId=', broadcastId.value)
  // console.log('[ws] brokerURL=', brokerURL)

  // const access = getAccessToken()
  // if (access) {
  //   client.connectHeaders = {
  //     access,
  //     Authorization: `Bearer ${access}`,
  //   }
  // }

  client.onConnect = () => {
    isChatConnected.value = true
    stompSubscription?.unsubscribe()
    stompSubscription = client.subscribe(`/sub/chat/${broadcastId.value}`, (frame) => {
      try {
        const payload = JSON.parse(frame.body) as LiveChatMessageDTO
        appendMessage(payload)
      } catch (error) {
        console.error('메시지 수신 에러:', error)
      }
    })
    if (shouldSendEnterMessage()) {
      sendSocketMessage('ENTER', `${nickname.value}님이 입장했습니다.`)
      markEnterMessageSent()
    }
  }

  client.onStompError = (frame) => {
    console.error('[livechat] stomp error', frame.headers, frame.body)
  }

  client.onWebSocketClose = (evt) => {
    isChatConnected.value = false
    console.warn('[ws close]', evt?.code, evt?.reason)
  }
  client.onWebSocketError = (evt) => {
    console.error('[ws error]', evt)
  }

  client.onDisconnect = () => {
    isChatConnected.value = false
  }
  stompClient.value = client
  client.activate()
}

const disconnectChat = () => {
  if (stompClient.value?.connected) {
    sendSocketMessage('EXIT', `${nickname.value}님이 퇴장했습니다.`)
  }
  stompSubscription?.unsubscribe()
  stompSubscription = null
  stompClient.value?.deactivate()
  stompClient.value = null
  isChatConnected.value = false
}

const formatPrice = (value?: number) => {
  if (!value || Number.isNaN(value)) return '₩0'
  return `₩${value.toLocaleString('ko-KR')}`
}

const mapStreamProduct = (product: NonNullable<BroadcastDetailResponse['products']>[number]) => {
  const totalQty = product.bpQuantity ?? product.stockQty ?? 0
  const stockQty = product.stockQty ?? totalQty
  const sold = Math.max(0, totalQty - stockQty)
  return {
    id: String(product.bpId ?? product.productId),
    title: product.name,
    option: product.name,
    status: product.status === 'SOLDOUT' ? '품절' : '판매중',
    price: formatPrice(product.originalPrice),
    sale: formatPrice(product.bpPrice),
    sold,
    stock: stockQty,
    thumb: resolveProductImageUrlFromRaw(product),
    pinned: product.pinned,
  }
}

const productItems = computed(() => stream.value?.products ?? [])
const sortedProducts = computed(() => {
  const items = [...productItems.value]
  items.sort((a, b) => {
    const aSoldOut = a.status === '품절'
    const bSoldOut = b.status === '품절'
    if (aSoldOut !== bSoldOut) return aSoldOut ? 1 : -1
    if (pinnedProductId.value) {
      if (a.id === pinnedProductId.value) return -1
      if (b.id === pinnedProductId.value) return 1
    }
    return 0
  })
  return items
})

const chatItems = computed(() => chatMessages.value)

const elapsedLabel = computed(() => {
  if (!latestDetail.value?.startedAt) return '00:00:00'
  now.value
  return formatElapsed(latestDetail.value.startedAt)
})

const hasSidePanels = computed(() => !isStopRestricted.value && (showProducts.value || showChat.value))
const gridStyles = computed(() => ({
  '--grid-template-columns': monitorColumns.value,
  '--stream-pane-height': streamPaneHeight.value,
  '--center-height': gridHeight.value ? `${gridHeight.value}px` : undefined,
}))
const stackedOrders = computed(() =>
  isStackedLayout.value ? { stream: 0, chat: 1, products: 2 } : null,
)

const monitorColumns = computed(() => {
  if (isStopRestricted.value) return 'minmax(0, 1fr)'
  if (showProducts.value && showChat.value) return '320px minmax(0, 1fr) 320px'
  if (showProducts.value) return '320px minmax(0, 1fr)'
  if (showChat.value) return 'minmax(0, 1fr) 320px'
  return 'minmax(0, 1fr)'
})
const hasPublisherStream = computed(() => openviduConnected.value && !!openviduPublisher.value)

const streamPaneHeight = computed(() => {
  const dynamic = gridHeight.value
  if (dynamic) {
    const min = 320
    const max = 675
    return `${Math.min(Math.max(dynamic, min), max)}px`
  }
  if (isStopRestricted.value) return 'clamp(620px, 72vh, 820px)'
  if (showProducts.value && showChat.value) return 'clamp(460px, 62vh, 680px)'
  if (showProducts.value || showChat.value) return 'clamp(520px, 68vh, 760px)'
  return 'clamp(560px, 74vh, 880px)'
})

const qCards = computed(() => broadcastInfo.value?.qCards ?? stream.value?.qCards ?? [])
const displayTitle = computed(() => broadcastInfo.value?.title ?? stream.value?.title ?? '방송 진행')
const displayDatetime = computed(
  () => stream.value?.datetime ?? '실시간 송출 화면과 판매 상품, 채팅을 관리합니다.',
)
const lifecycleStatus = computed(() =>
  computeLifecycleStatus({
    status: streamStatus.value,
    startAtMs: scheduleStartAtMs.value ?? undefined,
    endAtMs: scheduleEndAtMs.value ?? undefined,
  }),
)
const isInteractive = computed(() => lifecycleStatus.value === 'ON_AIR')
const isReadOnly = computed(() => lifecycleStatus.value !== 'ON_AIR')
const isStopped = computed(() => lifecycleStatus.value === 'STOPPED')
const waitingScreenUrl = computed(() => stream.value?.waitingScreen ?? '')
const readyCountdownLabel = computed(() => {
  if (lifecycleStatus.value !== 'READY' || !scheduleStartAtMs.value) return ''
  const diffMs = scheduleStartAtMs.value - now.value.getTime()
  if (diffMs <= 0) return '방송 시작 대기 중'
  const totalSeconds = Math.ceil(diffMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}분 ${String(seconds).padStart(2, '0')}초 뒤 방송 시작`
})
const streamPlaceholderMessage = computed(() => {
  if (lifecycleStatus.value === 'STOPPED') {
    return '방송 운영 정책 위반으로 송출 중지되었습니다.'
  }
  if (lifecycleStatus.value === 'ENDED') {
    return '방송이 종료되었습니다.'
  }
  if (lifecycleStatus.value === 'READY') {
    return '방송 시작 대기 중'
  }
  return '송출 화면 (WebRTC Stream)'
})
const showPlaceholderMessage = computed(() => {
  if (lifecycleStatus.value === 'STOPPED') return true
  if (['READY', 'ENDED', 'ON_AIR'].includes(lifecycleStatus.value)) {
    return !waitingScreenUrl.value
  }
  return true
})

const resolveDetailStatus = (detail: BroadcastDetailResponse) => {
  const normalized = normalizeBroadcastStatus(detail.status)
  if (detail.startedAt && ['READY', 'RESERVED'].includes(normalized)) {
    return 'ON_AIR'
  }
  return normalized
}

const resolveMediaSelection = (value: string, fallback: string) => {
  const trimmed = value?.trim()
  if (!trimmed || trimmed === 'default') return fallback
  return trimmed
}

const hasPersistedMediaConfig = (mediaConfig?: MediaConfig | null) => {
  if (!mediaConfig) return false
  const cameraId = mediaConfig.cameraId?.trim()
  const microphoneId = mediaConfig.microphoneId?.trim()
  return Boolean(
      (cameraId && cameraId !== 'default') || (microphoneId && microphoneId !== 'default'),
  )
}

const toMediaId = (value: string, fallback: string) => {
  if (!value || value === fallback) return 'default'
  return value
}

const normalizeMediaSelection = (
  value: string,
  devices: Array<{ id: string }>,
  fallback: string,
) => {
  if (!value || value === fallback) return fallback
  const exists = devices.some((device) => device.id === value)
  return exists ? value : fallback
}

const loadMediaDevices = async () => {
  if (!navigator.mediaDevices?.enumerateDevices) {
    availableMics.value = []
    availableCameras.value = []
    return
  }
  try {
    const devices = await navigator.mediaDevices.enumerateDevices()
    availableMics.value = devices
      .filter((device) => device.kind === 'audioinput')
      .map((device, idx) => ({
        id: device.deviceId,
        label: device.label || `마이크 ${idx + 1}`,
      }))
    availableCameras.value = devices
      .filter((device) => device.kind === 'videoinput')
      .map((device, idx) => ({
        id: device.deviceId,
        label: device.label || `카메라 ${idx + 1}`,
      }))
    selectedMic.value = normalizeMediaSelection(selectedMic.value, availableMics.value, '기본 마이크')
    selectedCamera.value = normalizeMediaSelection(
      selectedCamera.value,
      availableCameras.value,
      '기본 카메라',
    )
  } catch {
    availableMics.value = []
    availableCameras.value = []
  }
}

const ensureLocalMediaAccess = async () => {
  if (!navigator.mediaDevices?.getUserMedia) {
    return
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true })
    stream.getTracks().forEach((track) => track.stop())
  } catch {
    return
  }
}

const clearPublisherRestartTimer = () => {
  if (publisherRestartTimer) {
    window.clearTimeout(publisherRestartTimer)
    publisherRestartTimer = null
  }
}

const getPublisherMediaStream = (publisher: Publisher) => {
  const publisherStream = publisher.stream
  if (!publisherStream) return null
  if (typeof publisherStream.getMediaStream === 'function') {
    return publisherStream.getMediaStream()
  }
  return null
}

const waitForPublisherTracks = async (publisher: Publisher) => {
  const deadline = Date.now() + PUBLISHER_READY_TIMEOUT_MS
  while (Date.now() < deadline) {
    const mediaStream = getPublisherMediaStream(publisher)
    if (mediaStream) {
      const hasVideo = !videoEnabled.value || mediaStream.getVideoTracks().length > 0
      const hasAudio = !micEnabled.value || mediaStream.getAudioTracks().length > 0
      if (hasVideo && hasAudio) return true
    }
    await new Promise((resolve) => window.setTimeout(resolve, PUBLISHER_READY_POLL_MS))
  }
  return false
}

const resetOpenViduState = () => {
  openviduConnected.value = false
  publisherToken.value = null
  openviduPublisher.value = null
  openviduSession.value = null
  openviduInstance.value = null
  if (publisherContainerRef.value) {
    publisherContainerRef.value.innerHTML = ''
  }
}

const attachPublisherHandlers = (publisher: Publisher, broadcastId?: number) => {
  publisher.on('streamCreated', () => {
    if (!broadcastId) return
  })
  publisher.on('streamDestroyed', (event: StreamEvent) => {
    event.preventDefault()
  })
}

const disconnectOpenVidu = () => {
  if (openviduSession.value) {
    try {
      if (openviduPublisher.value) {
        openviduSession.value.unpublish(openviduPublisher.value)
      }
      openviduSession.value.disconnect()
    } catch {
      // noop
    }
  }
  resetOpenViduState()
}

const buildPublisherOptions = () => {
  const audioSource = toMediaId(selectedMic.value, '기본 마이크')
  const videoSource = toMediaId(selectedCamera.value, '기본 카메라')
  return {
    audioSource: audioSource === 'default' ? undefined : audioSource,
    videoSource: videoSource === 'default' ? undefined : videoSource,
    publishAudio: micEnabled.value,
    publishVideo: videoEnabled.value,
    insertMode: 'append' as const,
    mirror: true,
  }
}

const applyPublisherVolume = () => {
  if (!publisherContainerRef.value) return
  const video = publisherContainerRef.value.querySelector('video') as HTMLVideoElement | null
  if (!video) return
  video.muted = true
  video.autoplay = true
  video.playsInline = true
  video.volume = Math.min(1, Math.max(0, volume.value / 100))
  void video.play().catch(() => {})
}

const waitForPublisherContainer = async () => {
  if (publisherContainerRef.value) return publisherContainerRef.value
  await nextTick()
  if (publisherContainerRef.value) return publisherContainerRef.value
  await new Promise((resolve) => window.setTimeout(resolve, 50))
  return publisherContainerRef.value
}

const restartPublisher = async () => {
  if (!openviduSession.value || !openviduInstance.value || !publisherContainerRef.value) return
  try {
    if (openviduPublisher.value) {
      openviduSession.value.unpublish(openviduPublisher.value)
    }
    publisherContainerRef.value.innerHTML = ''
    const publisher = openviduInstance.value.initPublisher(
      publisherContainerRef.value,
      buildPublisherOptions(),
    )
    openviduPublisher.value = publisher
    const broadcastId = streamId.value ? Number(streamId.value) : undefined
    attachPublisherHandlers(publisher, Number.isNaN(broadcastId) ? undefined : broadcastId)
    await openviduSession.value.publish(publisher)
    applyPublisherVolume()
  } catch {
    disconnectOpenVidu()
  }
}

const connectPublisher = async (broadcastId: number, token: string) => {
  if (openviduConnected.value) return true
  const container = await waitForPublisherContainer()
  if (!container) {
    scheduleStartRetry(broadcastId, '방송 화면 준비 중입니다. 잠시 후 다시 시도합니다.')
    return false
  }
  try {
    disconnectOpenVidu()
    openviduInstance.value = new OpenVidu()
    openviduSession.value = openviduInstance.value.initSession()
    await openviduSession.value.connect(token)
    const publisher = openviduInstance.value.initPublisher(
      container,
      buildPublisherOptions(),
    )
    openviduPublisher.value = publisher
    attachPublisherHandlers(publisher, broadcastId)
    await openviduSession.value.publish(publisher)
    openviduConnected.value = true
    applyPublisherVolume()
    const tracksReady = await waitForPublisherTracks(publisher)
    if (tracksReady) {
      await requestStartRecording(broadcastId)
    } else {
      recordingStartRequested.value = false
    }
    startRetryCount.value = 0
    if (startRetryTimer.value) window.clearTimeout(startRetryTimer.value)
    startRetryTimer.value = null
    return true
  } catch {
    disconnectOpenVidu()
    if (['READY', 'ON_AIR'].includes(lifecycleStatus.value)) {
      scheduleStartRetry(broadcastId, '방송 송출 연결에 실패했습니다. 다시 연결을 시도합니다.')
    }
    return false
  }
}

const requestPublisherToken = async (broadcastId: number) => {
  if (publisherTokenInFlight.value) return null
  publisherTokenInFlight.value = true
  try {
    const token = await startSellerBroadcast(broadcastId)
    publisherToken.value = token
    return token
  } catch {
    publisherToken.value = null
    return null
  } finally {
    publisherTokenInFlight.value = false
  }
}

const ensurePublisherConnected = async (broadcastId: number) => {
  if (openviduConnected.value) return
  await ensureLocalMediaAccess()
  await loadMediaDevices()
  if (!publisherToken.value) {
    const token = await requestPublisherToken(broadcastId)
    if (!token) return
  }
  if (!publisherToken.value) return
  await connectPublisher(broadcastId, publisherToken.value)
}

const clearStartTimer = () => {
  if (startTimer.value) {
    window.clearTimeout(startTimer.value)
    startTimer.value = null
  }
}

const clearEndRequestTimer = () => {
  if (endRequestTimer.value) {
    window.clearTimeout(endRequestTimer.value)
    endRequestTimer.value = null
  }
}

const stopMicMeter = () => {
  if (micMeterFrame.value !== null) {
    cancelAnimationFrame(micMeterFrame.value)
    micMeterFrame.value = null
  }
  if (micAudioContext.value) {
    micAudioContext.value.close()
    micAudioContext.value = null
  }
  micAnalyser.value = null
  if (micStream.value) {
    micStream.value.getTracks().forEach((track) => track.stop())
    micStream.value = null
  }
}

const startMicMeter = async () => {
  if (!navigator.mediaDevices?.getUserMedia) {
    micInputLevel.value = 0
    return
  }
  if (!micEnabled.value) {
    micInputLevel.value = 0
    stopMicMeter()
    return
  }
  stopMicMeter()
  try {
    const constraints: MediaStreamConstraints = {
      audio: selectedMic.value !== '기본 마이크' ? { deviceId: { exact: selectedMic.value } } : true,
    }
    const stream = await navigator.mediaDevices.getUserMedia(constraints)
    micStream.value = stream
    const [track] = stream.getAudioTracks()
    if (!track) {
      micInputLevel.value = 0
      return
    }
    const context = new AudioContext()
    const analyserNode = context.createAnalyser()
    analyserNode.fftSize = 512
    const source = context.createMediaStreamSource(stream)
    source.connect(analyserNode)
    micAudioContext.value = context
    micAnalyser.value = analyserNode
    const buffer = new Uint8Array(analyserNode.fftSize)
    const update = () => {
      analyserNode.getByteTimeDomainData(buffer)
      let sum = 0
      for (const sample of buffer) {
        const normalized = (sample - 128) / 128
        sum += normalized * normalized
      }
      const rms = Math.sqrt(sum / buffer.length)
      micInputLevel.value = Math.min(100, Math.round(rms * 140))
      micMeterFrame.value = requestAnimationFrame(update)
    }
    update()
  } catch (error) {
    if (
      error instanceof DOMException &&
      error.name === 'OverconstrainedError' &&
      selectedMic.value !== '기본 마이크'
    ) {
      selectedMic.value = '기본 마이크'
      void startMicMeter()
      return
    }
    micInputLevel.value = 0
  }
}

const scheduleMediaConfigSave = () => {
  if (!mediaConfigReady.value) return
  const idValue = streamId.value ? Number(streamId.value) : NaN
  if (Number.isNaN(idValue)) return
  if (mediaSaveTimer) window.clearTimeout(mediaSaveTimer)
  mediaSaveTimer = window.setTimeout(async () => {
    const payload = {
      cameraId: toMediaId(selectedCamera.value, '기본 카메라'),
      microphoneId: toMediaId(selectedMic.value, '기본 마이크'),
      cameraOn: videoEnabled.value,
      microphoneOn: micEnabled.value,
      volume: volume.value,
    }
    try {
      await saveMediaConfig(idValue, payload)
    } catch {
      return
    }
  }, 400)
}

const resetRealtimeState = () => {
  sseSource.value?.close()
  sseSource.value = null
  sseConnected.value = false
  if (sseRetryTimer.value) window.clearTimeout(sseRetryTimer.value)
  sseRetryTimer.value = null
  if (statsTimer.value) window.clearInterval(statsTimer.value)
  statsTimer.value = null
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = null
    if (startRetryTimer.value) window.clearTimeout(startRetryTimer.value)
    startRetryTimer.value = null
    startRetryCount.value = 0
    disconnectChat()
  clearStartTimer()
  clearEndRequestTimer()
  clearPublisherRestartTimer()
  disconnectOpenVidu()
  startRequested.value = false
  recordingStartRequested.value = false
  endRequested.value = false
}

const requestStartBroadcast = async (broadcastId: number) => {
  if (startRequested.value) return
  startRequested.value = true
  try {
    await ensureLocalMediaAccess()
    const token = await startSellerBroadcast(broadcastId)
    publisherToken.value = token
    const connected = await connectPublisher(broadcastId, token)
    if (!connected) {
      startRequested.value = false
      return
    }
    startRetryCount.value = 0
    if (startRetryTimer.value) window.clearTimeout(startRetryTimer.value)
    startRetryTimer.value = null
    scheduleRefresh(broadcastId)
  } catch {
    scheduleStartRetry(broadcastId, '방송 시작에 실패했습니다. 잠시 후 다시 시도합니다.')
    startRequested.value = false
  }
}

const scheduleStartRetry = (broadcastId: number, message?: string) => {
  if (lifecycleStatus.value !== 'ON_AIR') return
  if (startRetryCount.value >= MAX_START_RETRIES) {
    if (message) {
      alert(message)
    }
    return
  }
  startRetryCount.value += 1
  if (startRetryTimer.value) window.clearTimeout(startRetryTimer.value)
  startRetryTimer.value = window.setTimeout(() => {
    void requestStartBroadcast(broadcastId)
  }, START_RETRY_DELAY_MS)
}

const requestStartRecording = async (broadcastId: number) => {
  if (recordingStartRequested.value) return
  recordingStartRequested.value = true
  try {
    await startSellerRecording(broadcastId)
  } catch {
    recordingStartRequested.value = false
  }
}

const requestJoinToken = async (broadcastId: number) => {
  if (lifecycleStatus.value !== 'ON_AIR') return
  if (joinInFlight.value) return
  if (joinedBroadcastId.value === broadcastId) return
  if (!viewerId.value) {
    viewerId.value = resolveViewerId(getAuthUser())
  }
  if (!viewerId.value) return
  joinInFlight.value = true
  try {
    await joinBroadcast(broadcastId, viewerId.value)
    joinedBroadcastId.value = broadcastId
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

const handlePageHide = () => {
  void sendLeaveSignal(true)
}

const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (!isInteractive.value) return
  event.preventDefault()
  event.returnValue = ''
}

const scheduleAutoStart = (broadcastId: number, scheduledAtMs: number | null, status: BroadcastStatus) => {
  clearStartTimer()
  if (!scheduledAtMs || status !== 'READY') return
  const delay = Math.max(0, scheduledAtMs - Date.now())
  if (delay === 0) {
    void requestStartBroadcast(broadcastId)
    return
  }
  startTimer.value = window.setTimeout(() => {
    void requestStartBroadcast(broadcastId)
  }, delay)
}

const updateGridWidth = (width?: number) => {
  if (typeof width === 'number') {
    gridWidth.value = width
    return
  }
  const rectWidth = streamGridRef.value?.clientWidth
  if (rectWidth) {
    gridWidth.value = rectWidth
    return
  }
  gridWidth.value = typeof window !== 'undefined' ? window.innerWidth : 0
}

const scrollChatToBottom = () => {
  nextTick(() => {
    const el = chatListRef.value
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
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

const hydrateStream = async () => {
  isLoadingStream.value = true
  const id = streamId.value
  if (!id) {
    stream.value = null
    pinnedProductId.value = null
    broadcastInfo.value = null
    chatMessages.value = []
    viewerCount.value = 0
    likeCount.value = 0
    streamStatus.value = 'RESERVED'
    scheduleStartAtMs.value = null
    scheduleEndAtMs.value = null
    isLoadingStream.value = false
    clearStartTimer()
    clearEndRequestTimer()
    startRequested.value = false
    endRequested.value = false
    return
  }

  const idValue = Number(id)
  if (Number.isNaN(idValue)) {
    stream.value = null
    viewerCount.value = 0
    likeCount.value = 0
    streamStatus.value = 'RESERVED'
    scheduleStartAtMs.value = null
    scheduleEndAtMs.value = null
    isLoadingStream.value = false
    clearStartTimer()
    clearEndRequestTimer()
    startRequested.value = false
    endRequested.value = false
    return
  }

  try {
    mediaConfigReady.value = false
    const [detail, stats, chats, mediaConfig] = await Promise.all([
      fetchSellerBroadcastDetail(idValue),
      fetchBroadcastStats(idValue).catch(() => null),
      fetchRecentLiveChats(idValue, 300).catch(() => []),
      fetchMediaConfig(idValue).catch(() => null),
    ])
    const baseTime = detail.scheduledAt ?? detail.startedAt ?? ''
    const startAtMs = baseTime ? parseLiveDate(baseTime).getTime() : NaN
    scheduleStartAtMs.value = Number.isNaN(startAtMs) ? null : startAtMs
    scheduleEndAtMs.value = scheduleStartAtMs.value ? getScheduledEndMs(scheduleStartAtMs.value) ?? null : null
    streamStatus.value = resolveDetailStatus(detail)

    const products = (detail.products ?? []).map((product) => mapStreamProduct(product))

    stream.value = {
      title: detail.title ?? '',
      datetime: formatScheduleWindow(detail.scheduledAt, detail.startedAt),
      category: detail.categoryName ?? '',
      notice: detail.notice ?? defaultNotice,
      products,
      chat: [],
      qCards: (detail.qcards ?? []).map((card) => card.question),
      thumbnail: detail.thumbnailUrl,
      waitingScreen: detail.waitScreenUrl,
    }

    pinnedProductId.value = products.find((item) => item.pinned)?.id ?? null
    latestDetail.value = detail
    broadcastInfo.value = {
      title: detail.title ?? '',
      category: detail.categoryName ?? '',
      notice: detail.notice ?? defaultNotice,
      thumbnail: detail.thumbnailUrl,
      waitingScreen: detail.waitScreenUrl,
      qCards: (detail.qcards ?? []).map((card) => card.question),
    }

    viewerCount.value = stats?.viewerCount ?? detail.totalViews ?? 0
    likeCount.value = stats?.likeCount ?? detail.totalLikes ?? 0

    if (mediaConfig) {
      selectedMic.value = resolveMediaSelection(mediaConfig.microphoneId, '기본 마이크')
      selectedCamera.value = resolveMediaSelection(mediaConfig.cameraId, '기본 카메라')
      micEnabled.value = mediaConfig.microphoneOn
      videoEnabled.value = mediaConfig.cameraOn
      volume.value = mediaConfig.volume
      hasSavedMediaConfig.value = hasPersistedMediaConfig(mediaConfig)
    } else {
      selectedMic.value = '기본 마이크'
      selectedCamera.value = '기본 카메라'
      micEnabled.value = true
      videoEnabled.value = true
      volume.value = 50
      hasSavedMediaConfig.value = false
    }
    mediaConfigReady.value = true

    chatMessages.value = chats.map((item) => ({
      id: `${item.sentAt}-${item.sender}`,
      name: item.sender || item.memberEmail || '시청자',
      message: item.content,
      senderRole: item.senderRole,
      memberLoginId: item.memberEmail,
      time: formatChatTime(item.sentAt),
    }))
    isLoadingStream.value = false
    scrollChatToBottom()
    scheduleAutoStart(idValue, scheduleStartAtMs.value, streamStatus.value)
  } catch {
    stream.value = null
    chatMessages.value = []
    viewerCount.value = 0
    likeCount.value = 0
    streamStatus.value = 'RESERVED'
    scheduleStartAtMs.value = null
    scheduleEndAtMs.value = null
    isLoadingStream.value = false
    mediaConfigReady.value = false
    hasSavedMediaConfig.value = false
    clearStartTimer()
    clearEndRequestTimer()
    startRequested.value = false
    endRequested.value = false
  }
}

const refreshStats = async (broadcastId: number) => {
  try {
    const stats = await fetchBroadcastStats(broadcastId)
    viewerCount.value = stats.viewerCount ?? 0
    likeCount.value = stats.likeCount ?? 0
  } catch {
    return
  }
}

const refreshProducts = async (broadcastId: number) => {
  try {
    const detail = await fetchSellerBroadcastDetail(broadcastId)
    const products = (detail.products ?? []).map((product) => mapStreamProduct(product))
    pinnedProductId.value = products.find((item) => item.pinned)?.id ?? null
    if (stream.value) {
      stream.value = { ...stream.value, products }
    }
  } catch {
    return
  }
}

const refreshInfo = async (broadcastId: number) => {
  try {
    const detail = await fetchSellerBroadcastDetail(broadcastId)
    const baseTime = detail.scheduledAt ?? detail.startedAt ?? ''
    const startAtMs = baseTime ? parseLiveDate(baseTime).getTime() : NaN
    scheduleStartAtMs.value = Number.isNaN(startAtMs) ? null : startAtMs
    scheduleEndAtMs.value = scheduleStartAtMs.value ? getScheduledEndMs(scheduleStartAtMs.value) ?? null : null
    streamStatus.value = resolveDetailStatus(detail)
    if (stream.value) {
      stream.value = {
        ...stream.value,
        title: detail.title ?? '',
        datetime: formatScheduleWindow(detail.scheduledAt, detail.startedAt),
        category: detail.categoryName ?? '',
        notice: detail.notice ?? defaultNotice,
        thumbnail: detail.thumbnailUrl,
        waitingScreen: detail.waitScreenUrl,
        qCards: (detail.qcards ?? []).map((card) => card.question),
      }
    }
    if (broadcastInfo.value) {
      broadcastInfo.value = {
        ...broadcastInfo.value,
        title: detail.title ?? '',
        category: detail.categoryName ?? '',
        notice: detail.notice ?? defaultNotice,
        thumbnail: detail.thumbnailUrl,
        waitingScreen: detail.waitScreenUrl,
        qCards: (detail.qcards ?? []).map((card) => card.question),
      }
    }
    latestDetail.value = detail
    scheduleAutoStart(broadcastId, scheduleStartAtMs.value, streamStatus.value)
  } catch {
    return
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

const buildStopConfirmMessage = () => {
  return '방송 운영 정책 위반으로 방송이 중지되었습니다.\n방송에서 나가겠습니까?'
}

const handleStopConfirm = () => {
  handleGoToList()
}

const handleStopCancel = () => {
  isStopRestricted.value = true
  showChat.value = false
  showProducts.value = false
  showSettings.value = false
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

const scheduleRefresh = (broadcastId: number) => {
  if (refreshTimer.value) window.clearTimeout(refreshTimer.value)
  refreshTimer.value = window.setTimeout(() => {
    void refreshInfo(broadcastId)
    void refreshStats(broadcastId)
    void refreshProducts(broadcastId)
  }, 500)
}

const handleSseEvent = (event: MessageEvent) => {
  const id = streamId.value ? Number(streamId.value) : NaN
  if (Number.isNaN(id)) return
  const data = parseSseData(event)
  switch (event.type) {
    case 'BROADCAST_READY':
    case 'BROADCAST_UPDATED':
    case 'BROADCAST_STARTED':
      scheduleRefresh(id)
      break
    case 'PRODUCT_PINNED':
      pinnedProductId.value = typeof data === 'number' ? String(data) : pinnedProductId.value
      scheduleRefresh(id)
      break
    case 'PRODUCT_UNPINNED':
      pinnedProductId.value = null
      scheduleRefresh(id)
      break
    case 'PRODUCT_SOLD_OUT':
      scheduleRefresh(id)
      break
    case 'SANCTION_UPDATED':
      scheduleRefresh(id)
      break
    case 'BROADCAST_ENDING_SOON':
      alert('방송 종료 1분 전입니다.')
      break
    case 'BROADCAST_CANCELED':
      alert('방송이 자동 취소되었습니다.')
      handleGoToList()
      break
    case 'BROADCAST_ENDED':
      if (endRequested.value) {
        endRequested.value = false
        clearEndRequestTimer()
        scheduleRefresh(id)
        break
      }
      alert('방송이 종료되었습니다.')
      scheduleRefresh(id)
      break
    case 'BROADCAST_SCHEDULED_END':
      alert('방송이 종료되었습니다.')
      handleGoToList()
      break
    case 'BROADCAST_STOPPED':
      streamStatus.value = 'STOPPED'
      scheduleRefresh(id)
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
  const user = getAuthUser()
  const viewerId = resolveViewerId(user)
  const query = viewerId ? `?viewerId=${encodeURIComponent(viewerId)}` : ''
  const source = new EventSource(`${apiBase}/broadcasts/${broadcastId}/subscribe${query}`)
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
  events.forEach((name) => {
    source.addEventListener(name, handleSseEvent)
  })
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
    if (!sseConnected.value) {
      void refreshProducts(broadcastId)
    }
  }, 5000)
}

watch(
  () => route.params.id,
  () => {
    hydrateStream()
  },
  { immediate: true },
)

watch(
  () => streamId.value,
  (value) => {
    resetRealtimeState()
    hasOpenedDeviceModal.value = false
    if (joinedBroadcastId.value) {
      void sendLeaveSignal()
    }
    leaveRequested.value = false
    joinedBroadcastId.value = null
    joinedViewerId.value = null
    if (!value) {
      return
    }
    const idValue = Number(value)
    if (Number.isNaN(idValue)) {
      return
    }
    connectSse(idValue)
    startStatsPolling(idValue)
    void requestJoinToken(idValue)
  },
  { immediate: true },
)

watch(
  () => broadcastId.value,
  (newId) => {
    chatMessages.value = []
    disconnectChat()
    if (newId) {
      connectChat()
    }
  },
  { immediate: true },
)

const handleResize = () => updateGridWidth()

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && showSettings.value) {
    showSettings.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  window.addEventListener('resize', handleResize)
  window.addEventListener('pagehide', handlePageHide)
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('deskit-user-updated', handleAuthUpdate)
  window.addEventListener('visibilitychange', handleSseVisibilityChange)
  window.addEventListener('focus', handleSseVisibilityChange)
  viewerId.value = resolveViewerId(getAuthUser())
  refreshAuth()
  monitorRef.value = streamGridRef.value ?? streamCenterRef.value
  updateGridWidth()
  void loadMediaDevices()
  if (navigator.mediaDevices?.addEventListener) {
    navigator.mediaDevices.addEventListener('devicechange', loadMediaDevices)
  }
  if (streamGridRef.value) {
    gridObserver = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (entry?.contentRect?.width) {
        updateGridWidth(entry.contentRect.width)
      }
    })
    gridObserver.observe(streamGridRef.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('pagehide', handlePageHide)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('deskit-user-updated', handleAuthUpdate)
  window.removeEventListener('visibilitychange', handleSseVisibilityChange)
  window.removeEventListener('focus', handleSseVisibilityChange)
  void sendLeaveSignal()
  disconnectChat()
  if (navigator.mediaDevices?.removeEventListener) {
    navigator.mediaDevices.removeEventListener('devicechange', loadMediaDevices)
  }
  if (mediaSaveTimer) {
    window.clearTimeout(mediaSaveTimer)
    mediaSaveTimer = null
  }
  gridObserver?.disconnect()
  stopMicMeter()
  resetRealtimeState()
})

const openConfirm = (options: Partial<typeof confirmState>, onConfirm: () => void, onCancel: () => void = () => {}) => {
  confirmState.title = options.title ?? ''
  confirmState.description = options.description ?? ''
  confirmState.confirmText = options.confirmText ?? '확인'
  confirmState.cancelText = options.cancelText ?? '취소'
  confirmAction.value = onConfirm
  confirmCancelAction.value = onCancel
  confirmState.open = true
}

const handleConfirmAction = () => {
  confirmAction.value?.()
  confirmAction.value = () => {}
}

const handleConfirmCancel = () => {
  confirmCancelAction.value?.()
  confirmCancelAction.value = () => {}
}

const setPinnedProduct = async (productId: string | null) => {
  if (!isInteractive.value) return
  const previousPinned = pinnedProductId.value
  pinnedProductId.value = productId
  const broadcastValue = streamId.value ? Number(streamId.value) : NaN
  if (Number.isNaN(broadcastValue)) return
  if (!productId) {
    if (!previousPinned) return
    await unpinSellerBroadcastProduct(broadcastValue).catch(() => {})
    return
  }
  const productValue = Number(productId)
  if (Number.isNaN(productValue)) return
  await pinSellerBroadcastProduct(broadcastValue, productValue).catch(() => {})
}

const handlePinProduct = (productId: string) => {
  if (!isInteractive.value) return
  if (pinnedProductId.value && pinnedProductId.value !== productId) {
    openConfirm(
      {
        title: '상품 PIN 변경',
        description: 'PIN 상품을 변경하시겠습니까?',
        confirmText: '변경',
      },
      () => setPinnedProduct(productId),
    )
    return
  }
  setPinnedProduct(pinnedProductId.value === productId ? null : productId)
}

const openSanction = (item: StreamChat) => {
  if (item.name === 'SYSTEM') return
  if (!item.memberLoginId) {
    alert('로그인된 시청자만 제재할 수 있습니다.')
    return
  }
  sanctionTarget.value = { loginId: item.memberLoginId, connectionId: item.connectionId }
  showSanctionModal.value = true
}

const applySanction = (payload: { type: string; reason: string }) => {
  if (!sanctionTarget.value) return
  if (!broadcastId.value) return
  const sanctionType = payload.type === '채팅 금지' ? 'MUTE' : 'OUT'
  void sanctionSellerViewer(broadcastId.value, {
    memberLoginId: sanctionTarget.value.loginId,
    status: sanctionType,
    reason: payload.reason,
    connectionId: sanctionTarget.value.connectionId,
  })
  sanctionedUsers.value = {
    ...sanctionedUsers.value,
    [sanctionTarget.value.loginId]: { type: payload.type, reason: payload.reason },
  }
  alert(`${sanctionTarget.value.loginId}님에게 제재가 적용되었습니다.`)
  sanctionTarget.value = null
  const now = new Date()
  const at = `${now.getHours()}시 ${String(now.getMinutes()).padStart(2, '0')}분`
  chatMessages.value = [
    ...chatMessages.value,
    {
      id: `sys-${Date.now()}`,
      name: 'SYSTEM',
      message: `${payload.type} 처리됨 (사유: ${payload.reason})`,
      time: at,
    },
  ]
  scrollChatToBottom()
}

watch(showSanctionModal, (open) => {
  if (!open) {
    sanctionTarget.value = null
  }
})

const handleSendChat = () => {
  if (!isInteractive.value || !isLoggedIn.value || !isChatConnected.value) return
  if (!chatText.value.trim()) return
  sendSocketMessage('TALK', chatText.value.trim())
  chatText.value = ''
}

watch(showChat, (open) => {
  if (open) {
    scrollChatToBottom()
  }
})

watch(showSettings, async (open) => {
  if (open) {
    await ensureLocalMediaAccess()
    await loadMediaDevices()
    await startMicMeter()
    return
  }
  stopMicMeter()
})

watch(lifecycleStatus, () => {
  const idValue = streamId.value ? Number(streamId.value) : NaN
  if (Number.isNaN(idValue)) return
  if (lifecycleStatus.value === 'STOPPED') {
    promptStoppedEntry()
  } else {
    isStopRestricted.value = false
    stopEntryPrompted.value = false
  }
  void requestJoinToken(idValue)
  if (lifecycleStatus.value === 'ON_AIR') {
    void ensurePublisherConnected(idValue)
    return
  }
  if (['STOPPED', 'ENDED'].includes(lifecycleStatus.value)) {
    disconnectOpenVidu()
  }
})

watch([lifecycleStatus, publisherContainerRef], ([status, container]) => {
  if (status !== 'ON_AIR') return
  if (!container) return
  const idValue = streamId.value ? Number(streamId.value) : NaN
  if (Number.isNaN(idValue)) return
  void ensurePublisherConnected(idValue)
})

onBeforeRouteLeave(async () => {
  if (!isInteractive.value) return true
  return await confirmLeaveBroadcast()
})

watch([selectedMic, micEnabled], () => {
  if (showSettings.value) {
    void startMicMeter()
  }
})

watch([selectedMic, selectedCamera, micEnabled, videoEnabled, volume], () => {
  scheduleMediaConfigSave()
})

watch([selectedMic, selectedCamera], () => {
  if (!openviduConnected.value) return
  clearPublisherRestartTimer()
  publisherRestartTimer = window.setTimeout(() => {
    void restartPublisher()
  }, 200)
})

watch([micEnabled, videoEnabled], ([micOn, videoOn]) => {
  if (!openviduPublisher.value) return
  openviduPublisher.value.publishAudio(micOn)
  openviduPublisher.value.publishVideo(videoOn)
})

watch(volume, () => {
  applyPublisherVolume()
})

watch([stream, lifecycleStatus, hasSavedMediaConfig], ([value, status, hasConfig]) => {
  if (!value || hasOpenedDeviceModal.value || hasConfig) return
  if (status !== 'READY') return
  showDeviceModal.value = true
  hasOpenedDeviceModal.value = true
})

type UpdateBroadcastPayload = Parameters<typeof updateBroadcast>[1]

const buildUpdatePayload = (detail: BroadcastDetailResponse, info: EditableBroadcastInfo): UpdateBroadcastPayload => {
  const products = (detail.products ?? []).map((product) => ({
    productId: product.productId,
    bpPrice: product.bpPrice,
    bpQuantity: product.bpQuantity,
  }))
  const status = normalizeBroadcastStatus(detail.status)
  const scheduledAt = status === 'RESERVED' || status === 'CANCELED' ? detail.scheduledAt ?? null : null

  return {
    title: info.title.trim() || detail.title,
    notice: info.notice?.trim() ?? detail.notice ?? '',
    categoryId: detail.categoryId ?? 0,
    scheduledAt,
    thumbnailUrl: info.thumbnail ?? detail.thumbnailUrl ?? '',
    waitScreenUrl: info.waitingScreen ?? detail.waitScreenUrl ?? null,
    broadcastLayout: detail.layout ?? 'FULL',
    products,
    qcards: (detail.qcards ?? []).map((card) => ({ question: card.question })),
  }
}

const handleBasicInfoSave = async (payload: EditableBroadcastInfo) => {
  if (!broadcastInfo.value) return
  const idValue = streamId.value ? Number(streamId.value) : NaN
  const detail = latestDetail.value
  if (Number.isNaN(idValue) || !detail) {
    broadcastInfo.value = { ...broadcastInfo.value, ...payload }
    return
  }
  if (!detail.categoryId) {
    alert('카테고리 정보를 불러올 수 없습니다.')
    return
  }
  if (!detail.products || detail.products.length === 0) {
    alert('판매 상품 정보를 불러올 수 없습니다.')
    return
  }
  try {
    await updateBroadcast(idValue, buildUpdatePayload(detail, payload))
    await refreshInfo(idValue)
  } catch {
    alert('기본정보 수정에 실패했습니다.')
  }
}

const handleDeviceSetupApply = (payload: { cameraId: string; microphoneId: string }) => {
  if (payload.cameraId) {
    selectedCamera.value = payload.cameraId
  }
  if (payload.microphoneId) {
    selectedMic.value = payload.microphoneId
  }
  mediaConfigReady.value = true
  scheduleMediaConfigSave()
}

const handleGoToList = () => {
  router.push({ name: 'seller-live' }).catch(() => {})
}

const handleEndBroadcast = () => {
  const idValue = streamId.value ? Number(streamId.value) : NaN
  if (Number.isNaN(idValue)) return
  endRequested.value = true
  clearEndRequestTimer()
  endRequestTimer.value = window.setTimeout(() => {
    endRequested.value = false
    endRequestTimer.value = null
  }, 10000)
  void endSellerBroadcast(idValue)
    .then(() => {
      alert('방송이 종료되었습니다.')
    })
    .catch(() => {
      endRequested.value = false
      clearEndRequestTimer()
      alert('방송 종료에 실패했습니다.')
    })
}

const requestEndBroadcast = () => {
  if (!isInteractive.value) return
  openConfirm(
    {
      title: '방송 종료',
      description: '방송 종료 시 송출이 중단되며, 시청자 화면은 대기화면으로 전환됩니다. VOD 인코딩이 자동으로 시작됩니다.',
      confirmText: '종료',
      cancelText: '취소',
    },
    handleEndBroadcast,
  )
}

const confirmLeaveBroadcast = () =>
  new Promise<boolean>((resolve) => {
    openConfirm(
      {
        title: '방송 종료',
        description: '방송 페이지를 나가면 방송이 종료됩니다. 계속 진행하시겠습니까?',
        confirmText: '종료 후 이동',
        cancelText: '취소',
      },
      () => {
        handleEndBroadcast()
        resolve(true)
      },
      () => resolve(false),
    )
  })

const toggleFullscreen = async () => {
  const el = monitorRef.value
  if (!el) return
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await el.requestFullscreen()
    }
  } catch {
    return
  }
}
</script>

<template>
  <PageContainer>
    <div v-if="stopConfirmOpen" class="stop-blocker" aria-hidden="true"></div>
    <header class="stream-header">
      <div>
        <h2 class="section-title">{{ displayTitle }}</h2>
        <p class="ds-section-sub">
          {{ displayDatetime }}
          <span v-if="readyCountdownLabel" class="stream-countdown">{{ readyCountdownLabel }}</span>
        </p>
      </div>
      <div class="stream-actions">
        <button type="button" class="stream-btn" :disabled="!stream || isStopped" @click="showBasicInfo = true">기본정보 수정</button>
        <button type="button" class="stream-btn" :disabled="!stream || !qCards.length || isStopped" @click="showQCards = true">큐카드 보기</button>
        <button type="button" class="stream-btn stream-btn--danger" :disabled="!stream || isStopped" @click="requestEndBroadcast">
          방송 종료
        </button>
      </div>
    </header>

    <section
      ref="streamGridRef"
      class="stream-grid"
      :class="{
        'stream-grid--chat': showChat,
        'stream-grid--products': showProducts,
        'stream-grid--stacked': isStackedLayout,
      }"
      :style="gridStyles"
    >
      <aside
        v-if="showProducts && !isStopRestricted"
        class="stream-panel stream-panel--products ds-surface"
        :class="{ 'stream-panel--readonly': isReadOnly }"
        :style="stackedOrders ? { order: stackedOrders.products } : undefined"
      >
        <div class="panel-head">
          <div class="panel-head__left">
            <h3>상품 관리</h3>
          </div>
          <button type="button" class="panel-close" aria-label="상품 관리 닫기" @click="showProducts = false">×</button>
        </div>
        <div class="panel-list">
          <article
            v-for="item in sortedProducts"
            :key="item.id"
            class="panel-item"
            :class="{ 'is-pinned': pinnedProductId === item.id, 'is-soldout': item.status === '품절' }"
          >
            <span v-if="pinnedProductId === item.id" class="pin-badge">PIN</span>
            <div class="panel-thumb ds-thumb-frame ds-thumb-square">
              <img class="ds-thumb-img" :src="item.thumb" :alt="item.title" loading="lazy" @error="handleImageError" />
            </div>
            <div class="panel-meta">
              <p class="panel-title">{{ item.title }}</p>
              <p class="panel-sub">{{ item.option }}</p>
              <p class="panel-price">
                <span class="panel-sale">{{ item.sale }}</span>
                <span class="panel-origin">{{ item.price }}</span>
              </p>
              <p class="panel-stats">판매 {{ item.sold }} · 재고 {{ item.stock }}</p>
            </div>
            <div class="panel-actions">
              <span class="panel-status" :class="{ 'is-soldout': item.status === '품절' }">{{ item.status }}</span>
              <button
                type="button"
                class="pin-btn"
                :disabled="!isInteractive || item.status === '품절'"
                :class="{ 'is-active': pinnedProductId === item.id }"
                aria-label="고정"
                @click="handlePinProduct(item.id)"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path
                    d="M9 4h6l1 5-2 2v6l-2-1-2 1v-6l-2-2 1-5z"
                    stroke="currentColor"
                    stroke-width="1.7"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </div>
          </article>
        </div>
      </aside>

      <div
        ref="streamCenterRef"
        class="stream-center ds-surface"
        :style="stackedOrders ? { order: stackedOrders.stream } : undefined"
      >
        <div class="stream-center__body">
          <div
            class="stream-player"
            :class="{
              'stream-player--fullscreen': isFullscreen,
              'stream-player--constrained': hasSidePanels,
            }"
          >
            <div
              v-show="hasPublisherStream"
              ref="publisherContainerRef"
              class="stream-player__publisher"
            ></div>
            <div class="stream-overlay stream-overlay--stack">
              <div class="stream-overlay__row">⏱ 경과 {{ elapsedLabel }}</div>
              <div class="stream-overlay__row">👥 {{ viewerCount.toLocaleString('ko-KR') }}명</div>
              <div class="stream-overlay__row">❤ {{ likeCount.toLocaleString('ko-KR') }}</div>
            </div>
            <div v-if="!isStopRestricted" class="stream-fab">
              <button
                type="button"
                class="fab-btn"
                :class="{ 'is-off': !showProducts }"
                :aria-label="showProducts ? '상품 패널 닫기' : '상품 패널 열기'"
                @click="showProducts = !showProducts"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M3 7h18l-2 12H5L3 7z" stroke="currentColor" stroke-width="1.7" />
                  <path d="M10 11v4M14 11v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                  <circle cx="9" cy="19" r="1" fill="currentColor" />
                  <circle cx="15" cy="19" r="1" fill="currentColor" />
                </svg>
              </button>
              <button
                type="button"
                class="fab-btn"
                :class="{ 'is-off': !showChat }"
                :aria-label="showChat ? '채팅 패널 닫기' : '채팅 패널 열기'"
                @click="showChat = !showChat"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M3 20l1.62-3.24A2 2 0 0 1 6.42 16H20a1 1 0 0 0 1-1V5a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v15z" stroke="currentColor" stroke-width="1.7" />
                  <path d="M7 9h10M7 12h6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                </svg>
              </button>
              <button
                type="button"
                class="fab-btn"
                :class="{ 'is-off': !showSettings }"
                aria-label="방송 설정 토글"
                @click="showSettings = !showSettings"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                  <circle cx="9" cy="6" r="2" stroke="currentColor" stroke-width="1.7" />
                  <circle cx="14" cy="12" r="2" stroke="currentColor" stroke-width="1.7" />
                  <circle cx="7" cy="18" r="2" stroke="currentColor" stroke-width="1.7" />
                </svg>
              </button>
              <button type="button" class="fab-btn" aria-label="전체 화면" @click="toggleFullscreen">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
            </div>
            <div v-if="isLoadingStream" class="stream-empty">
              <p class="stream-title">방송 정보를 불러오는 중입니다.</p>
              <p class="stream-sub">잠시만 기다려주세요.</p>
            </div>
            <div v-else-if="!stream" class="stream-empty">
              <p class="stream-title">방송 정보를 불러올 수 없습니다.</p>
              <p class="stream-sub">라이브 관리 페이지에서 다시 시도해주세요.</p>
              <div class="stream-actions">
                <button type="button" class="stream-btn" @click="handleGoToList">목록으로 이동</button>
              </div>
            </div>
            <div
              v-else
              v-show="!hasPublisherStream"
              class="stream-placeholder"
              :class="{ 'stream-placeholder--waiting': lifecycleStatus !== 'ON_AIR' }"
            >
              <img
                v-if="waitingScreenUrl && lifecycleStatus !== 'ON_AIR' && lifecycleStatus !== 'STOPPED'"
                class="stream-placeholder__image"
                :src="waitingScreenUrl"
                alt="대기 화면"
              />
              <p v-if="showPlaceholderMessage" class="stream-title">{{ streamPlaceholderMessage }}</p>
              <p v-if="lifecycleStatus === 'ON_AIR'" class="stream-sub">현재 송출 중인 화면이 표시됩니다.</p>
              <p v-else-if="!waitingScreenUrl && lifecycleStatus !== 'STOPPED'" class="stream-sub">대기 화면 이미지가 없습니다.</p>
            </div>
          </div>
        </div>
        <div v-if="showSettings && !isStopRestricted" class="stream-settings ds-surface" role="dialog" aria-label="방송 설정">
          <div class="stream-settings__grid">
            <div class="stream-settings__group">
              <div class="stream-settings__toggles">
                <button
                  type="button"
                  class="stream-toggle"
                  :class="{ 'is-off': !micEnabled }"
                  :aria-pressed="micEnabled"
                  @click="micEnabled = !micEnabled"
                >
                  <span class="stream-toggle__icon" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                      <rect x="9" y="4" width="6" height="10" rx="3" stroke="currentColor" stroke-width="1.7" />
                      <path d="M6 11a6 6 0 0012 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                      <path d="M12 17v3" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                    </svg>
                  </span>
                  <span>마이크</span>
                </button>
                <button
                  type="button"
                  class="stream-toggle"
                  :class="{ 'is-off': !videoEnabled }"
                  :aria-pressed="videoEnabled"
                  @click="videoEnabled = !videoEnabled"
                >
                  <span class="stream-toggle__icon" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                      <rect x="4" y="7" width="11" height="10" rx="2" stroke="currentColor" stroke-width="1.7" />
                      <path d="M15 10l5-3v10l-5-3v-4z" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </span>
                  <span>카메라</span>
                </button>
              </div>
            </div>
            <div class="stream-settings__group">
              <div class="stream-settings__slider">
                <span class="stream-settings__icon" aria-label="볼륨">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path
                      d="M5 10h4l5-4v12l-5-4H5z"
                      stroke="currentColor"
                      stroke-width="1.7"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                    <path d="M17 9a3 3 0 010 6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                  </svg>
                </span>
                <input v-model.number="volume" type="range" min="0" max="100" aria-label="볼륨 조절" />
                <span class="stream-settings__value">{{ volume }}%</span>
              </div>
            </div>
            <div class="stream-settings__group stream-settings__group--end">
              <button type="button" class="stream-settings__close" aria-label="설정 닫기" @click="showSettings = false">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M6 6l12 12" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                  <path d="M18 6l-12 12" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                </svg>
              </button>
            </div>
            <div class="stream-settings__group">
              <label class="stream-settings__label">마이크</label>
              <select v-model="selectedMic" class="stream-settings__select" aria-label="마이크 선택">
                <option value="기본 마이크">기본 마이크</option>
                <option v-for="device in availableMics" :key="device.id" :value="device.id">
                  {{ device.label }}
                </option>
              </select>
            </div>
            <div class="stream-settings__group">
              <label class="stream-settings__label">카메라</label>
              <select v-model="selectedCamera" class="stream-settings__select" aria-label="카메라 선택">
                <option value="기본 카메라">기본 카메라</option>
                <option v-for="device in availableCameras" :key="device.id" :value="device.id">
                  {{ device.label }}
                </option>
              </select>
            </div>
            <div class="stream-settings__group">
              <span class="stream-settings__label">입력 레벨</span>
              <div class="stream-settings__meter" role="progressbar" :aria-valuenow="micInputLevel" aria-valuemin="0" aria-valuemax="100">
                <span class="stream-settings__meter-fill" :style="{ width: `${micInputLevel}%` }"></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <aside
        v-if="showChat && !isStopRestricted"
        class="stream-panel stream-chat stream-panel--chat ds-surface"
        :class="{ 'stream-panel--readonly': isReadOnly }"
        :style="stackedOrders ? { order: stackedOrders.chat } : undefined"
      >
        <div class="panel-head">
          <div class="panel-head__left">
            <h3>실시간 채팅</h3>
          </div>
          <button type="button" class="panel-close" aria-label="채팅 패널 닫기" @click="showChat = false">×</button>
        </div>
        <div ref="chatListRef" class="panel-chat chat-messages" @scroll="handleChatScroll">
          <div
            v-for="item in chatItems"
            :key="item.id"
            class="chat-message"
            :class="{ 'chat-message--muted': sanctionedUsers[item.name], 'chat-message--system': item.name === 'SYSTEM' }"
            @contextmenu.prevent="openSanction(item)"
          >
            <div class="chat-meta">
              <span class="chat-user">{{ formatChatUser(item) }}</span>
              <span class="chat-time">{{ item.time }}</span>
              <span v-if="sanctionedUsers[item.name]" class="chat-badge">{{ sanctionedUsers[item.name]?.type }}</span>
            </div>
            <p class="chat-text">{{ item.message }}</p>
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
          <input
            v-model="chatText"
            type="text"
            placeholder="메시지를 입력하세요"
            :disabled="!isLoggedIn || !isChatConnected || isReadOnly"
            @keyup.enter="handleSendChat"
          />
          <button type="button" class="stream-btn primary" :disabled="!isLoggedIn || !isChatConnected || isReadOnly" @click="handleSendChat">전송</button>
        </div>
        <p v-if="isReadOnly" class="chat-helper">방송 중에만 채팅을 이용할 수 있습니다.</p>
      </aside>
    </section>
    <Teleport :to="modalHostTarget">
      <ConfirmModal
        v-model="stopConfirmOpen"
        title="방송 송출 중지"
        :description="stopConfirmMessage"
        confirm-text="나가기"
        cancel-text="계속 보기"
        @confirm="handleStopConfirm"
        @cancel="handleStopCancel"
      />
      <ConfirmModal
        v-model="confirmState.open"
        :title="confirmState.title"
        :description="confirmState.description"
        :confirm-text="confirmState.confirmText"
        :cancel-text="confirmState.cancelText"
        @confirm="handleConfirmAction"
        @cancel="handleConfirmCancel"
      />
      <QCardModal v-model="showQCards" :q-cards="qCards" :initial-index="qCardIndex" @update:initialIndex="qCardIndex = $event" />
      <BasicInfoEditModal v-if="broadcastInfo" v-model="showBasicInfo" :broadcast="broadcastInfo" @save="handleBasicInfoSave" />
      <ChatSanctionModal v-model="showSanctionModal" :username="sanctionTarget?.loginId ?? null" @save="applySanction" />
    </Teleport>
    <DeviceSetupModal
      v-model="showDeviceModal"
      :broadcast-title="displayTitle"
      :initial-camera-id="selectedCamera === '기본 카메라' ? '' : selectedCamera"
      :initial-mic-id="selectedMic === '기본 마이크' ? '' : selectedMic"
      @apply="handleDeviceSetupApply"
    />
  </PageContainer>
</template>

<style scoped>
.stop-blocker {
  position: fixed;
  inset: 0;
  background: var(--surface);
  z-index: 1300;
}

.stream-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.stream-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.stream-btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 16px;
  font-weight: 800;
  cursor: pointer;
}

.stream-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.stream-btn--danger {
  border-color: rgba(239, 68, 68, 0.35);
  color: #ef4444;
}

.stream-grid {
  display: grid;
  grid-template-columns: var(--grid-template-columns, 320px minmax(0, 1fr) 320px);
  gap: 18px;
  align-items: start;
  --stream-pane-height: clamp(300px, 70vh, 675px);
}

.stream-panel {
  padding: 16px;
  gap: 12px;
  height: var(--stream-pane-height);
  max-height: var(--stream-pane-height);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.stream-panel--chat {
  position: relative;
}

.stream-panel--readonly {
  opacity: 0.6;
}

.chat-helper {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 0.9rem;
  font-weight: 700;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex: 0 0 auto;
}

.panel-head__left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.panel-close {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  width: 32px;
  height: 32px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 900;
}

.panel-head h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.panel-count {
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  color: var(--text-strong);
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 800;
}

.panel-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
  flex: 1 1 auto;
  min-height: 0;
}

.panel-item {
  position: relative;
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border-radius: 12px;
  background: var(--surface-weak);
}

.panel-item.is-pinned {
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.4);
  background: rgba(59, 130, 246, 0.08);
}

.panel-item.is-soldout {
  opacity: 0.72;
}

.panel-thumb {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
}

.panel-meta {
  min-width: 0;
}

.panel-title {
  margin: 0;
  color: var(--text-strong);
  font-weight: 800;
  font-size: 0.9rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  gap: 6px;
  align-items: center;
}

.panel-sub {
  margin: 4px 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.panel-price {
  margin: 6px 0 2px;
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.panel-sale {
  font-weight: 800;
  color: var(--text-strong);
  font-size: 0.9rem;
}

.panel-origin {
  color: var(--text-muted);
  font-size: 0.75rem;
  text-decoration: line-through;
}

.panel-stats {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.panel-actions {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.panel-status {
  display: inline-flex;
  padding: 3px 6px;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 800;
  color: #0f766e;
  background: rgba(16, 185, 129, 0.12);
}

.panel-status.is-soldout {
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.12);
}

.pin-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--primary-color);
  color: #fff;
  font-size: 0.7rem;
  font-weight: 900;
  position: absolute;
  top: 8px;
  left: 8px;
}

.pin-btn {
  border: none;
  background: transparent;
  font-size: 1rem;
  cursor: pointer;
}

.pin-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pin-btn.is-active {
  color: var(--primary-color);
}

.stream-overlay {
  position: absolute;
  top: 16px;
  right: 16px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  border-radius: 12px;
  padding: 10px 12px;
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  z-index: 2;
  width: fit-content;
  min-width: 0;
}

.stream-overlay__row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  font-size: 0.8rem;
}

.stream-fab {
  position: absolute;
  bottom: 16px;
  right: 16px;
  display: grid;
  grid-auto-rows: 1fr;
  gap: 6px;
  justify-items: end;
}

.fab-btn {
  width: 39px;
  height: 39px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  cursor: pointer;
  font-size: 1.05rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 10px;
}

.fab-btn.is-off {
  opacity: 0.6;
}

.stream-center {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: var(--stream-pane-height);
  height: var(--stream-pane-height);
  max-height: var(--stream-pane-height);
  position: relative;
  background: #1c1d21;
  width: 100%;
}

.stream-center__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.stream-player {
  position: relative;
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
  border-radius: 16px;
  background: #0b0f1a;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  max-width: 100%;
}

.stream-player__publisher {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: #000;
}

.stream-player__publisher :deep(video) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}

.stream-player__publisher :deep(canvas),
.stream-player__publisher :deep(.OV_video-element) {
  transform: scaleX(-1);
}

.stream-player--fullscreen {
  max-height: none;
  width: min(100vw, calc(100vh * (16 / 9)));
  height: min(100vh, calc(100vw * (9 / 16)));
  border-radius: 0;
  background: #000;
}

.stream-player--constrained {
  max-width: min(100%, calc((100vh - 120px) * (16 / 9)));
}

.stream-placeholder {
  display: grid;
  gap: 8px;
  padding-top: 24px;
  text-align: center;
}

.stream-placeholder--waiting {
  gap: 12px;
  padding: 0;
  width: 100%;
  height: 100%;
  place-items: center;
}

.stream-placeholder__image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 12px;
  background: #000;
}

.stream-empty {
  display: grid;
  gap: 6px;
  padding-top: 24px;
  text-align: center;
}

.stream-title {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
  font-size: 1.1rem;
}

.stream-placeholder--waiting .stream-title {
  color: #ffffff;
  font-size: 1.35rem;
  text-shadow: 0 3px 12px rgba(0, 0, 0, 0.45);
}

.stream-sub {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.panel-chat {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 0;
  align-items: stretch;
  justify-content: flex-start;
}

.chat-messages {
  flex: 1 1 auto;
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
  gap: 6px;
}

.chat-message--system .chat-user {
  color: #ef4444;
}

.chat-message--muted .chat-text {
  color: var(--text-muted);
}

.chat-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: var(--text-muted);
  font-weight: 700;
}

.chat-user {
  color: var(--text-strong);
  font-weight: 800;
}

.chat-time {
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 700;
}

.chat-text {
  margin: 0;
  color: var(--text-strong);
  font-weight: 700;
  font-size: 0.9rem;
  line-height: 1.45;
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
  flex: 0 0 auto;
}

.chat-input input {
  flex: 1;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.stream-btn.primary {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.stream-countdown {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 10px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
  font-weight: 800;
  font-size: 0.85rem;
}


.stream-settings {
  position: absolute;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  width: min(920px, calc(100% - 32px));
  display: block;
  padding: 16px 20px;
  border-radius: 16px;
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.16);
  z-index: 5;
}

.stream-settings__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  align-items: center;
}

.stream-settings__group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stream-settings__group--end {
  align-items: flex-end;
}

.stream-settings__toggles {
  display: flex;
  gap: 8px;
}

.stream-toggle {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 8px 12px;
  font-weight: 800;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  font-size: 0.9rem;
}

.stream-toggle.is-off {
  background: rgba(239, 68, 68, 0.16);
  border-color: rgba(239, 68, 68, 0.6);
  color: #b91c1c;
}

.stream-toggle.is-off::after {
  content: '';
  position: absolute;
  left: -20%;
  top: 50%;
  width: 140%;
  height: 2px;
  transform: rotate(-20deg);
  background: rgba(239, 68, 68, 0.9);
  z-index: 0;
}

.stream-toggle > * {
  position: relative;
  z-index: 1;
}

.stream-settings__slider {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stream-settings__slider input[type='range'] {
  width: 200px;
  accent-color: var(--primary-color);
}

.stream-settings__value {
  font-weight: 800;
  color: var(--text-strong);
}

.stream-settings__label {
  font-weight: 800;
  color: var(--text-strong);
}

.stream-settings__select {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 8px 10px;
  font-weight: 700;
}

.stream-settings__meter {
  height: 10px;
  border-radius: 999px;
  background: var(--surface-weak);
  overflow: hidden;
}

.stream-settings__meter-fill {
  display: block;
  height: 100%;
  background: var(--primary-color);
  border-radius: inherit;
}

.stream-settings__close {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 8px 10px;
  font-weight: 800;
  cursor: pointer;
}

.stream-grid:fullscreen {
  gap: 14px;
}

.stream-grid:fullscreen .stream-panel,
.stream-grid:fullscreen .stream-center {
  height: 100vh;
  max-height: 100vh;
  min-height: 0;
}

.stream-grid:fullscreen .stream-player {
  max-height: 100vh;
  width: min(100vw, calc(100vh * (16 / 9)));
  height: min(100vh, calc(100vw * (9 / 16)));
  border-radius: 0;
  background: #000;
}

.stream-grid:fullscreen.stream-grid--chat .stream-player {
  width: min(max(320px, calc(100vw - 380px)), calc(100vh * (16 / 9)));
  height: min(100vh, max(200px, calc((100vw - 380px) * (9 / 16))));
}

.stream-grid:fullscreen.stream-grid--products:not(.stream-grid--chat) .stream-player {
  width: min(max(320px, calc(100vw - 340px)), calc(100vh * (16 / 9)));
  height: min(100vh, max(200px, calc((100vw - 340px) * (9 / 16))));
}

.stream-grid:fullscreen.stream-grid--products.stream-grid--chat .stream-player {
  width: min(max(320px, calc(100vw - 720px)), calc(100vh * (16 / 9)));
  height: min(100vh, max(200px, calc((100vw - 720px) * (9 / 16))));
}

.stream-grid:not(.stream-grid--products):not(.stream-grid--chat) .stream-player {
  max-width: 100%;
}

.stream-grid:not(.stream-grid--products):not(.stream-grid--chat) {
  gap: 0;
}

.stream-grid--stacked {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: stretch;
}

.stream-grid--stacked .stream-center,
.stream-grid--stacked .stream-panel {
  height: auto;
  max-height: none;
  min-height: 0;
  width: 100%;
}

.stream-grid--stacked .stream-center {
  order: 0 !important;
}

.stream-grid--stacked .stream-panel--chat {
  order: 1 !important;
  max-height: min(70vh, 720px);
  overflow: hidden;
  min-height: 0;
}

.stream-grid--stacked .stream-panel--products {
  order: 2 !important;
}

.stream-grid--stacked .panel-head {
  flex-wrap: wrap;
  gap: 8px;
}

.stream-grid--stacked .panel-head__left {
  flex: 1 1 auto;
}

@media (max-width: 960px) {
  .stream-panel {
    overflow: visible;
  }
}

@media (max-width: 720px) {
  .stream-settings {
    flex-direction: column;
  }

  .stream-settings__grid {
    grid-template-columns: 1fr;
  }

  .stream-settings__slider input[type='range'] {
    width: 100%;
  }
}
</style>
