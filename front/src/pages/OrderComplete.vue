<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { getMyOrderDetail } from '../api/orders'
import { productsData } from '../lib/products-data'
import { loadLastOrder } from '../lib/order/order-storage'

const router = useRouter()
const route = useRoute()
const receipt = ref<{
  orderId: string
  createdAt: string
  items: Array<{
    productId: string
    name: string
    quantity: number
    price: number
  }>
  status?: string
  shipping: {
    buyerName: string
    zipcode: string
    address1: string
    address2: string
  }
  paymentMethodLabel: string
  totals: {
    total: number
  }
} | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')

const formatPrice = (value: number) => `${value.toLocaleString('ko-KR')}원`

const statusConfig = computed(() => {
  const status = receipt.value?.status
  if (status === 'PAYMENT_PENDING' || status === 'CREATED') {
    return {
      title: '결제 진행 중입니다.',
      description: '결제 확인 후 주문이 완료됩니다.',
      ctaLabel: '주문 내역 보기',
      ctaPath: '/my/orders',
    }
  }
  if (status === 'COMPLETED' || status === 'PAID') {
    return {
      title: '주문이 완료되었습니다.',
      description: '주문 내역에서 배송 정보를 확인하세요.',
      ctaLabel: '주문 내역 보기',
      ctaPath: '/my/orders',
    }
  }
  if (
    status === 'CANCELED' ||
    status === 'CANCELLED' ||
    status === 'CANCEL_REQUESTED' ||
    status === 'REFUNDED' ||
    status === 'REFUND_REJECTED'
  ) {
    return {
      title: '주문이 취소되었습니다.',
      description: '취소된 주문입니다.',
      ctaLabel: '메인으로 이동',
      ctaPath: '/',
    }
  }
  return {
    title: '결제가 완료되었습니다.',
    description: '주문 정보가 준비되었습니다.',
    ctaLabel: '메인으로 이동',
    ctaPath: '/',
  }
})

const resolveItemName = (productId: string, index: number) => {
  const product = productsData.find((item: any) => String(item.product_id) === String(productId))
  return String(product?.name ?? `상품 ${index + 1}`)
}

const loadOrderDetail = async (orderId: string) => {
  if (!orderId) {
    errorMessage.value = '주문 정보를 찾을 수 없습니다.'
    receipt.value = null
    return
  }

  const numericId = Number(orderId)
  if (!Number.isFinite(numericId)) {
    errorMessage.value = '주문 정보를 찾을 수 없습니다.'
    receipt.value = null
    return
  }

  isLoading.value = true
  errorMessage.value = ''
  receipt.value = null
  try {
    const response = await getMyOrderDetail(numericId)
    if (!response?.order_id) {
      errorMessage.value = '주문 정보를 찾을 수 없습니다.'
      receipt.value = null
      return
    }
    const cached = loadLastOrder()
    const resolvedOrderId = response.order_number || String(response.order_id)
    const cachedMatches = cached?.orderId === resolvedOrderId
    const cachedItems = cachedMatches ? cached?.items ?? [] : []
    const items = response.items.map((item, index) => {
      const productId = String(item.product_id)
      const cachedItem = cachedItems.find((cachedItem) => cachedItem.productId === productId)
      return {
        productId,
        name: cachedItem?.name ?? String(item.product_name ?? resolveItemName(productId, index)),
        quantity: item.quantity,
        price: item.unit_price,
      }
    })
    const total = Number(response.order_amount ?? 0) || 0
    receipt.value = {
      orderId: resolvedOrderId,
      createdAt: response.created_at,
      items,
      status: response.status ?? undefined,
      shipping: cachedMatches && cached
        ? cached.shipping
        : {
            buyerName: '',
            zipcode: '',
            address1: '',
            address2: '',
          },
      paymentMethodLabel: cachedMatches && cached ? cached.paymentMethodLabel : '토스페이',
      totals: {
        total,
      },
    }
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      router.push('/login').catch(() => {})
      return
    }
    if (status === 404) {
      errorMessage.value = '주문 정보를 찾을 수 없습니다.'
      receipt.value = null
      return
    }
    errorMessage.value = '주문 정보를 불러오지 못했습니다.'
    receipt.value = null
  } finally {
    isLoading.value = false
  }
}

watch(
  () => route.params.orderId,
  (orderId) => {
    loadOrderDetail(String(orderId ?? ''))
  },
  { immediate: true },
)

const handlePrimaryAction = () => {
  router.push(statusConfig.value.ctaPath).catch(() => {})
}
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="주문 완료" />

    <div class="checkout-steps">
      <span class="checkout-step">01 장바구니</span>
      <span class="checkout-step__divider">></span>
      <span class="checkout-step">02 주문/결제</span>
      <span class="checkout-step__divider">></span>
      <span class="checkout-step checkout-step--active">03 주문 완료</span>
    </div>

    <div v-if="isLoading" class="checkout-empty">
      <p>주문 정보를 불러오는 중입니다.</p>
    </div>

    <div v-else-if="!receipt" class="checkout-empty">
      <p>{{ errorMessage || '주문 정보가 없습니다.' }}</p>
      <RouterLink to="/cart" class="link">장바구니로 이동</RouterLink>
      <RouterLink to="/" class="link">메인으로 이동</RouterLink>
    </div>

    <section v-else class="panel">
      <div class="success">
        <div class="success-icon">✅</div>
        <div>
          <h2 class="success-title">{{ statusConfig.title }}</h2>
          <p class="success-desc">{{ statusConfig.description }} 주문번호 : {{ receipt.orderId }}</p>
        </div>
      </div>

      <p class="meta">
        {{ receipt.shipping.buyerName || '고객' }} 고객의
        {{ receipt.items[0]?.name }} 등 {{receipt.items.length}}건에 대한 결제 내역입니다.
      </p>

      <div class="table">
        <div class="table-head">
          <span>번호</span>
          <span>상품명</span>
          <span>개수</span>
          <span>가격</span>
          <span>결제 금액</span>
        </div>
        <div
          v-for="(item, idx) in receipt.items"
          :key="item.productId"
          class="table-row"
        >
          <span>{{ idx + 1 }}</span>
          <span class="ellipsis">{{ item.name }}</span>
          <span>{{ item.quantity }}</span>
          <span>{{ formatPrice(item.price) }}</span>
          <span>{{ formatPrice(item.price * item.quantity) }}</span>
        </div>
      </div>

      <div class="summary">
        <div class="summary-row">
          <span>총 결제 금액</span>
          <strong>{{ formatPrice(receipt.totals.total) }}</strong>
        </div>
        <div class="summary-row">
          <span>결제 수단</span>
          <strong>{{ receipt.paymentMethodLabel }}</strong>
        </div>
        <div class="summary-row">
          <span>배송 주소</span>
          <strong>
            {{ receipt.shipping.zipcode }} {{ receipt.shipping.address1 }} {{ receipt.shipping.address2 }}
          </strong>
        </div>
        <div class="summary-row">
          <span>수령인</span>
          <strong>{{ receipt.shipping.buyerName || '고객' }}</strong>
        </div>
      </div>

      <div class="actions">
        <button type="button" class="btn primary" @click="handlePrimaryAction">
          {{ statusConfig.ctaLabel }}
        </button>
      </div>
    </section>
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

.panel {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.success {
  display: flex;
  align-items: center;
  gap: 12px;
}

.success-icon {
  font-size: 1.5rem;
}

.success-title {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 900;
}

.success-desc {
  margin: 2px 0 0;
  color: var(--text-muted);
  font-weight: 700;
}

.meta {
  margin: 0;
  color: var(--text-muted);
}

.table {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

.table-head,
.table-row {
  display: grid;
  grid-template-columns: 60px 1fr 80px 120px 140px;
  gap: 8px;
  padding: 10px 12px;
  align-items: center;
}

.table-head {
  background: var(--surface-weak);
  font-weight: 800;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary {
  border-top: 1px solid var(--border-color);
  padding-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.summary-row strong {
  font-variant-numeric: tabular-nums;
}

.actions {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.btn {
  border: 1px solid var(--border-color);
  background: var(--primary-color);
  color: #fff;
  font-weight: 900;
  border-radius: 12px;
  padding: 12px 16px;
  cursor: pointer;
}

@media (max-width: 900px) {
  .table-head,
  .table-row {
    grid-template-columns: 40px 1fr 60px 100px 120px;
  }
}

@media (max-width: 640px) {
  .table-head,
  .table-row {
    grid-template-columns: 32px 1fr 52px 80px 100px;
    font-size: 0.92rem;
  }
}
</style>
