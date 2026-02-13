<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { getProductDetail } from '../api/products'
import {
  loadCheckout,
  updateShipping,
  type CheckoutDraft,
  type CheckoutItem,
  type ShippingInfo,
  type PaymentMethod,
  updateCheckoutItemsPricing,
} from '../lib/checkout/checkout-storage'
import { abandonCreatedOrder, createOrder } from '../api/orders'
import { getMyAddresses } from '../api/addresses'
import type { AddressResponse } from '../api/types/addresses'
import { getAuthUser } from '../lib/auth'
import {
  clearPendingTossPayment,
  savePendingTossPayment,
  type PendingTossPayment,
} from '../lib/checkout/toss-payment-storage'

const router = useRouter()
const route = useRoute()

const TOSS_CLIENT_KEY =
  import.meta.env.VITE_TOSS_CLIENT_KEY || 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm'

const draft = ref<CheckoutDraft | null>(null)
const isSubmitting = ref(false)
const priceNotice = ref('')
const priceSyncTimer = ref<number | null>(null)
const priceSyncInFlight = ref(false)
const priceChangePending = ref(false)
const priceChangeModalOpen = ref(false)
const priceChangeMessage = ref('')
let inflight: Promise<void> | null = null
const addresses = ref<AddressResponse[]>([])
const addressesLoading = ref(false)
const addressesError = ref('')
const selectedAddressId = ref<number | null>(null)

const form = reactive<ShippingInfo>({
  buyerName: '',
  zipcode: '',
  address1: '',
  address2: '',
  isDefault: false,
})
const address2Ref = ref<HTMLInputElement | null>(null)
let postcodeScriptPromise: Promise<void> | null = null

const errors = reactive<Record<keyof ShippingInfo, string>>({
  buyerName: '',
  zipcode: '',
  address1: '',
  address2: '',
  isDefault: '',
})

const isTossReady = ref(false)
const tossError = ref('')
const tossRendered = ref(false)
let tossWidgets: any = null
let paymentMethodWidget: any = null
let tossPaymentsClient: any = null
let tossScriptPromise: Promise<void> | null = null
let tossInitPromise: Promise<void> | null = null

const step = computed<'shipping' | 'payment'>(() =>
  route.query.step === 'payment' ? 'payment' : 'shipping',
)

const items = computed(() => draft.value?.items ?? [])
const listPriceTotal = computed(() =>
  items.value.reduce((sum, item) => {
    const base =
      item.originalPrice && item.originalPrice > item.price ? item.originalPrice : item.price
    return sum + base * item.quantity
  }, 0),
)
const salePriceTotal = computed(() =>
  items.value.reduce((sum, item) => sum + item.price * item.quantity, 0),
)
const discountTotal = computed(() => {
  const diff = listPriceTotal.value - salePriceTotal.value
  return diff > 0 ? diff : 0
})
const shippingFee = computed(() => {
  if (items.value.length === 0) return 0
  return salePriceTotal.value >= 50000 ? 0 : 3000
})
const total = computed(() => salePriceTotal.value + shippingFee.value)
const totalQuantity = computed(() =>
  items.value.reduce((sum, item) => sum + item.quantity, 0),
)

const formatPrice = (value: number) => `${value.toLocaleString('ko-KR')}원`

const refreshDraft = () => {
  draft.value = loadCheckout()
  if (!draft.value) return

  form.buyerName = draft.value.shipping?.buyerName ?? ''
  form.zipcode = draft.value.shipping?.zipcode ?? ''
  form.address1 = draft.value.shipping?.address1 ?? ''
  form.address2 = draft.value.shipping?.address2 ?? ''
  form.isDefault = draft.value.shipping?.isDefault ?? false
}

const hasAddresses = computed(() => addresses.value.length > 0)

type StringKeys<T> = { [K in keyof T]-?: T[K] extends string ? K : never }[keyof T]
type ShippingTextField = StringKeys<ShippingInfo>

const resolvePricing = (product: any) => {
  const price = Number(product?.price ?? 0) || 0
  const candidateOriginal = Number(product?.cost_price ?? product?.costPrice ?? price) || price
  const originalPrice = candidateOriginal > price ? candidateOriginal : price
  const discountRate = originalPrice > price ? Math.round((1 - price / originalPrice) * 100) : 0
  const stock = Math.max(1, Number(product?.stock_qty ?? product?.stock ?? 99) || 99)
  return { price, originalPrice, discountRate, stock }
}

const syncPrices = async () => {
  if (priceSyncInFlight.value) return false
  const current = loadCheckout()
  if (!current || current.items.length === 0) return false
  priceSyncInFlight.value = true
  try {
    const results = await Promise.all(
      current.items.map(async (item) => {
        try {
          const detail = await getProductDetail(item.productId)
          return { item, detail }
        } catch {
          return { item, detail: null }
        }
      }),
    )
    const patches: Array<{
      productId: string
      price: number
      originalPrice: number
      discountRate: number
      stock: number
    }> = []
    let priceChanged = false
    let quantityAdjusted = false
    results.forEach(({ item, detail }) => {
      if (!detail) return
      const pricing = resolvePricing(detail)
      const needsUpdate =
        item.price !== pricing.price ||
        item.originalPrice !== pricing.originalPrice ||
        item.discountRate !== pricing.discountRate ||
        item.stock !== pricing.stock
      if (needsUpdate) {
        patches.push({ productId: item.productId, ...pricing })
      }
      if (item.price !== pricing.price) {
        priceChanged = true
      }
      if (item.quantity > pricing.stock) {
        quantityAdjusted = true
      }
    })
    if (patches.length > 0) {
      draft.value = updateCheckoutItemsPricing(patches)
      const noticeMessage = priceChanged
        ? '가격이 변경된 상품이 있어 결제 금액을 업데이트했습니다.'
        : quantityAdjusted
          ? '재고가 변경되어 수량을 조정했습니다.'
          : ''
      if (noticeMessage) {
        priceNotice.value = noticeMessage
        priceChangeMessage.value = priceChanged
          ? '상품 가격이 변경되어 결제 금액을 갱신했습니다. 확인 후 계속 진행해주세요.'
          : '재고가 변경되어 수량을 조정했습니다. 확인 후 계속 진행해주세요.'
        priceChangePending.value = true
        priceChangeModalOpen.value = true
        if (step.value === 'payment') {
          goShipping()
        }
        return true
      }
    }
    return false
  } finally {
    priceSyncInFlight.value = false
  }
}

const persistField = (field: ShippingTextField, value: string) => {
  let sanitized = value
  if (field === 'zipcode') {
    sanitized = sanitized.replace(/[^0-9]/g, '').slice(0, 5)
  }

  form[field] = sanitized
  const updated = updateShipping({ [field]: sanitized } as Partial<ShippingInfo>)
  if (updated) {
    draft.value = updated
  }
}

const toggleDefault = (checked: boolean) => {
  form.isDefault = checked
  const updated = updateShipping({ isDefault: checked })
  if (updated) {
    draft.value = updated
  }
}

const resolveReceiver = () => {
  const selected = addresses.value.find((item: AddressResponse) => item.address_id === selectedAddressId.value)
  if (selected && selected.receiver.trim()) return selected.receiver.trim()
  const userName = getAuthUser()?.name ?? ''
  if (userName.trim()) return userName.trim()
  return '고객'
}

const applySelectedAddress = (address: AddressResponse) => {
  form.zipcode = address.postcode
  form.address1 = address.addr_detail
  form.address2 = ''
  form.buyerName = address.receiver
  const updated = updateShipping({
    buyerName: address.receiver,
    zipcode: address.postcode,
    address1: address.addr_detail,
    address2: '',
  })
  if (updated) {
    draft.value = updated
  }
}

const selectAddress = (address: AddressResponse) => {
  selectedAddressId.value = address.address_id
  applySelectedAddress(address)
}

const loadAddresses = async () => {
  addressesLoading.value = true
  addressesError.value = ''
  try {
    const response = await getMyAddresses()
    addresses.value = Array.isArray(response) ? response : []
    if (addresses.value.length > 0) {
      const defaultAddress =
        addresses.value.find((item: AddressResponse) => item.is_default) ?? addresses.value[0]
      if (!defaultAddress) {
        form.isDefault = true
        form.buyerName = resolveReceiver()
        updateShipping({ isDefault: true, buyerName: form.buyerName })
        return
      }
      selectedAddressId.value = defaultAddress.address_id
      applySelectedAddress(defaultAddress)
      form.isDefault = false
      updateShipping({ isDefault: false })
    } else {
      form.isDefault = true
      form.buyerName = resolveReceiver()
      updateShipping({ isDefault: true, buyerName: form.buyerName })
    }
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      router.push('/login').catch(() => {})
      return
    }
    addressesError.value = '배송지 정보를 불러오지 못했습니다.'
    addresses.value = []
  } finally {
    addressesLoading.value = false
  }
}

const loadPostcodeScript = () => {
  if (typeof window === 'undefined') return Promise.reject(new Error('window missing'))
  if ((window as any).daum?.Postcode) return Promise.resolve()
  if (!postcodeScriptPromise) {
    postcodeScriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = '//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'
      script.async = true
      script.onload = () => resolve()
      script.onerror = () => reject(new Error('postcode script load failed'))
      document.head.appendChild(script)
    })
  }
  return postcodeScriptPromise
}

const openPostcode = async () => {
  await loadPostcodeScript()
  const daumPostcode = (window as any).daum?.Postcode
  if (!daumPostcode) return

  new daumPostcode({
    oncomplete: (data: any) => {
      const roadAddr = data.roadAddress || ''
      const jibunAddr = data.jibunAddress || ''
      let extraRoadAddr = ''

      if (data.bname && /[가-힣]/.test(data.bname)) {
        extraRoadAddr += data.bname
      }
      if (data.buildingName && data.apartment === 'Y') {
        extraRoadAddr += extraRoadAddr ? `, ${data.buildingName}` : data.buildingName
      }
      if (extraRoadAddr) {
        extraRoadAddr = ` (${extraRoadAddr})`
      }

      const address1 = roadAddr ? `${roadAddr}${extraRoadAddr}` : jibunAddr
      const updated = updateShipping({
        zipcode: data.zonecode,
        address1,
      })
      form.zipcode = data.zonecode
      form.address1 = address1
      if (updated) {
        draft.value = updated
      }
      address2Ref.value?.focus()
    },
  }).open()
}

const validate = () => {
  errors.buyerName = ''
  errors.zipcode = ''
  errors.address1 = ''
  errors.address2 = ''
  errors.isDefault = ''

  const zip = form.zipcode.trim()
  const addr1 = form.address1.trim()
  const addr2 = form.address2.trim()

  if (!zip) errors.zipcode = '우편번호를 입력해주세요.'
  else if (!/^[0-9]{5}$/.test(zip)) errors.zipcode = '우편번호는 5자리 숫자입니다.'

  if (!addr1) errors.address1 = '주소를 입력해주세요.'

  form.zipcode = zip
  form.address1 = addr1
  form.address2 = addr2

  return !errors.zipcode && !errors.address1 && !errors.address2
}

const canProceed = computed(() => {
  const zip = form.zipcode.trim()
  const addr1 = form.address1.trim()
  return /^[0-9]{5}$/.test(zip) && !!addr1
})

const handleNext = async () => {
  if (!validate()) return
  const hasUpdates = await syncPrices()
  if (hasUpdates) return
  updateShipping({ ...form })
  router.push({ path: '/checkout', query: { step: 'payment' } }).catch(() => {})
}

const handleBack = () => {
  router.back()
}

const goShipping = () => {
  router.push({ path: '/checkout' }).catch(() => {})
}

const confirmPriceChange = () => {
  priceChangePending.value = false
  priceChangeModalOpen.value = false
  priceChangeMessage.value = ''
}

const loadTossScript = () => {
  if (typeof window === 'undefined') return Promise.reject(new Error('window missing'))
  if ((window as any).TossPayments) return Promise.resolve()
  if (!tossScriptPromise) {
    tossScriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://js.tosspayments.com/v2/standard'
      script.async = true
      script.onload = () => resolve()
      script.onerror = () => reject(new Error('toss script load failed'))
      document.head.appendChild(script)
    })
  }
  return tossScriptPromise
}

const ensureTossWidgets = async () => {
  if (tossRendered.value && isTossReady.value) {
    return
  }
  if (tossInitPromise) {
    await tossInitPromise
    return
  }
  tossInitPromise = (async () => {
    await loadTossScript()
    const tossPayments = (window as any).TossPayments
    if (!tossPayments) {
      throw new Error('toss payments missing')
    }
    if (!tossPaymentsClient) {
      tossPaymentsClient = tossPayments(TOSS_CLIENT_KEY)
    }
    if (!tossWidgets) {
      const customerKey = tossPayments.ANONYMOUS
      tossWidgets = tossPaymentsClient.widgets({ customerKey })
    }

    await nextTick()
    const paymentMethodEl = document.querySelector('#payment-method')
    const agreementEl = document.querySelector('#agreement')
    if (!paymentMethodEl || !agreementEl) {
      throw new Error('toss mount missing')
    }
    await tossWidgets.setAmount({
      currency: 'KRW',
      value: total.value,
    })

    const [methodsWidget] = await Promise.all([
      tossWidgets.renderPaymentMethods({
        selector: '#payment-method',
        variantKey: 'DEFAULT',
      }),
      tossWidgets.renderAgreement({
        selector: '#agreement',
        variantKey: 'AGREEMENT',
      }),
    ])
    paymentMethodWidget = methodsWidget
    isTossReady.value = true
    tossRendered.value = true
  })()
  try {
    await tossInitPromise
  } catch (error) {
    isTossReady.value = false
    tossError.value = '결제 위젯을 불러오지 못했습니다.'
    throw error
  } finally {
    tossInitPromise = null
  }
}

const orderNameOf = (items: CheckoutItem[]) => {
  const base = items[0]?.name ?? 'DESKIT'
  if (items.length <= 1) return base
  return `${base} 외 ${items.length - 1}건`
}

const resolvePaymentMethodLabel = (selected: any) => {
  if (!selected) return '토스페이'
  const methodRaw = selected.method ?? selected.type ?? ''
  const method = String(methodRaw).toUpperCase()
  if (method === 'CARD') return '카드'
  if (method === 'EASY_PAY') {
    const easyPayProvider = selected.easyPay?.provider || selected.easyPay?.providerCode
    return easyPayProvider ? `간편결제(${easyPayProvider})` : '간편결제'
  }
  if (method === 'TRANSFER') return '계좌이체'
  if (method === 'VIRTUAL_ACCOUNT') return '가상계좌'
  if (method === 'MOBILE_PHONE') return '휴대폰'
  if (method === 'CULTURE_GIFT_CERTIFICATE') return '문화상품권'
  if (method === 'BOOK_GIFT_CERTIFICATE') return '도서문화상품권'
  if (method === 'GAME_GIFT_CERTIFICATE') return '게임문화상품권'
  if (method === 'PAYCO') return 'PAYCO'
  if (method === 'TOSSPAY') return '토스페이'

  const easyPayProvider = selected.easyPay?.provider || selected.easyPay?.providerCode
  if (easyPayProvider) return `간편결제(${easyPayProvider})`

  return methodRaw ? String(methodRaw) : '토스페이'
}

const buildTossOrderId = (orderNumber?: string, orderId?: number) => {
  const base =
    orderNumber && orderNumber.trim()
      ? orderNumber.trim()
      : `ORD-${Date.now()}-${orderId ?? '0000'}`
  const sanitized = base.replace(/[^a-zA-Z0-9-_]/g, '_')
  if (sanitized.length < 6) {
    return sanitized.padEnd(6, '0')
  }
  if (sanitized.length > 64) {
    return sanitized.slice(0, 64)
  }
  return sanitized
}

const handleTossPayment = async () => {
  if (inflight) return
  const current = draft.value ?? loadCheckout()
  if (!current || !current.items || current.items.length === 0) {
    router.push('/cart')
    return
  }
  const hasUpdates = await syncPrices()
  if (hasUpdates) {
    return
  }
  if (priceChangePending.value) {
    priceChangeModalOpen.value = true
    return
  }

  const orderItems = current.items
    .map((item) => ({
      product_id: Number(item.productId),
      quantity: item.quantity,
    }))
    .filter((item) => Number.isFinite(item.product_id) && item.quantity > 0)

  if (orderItems.length === 0) {
    return
  }

  isSubmitting.value = true
  tossError.value = ''
  let createdOrderId: number | null = null
  inflight = (async () => {
    clearPendingTossPayment()
    const addrDetail = [current.shipping?.address1, current.shipping?.address2]
      .map((value) => (value ?? '').trim())
      .filter((value) => value.length > 0)
      .join(' ')
    const response = await createOrder({
      items: orderItems,
      receiver: resolveReceiver(),
      postcode: String(current.shipping?.zipcode ?? '').trim(),
      addr_detail: addrDetail,
      is_default: Boolean(current.shipping?.isDefault),
    })
    if (!response?.order_id) {
      throw new Error('invalid order response')
    }
    createdOrderId = Number(response.order_id)

    await ensureTossWidgets()
    const selectedMethod = paymentMethodWidget?.getSelectedPaymentMethod
      ? await paymentMethodWidget.getSelectedPaymentMethod()
      : null
    const paymentMethodLabel = resolvePaymentMethodLabel(selectedMethod)
    const tossOrderId = buildTossOrderId(response.order_number, response.order_id ?? undefined)

    const pending: PendingTossPayment = {
      orderId: response.order_id,
      orderNumber: response.order_number ?? undefined,
      tossOrderId,
      orderAmount: Number(response.order_amount ?? total.value) || 0,
      paymentMethod: 'CARD' as PaymentMethod,
      paymentMethodLabel,
      createdAt: new Date().toISOString(),
      items: current.items,
      shipping: { ...current.shipping },
      totals: {
        listPriceTotal: listPriceTotal.value,
        salePriceTotal: salePriceTotal.value,
        discountTotal: discountTotal.value,
        shippingFee: shippingFee.value,
        total: total.value,
      },
    }
    savePendingTossPayment(pending)

    await tossWidgets.setAmount({
      currency: 'KRW',
      value: pending.orderAmount,
    })

    await tossWidgets.requestPayment({
      orderId: pending.tossOrderId,
      orderName: orderNameOf(current.items),
      successUrl: `${window.location.origin}/payments/success`,
      failUrl: `${window.location.origin}/payments/fail`,
      customerName: current.shipping?.buyerName || '고객',
    })
  })()
  try {
    await inflight
  } catch (error) {
    console.error(error)
    if (Number.isFinite(createdOrderId) && createdOrderId != null) {
      try {
        await abandonCreatedOrder(createdOrderId)
      } catch (cleanupError) {
        console.error(cleanupError)
      }
      clearPendingTossPayment()
    }
    tossError.value = '결제 요청이 실패했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    inflight = null
    isSubmitting.value = false
  }
}

watch(
  () => step.value,
  async (current) => {
    if (current !== 'payment') {
      isTossReady.value = false
      tossRendered.value = false
      tossError.value = ''
      tossWidgets = null
      paymentMethodWidget = null
      return
    }
    if (!draft.value) return
    tossError.value = ''
    try {
      await ensureTossWidgets()
    } catch (error) {
      console.error(error)
      return
    }
  },
  { immediate: true },
)

watch(
  () => total.value,
  async (value) => {
    if (step.value !== 'payment' || !tossWidgets) return
    try {
      await tossWidgets.setAmount({ currency: 'KRW', value })
    } catch {
      return
    }
  },
)

watch(
  () => draft.value,
  async (current) => {
    if (step.value !== 'payment' || !current || tossRendered.value) return
    try {
      await ensureTossWidgets()
    } catch (error) {
      console.error(error)
      return
    }
  },
)

const storageRefreshHandler = () => refreshDraft()

onMounted(() => {
  refreshDraft()
  loadAddresses()
  window.addEventListener('deskit-checkout-updated', storageRefreshHandler)
  window.addEventListener('storage', storageRefreshHandler)
  window.addEventListener('focus', syncPrices)
  document.addEventListener('visibilitychange', syncPrices)
  syncPrices()
  priceSyncTimer.value = window.setInterval(syncPrices, 15000)
})

onBeforeUnmount(() => {
  window.removeEventListener('deskit-checkout-updated', storageRefreshHandler)
  window.removeEventListener('storage', storageRefreshHandler)
  window.removeEventListener('focus', syncPrices)
  document.removeEventListener('visibilitychange', syncPrices)
  if (priceSyncTimer.value) {
    window.clearInterval(priceSyncTimer.value)
  }
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="주문/결제" />

    <div class="checkout-steps">
      <span class="checkout-step">01 장바구니</span>
      <span class="checkout-step__divider">></span>
      <span class="checkout-step checkout-step--active">02 주문/결제</span>
      <span class="checkout-step__divider">></span>
      <span class="checkout-step">03 주문 완료</span>
    </div>

    <div v-if="!draft" class="checkout-empty">
      <p>준비된 체크아웃 정보가 없습니다.</p>
      <RouterLink to="/cart" class="link">장바구니로 돌아가기</RouterLink>
    </div>

    <div v-else class="checkout-layout">
      <div v-if="priceNotice" class="price-notice">
        <span>{{ priceNotice }}</span>
        <button type="button" class="price-notice__close" @click="priceNotice = ''">닫기</button>
      </div>
      <div v-if="step === 'shipping'" class="left-col">
        <div class="left-stack">
          <section class="panel panel--form">
            <div class="panel__header">
              <div>
                <p class="eyebrow">배송 정보</p>
                <h3 class="panel__title">배송지 정보를 입력해주세요.</h3>
              </div>
            </div>

            <div class="form">
              <div v-if="addressesLoading" class="address-state">
                <p>배송지 정보를 불러오는 중입니다.</p>
              </div>
              <div v-else-if="addressesError" class="address-state error">
                <p>{{ addressesError }}</p>
              </div>
              <div v-else-if="hasAddresses" class="address-select">
                <p class="field-label">등록된 배송지</p>
                <div class="address-options">
                  <label
                    v-for="address in addresses"
                    :key="address.address_id"
                    class="address-option"
                  >
                    <input
                      type="radio"
                      name="shipping-address"
                      :value="address.address_id"
                      :checked="selectedAddressId === address.address_id"
                      @change="selectAddress(address)"
                    />
                    <div class="option-body">
                      <div class="option-head">
                        <span class="receiver">{{ address.receiver }}</span>
                        <span v-if="address.is_default" class="badge">기본</span>
                      </div>
                      <p class="address-text">{{ address.postcode }} {{ address.addr_detail }}</p>
                    </div>
                  </label>
                </div>
              </div>
              <div v-else class="address-form">
                <div class="field">
                  <label for="zipcode">우편번호</label>
                  <div class="field-row">
                    <input
                      id="zipcode"
                      type="text"
                      :value="form.zipcode"
                      placeholder="12345"
                      maxlength="5"
                      inputmode="numeric"
                      pattern="\\d{5}"
                      readonly
                      @blur="validate"
                    />
                    <button type="button" class="btn ghost btn-inline" @click="openPostcode">
                      우편번호 찾기
                    </button>
                  </div>
                  <p v-if="errors.zipcode" class="error">{{ errors.zipcode }}</p>
                </div>

                <div class="field">
                  <label for="address1">주소</label>
                  <input
                    id="address1"
                    type="text"
                    :value="form.address1"
                    placeholder="서울특별시 강남구 강남로 123"
                    readonly
                    @blur="validate"
                  />
                  <p v-if="errors.address1" class="error">{{ errors.address1 }}</p>
                </div>

                <div class="field">
                  <label for="address2">상세주소</label>
                  <input
                    id="address2"
                    type="text"
                    :value="form.address2"
                    placeholder="예) 101동 101호"
                    ref="address2Ref"
                    @input="persistField('address2', ($event.target as HTMLInputElement).value)"
                  />
                </div>

                <div class="field field--checkbox">
                  <label class="checkbox">
                    <input
                      type="checkbox"
                      :checked="form.isDefault"
                      :disabled="!hasAddresses"
                      @change="toggleDefault(($event.target as HTMLInputElement).checked)"
                    />
                    <span>기본값으로 설정</span>
                  </label>
                </div>
              </div>
            </div>
          </section>

          <section class="panel panel--form">
            <div class="panel__header">
              <div>
                <p class="eyebrow">결제 안내</p>
                <h3 class="panel__title">토스페이먼츠 결제위젯에서 진행해주세요.</h3>
              </div>
            </div>
          </section>
        </div>

        <div class="actions actions--left">
          <button type="button" class="btn ghost" @click="handleBack">이전</button>
          <button type="button" class="btn primary" :disabled="!canProceed || priceChangePending" @click="handleNext">
            다음
          </button>
        </div>
      </div>

      <section v-else class="panel panel--form">
        <div class="panel__header">
          <div>
            <p class="eyebrow">결제 방법</p>
            <h3 class="panel__title">결제 영역은 토스페이먼츠 위젯으로 표시됩니다.</h3>
          </div>
        </div>

        <div class="payment-widget">
          <div id="payment-method"></div>
          <div id="agreement"></div>
        </div>
        <p v-if="tossError" class="error">{{ tossError }}</p>

        <div class="actions">
          <button type="button" class="btn ghost" @click="goShipping">이전</button>
          <button
            type="button"
            class="btn primary"
            :disabled="!isTossReady || isSubmitting || priceChangePending"
            @click="handleTossPayment"
          >
            결제 진행
          </button>
        </div>
      </section>

      <aside class="panel panel--summary">
        <h3 class="panel__title">주문 예상 금액</h3>
        <p class="summary-meta-text">총 {{ items.length }}종 / {{ totalQuantity }}개</p>

        <div class="summary-row">
          <span>총상품 금액(정가)</span>
          <strong class="amount">{{ formatPrice(listPriceTotal) }}</strong>
        </div>

        <div class="summary-row">
          <span>총할인 금액</span>
          <strong class="amount" :class="{ discount: discountTotal > 0 }">
            {{ discountTotal > 0 ? `-${formatPrice(discountTotal)}` : '-' }}
          </strong>
        </div>

        <div class="summary-row">
          <span>상품 금액(할인 적용)</span>
          <strong class="amount">{{ formatPrice(salePriceTotal) }}</strong>
        </div>

        <div class="summary-row">
          <span>배송비</span>
          <strong class="amount">{{ items.length === 0 ? '-' : formatPrice(shippingFee) }}</strong>
        </div>
        <p v-if="items.length" class="summary-helper">5만원 이상 무료배송</p>

        <div class="summary-total">
          <span>총결제 금액</span>
          <strong class="amount total-amount">
            {{ items.length === 0 ? '-' : formatPrice(total) }}
          </strong>
        </div>
      </aside>
    </div>

    <div v-if="priceChangeModalOpen" class="modal-overlay" role="dialog" aria-modal="true">
      <div class="modal-card">
        <h3>변경 안내</h3>
        <p>
          {{
            priceChangeMessage ||
              '상품 가격이 변경되어 결제 금액을 갱신했습니다. 확인 후 계속 진행해주세요.'
          }}
        </p>
        <div class="modal-actions">
          <button type="button" class="btn primary" @click="confirmPriceChange">확인</button>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.checkout-steps {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  color: var(--text-muted);
  font-weight: 700;
}

.checkout-step {
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--surface-weak);
}

.checkout-step--active {
  background: var(--hover-bg);
  color: var(--primary-color);
}

.checkout-step__divider {
  color: var(--text-soft);
}

.checkout-empty {
  border: 1px dashed var(--border-color);
  padding: 16px;
  border-radius: 12px;
  color: var(--text-muted);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.link {
  color: var(--primary-color);
  font-weight: 800;
}

.checkout-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.65fr);
  gap: 18px;
  align-items: start;
}

.price-notice {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--surface-weak);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  font-weight: 600;
  color: var(--text-strong);
}

.price-notice__close {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-weight: 700;
  cursor: pointer;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  display: grid;
  place-items: center;
  z-index: 1300;
}

.modal-card {
  width: min(420px, 90vw);
  background: var(--surface);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid var(--border-color);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.2);
}

.modal-card h3 {
  margin: 0 0 10px;
}

.modal-card p {
  margin: 0;
  color: var(--text-muted);
}

.modal-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.panel {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  padding: 16px;
  box-sizing: border-box;
}

.left-col {
  display: flex;
  flex-direction: column;
}

.left-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.eyebrow {
  margin: 0;
  color: var(--text-soft);
  font-weight: 800;
  letter-spacing: 0.04em;
}

.panel__title {
  margin: 6px 0 0;
  font-size: 1.15rem;
  font-weight: 900;
  color: var(--text-strong);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 10px;
}

.field label {
  display: block;
  margin-bottom: 6px;
  font-weight: 800;
  color: var(--text-strong);
}

.field input {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--surface-weak);
  color: var(--text-strong);
  outline: none;
  box-sizing: border-box;
}

.field-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.field-row input {
  flex: 1;
}

.btn-inline {
  white-space: nowrap;
}

.field input:focus {
  border-color: var(--primary-color);
  background: var(--surface);
}

.address-state {
  border: 1px dashed var(--border-color);
  border-radius: 12px;
  padding: 12px;
  color: var(--text-muted);
  font-weight: 700;
}

.address-select {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-label {
  margin: 0;
  font-weight: 800;
  color: var(--text-strong);
}

.address-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.address-option {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  cursor: pointer;
}

.address-option input {
  margin-top: 2px;
}

.option-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.option-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-head .receiver {
  font-weight: 900;
  color: var(--text-strong);
}

.address-text {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.badge {
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(34, 197, 94, 0.55);
  color: #16a34a;
  background: rgba(34, 197, 94, 0.12);
  font-weight: 800;
  font-size: 12px;
}

.field--checkbox {
  margin-top: 4px;
}

.checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  color: var(--text-strong);
}

.checkbox input {
  width: 16px;
  height: 16px;
}

.error {
  margin: 6px 0 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--danger-color, #dc2626);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}

.btn {
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface);
  font-weight: 900;
  cursor: pointer;
}

.btn.ghost {
  color: var(--text-strong);
}

.btn.primary {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #fff;
}

.btn.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.payment-widget {
  border: 1px dashed var(--border-color);
  border-radius: 12px;
  padding: 16px;
  background: var(--surface-weak);
  margin-top: 10px;
}

.panel--summary {
  position: sticky;
  top: 80px;
  align-self: start;
}

.summary-meta-text {
  margin: 6px 0 14px;
  color: var(--text-muted);
  font-weight: 800;
}

.summary-row,
.summary-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--text-strong);
  padding: 8px 0;
}

.summary-helper {
  margin: 2px 0 10px;
  color: var(--text-muted);
  font-size: 0.9rem;
}

.summary-total {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
  font-size: 1.1rem;
  font-weight: 800;
}

.amount {
  font-variant-numeric: tabular-nums;
}

.discount {
  color: var(--primary-color);
  font-weight: 800;
}

.total-amount {
  font-size: 1.1rem;
  font-weight: 800;
}

@media (max-width: 1080px) {
  .checkout-layout {
    grid-template-columns: 1fr;
  }

  .panel--summary {
    position: static;
    order: -1;
  }

  .actions--left {
    justify-content: flex-start;
  }
}
</style>
