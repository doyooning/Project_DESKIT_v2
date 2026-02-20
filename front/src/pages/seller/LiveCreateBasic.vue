<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import PageContainer from '../../components/PageContainer.vue'
import PageHeader from '../../components/PageHeader.vue'
import LiveImageCropModal from '../../components/LiveImageCropModal.vue'
import {
  buildDraftFromReservation,
  clearDraft,
  clearDraftRestoreDecision,
  createEmptyDraft,
  type LiveCreateDraft,
  type LiveCreateProduct,
  loadWorkingDraft,
  loadDraft,
  saveDraft,
  saveWorkingDraft,
  clearWorkingDraft,
  getDraftRestoreDecision,
  setDraftRestoreDecision,
} from '../../composables/useLiveCreateDraft'
import {
  type BroadcastCategory,
  createBroadcast,
  deleteSellerImage,
  fetchCategories,
  fetchReservationSlots,
  fetchSellerBroadcasts,
  fetchSellerProducts,
  type ReservationSlot,
  type UploadImageType,
  uploadSellerImage,
  updateBroadcast,
} from '../../lib/live/api'
import {normalizeBroadcastStatus} from '../../lib/broadcastStatus'
import {parseLiveDate} from '../../lib/live/utils'

const router = useRouter()
const route = useRoute()

const draft = ref<LiveCreateDraft>(createEmptyDraft())
const productSearch = ref('')
const thumbError = ref('')
const standbyError = ref('')
const error = ref('')
const showTermsModal = ref(false)
const showProductModal = ref(false)
const modalProducts = ref<LiveCreateProduct[]>([])
const sellerProducts = ref<LiveCreateProduct[]>([])
const categories = ref<BroadcastCategory[]>([])
const reservationSlots = ref<ReservationSlot[]>([])
const reservedTimes = ref<string[]>([])
const activeDate = ref('')
const nowTick = ref(Date.now())
let nowTickTimer: number | null = null
const cropperOpen = ref(false)
const cropperSource = ref('')
const cropperFileName = ref('')
const cropTarget = ref<'thumb' | 'standby' | null>(null)
const cropperApplied = ref(false)
const thumbInputRef = ref<HTMLInputElement | null>(null)
const standbyInputRef = ref<HTMLInputElement | null>(null)
const thumbName = ref('')
const standbyName = ref('')
const thumbStoredName = ref('')
const standbyStoredName = ref('')

const reservationId = computed(() => {
  const queryValue = route.query.reservationId
  if (Array.isArray(queryValue)) return queryValue[0] ?? ''
  return typeof queryValue === 'string' ? queryValue : ''
})
const isEditMode = computed(() => route.query.mode === 'edit' && !!reservationId.value)
const modalCount = computed(() => modalProducts.value.length)
const extractFileName = (source: string) => {
  if (!source || source.startsWith('data:')) return ''
  const path = source.split('?')[0] ?? ''
  const segments = path.split('/')
  const last = segments[segments.length - 1] ?? ''
  return decodeURIComponent(last)
}
const extractStoredName = (source: string) => {
  if (!source) return ''
  try {
    const url = new URL(source)
    const path = url.pathname?.replace(/^\//, '') ?? ''
    return path
  } catch {
    return source.replace(/^\//, '')
  }
}
const thumbDisplayName = computed(() => thumbName.value || extractFileName(draft.value.thumb))
const standbyDisplayName = computed(() => standbyName.value || extractFileName(draft.value.standbyThumb))

const availableProducts = computed(() => sellerProducts.value)

const quantityInputs = ref<Record<string, string>>({})

const filteredProducts = computed(() => {
  const q = productSearch.value.trim().toLowerCase()
  if (!q) return availableProducts.value
  return availableProducts.value.filter((product) => {
    const name = product.name.toLowerCase()
    const option = product.option.toLowerCase()
    return name.includes(q) || option.includes(q)
  })
})

const isSelected = (productId: string, source: LiveCreateProduct[] = draft.value.products) =>
    source.some((item) => item.id === productId)

const resolveMaxQuantity = (product: LiveCreateProduct) => {
  const stock = Number.isFinite(product.stock) ? product.stock : 0
  const safetyStock = Number.isFinite(product.safetyStock) ? product.safetyStock : 0
  const reservedQty = typeof product.reservedBroadcastQty === 'number' ? product.reservedBroadcastQty : 0
  return Math.max(stock - safetyStock - reservedQty, 0)
}

const resolveMinQuantity = (product: LiveCreateProduct) => (resolveMaxQuantity(product) > 0 ? 1 : 0)

const syncQuantityInputs = (products: LiveCreateProduct[]) => {
  const existing = quantityInputs.value
  const next: Record<string, string> = {}
  products.forEach((product) => {
    const existingValue = existing[product.id]
    next[product.id] = existingValue ?? String(product.quantity)
  })
  quantityInputs.value = next
}

const syncDraftProductStock = () => {
  if (!draft.value.products.length || !sellerProducts.value.length) return
  const productMap = new Map(sellerProducts.value.map((product) => [product.id, product]))
  draft.value.products = draft.value.products.map((product) => {
    const source = productMap.get(product.id)
    if (!source) return product
    return {
      ...product,
      stock: source.stock,
      safetyStock: source.safetyStock,
      reservedBroadcastQty: source.reservedBroadcastQty,
      thumb: product.thumb || source.thumb,
    }
  })
}

const clampProductQuantity = (product: LiveCreateProduct, value?: number) => {
  const maxQuantity = resolveMaxQuantity(product)
  const minQuantity = resolveMinQuantity(product)
  const rawValue = typeof value === 'number' && Number.isFinite(value) ? value : product.quantity
  const normalized = typeof rawValue === 'number' && Number.isFinite(rawValue) ? rawValue : minQuantity
  const nextValue = maxQuantity > 0
      ? Math.min(Math.max(normalized, minQuantity), maxQuantity)
      : minQuantity
  return { ...product, quantity: nextValue }
}

const normalizeQuantityInput = (product: LiveCreateProduct, rawValue: string) => {
  const maxQuantity = resolveMaxQuantity(product)
  const minQuantity = resolveMinQuantity(product)
  const parsed = Number(rawValue)
  if (!rawValue.trim() || !Number.isFinite(parsed)) {
    return { value: minQuantity, adjusted: true, reason: 'min' }
  }
  if (parsed < minQuantity) {
    return { value: minQuantity, adjusted: true, reason: 'min' }
  }
  if (parsed > maxQuantity) {
    return { value: maxQuantity, adjusted: true, reason: 'max' }
  }
  return { value: parsed, adjusted: false, reason: 'ok' }
}

const addProduct = (product: LiveCreateProduct, target: LiveCreateProduct[]) => {
  if (!isSelected(product.id, target) && target.length >= 10) {
    error.value = '상품은 최대 10개까지 등록할 수 있습니다.'
    return target
  }
  if (isSelected(product.id, target)) return target
  return [...target, clampProductQuantity(product)]
}

const removeProduct = (productId: string, target: LiveCreateProduct[]) => target.filter((item) => item.id !== productId)

const toggleProductInModal = (product: LiveCreateProduct) => {
  modalProducts.value = isSelected(product.id, modalProducts.value)
      ? removeProduct(product.id, modalProducts.value)
      : addProduct(product, modalProducts.value)
}

const updateProductPrice = (productId: string, value: number) => {
  draft.value.products = draft.value.products.map((product) =>
      product.id === productId ? { ...product, broadcastPrice: value < 0 ? 0 : value } : product,
  )
}

const updateProductQuantityInput = (productId: string, value: string) => {
  quantityInputs.value = { ...quantityInputs.value, [productId]: value }
}

const commitProductQuantity = (productId: string) => {
  draft.value.products = draft.value.products.map((product) => {
    if (product.id !== productId) return product
    const rawValue = quantityInputs.value[productId] ?? String(product.quantity)
    const normalized = normalizeQuantityInput(product, rawValue)
    if (normalized.adjusted) {
      if (normalized.reason === 'max') {
        alert('판매 수량이 최대 가능 수량을 초과하여 최대 수량으로 변경되었습니다.')
      } else if (normalized.reason === 'min') {
        alert('판매 수량이 최소 수량보다 작아 최소 수량으로 변경되었습니다.')
      }
    }
    const nextProduct = { ...product, quantity: normalized.value }
    quantityInputs.value = { ...quantityInputs.value, [productId]: String(normalized.value) }
    return nextProduct
  })
}

const normalizeAllProductQuantities = () => {
  draft.value.products = draft.value.products.map((product) => {
    const rawValue = quantityInputs.value[product.id] ?? String(product.quantity)
    const normalized = normalizeQuantityInput(product, rawValue)
    if (normalized.adjusted) {
      if (normalized.reason === 'max') {
        alert('판매 수량이 최대 가능 수량을 초과하여 최대 수량으로 변경되었습니다.')
      } else if (normalized.reason === 'min') {
        alert('판매 수량이 최소 수량보다 작아 최소 수량으로 변경되었습니다.')
      }
    }
    quantityInputs.value = { ...quantityInputs.value, [product.id]: String(normalized.value) }
    return { ...product, quantity: normalized.value }
  })
}

const syncDraft = () => {
  const trimmedQuestions = draft.value.questions.map((q) => ({ ...q, text: q.text.trim() })).filter((q) => q.text.length > 0)
  const shouldUpdateQuestions =
      trimmedQuestions.length !== draft.value.questions.length ||
      trimmedQuestions.some((item, index) => item.text !== draft.value.questions[index]?.text)

  if (shouldUpdateQuestions) {
    draft.value.questions = trimmedQuestions
  }

  saveWorkingDraft({
    ...draft.value,
    title: draft.value.title.trim(),
    subtitle: draft.value.subtitle?.trim() ?? '',
    category: draft.value.category.trim(),
    notice: draft.value.notice.trim(),
    questions: trimmedQuestions,
    reservationId: reservationId.value || draft.value.reservationId,
  })
}

const restoreDraft = async () => {
  let baseDraft = createEmptyDraft()
  const workingDraft = loadWorkingDraft()
  const hasMatchingWorkingDraft = !!workingDraft && (!isEditMode.value || workingDraft.reservationId === reservationId.value)
  if (hasMatchingWorkingDraft) {
    baseDraft = { ...createEmptyDraft(), ...workingDraft }
  } else {
    const savedDraft = loadDraft()
    if (!isEditMode.value && savedDraft && (!savedDraft.reservationId || savedDraft.reservationId === reservationId.value)) {
      const decision = getDraftRestoreDecision()
      if (decision === 'accepted') {
        baseDraft = { ...createEmptyDraft(), ...savedDraft }
      } else if (decision === 'declined') {
        clearDraft()
      } else {
        const shouldRestore = window.confirm('이전에 작성 중인 내용을 불러올까요?')
        if (shouldRestore) {
          setDraftRestoreDecision('accepted')
          baseDraft = { ...createEmptyDraft(), ...savedDraft }
        } else {
          setDraftRestoreDecision('declined')
          clearDraft()
        }
      }
    }
  }

  const reservationDraft = isEditMode.value
      ? {
        ...baseDraft,
        ...(hasMatchingWorkingDraft ? {} : await buildDraftFromReservation(reservationId.value)),
        reservationId: reservationId.value,
      }
      : baseDraft

  draft.value = reservationDraft
  syncDraftProductStock()
  draft.value.products = draft.value.products.map((product) => clampProductQuantity(product))
  modalProducts.value = draft.value.products.map((p) => ({ ...p }))
  syncQuantityInputs(draft.value.products)
}

const openCropper = (file: File, target: 'thumb' | 'standby') => {
  const reader = new FileReader()
  reader.onload = () => {
    cropperSource.value = typeof reader.result === 'string' ? reader.result : ''
    cropperFileName.value = file.name
    cropTarget.value = target
    cropperApplied.value = false
    cropperOpen.value = true
  }
  reader.readAsDataURL(file)
}

const handleThumbUpload = (event: Event) => {
  thumbError.value = ''
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    thumbError.value = '이미지 파일만 업로드할 수 있습니다.'
    input.value = ''
    return
  }
  openCropper(file, 'thumb')
}

const handleStandbyUpload = (event: Event) => {
  standbyError.value = ''
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    standbyError.value = '이미지 파일만 업로드할 수 있습니다.'
    input.value = ''
    return
  }
  openCropper(file, 'standby')
}

const applyCroppedImage = (payload: { dataUrl: string; fileName: string }) => {
  const target = cropTarget.value
  if (!target) return
  const uploadTarget = target === 'thumb' ? 'THUMBNAIL' : 'WAIT_SCREEN'
  const existingUrl = target === 'thumb' ? draft.value.thumb : draft.value.standbyThumb
  const existingStored = target === 'thumb' ? thumbStoredName.value : standbyStoredName.value
  const prevStoredName = existingStored || extractStoredName(existingUrl)
  const [header, base64] = payload.dataUrl.split(',')
  if (!header || !base64) return
  const mimeMatch = header.match(/data:(.*?);base64/)
  const mimeType = mimeMatch?.[1] ?? 'image/jpeg'
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  const file = new File([bytes], payload.fileName, { type: mimeType })
  uploadSellerImage(uploadTarget as UploadImageType, file)
      .then((response) => {
        cropperApplied.value = true
        if (target === 'thumb') {
          draft.value.thumb = response.fileUrl
          thumbName.value = response.originalFileName
          thumbStoredName.value = response.storedFileName
        }
        if (target === 'standby') {
          draft.value.standbyThumb = response.fileUrl
          standbyName.value = response.originalFileName
          standbyStoredName.value = response.storedFileName
        }
        if (prevStoredName && prevStoredName !== response.storedFileName) {
          void deleteSellerImage(prevStoredName)
        }
      })
      .catch(() => {
        error.value = '이미지 업로드에 실패했습니다.'
      })
}

const clearThumb = () => {
  const storedName = thumbStoredName.value || extractStoredName(draft.value.thumb)
  if (storedName) {
    void deleteSellerImage(storedName)
  }
  draft.value.thumb = ''
  thumbName.value = ''
  thumbStoredName.value = ''
  if (thumbInputRef.value) thumbInputRef.value.value = ''
}

const clearStandby = () => {
  const storedName = standbyStoredName.value || extractStoredName(draft.value.standbyThumb)
  if (storedName) {
    void deleteSellerImage(storedName)
  }
  draft.value.standbyThumb = ''
  standbyName.value = ''
  standbyStoredName.value = ''
  if (standbyInputRef.value) standbyInputRef.value.value = ''
}

const handleThumbError = () => {
  thumbError.value = '이미지를 불러오지 못했습니다.'
  clearThumb()
}

const handleStandbyError = () => {
  standbyError.value = '이미지를 불러오지 못했습니다.'
  clearStandby()
}

const resetCropperState = () => {
  cropperSource.value = ''
  cropperFileName.value = ''
  cropTarget.value = null
  cropperApplied.value = false
}

const clearInputForTarget = (target: 'thumb' | 'standby') => {
  if (target === 'thumb' && thumbInputRef.value) {
    thumbInputRef.value.value = ''
  }
  if (target === 'standby' && standbyInputRef.value) {
    standbyInputRef.value.value = ''
  }
}

const getErrorMessage = (error: unknown, fallback: string) => {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message.trim()) {
      return message
    }
  }
  return fallback
}

const getErrorStatus = (error: unknown) => {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { status?: number } }).response
    if (response && typeof response.status === 'number') {
      return response.status
    }
  }
  return null
}

const submit = () => {
  error.value = ''
  thumbError.value = ''
  standbyError.value = ''

  draft.value.questions = draft.value.questions.map((q) => ({
    ...q,
    text: q.text.trim()
  })).filter((q) => q.text.length > 0)

  if (!draft.value.title.trim() || !draft.value.category || !draft.value.date || !draft.value.time) {
    error.value = '방송 제목, 카테고리, 일정을 입력해주세요.'
    return
  }

  if (!draft.value.products.length) {
    error.value = '최소 1개의 판매 상품을 등록해주세요.'
    return
  }

  if (!draft.value.thumb) {
    error.value = '썸네일을 등록 해주세요'
    return
  }

  normalizeAllProductQuantities()

  const invalidProduct = draft.value.products.find((product) => {
    const maxQuantity = resolveMaxQuantity(product)
    return maxQuantity < 1 || product.quantity < 1 || product.quantity > maxQuantity
  })
  if (invalidProduct) {
    error.value = '판매 수량을 재고 범위 내에서 선택해주세요.'
    return
  }

  if (!draft.value.termsAgreed) {
    error.value = '약관에 동의해주세요.'
    return
  }

  if (!timeOptions.value.includes(draft.value.time)) {
    alert('선택한 시간대의 예약이 마감되었습니다. 다른 시간대를 선택해주세요.')
    void reloadReservationSlots(draft.value.date)
    return
  }

  const confirmed = window.confirm(isEditMode.value ? '예약 수정을 진행할까요?' : '방송 등록을 진행할까요?')
  if (!confirmed) return

  const scheduledAt = `${draft.value.date} ${draft.value.time}:00`
  const payload = {
    title: draft.value.title.trim(),
    notice: draft.value.notice.trim(),
    categoryId: Number(draft.value.category),
    scheduledAt,
    thumbnailUrl: draft.value.thumb,
    waitScreenUrl: draft.value.standbyThumb || null,
    broadcastLayout: 'FULL',
    products: draft.value.products.map((product) => ({
      productId: Number(product.id.replace('prod-', '')),
      bpPrice: product.broadcastPrice,
      bpQuantity: product.quantity,
    })),
    qcards: draft.value.questions.map((q) => ({ question: q.text.trim() })).filter((q) => q.question.length > 0),
  }

  const request = isEditMode.value
      ? updateBroadcast(Number(reservationId.value), payload)
      : createBroadcast(payload)

  request
      .then((broadcastId) => {
        clearDraft()
        alert(isEditMode.value ? '예약 수정이 완료되었습니다.' : '방송 등록이 완료되었습니다.')
        const id = broadcastId ?? reservationId.value
        const redirectPath = isEditMode.value
            ? `/seller/broadcasts/reservations/${id}`
            : '/seller/live?tab=scheduled'
        router.push(redirectPath).catch(() => {})
      })
      .catch((apiError) => {
        if (apiError?.code === 'B005') {
          alert('선택한 시간대의 예약이 마감되었습니다. 다른 시간대를 선택해주세요.')
          void reloadReservationSlots(draft.value.date)
          return
        }
        error.value = getErrorMessage(apiError, '방송 등록에 실패했습니다.')
      })
}

const goPrev = () => {
  router.push({ path: '/seller/live/create', query: route.query }).catch(() => {})
}

const cancel = () => {
  const ok = window.confirm('작성 중인 내용을 취소하시겠어요?')
  if (!ok) return
  saveDraft(draft.value)
  clearDraftRestoreDecision()
  clearWorkingDraft()
  const redirect = isEditMode.value && reservationId.value
      ? `/seller/broadcasts/reservations/${reservationId.value}`
      : '/seller/live?tab=scheduled'
  router.push(redirect).catch(() => {})
}

const openProductModal = () => {
  modalProducts.value = draft.value.products.map((p) => ({ ...p }))
  productSearch.value = ''
  showProductModal.value = true
}

const cancelProductSelection = () => {
  modalProducts.value = draft.value.products.map((p) => ({ ...p }))
  showProductModal.value = false
}

const saveProductSelection = () => {
  draft.value.products = modalProducts.value.map((p) => clampProductQuantity(p))
  syncQuantityInputs(draft.value.products)
  showProductModal.value = false
  alert('상품 선택이 저장되었습니다.')
}

const confirmRemoveProduct = (productId: string) => {
  const ok = window.confirm('이 상품을 리스트에서 제거하시겠어요?')
  if (ok) {
    draft.value.products = removeProduct(productId, draft.value.products)
  }
}

const formatLocalDate = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const addDays = (date: Date, days: number) => {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

const minDate = computed(() => formatLocalDate(new Date()))

const maxDate = computed(() => formatLocalDate(addDays(new Date(), 15)))

const isPastSlotForToday = (date: string, time: string) => {
  if (!date || date !== minDate.value) return false
  const [hourText, minuteText] = time.split(':')
  const hour = Number(hourText)
  const minute = Number(minuteText)
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return false
  const slotDateTime = new Date(`${date}T00:00:00`)
  slotDateTime.setHours(hour, minute, 0, 0)
  return slotDateTime.getTime() <= nowTick.value
}

const getAvailableTimes = (date: string) => {
  if (!date) return []
  return reservationSlots.value
    .filter((slot) => !reservedTimes.value.includes(slot.time))
    .filter((slot) => !isPastSlotForToday(date, slot.time))
    .map((slot) => slot.time)
}

const normalizeCategorySelection = () => {
  if (!draft.value.category) return
  const current = categories.value.find((category) => category.id.toString() === draft.value.category)
  if (current) return
  const matched = categories.value.find((category) => category.name === draft.value.category)
  if (matched) draft.value.category = matched.id.toString()
}

const loadCategories = async () => {
  try {
    categories.value = await fetchCategories()
    normalizeCategorySelection()
  } catch (apiError) {
    error.value = getErrorMessage(apiError, '카테고리를 불러오지 못했습니다.')
  }
}

const loadProducts = async () => {
  try {
    sellerProducts.value = await fetchSellerProducts()
    syncDraftProductStock()
  } catch (apiError) {
    if (getErrorStatus(apiError) === 404) {
      sellerProducts.value = []
      return
    }
    error.value = getErrorMessage(apiError, '상품 목록을 불러오지 못했습니다.')
  }
}

const reloadReservationSlots = async (date: string) => {
  if (!date) return
  try {
    reservationSlots.value = await fetchReservationSlots(date)
    reservedTimes.value = []
    try {
      const reservedList = await fetchSellerBroadcasts({
        tab: 'RESERVED',
        size: 200,
        startDate: date,
        endDate: date,
      })
      const blocked = reservedList
        .filter((item) => normalizeBroadcastStatus(item.status) !== 'CANCELED')
        .filter((item) => !isEditMode.value || String(item.broadcastId) !== reservationId.value)
        .map((item) => item.startAt)
        .filter((value): value is string => Boolean(value))
        .map((value) => {
          const parsed = parseLiveDate(value)
          if (Number.isNaN(parsed.getTime())) return null
          return `${String(parsed.getHours()).padStart(2, '0')}:${String(parsed.getMinutes()).padStart(2, '0')}`
        })
        .filter((value): value is string => Boolean(value))
      reservedTimes.value = Array.from(new Set(blocked))
    } catch {
      reservedTimes.value = []
    }
    const availableTimes = getAvailableTimes(date)
    if (draft.value.time && !availableTimes.includes(draft.value.time)) {
      draft.value.time = ''
    }
  } catch (apiError) {
    error.value = getErrorMessage(apiError, '예약 가능 시간을 불러오지 못했습니다.')
  }
}

const timeOptions = computed(() => {
  return getAvailableTimes(draft.value.date)
})

watch(
    () => [isEditMode.value, reservationId.value],
    () => {
      restoreDraft()
    },
    { immediate: true },
)

watch(
    () => draft.value.date,
    (value) => {
      if (!value) return
      if (value < minDate.value) {
        draft.value.date = minDate.value
        return
      }
      if (value > maxDate.value) {
        draft.value.date = maxDate.value
        return
      }
      if (value !== activeDate.value) {
        activeDate.value = value
        void reloadReservationSlots(value)
      }
    },
)

watch(cropperOpen, (open, wasOpen) => {
  if (!open && wasOpen) {
    if (!cropperApplied.value && cropTarget.value) {
      clearInputForTarget(cropTarget.value)
    }
    resetCropperState()
  }
})

onMounted(async () => {
  await loadCategories()
  await loadProducts()
  nowTickTimer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, 30_000)
  draft.value.products = draft.value.products.map((product) => clampProductQuantity(product))
  syncQuantityInputs(draft.value.products)
  if (draft.value.date) {
    activeDate.value = draft.value.date
    void reloadReservationSlots(draft.value.date)
  }
})

watch(
    () => draft.value.products.map((product) => product.id),
    () => {
      syncQuantityInputs(draft.value.products)
    },
)

watch(timeOptions, (options) => {
  if (draft.value.time && !options.includes(draft.value.time)) {
    draft.value.time = ''
  }
})

watch(
    draft,
    () => {
      syncDraft()
    },
    { deep: true },
)

onBeforeUnmount(() => {
  if (nowTickTimer !== null) {
    window.clearInterval(nowTickTimer)
    nowTickTimer = null
  }
})
</script>

<template>
  <PageContainer>
    <PageHeader :eyebrow="isEditMode ? 'DESKIT' : 'DESKIT'" :title="isEditMode ? '예약 수정 - 기본 정보' : '방송 등록 - 기본 정보'" />
    <LiveImageCropModal
        v-model="cropperOpen"
        :image-src="cropperSource"
        :file-name="cropperFileName"
        @confirm="applyCroppedImage"
    />
    <section class="create-card ds-surface">
      <div class="step-meta">
        <span class="step-indicator">2 / 2 단계</span>
        <button type="button" class="btn ghost" @click="goPrev">이전</button>
      </div>
      <label class="field">
        <span class="field__label">방송 제목</span>
        <input v-model="draft.title" type="text" maxlength="30" placeholder="예: 홈오피스 라이브" />
        <span class="field__hint">{{ draft.title.length }}/30</span>
      </label>
      <div class="field-grid">
        <label class="field">
          <span class="field__label">카테고리</span>
          <select v-model="draft.category">
            <option value="" disabled>카테고리를 선택하세요</option>
            <option v-for="category in categories" :key="category.id" :value="category.id.toString()">
              {{ category.name }}
            </option>
          </select>
        </label>
      </div>
      <label class="field">
        <span class="field__label">공지사항</span>
        <textarea
            v-model="draft.notice"
            rows="3"
            maxlength="50"
            placeholder="시청자에게 안내할 공지를 입력하세요 (최대 50자)"
        ></textarea>
        <span class="field__hint">{{ draft.notice.length }}/50</span>
      </label>
      <div class="field-grid">
        <label class="field">
          <span class="field__label">방송 날짜</span>
          <input v-model="draft.date" type="date" :min="minDate" :max="maxDate" />
        </label>
        <label class="field">
          <span class="field__label">방송 시간</span>
          <select v-model="draft.time">
            <option value="" disabled>시간을 선택하세요</option>
            <option v-for="time in timeOptions" :key="time" :value="time">{{ time }}</option>
          </select>
        </label>
      </div>
      <div class="section-block">
        <div class="section-head">
          <h3>판매 상품 등록</h3>
          <span class="count-pill">선택 {{ draft.products.length }}개</span>
        </div>
        <div class="product-search-bar">
          <button type="button" class="btn" @click="openProductModal">상품 선택</button>
          <span class="search-hint">최소 1개, 최대 10개 선택</span>
        </div>
        <div v-if="draft.products.length" class="product-table-wrap">
          <table>
            <thead>
            <tr>
              <th>상품명</th>
              <th>정가</th>
              <th>방송 할인가</th>
              <th>판매 수량</th>
              <th>재고</th>
              <th></th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="product in draft.products" :key="product.id">
              <td>
                <div class="product-cell">
                  <div class="thumb" v-if="product.thumb">
                    <img :src="product.thumb" :alt="product.name" />
                  </div>
                  <div class="product-text">
                    <strong>{{ product.name }}</strong>
<!--                    <span class="product-option__meta">{{ product.option }}</span>-->
                  </div>
                </div>
              </td>
              <td class="numeric">{{ product.price.toLocaleString() }}원</td>
              <td>
                <input
                    class="table-input"
                    type="number"
                    min="0"
                    :value="product.broadcastPrice"
                    @input="updateProductPrice(product.id, Number(($event.target as HTMLInputElement).value))"
                />
              </td>
              <td>
                <input
                    class="table-input"
                    type="number"
                    :min="resolveMinQuantity(product)"
                    :max="resolveMaxQuantity(product)"
                    :value="quantityInputs[product.id] ?? product.quantity"
                    @input="updateProductQuantityInput(product.id, ($event.target as HTMLInputElement).value)"
                    @blur="commitProductQuantity(product.id)"
                />
              </td>
              <td class="numeric">{{ product.stock }}</td>
              <td>
                <button type="button" class="btn ghost" @click="confirmRemoveProduct(product.id)">
                  제거
                </button>
              </td>
            </tr>
            </tbody>
          </table>
          <p class="table-hint">{{ draft.products.length }}/10 개 선택됨 (최소 1개 필수)</p>
        </div>
      </div>
      <div class="section-block">
        <div class="section-head">
          <h3>썸네일/대기화면</h3>
        </div>
        <div class="field-grid">
        <label class="field">
          <span class="field__label">방송 썸네일 업로드</span>
          <div class="upload-control">
            <label class="btn upload-button">
              파일 선택
              <input ref="thumbInputRef" class="upload-input" type="file" accept="image/*" @change="handleThumbUpload" />
            </label>
            <span class="upload-filename">{{ thumbDisplayName || '선택된 파일 없음' }}</span>
          </div>
          <span v-if="thumbError" class="error">{{ thumbError }}</span>
          <div v-if="draft.thumb" class="preview">
            <img :src="draft.thumb" alt="방송 썸네일 미리보기" @error="handleThumbError" />
          </div>
          <button type="button" class="btn ghost upload-clear" @click="clearThumb">이미지 삭제</button>
        </label>
        <label class="field">
          <span class="field__label">대기화면 업로드</span>
          <div class="upload-control">
            <label class="btn upload-button">
              파일 선택
              <input
                ref="standbyInputRef"
                class="upload-input"
                type="file"
                accept="image/*"
                @change="handleStandbyUpload"
              />
            </label>
            <span class="upload-filename">{{ standbyDisplayName || '선택된 파일 없음' }}</span>
          </div>
          <span v-if="standbyError" class="error">{{ standbyError }}</span>
          <div v-if="draft.standbyThumb" class="preview">
            <img :src="draft.standbyThumb" alt="대기화면 미리보기" @error="handleStandbyError" />
          </div>
          <button type="button" class="btn ghost upload-clear" @click="clearStandby">이미지 삭제</button>
        </label>
        </div>
      </div>
      <div class="section-block">
        <label class="checkbox">
          <input v-model="draft.termsAgreed" type="checkbox" />
          <span>방송 운영 및 약관에 동의합니다. (필수)</span>
          <button type="button" class="link" @click="showTermsModal = true">자세히보기</button>
        </label>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="actions">
        <div class="action-buttons">
          <button type="button" class="btn" @click="cancel">취소</button>
          <button type="button" class="btn primary" @click="submit">{{ isEditMode ? '저장' : '방송 등록' }}</button>
        </div>
      </div>
      <teleport to="body">
        <div v-if="showProductModal" class="modal">
          <div class="modal__backdrop" @click="cancelProductSelection"></div>
          <div class="modal__content">
            <div class="modal__header">
              <h3>상품 선택</h3>
              <button type="button" class="btn ghost" aria-label="닫기" @click="cancelProductSelection">×</button>
            </div>
            <div class="modal__body">
              <div class="product-search-bar modal-search">
                <input v-model="productSearch" class="search-input__plain" type="text" placeholder="상품명을 검색하세요" />
                <span class="search-hint">체크박스로 선택 후 저장을 누르면 반영됩니다.</span>
              </div>
              <div class="product-grid">
                <label
                    v-for="product in filteredProducts"
                    :key="product.id"
                    class="product-card"
                    :class="{ checked: isSelected(product.id, modalProducts) }"
                >
                  <input
                      type="checkbox"
                      :checked="isSelected(product.id, modalProducts)"
                      @change="toggleProductInModal(product)"
                  />
                  <div class="product-thumb" v-if="product.thumb">
                    <img :src="product.thumb" :alt="product.name" />
                  </div>
                  <div class="product-content">
                    <div class="product-name">{{ product.name }}</div>
                    <div class="product-meta">
                      <span>{{ product.option }}</span>
                      <span>정가 {{ product.price.toLocaleString() }}원</span>
                      <span>재고 {{ product.stock }}</span>
                    </div>
                  </div>
                </label>
              </div>
            </div>
            <div class="modal__footer">
              <span class="modal__count">선택 {{ modalCount }}개</span>
              <div class="modal__actions">
                <button type="button" class="btn ghost" @click="cancelProductSelection">취소</button>
                <button type="button" class="btn primary" @click="saveProductSelection">저장</button>
              </div>
            </div>
          </div>
        </div>
      </teleport>
      <div v-if="showTermsModal" class="modal">
        <div class="modal__content">
          <div class="modal__header">
            <h3>방송 운영 및 약관</h3>
            <button type="button" class="btn ghost" @click="showTermsModal = false">닫기</button>
          </div>
          <div class="modal__body">
            <p>
              본 운영 정책은 DESKIT 창작자 이용약관 제1조에 따라 제정된 것으로써, 위 약관들과 더불어 관련 서비스
              이용계약을 구성합니다.
            </p>
            <p>판매자께서는 본 운영정책과 운영정책을 반드시 확인하시고 준수해야 합니다</p>
            <p>I. 라이브커머스 콘텐츠 게재제한</p>
            <p>
              일정한 라이브커머스 콘텐츠(이하 라이브커머스 및 숏클립 콘텐츠를 통칭할 필요가 있는 경우 ‘콘텐츠’라 함)가
              다음의 각 항목 중 어느 하나에 해당할 경우, 해당 콘텐츠의 라이브 진행이 중지·중단되거나 해당 콘텐츠가
              삭제될 수 있으며, DESKIT 서비스에서 비공개 처리될 수 있습니다.
            </p>
            <p>특히, 아래 1. ①의 사유가 확인되는 경우 즉시 형사고발 조치 및 서비스 이용의 영구정지가 이루어질 수 있습니다.</p>
            <p>1. 비공개 사유</p>
            <p>
              ① 정보통신망 이용촉진 및 정보보호등에 관한 법률 제44조의7의 불법정보 에 해당하거나, 방송통신심의위원회가
              정한 정보통신에 관한 심의규정에 위반하는 불법적인 내용이 포함된 경우
            </p>
            <p>[대표적 사례들]</p>
            <ul>
              <li>음란물 또는 잔인/폭력/혐오 등 청소년에게 부적합한 콘텐츠</li>
              <li>공공질서 및 미풍양속에 위배되는 저속, 음란한 내용을 포함하는 콘텐츠</li>
              <li>법령에 따라 분류된 비밀 등 국가기밀을 누설하는 내용의 콘텐츠</li>
              <li>타인에게 공포심, 불안감 또는 불쾌감을 유발하는 콘텐츠</li>
              <li>타 라이브커머스 콘텐츠 및 다른 창작자(판매회원)에 대한 비방 목적의 콘텐츠</li>
            </ul>
            <p>
              ② 전자상거래 등에서의 소비자보호에 관한 법률(이하 ‘전자상거래법’) 및 기타 관계 법령에서 인터넷 상의
              광고, 판촉 및 거래행위를 금하고 있는 상품과 관련된 경우
            </p>
            <p>[대표적 사례들]</p>
            <ul>
              <li>DESKIT 상품등록정책상 취급불가상품에 명시된 내역 등 매매부적합상품을 노출 또는 판매하는 콘텐츠</li>
              <li>
                상품 상세페이지에 상품 이미지와 내용을 등록하더라도 상품명과 상품의 대표이미지가 금액권 으로 표시하는 등
                올바르게 등록되지 않은 경우
              </li>
              <li>
                전자상거래법 제13조 소정의 상품의 정보에 관한 사항 및 거래조건이 부적절하게 등록된 상품을 판매하는 콘텐츠
              </li>
              <li>
                라이브에 노출되는 상품에 대응되지 아니하는 10,000원, 1,000원 등의 가격 단위 상품을 태그/연동하는
                등으로 전자상거래법상의 상품정보제공의무를 위반할 가능성이 있는 콘텐츠
              </li>
            </ul>
            <p>③ 타인을 사칭하거나 기타 사기, 기만 등 불법적이거나 공서양속에 반하는 내용의 제공을 목적으로 하는 경우</p>
            <p>[대표적 사례들]</p>
            <ul>
              <li>라이브커머스 및 숏클립 콘텐츠의 제목과 내용에 불법, 음란, 비속어 등을 기재한 경우</li>
              <li>라이브커머스 및 숏클립 콘텐츠 내에서 상품을 설명함에 있어 허위 또는 과장된 내용을 포함하는 경우</li>
            </ul>
            <p>④ 기타 관계 법령을 위반하거나, 타인의 권리를 침해하거나 침해할 여지가 있다고 판단할 상당한 이유가 있는 경우</p>
            <p>[대표적 사례들]</p>
            <ul>
              <li>지식재산권 침해, 저작권에 위배되는 콘텐츠</li>
              <li>초상권 및 저작권이 확보되지 않은 유명인 또는 캐릭터를 사용한 경우</li>
              <li>타인의 라이브커머스 또는 숏클립 영상을 허락 없이 재생산 하는 콘텐츠</li>
              <li>타인의 콘텐츠 중 창작성이 있는 아이템과 디자인, 이미지 등을 무단으로 도용한 상품을 연동, 판매한 경우</li>
              <li>타인의 상표와 로고를 사전 허가없이 사용한 경우</li>
              <li>
                음원 저작권 보유 또는 음원 사용 허락이 확인되지 않은 음원이 사용되는 콘텐츠(음원 저작권에 대한 올바른 표기:
                공지에 출처 작성 필수 또는 사전 증빙 제출하여 협의된 경우)
              </li>
              <li>타인이 소유하거나 타인이 제작한 영상을 사용한 콘텐츠 (TV방송, 드라마, 예능에 제품이 노출된 장면 컷 등)</li>
              <li>브랜드 본사 공식 대행사 또는 본사 채널 외에 위탁 판매자인 경우, 해당 제품의 TV CF 재생 불가</li>
              <li>타인의 초상권을 침해하는 콘텐츠</li>
              <li>야외 촬영 시 제 3자의 모습이 배경과 같이 노출되는 경우라도 특정인을 식별할 수 없도록 해야함</li>
              <li>부정경쟁방지 및 영업비밀보호에 관한 법률 등 관계 법령에 위배되는 콘텐츠</li>
              <li>타인의 영업비밀 등을 누설하는 내용의 콘텐츠</li>
              <li>
                타인의 상표, 로고 등을 적법한 승낙 없이 사용한 위조상품을 대상으로 하거나, 상표권을 직접 침해하지 않았더라도
                타인의 공식 상품이나 정품으로 오인될 수 있는 내용이거나, 국내외에 널리 인식된 타인의 성명, 상호, 상표,
                표장 기타 타인의 영업임을 표시하는 표지와 동일하거나 유사한 것을 사용하거나 이러한 것을 사용한 상품을 대상으로
                하는 등 부정경쟁행위에 해당하거나 부정경쟁행위로 의심될만한 타당한 정황이 있는 경우
              </li>
              <li>개인정보보호법 등 관련 법령에 위반하여 자신 또는 타인의 개인정보를 라이브커머스 콘텐츠 또는 댓글에 노출하는 경우</li>
            </ul>
            <p>
              ⑤ 권한 없는 제3자에게 계정을 유상 또는 무상으로 대여하거나 창작자(판매회원) 본인의 관여 없이 제3자에 의해
              콘텐츠가 제작될 수 있도록 하는 등의 경우
            </p>
            <p>[대표적 사례들]</p>
            <ul>
              <li>
                DESKIT과의 사전 서면 협의를 거치지 아니하고 본인 또는 타인 소유의 타 스토어/창작자(판매회원)의 상품을
                태그하는 등으로 계정 대여로 의심되는 라이브
              </li>
              <li>타인의 계정을 대여하여 라이브를 진행하는 경우</li>
              <li>
                창작자(판매회원) 본인의 관여가 있더라도, 라이브 서비스 이용 권한이 없는 다른 판매회원의 상품을 노출하는 등으로
                창작자(판매회원) 본인의 상품이 아닌 타인의 상품을 DESKIT의 명시적인 사전 서면 승인 없이 연동·태그 또는
                노출하는 경우
              </li>
            </ul>
            <p>⑥ 창작자(판매회원)의 라이브커머스 진행 내용이 서비스의 성격 및 실질에 부합하지 않는다고 판단하는 경우</p>
            <p>[대표적 사례들]</p>
            <ul>
              <li>사람이 등장하지 아니하거나 또는 실시간으로 진행되지 않는 라이브</li>
              <li>제품만 배치해두고 (비추고) 아무런 변화나 진행이 없는 라이브 및 숏클립</li>
              <li>미리 촬영/제작해 둔 라이브·홍보/광고 영상 등을 모니터 기타 단말기 등 다른 기기에 틀어놓고 그 기기를 촬영/송출하거나 반복해서 보여 주기만 하는 라이브</li>
              <li>사전 녹화한 영상 혹은 광고 영상을 한 콘텐츠 내에서 지속 반복 재생하는 경우</li>
              <li>네이버의 사전 서면 승인 없이 사전 녹화한 영상을 재생하는 경우</li>
              <li>
                어떠한 말도 하지 아니하고 (i) 상품의 언박싱만 하거나; (ii) 상품을 사용하는 모습만 보여주거나; (iii) 라이브
                진행과 무관한 일을 하는 등으로 서비스의 성격 및 실질에 부합하지 아니하는 내용의 라이브 커머스가 노출될 우려가
                있는 경우
              </li>
              <li>테스트 목적 송출 시 리허설 기능을 사용하지 아니함에 따라 서비스의 성격 및 실질에 부합하지 아니하는 테스트성 내용의 콘텐츠가 노출될 우려가 있는 경우</li>
              <li>콘텐츠 제목에 테스트, 리허설, TEST 등 리허설 진행 목적이 드러나는 내용을 포함한 경우</li>
              <li>콘텐츠에서 소개, 홍보, 노출하는 상품과 전혀 관련 없는 상품을 콘텐츠에 태그/연동하고 라이브를 진행하는 경우</li>
              <li>라이브 송출 시점 이래로 상품이 판매 불가 상태로 지속 구매 불가능한 경우</li>
              <li>
                스마트스토어 서비스에 등록되어 있지 않은 상품을 라이브에서 노출한 뒤 네이버의 구매안전 서비스, 결제대금 보호 서비스를
                통하지 아니하는 수단· 방법 등을 통한 직거래를 유도하는 행위
              </li>
              <li>
                외부 SNS, 메신저 또는 톡톡 서비스 등을 통해 별도 연락을 요구하거나 라이브커머스 콘텐츠 내 공지사항 또는 댓글로
                신뢰할 수 없는 외부 링크를 노출하는 등 네이버의 구매안전 서비스, 결제대금 보호 서비스를 통하지 아니하는 수단· 방법
                등을 통한 직거래를 유도하는 행위
              </li>
              <li>
                서비스의 기능을 비정상적으로 이용하여 라이브를 진행한 경우
              </li>
              <li>
                DESKIT의 사전 서면 승인 없이 자동화된 수단 (예: 매크로 프로그램, 로봇(봇), 스파이더, 스크래퍼, 스파이웨어 등)을
                이용하여 서비스에 접속하거나 창작자(판매회원) 본인 또는 다른 창작자(판매회원)의 계정에 접속하여 라이브를 진행하는 경우
              </li>
              <li>이미 등록한 숏클립과 동일한 내용을 지속 재등록 하는 경우</li>
              <li>다수의 채널에 동일한 내용의 콘텐츠를 게시 또는 송출하는 경우</li>
              <li>상품의 상세 페이지 내용이 충분히 기록되지 않은 상품을 연동하는 경우</li>
              <li>노출 등 기타 목적만을 위해 라이브를 진행하지 않고 일정을 지속적으로 변경하는 경우</li>
            </ul>
            <p>2. 부분공개 사유</p>
            <p>라이브커머스 콘텐츠의 일부 또는 전부가 서비스의 성격 및 실질에 부합하지 아니하여 서비스의 품질을 저하할 우려가 있는 경우</p>
            <p>[대표적 사례들]</p>
            <ul>
              <li>저품질 대표이미지를 사용한 콘텐츠</li>
              <li>규격 사이즈(720 X 1280)보다 지나치게 작거나 큰 이미지를 사용한 경우</li>
              <li>웹페이지 (스마트스토어 상품 상세페이지 등)의 스크린샷을 사용한 경우</li>
              <li>DESKIT의 사전 서면 승인 없이 대표 이미지에 텍스트가 포함된 경우</li>
              <li>모델이 속옷만 착용하는 등 과도한 노출이 포함된 경우</li>
              <li>네이버의 사전 서면 승인 없이 콘텐츠에서 음원을 사용한 경우</li>
              <li>창작자 본인의 상품 소개 및 판매와 관련 없는 일상 콘텐츠</li>
              <li>동일 또는 유사한 내용의 콘텐츠를 단기간내에 지속적으로 반복하여 게시함으로써 다른 창작자(판매회원)에게 불편을 초래하는 경우</li>
              <li>인위적인 사용자 반응을 과도하게 요구하는 컨텐츠 (좋아요, 찜 등)</li>
              <li>사용자와의 과도한 친목을 조장하거나 이를 명시적으로 노출하는 컨텐츠</li>
              <li>열악한 라이브 환경에서 라이브를 진행하거나 라이브 퀄리티가 현저하게 낮은 경우</li>
              <li>미풍양속에 반하거나, 통신망의 한계 등으로 인해 접속 품질이 극히 낮거나, 과도한 소음이 발생하는 등의 환경에서 진행하는 경우</li>
            </ul>
            <p>II. 콘텐츠 댓글 신고 정책</p>
            <ol>
              <li>관련 법령을 위반한 내용을 포함한 댓글</li>
              <li>욕설, 음란/외설적 내용, 타인비방, 폭력적인 내용의 댓글</li>
              <li>타인에게 공포심, 불안감 또는 불쾌감을 주는 댓글</li>
              <li>아웃콜 등 네이버의 구매안전 서비스, 결제대금 보호 서비스를 통하지 아니하는 수단·방법 등을 통한 직거래를 유도하는 댓글</li>
              <li>타인 또는 본인의 개인정보를 노출하는 댓글</li>
              <li>출연자의 명예를 손상시키거나 비방, 모욕감을 줄 수 있는 내용의 댓글</li>
              <li>콘텐츠와 전혀 관련 없는 광고성 댓글</li>
              <li>타 채널의 콘텐츠 또는 다른 창작자(판매회원)에 대한 비방 목적의 댓글</li>
              <li>라이브 진행 또는 콘텐츠를 통한 상품 판매를 방해하는 목적의 댓글</li>
              <li>콘텐츠와 무관한 내용의 종교적, 정치적 발언을 하는 댓글</li>
            </ol>
            <p>[페널티(비공개/부분공개) 부과 기준]</p>
            <p>항목 - 콘텐츠 비공개/부분공개</p>
            <p>상세 기준 - 본 운영정책에 명시된 콘텐츠 게재제한 정책 위반 라이브 진행 1회 시</p>
            <p>페널티(비공개/부분공개) 부여기간 - 영구 비공개(단, 객관적/합리적 증거자료에 기반한 소명 시 심사 후 해제)</p>
          </div>
        </div>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.create-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.step-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.step-indicator {
  color: var(--text-muted);
  font-weight: 800;
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

.field__hint {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

input,
select,
textarea {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

input[type='file'] {
  padding: 8px 0;
}

.upload-control {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.upload-input {
  display: none;
}

.upload-button {
  cursor: pointer;
  font-size: 0.7rem;
  margin: 0;
  padding: 0;
  font-weight: bold;
}

.upload-button input {
  display: none;
}

.section-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.count-pill {
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  color: var(--text-strong);
  padding: 6px 10px;
  border-radius: 999px;
  font-weight: 800;
  font-size: 0.85rem;
}

.empty-hint {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.product-select {
  display: none;
}

.product-table-wrap {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

.product-table-wrap table {
  width: 100%;
  border-collapse: collapse;
}

.product-table-wrap th,
.product-table-wrap td {
  padding: 10px;
  border-bottom: 1px solid var(--border-color);
  text-align: left;
  font-weight: 700;
}

.product-table-wrap th {
  background: var(--surface-weak);
  font-weight: 900;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-weak);
  border: 1px solid var(--border-color);
  flex-shrink: 0;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.numeric {
  text-align: right;
}

.table-input {
  width: 120px;
}

.table-hint {
  margin: 0;
  padding: 8px 12px;
  color: var(--text-muted);
  font-weight: 700;
}

.preview {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface-weak);
}

.preview img {
  width: 100%;
  height: 140px;
  object-fit: cover;
  display: block;
}

.upload-filename {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.upload-clear {
  margin-top: 6px;
}

.checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--text-strong);
}

.error {
  margin: 0;
  color: #ef4444;
  font-weight: 700;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.modal__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.modal__footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.modal__count {
  font-weight: 800;
  color: var(--text-strong);
}

.modal__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.btn {
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 900;
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  cursor: pointer;
}

.btn.ghost {
  color: var(--text-muted);
}

.btn.primary {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.product-search-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.modal-search {
  align-items: flex-start;
  flex-direction: column;
}

.search-input {
  position: relative;
  flex: 1 1 320px;
}

.search-input__plain {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.search-input input {
  width: 100%;
  padding-left: 34px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
}

.search-hint {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.product-card {
  display: grid;
  grid-template-columns: auto 60px 1fr;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface);
  align-items: center;
  cursor: pointer;
}

.product-card input {
  justify-self: center;
}

.product-card.checked {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(45, 127, 249, 0.2);
}

.product-thumb {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-weak);
  border: 1px solid var(--border-color);
}

.product-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.product-name {
  font-weight: 900;
  color: var(--text-strong);
}

.product-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.link {
  background: none;
  border: none;
  padding: 0;
  color: var(--primary-color);
  cursor: pointer;
  font-weight: 800;
  text-decoration: underline;
}

.modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1300;
  padding: 16px;
}

.modal__content {
  background: var(--surface);
  border-radius: 16px;
  padding: 18px;
  max-width: 520px;
  max-height: calc(100vh - 32px);
  width: 100%;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.modal__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--text-strong);
  font-weight: 700;
  line-height: 1.5;
}

@media (max-width: 720px) {
  .field-grid {
    grid-template-columns: 1fr;
  }

  .product-card {
    grid-template-columns: auto 50px 1fr;
  }

  .product-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
}
</style>
