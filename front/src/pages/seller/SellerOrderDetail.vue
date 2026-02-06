<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import { getSellerOrderDetail } from '../../api/sellerOrders'
import type { SellerOrderDetailResponse } from '../../api/sellerOrders'

const route = useRoute()
const router = useRouter()

const order = ref<SellerOrderDetailResponse | null>(null)
const errorMessage = ref('')
const isLoading = ref(false)

const statusLabel = (status?: string) => {
  const map: Record<string, string> = {
    CREATED: '주문 생성',
    PAID: '결제 완료',
    CANCEL_REQUESTED: '취소 요청',
    CANCELLED: '취소 완료',
    REFUND_REQUESTED: '환불 요청',
    REFUND_REJECTED: '환불 거절',
    REFUNDED: '환불 완료',
  }
  return map[status ?? ''] ?? '알 수 없음'
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '-'
  const yy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yy}.${mm}.${dd} ${hh}:${mi}`
}

const formatPrice = (value?: number) => `${(value ?? 0).toLocaleString('ko-KR')}원`

const load = async () => {
  const raw = route.params.orderId
  const numericId = Number(raw)
  if (!Number.isFinite(numericId) || numericId <= 0) {
    errorMessage.value = '주문 정보를 확인할 수 없습니다.'
    return
  }
  isLoading.value = true
  errorMessage.value = ''
  try {
    order.value = await getSellerOrderDetail(numericId)
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 403) {
      errorMessage.value = '권한이 없습니다.'
    } else if (status === 404) {
      errorMessage.value = '주문을 찾을 수 없습니다.'
    } else {
      errorMessage.value = '주문 정보를 불러오지 못했습니다.'
    }
    order.value = null
  } finally {
    isLoading.value = false
  }
}

watch(
  () => route.params.orderId,
  () => {
    order.value = null
    errorMessage.value = ''
    load()
  },
  { immediate: true },
)
</script>

<template>
  <section class="seller-order-detail">
    <PageHeader eyebrow="DESKIT" title="주문 상세" />

    <div v-if="errorMessage" class="empty">
      {{ errorMessage }}
    </div>
    <div v-else-if="isLoading" class="empty">불러오는 중...</div>
    <div v-else-if="!order" class="empty">주문 정보가 없습니다.</div>
    <div v-else class="detail">
      <header class="card">
        <div class="header-row">
          <div>
            <p class="label">주문번호</p>
            <p class="value mono">{{ order.order_number }}</p>
          </div>
          <div>
            <p class="label">상태</p>
            <p class="value status">{{ statusLabel(order.status) }}</p>
          </div>
          <div>
            <p class="label">총금액</p>
            <p class="value">{{ formatPrice(order.order_amount) }}</p>
          </div>
        </div>
        <div class="meta">
          <div>
            <span class="label">주문일</span>
            <span class="value">{{ formatDateTime(order.created_at) }}</span>
          </div>
          <div>
            <span class="label">결제일</span>
            <span class="value">{{ formatDateTime(order.paid_at) }}</span>
          </div>
          <div>
            <span class="label">취소일</span>
            <span class="value">{{ formatDateTime(order.cancelled_at) }}</span>
          </div>
          <div>
            <span class="label">환불일</span>
            <span class="value">{{ formatDateTime(order.refunded_at) }}</span>
          </div>
        </div>
      </header>

      <section class="card">
        <h3 class="section-title">주문 상품</h3>
        <div v-if="!order.items?.length" class="empty-row">상품 정보가 없습니다.</div>
        <div v-else class="items">
          <div class="row header">
            <span>상품명</span>
            <span>수량</span>
            <span>단가</span>
            <span>합계</span>
          </div>
          <div
            v-for="(item, index) in order.items"
            :key="item.order_item_id ?? `${item.product_id}-${index}`"
            class="row data"
          >
            <span>{{ item.product_name ?? `상품 #${item.product_id}` }}</span>
            <span>{{ item.quantity ?? 0 }}</span>
            <span>{{ formatPrice(item.unit_price ?? 0) }}</span>
            <span>
              {{
                formatPrice(
                  item.subtotal_price ?? (item.unit_price ?? 0) * (item.quantity ?? 0),
                )
              }}
            </span>
          </div>
        </div>
      </section>

      <div class="actions">
        <button type="button" class="btn ghost" @click="router.push('/seller/orders')">
          목록으로 돌아가기
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.seller-order-detail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  background: var(--surface);
}

.header-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.label {
  font-size: 12px;
  color: var(--text-muted, #6b7280);
}

.value {
  font-size: 15px;
  font-weight: 600;
}

.status {
  color: var(--primary-color, #111827);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
}

.meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
  font-size: 13px;
}

.meta .value {
  font-weight: 500;
  margin-left: 6px;
}

.section-title {
  font-size: 16px;
  margin-bottom: 12px;
}

.items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.row {
  display: grid;
  grid-template-columns: 2fr 0.7fr 1fr 1fr;
  gap: 12px;
  align-items: center;
}

.row.header {
  font-size: 13px;
  color: var(--text-muted, #6b7280);
}

.row.data {
  padding: 10px 0;
  border-top: 1px solid var(--border-color);
}

.empty,
.empty-row {
  padding: 24px 12px;
  text-align: center;
  border: 1px dashed var(--border-color);
  border-radius: 12px;
  color: var(--text-muted, #6b7280);
}

.empty-row {
  margin-top: 8px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}

.btn {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 12px;
  background: transparent;
  cursor: pointer;
}

@media (max-width: 768px) {
  .header-row {
    grid-template-columns: 1fr;
  }

  .row {
    grid-template-columns: 1.6fr 0.6fr 0.8fr;
  }

  .row span:nth-child(4) {
    display: none;
  }
}
</style>
