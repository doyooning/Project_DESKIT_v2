import {http} from '../../api/http'
import {parseLiveDate} from './utils'
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export type BroadcastCategory = {
  id: number
  name: string
}

export type ReservationSlot = {
  time: string
  remainingCapacity: number
}

export type SellerProduct = {
  id: string
  name: string
  option: string
  price: number
  broadcastPrice: number
  stock: number
  safetyStock: number
  reservedBroadcastQty?: number
  quantity: number
  thumb?: string
}

export type BroadcastListItem = {
  broadcastId: number
  title: string
  notice?: string
  sellerName?: string
  categoryName?: string
  thumbnailUrl?: string
  status?: string
  startAt?: string
  endAt?: string
  viewerCount?: number
  liveViewerCount?: number
  reportCount?: number
  totalSales?: number | string
  totalLikes?: number
  isPublic?: boolean
  public?: boolean
  adminLock?: boolean
}

export type BroadcastDetail = {
  broadcastId: number
  sellerName?: string
  title: string
  notice?: string
  status?: string
  categoryName?: string
  scheduledAt?: string
  startedAt?: string
  thumbnailUrl?: string
  waitScreenUrl?: string
  stoppedReason?: string
  vodUrl?: string
  totalViews?: number
  totalLikes?: number
  totalReports?: number
}

export type BroadcastProductItem = {
  id: string
  name: string
  imageUrl: string
  price: number
  originalPrice?: number
  totalQty?: number
  isPinned?: boolean
  isSoldOut: boolean
  stockQty: number
}

export type BroadcastStats = {
  viewerCount: number
  likeCount: number
  reportCount: number
}

export type BroadcastLikeResponse = {
  liked: boolean
  likeCount: number
}

export type BroadcastReportResponse = {
  reported: boolean
  reportCount: number
}

export type BroadcastResult = {
  broadcastId: number
  title: string
  startAt?: string
  endAt?: string
  durationMinutes?: number
  status?: string
  stoppedReason?: string
  totalViews: number
  totalLikes: number
  totalSales: number | string
  totalChats: number
  maxViewers: number
  maxViewerTime?: string
  avgWatchTime?: number
  reportCount: number
  sanctionCount: number
  vodUrl?: string
  vodStatus?: string
  vodAdminLock?: boolean
  encoding?: boolean
  productStats?: Array<{
    productId: number
    productName: string
    imageUrl?: string
    price: number
    salesQuantity: number
    salesAmount: number | string
  }>
}

export type BroadcastDetailResponse = {
  broadcastId: number
  sellerId?: number
  sellerName?: string
  sellerProfileUrl?: string
  title: string
  notice?: string
  status?: string
  layout?: string
  categoryId?: number
  categoryName?: string
  scheduledAt?: string
  startedAt?: string
  thumbnailUrl?: string
  waitScreenUrl?: string
  streamKey?: string
  vodUrl?: string
  stoppedReason?: string
  totalViews?: number
  totalLikes?: number
  totalReports?: number
  products?: Array<{
    bpId: number
    productId: number
    name: string
    imageUrl?: string
    originalPrice: number
    stockQty?: number
    safetyStock?: number
    productStockQty?: number
    bpPrice: number
    bpQuantity: number
    displayOrder: number
    pinned: boolean
    status: string
  }>
  qcards?: Array<{ qcardId: number; sortOrder: number; question: string }>
}

export type StatisticsResponse = {
  salesChart: Array<{ label: string; value: number | string }>
  arpuChart: Array<{ label: string; value: number | string }>
  bestBroadcasts: Array<{ broadcastId: number; title: string; totalSales: number | string; totalViews: number }>
  worstBroadcasts: Array<{ broadcastId: number; title: string; totalSales: number | string; totalViews: number }>
  topViewerBroadcasts: Array<{ broadcastId: number; title: string; totalSales: number | string; totalViews: number }>
  worstViewerBroadcasts?: Array<{ broadcastId: number; title: string; totalSales: number | string; totalViews: number }>
  bestProducts?: Array<{ productId: number; title: string; totalSales: number | string }>
  worstProducts?: Array<{ productId: number; title: string; totalSales: number | string }>
}

export type SanctionStatisticsResponse = {
  forceStopChart: Array<{ label: string; count: number }>
  viewerBanChart: Array<{ label: string; count: number }>
  worstSellers: Array<{ sellerId: number; sellerName: string; email?: string; sanctionCount: number }>
  worstViewers: Array<{ viewerId: string; name?: string; sanctionCount: number }>
}

export type LiveChatMessage = {
  broadcastId: number
  memberEmail?: string
  type?: string
  sender: string
  content: string
  senderRole?: string
  vodPlayTime?: number
  sentAt: number
}

export type MediaConfig = {
  cameraId: string
  microphoneId: string
  cameraOn: boolean
  microphoneOn: boolean
  volume: number
}

type ApiResult<T> = {
  success: boolean
  data: T
  error?: { code: string; message: string }
}

export type ImageUploadResponse = {
  originalFileName: string
  storedFileName: string
  fileUrl: string
  fileSize: number
}

export type UploadImageType = 'THUMBNAIL' | 'WAIT_SCREEN'

type BroadcastPayload = {
  title: string
  notice: string
  categoryId: number
  scheduledAt: string | null
  thumbnailUrl: string
  waitScreenUrl: string | null
  broadcastLayout: string
  products: Array<{ productId: number; bpPrice: number; bpQuantity: number }>
  qcards: Array<{ question: string }>
}

type BroadcastAllResponse = {
  onAir: BroadcastListItem[]
  reserved: BroadcastListItem[]
  vod: BroadcastListItem[]
}

const addMinutes = (value: string, minutes: number) => {
  const base = parseLiveDate(value)
  if (Number.isNaN(base.getTime())) {
    return value
  }
  const next = new Date(base.getTime() + minutes * 60 * 1000)
  return next.toISOString()
}

const ensureEndAt = (startAt?: string, endAt?: string, status?: string) => {
  if (endAt) return endAt
  if (!startAt) return ''
  if (status === 'ENDED' || status === 'VOD' || status === 'STOPPED') {
    return addMinutes(startAt, 30)
  }
  return addMinutes(startAt, 30)
}

const ensureSuccess = <T>(response: ApiResult<T>) => {
  if (response?.success) return response.data
  const error = response?.error
  const message = error?.message ?? '요청 처리에 실패했습니다.'
  throw {message, code: error?.code}
}

const inflight = new Map<string, Promise<any>>()

const withInFlight = async <T>(key: string, request: () => Promise<T>): Promise<T> => {
  const existing = inflight.get(key)
  if (existing) {
    return existing as Promise<T>
  }
  const promise = request()
  inflight.set(key, promise)
  try {
    return await promise
  } finally {
    inflight.delete(key)
  }
}

const resolveBroadcastList = (payload: unknown): BroadcastListItem[] => {
  if (Array.isArray(payload)) {
    return payload as BroadcastListItem[]
  }
  const content = (payload as { content?: BroadcastListItem[] } | null)?.content
  if (Array.isArray(content)) return content
  const slice = (payload as { slice?: BroadcastListItem[] } | null)?.slice
  if (Array.isArray(slice)) return slice
  return []
}

export const fetchCategories = async (): Promise<BroadcastCategory[]> => {
  const { data } = await http.get<ApiResult<Array<{ categoryId: number; categoryName: string }>>>('/api/categories')
  const payload = ensureSuccess(data)
  return payload.map((category) => ({ id: category.categoryId, name: category.categoryName }))
}

export const fetchSellerProducts = async (): Promise<SellerProduct[]> => {
  const { data } = await http.get<
    ApiResult<Array<{ productId: number; productName: string; price: number; stockQty: number; safetyStock?: number; reservedBroadcastQty?: number; imageUrl?: string }>>
  >(
    '/api/seller/broadcasts/products',
  )
  const payload = ensureSuccess(data)
  return payload.map((item) => ({
    id: `prod-${item.productId}`,
    name: item.productName,
    option: '-',
    price: item.price,
    broadcastPrice: item.price,
    stock: item.stockQty,
    safetyStock: item.safetyStock ?? 0,
    reservedBroadcastQty: item.reservedBroadcastQty ?? 0,
    quantity: 1,
    thumb: item.imageUrl ?? '',
  }))
}

export const fetchReservationSlots = async (date: string): Promise<ReservationSlot[]> => {
  const { data } = await http.get<ApiResult<Array<{ slotDateTime: string; remainingCapacity: number; selectable: boolean }>>>(
    '/api/seller/broadcasts/reservation-slots',
    { params: { date } },
  )
  const payload = ensureSuccess(data)
  return payload
    .filter((slot) => slot.selectable)
    .map((slot) => ({
      time: slot.slotDateTime.slice(11, 16),
      remainingCapacity: slot.remainingCapacity,
    }))
}

export const fetchPublicBroadcastOverview = async (): Promise<BroadcastListItem[]> => {
  const { data } = await http.get<ApiResult<BroadcastAllResponse>>('/api/broadcasts', { params: { tab: 'ALL' } })
  const payload = ensureSuccess(data)
  return [...(payload.onAir ?? []), ...(payload.reserved ?? []), ...(payload.vod ?? [])].map((item) => ({
    ...item,
    startAt: item.startAt ?? '',
    endAt: ensureEndAt(item.startAt, item.endAt, item.status),
  }))
}

export const fetchPublicBroadcastDetail = async (broadcastId: number): Promise<BroadcastDetail> => {
  return withInFlight(`public-detail-${broadcastId}`, async () => {
    const { data } = await http.get<ApiResult<BroadcastDetail>>(`/api/broadcasts/${broadcastId}`)
    return ensureSuccess(data)
  })
}

export const fetchBroadcastProducts = async (broadcastId: number): Promise<BroadcastProductItem[]> => {
  const { data } = await http.get<
    ApiResult<
      Array<{
        productId: number
        name: string
        imageUrl?: string
        originalPrice?: number
        bpPrice: number
        originalCostPrice?: number
        bpQuantity: number
        stockQty?: number
        status: string
        isPinned?: boolean
        pinned?: boolean
      }>
    >
  >(`/api/broadcasts/${broadcastId}/products`)
  const payload = ensureSuccess(data)
  return payload.map((item) => ({
    id: String(item.productId),
    name: item.name,
    imageUrl: item.imageUrl ?? '',
    price: item.bpPrice,
    originalPrice: item.originalPrice ?? item.bpPrice,
    totalQty: item.bpQuantity,
    isPinned: item.isPinned ?? item.pinned ?? false,
    isSoldOut: item.status === 'SOLDOUT' || item.bpQuantity <= 0,
    stockQty: item.stockQty ?? item.bpQuantity,
  }))
}

export const fetchChatPermission = async (broadcastId: number, memberId?: number): Promise<boolean> => {
  const params = memberId ? { memberId } : undefined
  const { data } = await http.get<ApiResult<boolean>>(`/api/broadcasts/${broadcastId}/chat-permission`, { params })
  return ensureSuccess(data)
}

export const fetchBroadcastStats = async (broadcastId: number): Promise<BroadcastStats> => {
  return withInFlight(`broadcast-stats-${broadcastId}`, async () => {
    const { data } = await http.get<ApiResult<BroadcastStats>>(`/api/broadcasts/${broadcastId}/stats`)
    return ensureSuccess(data)
  })
}

export const joinBroadcast = async (broadcastId: number, viewerId?: string | null): Promise<string> => {
  const headers = viewerId ? { 'X-Viewer-Id': viewerId } : undefined
  const { data } = await http.post<ApiResult<string>>(`/api/broadcasts/${broadcastId}/join`, null, { headers })
  return ensureSuccess(data)
}

export const leaveBroadcast = async (broadcastId: number, viewerId?: string | null): Promise<void> => {
  const headers = viewerId ? { 'X-Viewer-Id': viewerId } : undefined
  const { data } = await http.post<ApiResult<void>>(`/api/broadcasts/${broadcastId}/leave`, null, {
    headers,
    params: viewerId ? { viewerId } : undefined,
  })
  ensureSuccess(data)
}

export const recordVodView = async (broadcastId: number, viewerId?: string | null): Promise<void> => {
  const headers = viewerId ? { 'X-Viewer-Id': viewerId } : undefined
  const { data } = await http.post<ApiResult<void>>(`/api/broadcasts/${broadcastId}/vod/view`, null, {
    headers,
    params: viewerId ? { viewerId } : undefined,
  })
  ensureSuccess(data)
}

export const toggleBroadcastLike = async (broadcastId: number): Promise<BroadcastLikeResponse> => {
  const { data } = await http.post<ApiResult<BroadcastLikeResponse>>(`/api/member/broadcasts/${broadcastId}/like`)
  return ensureSuccess(data)
}

export const fetchBroadcastLikeStatus = async (broadcastId: number): Promise<BroadcastLikeResponse> => {
  const { data } = await http.get<ApiResult<BroadcastLikeResponse>>(`/api/member/broadcasts/${broadcastId}/like-status`)
  return ensureSuccess(data)
}

export const reportBroadcast = async (broadcastId: number): Promise<BroadcastReportResponse> => {
  const { data } = await http.post<ApiResult<BroadcastReportResponse>>(`/api/member/broadcasts/${broadcastId}/report`)
  return ensureSuccess(data)
}

export const fetchSellerBroadcastReport = async (broadcastId: number): Promise<BroadcastResult> => {
  const { data } = await http.get<ApiResult<BroadcastResult>>(`/api/seller/broadcasts/${broadcastId}/report`)
  return ensureSuccess(data)
}

export const fetchAdminBroadcastReport = async (broadcastId: number): Promise<BroadcastResult> => {
  const { data } = await http.get<ApiResult<BroadcastResult>>(`/api/admin/broadcasts/${broadcastId}/report`)
  return ensureSuccess(data)
}

export const fetchSellerBroadcastDetail = async (broadcastId: number): Promise<BroadcastDetailResponse> => {
  return withInFlight(`seller-detail-${broadcastId}`, async () => {
    const { data } = await http.get<ApiResult<BroadcastDetailResponse>>(`/api/seller/broadcasts/${broadcastId}`)
    return ensureSuccess(data)
  })
}

export const startSellerBroadcast = async (broadcastId: number): Promise<string> => {
  const { data } = await http.post<ApiResult<string>>(`/api/seller/broadcasts/${broadcastId}/start`)
  return ensureSuccess(data)
}

export const startSellerRecording = async (broadcastId: number): Promise<void> => {
  const { data } = await http.post<ApiResult<void>>(`/api/seller/broadcasts/${broadcastId}/recording/start`)
  ensureSuccess(data)
}

export const endSellerBroadcast = async (broadcastId: number): Promise<void> => {
  const { data } = await http.post<ApiResult<void>>(`/api/seller/broadcasts/${broadcastId}/end`)
  return ensureSuccess(data)
}

export const fetchAdminBroadcastDetail = async (broadcastId: number): Promise<BroadcastDetailResponse> => {
  return withInFlight(`admin-detail-${broadcastId}`, async () => {
    const { data } = await http.get<ApiResult<BroadcastDetailResponse>>(`/api/admin/broadcasts/${broadcastId}`)
    return ensureSuccess(data)
  })
}

export const stopAdminBroadcast = async (broadcastId: number, reason: string) => {
  const { data } = await http.put<ApiResult<void>>(`/api/admin/broadcasts/${broadcastId}/stop`, { reason })
  return ensureSuccess(data)
}

export const cancelAdminBroadcast = async (broadcastId: number, reason: string) => {
  const { data } = await http.put<ApiResult<void>>(`/api/admin/broadcasts/${broadcastId}/cancel`, { reason })
  return ensureSuccess(data)
}

export const cancelSellerBroadcast = async (broadcastId: number) => {
  const { data } = await http.delete<ApiResult<void>>(`/api/seller/broadcasts/${broadcastId}`)
  return ensureSuccess(data)
}

export const pinSellerBroadcastProduct = async (broadcastId: number, productId: number) => {
  const { data } = await http.post<ApiResult<void>>(`/api/seller/broadcasts/${broadcastId}/pin/${productId}`)
  return ensureSuccess(data)
}

export const unpinSellerBroadcastProduct = async (broadcastId: number) => {
  const { data } = await http.delete<ApiResult<void>>(`/api/seller/broadcasts/${broadcastId}/pin`)
  return ensureSuccess(data)
}

export const fetchSellerStatistics = async (period: string): Promise<StatisticsResponse> => {
  const { data } = await http.get<ApiResult<StatisticsResponse>>('/api/seller/broadcasts/statistics', { params: { period } })
  return ensureSuccess(data)
}

export const fetchAdminStatistics = async (period: string): Promise<StatisticsResponse> => {
  const { data } = await http.get<ApiResult<StatisticsResponse>>('/api/admin/statistics', { params: { period } })
  return ensureSuccess(data)
}

export const fetchSanctionStatistics = async (period: string): Promise<SanctionStatisticsResponse> => {
  const { data } = await http.get<ApiResult<SanctionStatisticsResponse>>('/api/admin/sanctions/statistics', { params: { period } })
  return ensureSuccess(data)
}

export const sanctionSellerViewer = async (
  broadcastId: number,
  payload: { memberLoginId: string; status: 'MUTE' | 'OUT'; reason?: string; connectionId?: string },
): Promise<void> => {
  const { data } = await http.post<ApiResult<void>>(`/api/seller/broadcasts/${broadcastId}/sanctions`, {
    ...payload,
    actorType: 'SELLER',
  })
  return ensureSuccess(data)
}

export const sanctionAdminViewer = async (
  broadcastId: number,
  payload: { memberLoginId: string; status: 'MUTE' | 'OUT'; reason?: string; connectionId?: string },
): Promise<void> => {
  const { data } = await http.post<ApiResult<void>>(`/api/admin/broadcasts/${broadcastId}/sanctions`, {
    ...payload,
    actorType: 'ADMIN',
  })
  return ensureSuccess(data)
}

export const fetchRecentLiveChats = async (broadcastId: number, seconds = 60): Promise<LiveChatMessage[]> => {
  const { data } = await http.get<ApiResult<LiveChatMessage[]>>(`${apiBase}/livechats/${broadcastId}/recent`, { params: { seconds } })
  return ensureSuccess(data)
}

export const fetchMediaConfig = async (broadcastId: number): Promise<MediaConfig> => {
  const { data } = await http.get<ApiResult<MediaConfig>>(`/api/seller/broadcasts/${broadcastId}/media-config`)
  return ensureSuccess(data)
}

export const saveMediaConfig = async (broadcastId: number, payload: MediaConfig): Promise<void> => {
  const { data } = await http.put<ApiResult<void>>(`/api/seller/broadcasts/${broadcastId}/media-config`, payload)
  return ensureSuccess(data)
}

export const fetchSellerBroadcasts = async (params: {
  tab?: string
  statusFilter?: string
  sortType?: string
  categoryId?: number
  isPublic?: boolean
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}) => {
  const { data } = await http.get<ApiResult<{ content?: BroadcastListItem[]; slice?: BroadcastListItem[] }>>('/api/seller/broadcasts', { params })
  const payload = ensureSuccess(data)
  return resolveBroadcastList(payload)
}

export const fetchAdminBroadcasts = async (params: {
  tab?: string
  statusFilter?: string
  sortType?: string
  categoryId?: number
  isPublic?: boolean
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}) => {
  const { data } = await http.get<ApiResult<{ content?: BroadcastListItem[]; slice?: BroadcastListItem[] }>>('/api/admin/broadcasts', { params })
  const payload = ensureSuccess(data)
  return resolveBroadcastList(payload)
}

export const createBroadcast = async (payload: BroadcastPayload): Promise<number> => {
  const { data } = await http.post<ApiResult<number>>('/api/seller/broadcasts', payload)
  return ensureSuccess(data)
}

export const updateBroadcast = async (broadcastId: number, payload: BroadcastPayload): Promise<number> => {
  const { data } = await http.put<ApiResult<number>>(`/api/seller/broadcasts/${broadcastId}`, payload)
  return ensureSuccess(data)
}

export const updateSellerVodVisibility = async (broadcastId: number, status: 'PUBLIC' | 'PRIVATE'): Promise<string> => {
  const { data } = await http.put<ApiResult<string>>(`/api/seller/broadcasts/${broadcastId}/vod/visibility`, { status })
  return ensureSuccess(data)
}

export const deleteSellerVod = async (broadcastId: number) => {
  const { data } = await http.delete<ApiResult<void>>(`/api/seller/broadcasts/${broadcastId}/vod`)
  return ensureSuccess(data)
}

export const updateAdminVodVisibility = async (broadcastId: number, status: 'PUBLIC' | 'PRIVATE'): Promise<string> => {
  const { data } = await http.put<ApiResult<string>>(`/api/admin/broadcasts/${broadcastId}/vod/visibility`, { status })
  return ensureSuccess(data)
}

export const deleteAdminVod = async (broadcastId: number) => {
  const { data } = await http.delete<ApiResult<void>>(`/api/admin/broadcasts/${broadcastId}/vod`)
  return ensureSuccess(data)
}

export const uploadSellerImage = async (type: UploadImageType, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post<ApiResult<ImageUploadResponse>>(`/api/seller/uploads/${type}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return ensureSuccess(data)
}

export const deleteSellerImage = async (fileName: string) => {
  if (!fileName) return
  const { data } = await http.delete<ApiResult<string>>('/api/seller/uploads', { params: { fileName } })
  return ensureSuccess(data)
}
