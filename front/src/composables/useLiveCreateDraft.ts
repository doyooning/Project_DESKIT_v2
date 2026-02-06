import { getAuthUser, isSeller } from '../lib/auth'
import { resolveViewerId } from '../lib/live/viewer'
import { fetchSellerBroadcastDetail, type BroadcastDetailResponse } from '../lib/live/api'

export type LiveCreateProduct = {
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

export type LiveCreateDraft = {
  questions: Array<{ id: string; text: string }>
  title: string
  subtitle: string
  category: string
  notice: string
  date: string
  time: string
  thumb: string
  standbyThumb: string
  termsAgreed: boolean
  products: LiveCreateProduct[]
  reservationId?: string
}

export const DRAFT_KEY = 'deskit_seller_broadcast_draft_v3'
const DRAFT_RESTORE_KEY = 'deskit_seller_broadcast_draft_restore_v1'
const DRAFT_SCHEMA_VERSION = 1

type StoredDraft = {
  version: number
  ownerId: string
  savedAt: number
  data: LiveCreateDraft
}

const resolveSellerKey = ({ allowToken = false }: { allowToken?: boolean } = {}) => {
  const user = getAuthUser()
  if (user) {
    if (!isSeller()) return ''
    const resolvedFromUser = resolveViewerId(user)
    if (resolvedFromUser) return resolvedFromUser
    if (allowToken) {
      const resolvedFromToken = resolveViewerId(null)
      if (resolvedFromToken) return resolvedFromToken
    }
    return user.email?.trim() ?? ''
  }
  if (!allowToken) return ''
  return resolveViewerId(null) ?? ''
}

const getDraftStorage = () => sessionStorage
let workingDraft: LiveCreateDraft | null = null

const clearDraftStorage = () => {
  const storage = getDraftStorage()
  storage.removeItem(DRAFT_KEY)
  storage.removeItem(DRAFT_RESTORE_KEY)
  workingDraft = null
}

type DraftRestoreDecision = 'accepted' | 'declined'

export const getDraftRestoreDecision = (): DraftRestoreDecision | null => {
  const stored = getDraftStorage().getItem(DRAFT_RESTORE_KEY)
  if (stored === 'accepted' || stored === 'declined') return stored
  return null
}

export const setDraftRestoreDecision = (decision: DraftRestoreDecision) => {
  getDraftStorage().setItem(DRAFT_RESTORE_KEY, decision)
}

export const clearDraftRestoreDecision = () => {
  getDraftStorage().removeItem(DRAFT_RESTORE_KEY)
}


const createId = () => `q-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

const createQuestion = (text: string) => ({ id: createId(), text })

const mapQuestions = (seeds: string[]) => (seeds.length ? seeds : ['']).map((text) => createQuestion(text))

export const createDefaultQuestions = () => mapQuestions([])

export const createEmptyDraft = (): LiveCreateDraft => ({
  questions: createDefaultQuestions(),
  title: '',
  subtitle: '',
  category: '',
  notice: '',
  date: '',
  time: '',
  thumb: '',
  standbyThumb: '',
  termsAgreed: false,
  products: [],
  reservationId: undefined,
})

const parseStoredDraft = (raw: string | null): StoredDraft | null => {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as StoredDraft
    if (!parsed || typeof parsed !== 'object') return null
    if (parsed.version !== DRAFT_SCHEMA_VERSION) return null
    if (!parsed.ownerId) return null
    if (!parsed.data || typeof parsed.data !== 'object') return null
    return parsed
  } catch (error) {
    console.error('Failed to parse draft', error)
    return null
  }
}

const hasTextValue = (value: unknown) => typeof value === 'string' && value.trim().length > 0

const hasDraftContent = (draft: LiveCreateDraft) => {
  const hasQuestions = draft.questions.some((question) => hasTextValue(question.text))
  const hasProducts = draft.products.length > 0
  const hasText =
    hasTextValue(draft.title) ||
    hasTextValue(draft.subtitle) ||
    hasTextValue(draft.category) ||
    hasTextValue(draft.notice) ||
    hasTextValue(draft.date) ||
    hasTextValue(draft.time) ||
    hasTextValue(draft.thumb) ||
    hasTextValue(draft.standbyThumb)
  const hasReservation = hasTextValue(draft.reservationId)
  return hasQuestions || hasProducts || hasText || hasReservation || draft.termsAgreed
}

window.addEventListener('deskit-user-updated', () => {
  const user = getAuthUser()
  if (!user) {
    clearDraftStorage()
    return
  }
  const ownerId = resolveSellerKey({ allowToken: true })
  const stored = parseStoredDraft(getDraftStorage().getItem(DRAFT_KEY))
  if (!ownerId || (stored && stored.ownerId !== ownerId)) {
    clearDraftStorage()
  }
})

const normalizeDraft = (payload: LiveCreateDraft): LiveCreateDraft => {
  const { cueTitle: _ignoredCueTitle, cueNotes: _ignoredCueNotes, ...rest } = payload as Record<string, any>

  return {
    ...createEmptyDraft(),
    ...rest,
    questions: Array.isArray(rest.questions)
      ? rest.questions
          .filter((item: any) => item && typeof item.id === 'string' && typeof item.text === 'string')
          .map((item: any) => ({ id: item.id, text: item.text }))
      : createEmptyDraft().questions,
    products: Array.isArray(rest.products)
      ? rest.products
          .filter((item: any) => item && typeof item.id === 'string')
          .map((item: any) => ({
            id: item.id,
            name: item.name ?? '',
            option: item.option ?? '',
            price: typeof item.price === 'number' ? item.price : 0,
            broadcastPrice: typeof item.broadcastPrice === 'number' ? item.broadcastPrice : 0,
            stock: typeof item.stock === 'number' ? item.stock : 0,
            safetyStock: typeof item.safetyStock === 'number' ? item.safetyStock : 0,
            quantity: typeof item.quantity === 'number' ? item.quantity : 1,
            thumb: item.thumb ?? '',
          }))
      : [],
  }
}

export const loadWorkingDraft = (): LiveCreateDraft | null => {
  if (!workingDraft) return null
  return normalizeDraft(workingDraft)
}

export const saveWorkingDraft = (draft: LiveCreateDraft) => {
  workingDraft = normalizeDraft(draft)
}

export const clearWorkingDraft = () => {
  workingDraft = null
}

export const loadDraft = (): LiveCreateDraft | null => {
  const ownerId = resolveSellerKey()
  if (!ownerId) return null
  const stored = parseStoredDraft(getDraftStorage().getItem(DRAFT_KEY))
  if (!stored) {
    clearDraftStorage()
    return null
  }
  if (stored.ownerId !== ownerId) {
    clearDraftStorage()
    return null
  }
  const normalized = normalizeDraft(stored.data)
  if (!hasDraftContent(normalized)) {
    clearDraftStorage()
    return null
  }
  return normalized
}

export const saveDraft = (draft: LiveCreateDraft) => {
  const ownerId = resolveSellerKey()
  if (!ownerId) return
  if (!hasDraftContent(draft)) {
    clearDraftStorage()
    return
  }
  const payload: StoredDraft = {
    version: DRAFT_SCHEMA_VERSION,
    ownerId,
    savedAt: Date.now(),
    data: draft,
  }
  getDraftStorage().setItem(DRAFT_KEY, JSON.stringify(payload))
}

export const clearDraft = () => {
  clearDraftStorage()
}

const formatReservationDate = (scheduledAt?: string) => {
  if (!scheduledAt) return { date: '', time: '' }
  const normalized = scheduledAt.replace('T', ' ')
  const [datePart, timePart] = normalized.split(' ')
  return {
    date: datePart?.replace(/\./g, '-') ?? '',
    time: timePart?.slice(0, 5) ?? '',
  }
}

const mapReservationProducts = (detail: BroadcastDetailResponse) =>
  (detail.products ?? []).map((item) => ({
    id: `prod-${item.productId}`,
    name: item.name,
    option: item.name,
    price: item.originalPrice ?? 0,
    broadcastPrice: item.bpPrice ?? item.originalPrice ?? 0,
    stock: item.stockQty ?? item.bpQuantity ?? 0,
    safetyStock: item.safetyStock ?? 0,
    quantity: item.bpQuantity ?? 1,
    thumb: item.imageUrl ?? '',
  }))

const mapReservationQuestions = (detail: BroadcastDetailResponse) =>
  mapQuestions((detail.qcards ?? []).map((card) => card.question))

export const buildDraftFromReservation = async (reservationId: string): Promise<LiveCreateDraft> => {
  try {
    const detail = await fetchSellerBroadcastDetail(Number(reservationId))
    const { date, time } = formatReservationDate(detail.scheduledAt)

    return {
      ...createEmptyDraft(),
      title: detail.title ?? '',
      subtitle: detail.sellerName ?? '',
      category: detail.categoryId ? String(detail.categoryId) : '',
      notice: detail.notice ?? '',
      date,
      time,
      thumb: detail.thumbnailUrl ?? '',
      standbyThumb: detail.waitScreenUrl ?? '',
      products: mapReservationProducts(detail),
      questions: mapReservationQuestions(detail),
      reservationId,
    }
  } catch (error) {
    console.error('Failed to load reservation draft', error)
    return createEmptyDraft()
  }
}
