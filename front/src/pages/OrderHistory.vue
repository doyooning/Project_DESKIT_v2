<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { cancelOrder, getMyOrderDetail, getMyOrders } from '../api/orders'
import { productsData } from '../lib/products-data'

type OrderStatus =
  | 'CREATED'
  | 'PAID'
  | 'CANCEL_REQUESTED'
  | 'CANCELLED'
  | 'REFUND_REQUESTED'
  | 'REFUND_REJECTED'
  | 'REFUNDED'
  | 'COMPLETED'

type OrderSummaryResponse = {
  order_id: number
  order_number: string
  status: OrderStatus
  order_amount: number
  created_at: string
  cancel_reason?: string
  cancel_requested_at?: string
}

type OrderDetailWithCancel = {
  cancel_reason?: string
  cancel_requested_at?: string
  updated_at?: string
}

type OrderItemView = {
  productId: string
  name: string
  quantity: number
  price: number
  originalPrice: number
  discountRate: number
  image?: string
  thumbnail?: string
  thumb?: string
  imageUrl?: string
  img?: string
}

type OrderViewModel = {
  orderId: string
  createdAt: string
  items: OrderItemView[]
  status?: OrderStatus
  cancelReason?: string
  cancelRequestedAt?: string
  shipping: {
    buyerName: string
    zipcode: string
    address1: string
    address2: string
  }
  paymentMethodLabel: string
  totals: {
    listPriceTotal: number
    salePriceTotal: number
    discountTotal: number
    shippingFee: number
    total: number
  }
  orderPk?: number
  itemsLoading?: boolean
  itemsError?: string
}

const orders = ref<OrderViewModel[]>([])
const router = useRouter()
const isModalOpen = ref(false)
const selectedOrderId = ref<string | null>(null)
const cancelReasonCategory = ref('')
const cancelError = ref('')
const cancelReasons = [
  '단순 변심',
  '가격 변동(쿠폰·프로모션) 불만',
  '옵션/수량/정보 선택 오류',
  '배송 지연(일정 문제)',
  '재고 없음/판매자 사정으로 취소(품절 포함)',
  '기타',
]

const statusLabel = (status?: OrderStatus) => {
  const map: Record<string, string> = {
    CREATED: '주문 생성',
    PAID: '결제 완료',
    CANCEL_REQUESTED: '취소 요청',
    CANCELLED: '취소 완료',
    COMPLETED: '구매 확정',
    REFUND_REQUESTED: '환불 요청',
    REFUND_REJECTED: '환불 거절',
    REFUNDED: '환불 완료',
  }
  return map[status ?? 'PAID'] ?? '결제 완료'
}

const totalCount = computed(() => orders.value.length)

const mapOrderSummaryToView = (order: OrderSummaryResponse): OrderViewModel => ({
  orderId: String(order.order_number ?? ''),
  createdAt: String(order.created_at ?? ''),
  items: [],
  status: order.status ?? 'PAID',
  cancelReason: order.cancel_reason ?? undefined,
  cancelRequestedAt: order.cancel_requested_at ?? undefined,
  shipping: {
    buyerName: '',
    zipcode: '',
    address1: '',
    address2: '',
  },
  paymentMethodLabel: '토스페이',
  totals: {
    listPriceTotal: Number(order.order_amount ?? 0) || 0,
    salePriceTotal: Number(order.order_amount ?? 0) || 0,
    discountTotal: 0,
    shippingFee: 0,
    total: Number(order.order_amount ?? 0) || 0,
  },
  orderPk: Number(order.order_id ?? 0) || undefined,
  itemsLoading: false,
  itemsError: '',
})

const load = async () => {
  try {
    const response = await getMyOrders()
    orders.value = Array.isArray(response) ? response.map(mapOrderSummaryToView) : []
    await preloadOrderItems(orders.value)
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      router.push('/login').catch(() => {})
      return
    }
    orders.value = []
  }
}

const formatDate = (value: string) => {
  const d = new Date(value)
  const yy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yy}.${mm}.${dd}`
}

const formatDateTime = (value: string) => {
  const d = new Date(value)
  const yy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yy}.${mm}.${dd} ${hh}:${mi}`
}

const formatPrice = (value: number) => `${value.toLocaleString('ko-KR')}원`

const titleOf = (order: { items: OrderItemView[] }) => {
  const base = order.items[0]?.name ?? '상품'
  if (order.items.length > 1) return `${base} 외 ${order.items.length - 1}건`
  return base
}

const fallbackTitleItem: OrderItemView = {
  productId: '',
  name: '상품',
  quantity: 1,
  price: 0,
  originalPrice: 0,
  discountRate: 0,
}

const modalTitle = (orderId: string) => {
  const order = orders.value.find((item) => item.orderId === orderId)
  return titleOf(order ?? { items: [fallbackTitleItem] })
}

const productImageOf = (productId: string) => {
  const p = productsData.find((x: any) => String(x.product_id) === String(productId))
  return String(p?.imageUrl ?? '')
}

const thumbOf = (order: OrderViewModel) => {
  const first = order.items?.[0]
  const direct = first?.image ?? first?.thumbnail ?? first?.thumb ?? first?.imageUrl ?? first?.img ?? ''
  if (direct) return String(direct)

  const pid = first?.productId ?? null

  return pid != null && String(pid) !== '' ? productImageOf(String(pid)) : ''
}

const quantityOf = (order: OrderViewModel) =>
  order.items.reduce((sum, item) => sum + item.quantity, 0)

const canRequestCancel = (status?: OrderStatus) =>
  (status ?? 'PAID') === 'CREATED' || (status ?? 'PAID') === 'PAID'

const canViewReason = (status?: OrderStatus) =>
  (status ?? 'PAID') === 'CANCEL_REQUESTED' ||
  (status ?? 'PAID') === 'REFUND_REQUESTED' ||
  (status ?? 'PAID') === 'CANCELLED' ||
  (status ?? 'PAID') === 'REFUNDED' ||
  (status ?? 'PAID') === 'REFUND_REJECTED'

const isTerminalStatus = (status?: OrderStatus) =>
  (status ?? 'PAID') === 'CANCELLED' ||
  (status ?? 'PAID') === 'REFUNDED' ||
  (status ?? 'PAID') === 'COMPLETED'
const cancelReasonTitle = (status?: OrderStatus) =>
  (status ?? 'PAID') === 'REFUND_REQUESTED' ? '환불 사유 보기' : '취소 사유 보기'

const openModal = (orderId: string) => {
  selectedOrderId.value = orderId
  cancelReasonCategory.value = ''
  cancelError.value = ''
  isModalOpen.value = true
}

const closeModal = () => {
  isModalOpen.value = false
  selectedOrderId.value = null
  cancelReasonCategory.value = ''
  cancelError.value = ''
}

const submitCancel = async () => {
  if (!selectedOrderId.value) return
  if (!cancelReasonCategory.value.trim()) {
    cancelError.value = '취소 사유를 선택해주세요.'
    return
  }
  const targetOrder = orders.value.find((order) => order.orderId === selectedOrderId.value)
  const numericId = Number(targetOrder?.orderPk)
  if (!Number.isFinite(numericId)) {
    cancelError.value = '주문 정보를 확인할 수 없습니다.'
    return
  }
  try {
    await cancelOrder(numericId, cancelReasonCategory.value)
    await load()
    closeModal()
  } catch {
    cancelError.value = '취소 요청에 실패했습니다.'
  }
}

const resolveItemName = (productId: string, index: number) => {
  const p = productsData.find((x: any) => String(x.product_id) === String(productId))
  return String(p?.name ?? `상품 ${index + 1}`)
}

const itemLoadErrorMessage = '상품 정보를 불러오지 못했습니다. 다시 시도해주세요.'

const handleItemsToggle = async (order: OrderViewModel, event: Event) => {
  const target = event.target
  if (!(target instanceof HTMLDetailsElement)) return
  if (!target.open) return
  if (order.items.length) return
  if (order.itemsLoading) return

  order.itemsLoading = true
  order.itemsError = ''

  const numericId = Number(order.orderPk ?? order.orderId)
  if (!Number.isFinite(numericId)) {
    order.itemsError = itemLoadErrorMessage
    order.itemsLoading = false
    return
  }

  try {
    const response = await getMyOrderDetail(numericId)
    order.items = response.items.map((item, index) => ({
      productId: String(item.product_id),
      name: String(item.product_name ?? resolveItemName(String(item.product_id), index)),
      quantity: item.quantity,
      price: item.unit_price,
      originalPrice: item.unit_price,
      discountRate: 0,
    }))
  } catch {
    order.itemsError = itemLoadErrorMessage
  } finally {
    order.itemsLoading = false
  }
}

const handleReasonToggle = async (order: OrderViewModel, event: Event) => {
  const target = event.target
  if (!(target instanceof HTMLDetailsElement)) return
  if (!target.open) return
  if (order.cancelReason && order.cancelReason.trim()) return

  const numericId = Number(order.orderPk ?? order.orderId)
  if (!Number.isFinite(numericId)) return

  try {
    const response = (await getMyOrderDetail(numericId)) as OrderDetailWithCancel
    if (typeof response.cancel_reason === 'string' && response.cancel_reason.trim()) {
      order.cancelReason = response.cancel_reason.trim()
    }
    const requestedAt = response.cancel_requested_at ?? response.updated_at
    if (requestedAt) {
      order.cancelRequestedAt = String(requestedAt)
    }
  } catch {
    return
  }
}

const preloadOrderItems = async (targets: OrderViewModel[]) => {
  const pending = targets.filter((order) => {
    if (order.items.length) return false
    if (order.itemsLoading) return false
    const numericId = Number(order.orderPk ?? order.orderId)
    return Number.isFinite(numericId)
  })
  if (!pending.length) return

  await Promise.allSettled(
    pending.map(async (order) => {
      order.itemsLoading = true
      order.itemsError = ''
      try {
        const response = await getMyOrderDetail(Number(order.orderPk ?? order.orderId))
        order.items = response.items.map((item, index) => ({
          productId: String(item.product_id),
          name: String(item.product_name ?? resolveItemName(String(item.product_id), index)),
          quantity: item.quantity,
          price: item.unit_price,
          originalPrice: item.unit_price,
          discountRate: 0,
        }))
      } catch {
        order.itemsError = itemLoadErrorMessage
      } finally {
        order.itemsLoading = false
      }
    }),
  )
}

onMounted(() => {
  load()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="주문내역" />
    <p class="count">나의 주문 내역 ({{ totalCount }})</p>

    <div v-if="!orders.length" class="history-empty">
      <p>주문 내역이 없습니다.</p>
      <RouterLink to="/products" class="link">상품 보러가기</RouterLink>
    </div>

    <section v-else class="history-list">
      <article v-for="order in orders" :key="order.orderId" class="history-card">
        <div class="card-grid">
          <div class="info">
            <div class="info__row header">
              <div class="thumb ds-thumb-frame ds-thumb-square" :class="{ 'thumb--empty': !thumbOf(order) }">
                <img v-if="thumbOf(order)" class="ds-thumb-img" :src="thumbOf(order)" :alt="order.items[0]?.name || '상품'" />
                <span v-else class="thumb__ph">DESKIT</span>
              </div>
              <div class="header-block">
                <h3 class="title">{{ titleOf(order) }}</h3>
                <div class="meta-row">
                  <span class="order-id">주문번호 · {{ order.orderId }}</span>
                  <span class="date">{{ formatDate(order.createdAt) }}</span>
                </div>
              </div>
            </div>
            <details v-if="canViewReason(order.status)" class="reason-disclosure" @toggle="handleReasonToggle(order, $event)">
              <summary class="reason-summary">{{ cancelReasonTitle(order.status) }}</summary>
              <div class="reason-panel">
                <div class="reason-meta">
                  <span class="reason-meta__label">취소 요청</span>
                  <span class="reason-meta__time">
                    {{
                      formatDateTime(
                        order.cancelRequestedAt ? String(order.cancelRequestedAt) : order.createdAt,
                      )
                    }}
                  </span>
                </div>
                <p class="reason-text">
                  {{ order.cancelReason?.trim() ? order.cancelReason : '사유가 입력되지 않았습니다.' }}
                </p>
              </div>
            </details>

            <details class="items-dropdown" @toggle="handleItemsToggle(order, $event)">
              <summary>주문 상품 보기 ({{ order.items.length }})</summary>
              <div class="items-list">
                <div class="items-meta">
                  <span>총금액 · {{ formatPrice(order.totals.total) }}</span>
                  <span>수량 · {{ quantityOf(order) }}개</span>
                </div>
                <div class="items-header">
                  <span>상품</span>
                  <span>구매가(합계)</span>
                </div>
                <p v-if="order.itemsError" class="error">
                  {{ order.itemsError }}
                </p>
                <p v-else-if="order.itemsLoading" class="error">
                  상품 정보를 불러오는 중입니다.
                </p>
                <div
                  v-for="(item, idx) in order.items"
                  :key="item.productId ? String(item.productId) : `${idx}-${item.name}`"
                  class="item-row"
                >
                  <RouterLink
                    v-if="item.productId"
                    :to="`/products/${item.productId}`"
                    class="item-link"
                  >
                    {{ item.name }}
                    <span v-if="item.quantity > 1" class="item-qty">x{{ item.quantity }}</span>
                  </RouterLink>
                  <span v-else class="item-name">
                    {{ item.name }}
                    <span v-if="item.quantity > 1" class="item-qty">x{{ item.quantity }}</span>
                  </span>
                  <span class="item-price">
                    {{ (item.price * item.quantity).toLocaleString('ko-KR') }}원
                  </span>
                </div>
              </div>
            </details>
          </div>

          <div class="actions">
            <div class="status-block">
              <span class="status-pill" :data-status="order.status ?? 'PAID'">
                {{ statusLabel(order.status) }}
              </span>
            </div>
            <div class="action-block">
              <button
                v-if="canRequestCancel(order.status) && !isTerminalStatus(order.status)"
                type="button"
                class="btn cancel"
                @click="openModal(order.orderId)"
              >
                취소 요청
              </button>
            </div>
          </div>
        </div>
      </article>
    </section>

    <transition name="fade">
      <div v-if="isModalOpen" class="modal-overlay" @click.self="closeModal">
        <div class="modal">
          <h3 class="modal__title">주문 취소</h3>
          <p class="modal__subtitle">취소 사유를 선택해주세요.</p>
          <div v-if="selectedOrderId" class="modal__summary">
            <p>주문번호: {{ selectedOrderId }}</p>
            <p>{{ modalTitle(selectedOrderId) }}</p>
          </div>
          <label class="field">
            <span class="field__label">취소 사유</span>
            <select v-model="cancelReasonCategory">
              <option value="" disabled>사유를 선택해주세요</option>
              <option v-for="reason in cancelReasons" :key="reason" :value="reason">
                {{ reason }}
              </option>
            </select>
            <p v-if="cancelError" class="error">{{ cancelError }}</p>
          </label>
          <div class="modal__actions">
            <button type="button" class="btn ghost" @click="closeModal">닫기</button>
            <button type="button" class="btn primary" @click="submitCancel">신청</button>
          </div>
        </div>
      </div>
    </transition>
  </PageContainer>
</template>

<style scoped>
.count {
  margin: 4px 0 14px;
  color: var(--text-muted);
  font-weight: 800;
}

.history-empty {
  border: 1px dashed var(--border-color);
  padding: 18px;
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

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-card {
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface);
  padding: 14px;
  --panel-max-width: 820px;
  --panel-width: min(100%, var(--panel-max-width));
}

.card-grid {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  align-items: start;
}

.header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.header-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.order-id {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info__row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.title {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
}

.date {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.items-dropdown {
  margin-top: 12px;
}

.items-dropdown summary {
  cursor: pointer;
  list-style: none;
  font-weight: 800;
  color: var(--primary-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  width: var(--panel-width);
  max-width: var(--panel-max-width);
  margin: 0;
  padding: 6px 8px;
  border-radius: 8px;
  transition: background 0.2s ease;
}

.items-dropdown summary::-webkit-details-marker {
  display: none;
}

.items-dropdown summary:hover,
.items-dropdown summary:focus-visible {
  background: #f2f2f2;
}

.items-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.92rem;
  padding: 2px 2px 6px;
}

.reason-disclosure {
  margin-top: 10px;
}

.reason-summary {
  cursor: pointer;
  font-weight: 800;
  color: var(--text-muted);
  list-style: none;
  display: flex;
  align-items: center;
  gap: 4px;
  width: var(--panel-width);
  max-width: var(--panel-max-width);
  margin: 0;
  padding: 6px 8px;
  border-radius: 8px;
  transition: background 0.2s ease;
}

.reason-summary::-webkit-details-marker {
  display: none;
}

.reason-summary::after {
  content: '▼';
  font-weight: 900;
  opacity: 0.7;
}

.reason-disclosure[open] .reason-summary::after {
  content: '▲';
}

.reason-summary:hover,
.reason-summary:focus-visible {
  background: #f2f2f2;
}

.reason-panel {
  margin-top: 8px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: #f5f5f5;
  padding: 10px;
  width: var(--panel-width);
  max-width: var(--panel-max-width);
  margin: 0;
}

.reason-text {
  margin: 0;
  color: var(--text-strong);
  font-weight: 700;
  line-height: 1.45;
}

.reason-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 0 0 8px;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.92rem;
}

.reason-meta__label {
  font-weight: 800;
}

.reason-meta__time {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.thumb {
  width: 72px;
  height: 72px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.thumb__ph {
  font-weight: 900;
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.06em;
}


.items-list {
  margin-top: 8px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: #f5f5f5;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: var(--panel-width);
  max-width: var(--panel-max-width);
  margin: 0;
}

.items-dropdown[open] .items-list {
  border-color: rgba(17, 24, 39, 0.18);
}

.reason-disclosure[open] .reason-panel {
  border: 1px solid rgba(17, 24, 39, 0.18);
}

.items-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 2px 8px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-muted);
  font-weight: 700;
  font-size: 12px;
}

.item-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 6px;
}

.item-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.item-name {
  font-weight: 800;
  color: var(--text-strong);
  flex: 1;
  min-width: 0;
}

.item-price {
  font-variant-numeric: tabular-nums;
  color: var(--text-strong);
  font-weight: 800;
  white-space: nowrap;
}

.item-qty {
  color: var(--text-muted);
  font-weight: 700;
  margin-left: 4px;
}

.item-link {
  display: block;
  min-width: 0;
  font-weight: 800;
  color: var(--primary-color, var(--text-strong));
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-link:focus-visible,
.item-link:hover {
  text-decoration: underline;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
  min-width: 160px;
}

.action-block {
  margin-top: auto;
  align-self: flex-end;
}

.status-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}

.status-pill {
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 8px 12px;
  background: var(--surface-weak);
  color: var(--text-strong);
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 120px;
  font-size: 13px;
  cursor: default;
}

.status-pill[data-status='PAID'] {
  border-color: rgba(34, 197, 94, 0.55);
  color: #16a34a;
  background: rgba(34, 197, 94, 0.1);
}

.status-pill[data-status='CREATED'] {
  opacity: 0.9;
}

.status-pill[data-status='CANCEL_REQUESTED'] {
  border-color: rgba(209, 67, 67, 0.5);
  color: #d14343;
  background: rgba(209, 67, 67, 0.1);
}

.status-pill[data-status='CANCELED'] {
  opacity: 0.65;
}

.status-pill[data-status='REFUND_REJECTED'] {
  border-color: #d14343;
  color: #d14343;
  background: rgba(209, 67, 67, 0.1);
}

.status-pill[data-status='REFUNDED'] {
  opacity: 0.8;
}

.btn {
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: var(--surface);
  font-weight: 800;
  cursor: pointer;
  min-height: 40px;
  min-width: 96px;
}

.btn.cancel {
  border: none;
  background: transparent;
  color: var(--text-strong);
  padding: 0;
  font-size: 13px;
  font-weight: 700;
  text-decoration: underline;
}

.btn.ghost {
  color: var(--text-strong);
}

.btn.primary {
  background: var(--primary-color, #111827);
  border-color: var(--primary-color, #111827);
  color: #fff;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #e5e7eb;
  border-color: var(--border-color);
  color: var(--text-muted);
}

.btn.cancel:hover,
.btn.cancel:focus-visible {
  text-decoration: underline;
  outline: none;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  z-index: 20;
}

.modal {
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 16px;
  width: min(420px, 100%);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.12);
}

.modal__title {
  margin: 0 0 4px;
  font-size: 1.1rem;
  font-weight: 900;
}

.modal__subtitle {
  margin: 0 0 10px;
  color: var(--text-muted);
  font-weight: 700;
}

.modal__summary {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px;
  margin-bottom: 10px;
  background: #f5f5f5;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field__label {
  font-weight: 800;
  color: var(--text-strong);
}

.field textarea {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px;
  resize: vertical;
  min-height: 80px;
}

.field select {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px;
  background: var(--surface);
  font-weight: 700;
  color: var(--text-strong);
  outline: none;
}

.field select:focus-visible {
  outline: 2px solid #d1d5db;
  outline-offset: 2px;
}

.error {
  margin: 0;
  color: #d14343;
  font-weight: 800;
  font-size: 0.92rem;
}

.modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 860px) {
  .card-grid {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }
  .actions {
    align-items: flex-start;
  }
  .status-block {
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
  .header {
    align-items: center;
  }
  .actions {
    align-items: flex-start;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
    min-width: 0;
  }
  .status-block {
    align-items: flex-start;
  }
}
</style>
