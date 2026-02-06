<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import { getSellerOrders } from '../../api/sellerOrders'
import type { SellerOrderSummaryResponse } from '../../api/sellerOrders'

type StatusFilter = '' | 'PAID' | 'CANCEL_REQUESTED' | 'CANCELLED'

const route = useRoute()
const router = useRouter()

const orders = ref<SellerOrderSummaryResponse[]>([])
const errorMessage = ref('')
const isLoading = ref(false)
const statusFilter = ref<StatusFilter>('')
const page = ref(0)
const size = ref(10)
const totalPages = ref(0)
const totalElements = ref(0)

const statusLabel = (status?: string) => {
  const map: Record<string, string> = {
    CREATED: '주문 생성',
    PAID: '결제 완료',
    CANCEL_REQUESTED: '취소 요청',
    CANCELLED: '취소 완료',
  }
  return map[status ?? ''] ?? '알 수 없음'
}

const formatDate = (value?: string) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '-'
  const yy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yy}.${mm}.${dd}`
}

const formatPrice = (value?: number) => `${(value ?? 0).toLocaleString('ko-KR')}원`

const summaryText = (order: SellerOrderSummaryResponse) => {
  const name = order.first_product_name || '상품'
  const count = order.item_count ?? 0
  if (count <= 1) return name
  return `${name} 외 ${count - 1}건`
}

const load = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const response = await getSellerOrders({
      status: statusFilter.value || null,
      page: page.value,
      size: size.value,
    })
    orders.value = response.content || []
    totalPages.value = response.totalPages ?? 0
    totalElements.value = response.totalElements ?? 0
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 403) {
      errorMessage.value = '권한이 없습니다.'
    } else {
      errorMessage.value = '주문 목록을 불러오지 못했습니다.'
    }
    orders.value = []
    totalPages.value = 0
    totalElements.value = 0
  } finally {
    isLoading.value = false
  }
}

const syncFromQuery = () => {
  const queryPage = Number(route.query.page ?? 0)
  const querySize = Number(route.query.size ?? 10)
  const queryStatus = String(route.query.status ?? '')
  page.value = Number.isFinite(queryPage) && queryPage >= 0 ? queryPage : 0
  size.value = Number.isFinite(querySize) && querySize > 0 ? querySize : 10
  statusFilter.value = (queryStatus || '') as StatusFilter
}

const updateQuery = () => {
  router.replace({
    query: {
      ...route.query,
      page: String(page.value),
      size: String(size.value),
      status: statusFilter.value || undefined,
    },
  })
}

const canGoPrev = computed(() => page.value > 0)
const canGoNext = computed(() => page.value + 1 < totalPages.value)

const goPrev = () => {
  if (!canGoPrev.value) return
  page.value -= 1
  updateQuery()
}

const goNext = () => {
  if (!canGoNext.value) return
  page.value += 1
  updateQuery()
}

const handleFilterChange = () => {
  page.value = 0
  updateQuery()
}

const openDetail = (orderId?: number) => {
  if (!orderId) return
  router.push(`/seller/orders/${orderId}`).catch(() => {})
}

onMounted(() => {
  syncFromQuery()
  load()
})

watch(
  () => route.query,
  () => {
    syncFromQuery()
    load()
  },
)
</script>

<template>
  <section class="seller-orders">
    <PageHeader eyebrow="DESKIT" title="주문 내역" />

    <div class="toolbar">
      <label class="filter">
        <span>상태</span>
        <select v-model="statusFilter" @change="handleFilterChange">
          <option value="">전체</option>
          <option value="PAID">결제 완료</option>
          <option value="CANCEL_REQUESTED">취소 요청</option>
          <option value="CANCELLED">취소 완료</option>
        </select>
      </label>
      <span class="count">총 {{ totalElements }}건</span>
    </div>

    <div v-if="errorMessage" class="empty">
      {{ errorMessage }}
    </div>
    <div v-else-if="isLoading" class="empty">불러오는 중...</div>
    <div v-else-if="!orders.length" class="empty">주문 내역이 없습니다.</div>
    <div v-else class="table">
      <div class="row header">
        <span>주문번호</span>
        <span>상품</span>
        <span>상태</span>
        <span>금액</span>
        <span>주문일</span>
      </div>
      <button
        v-for="order in orders"
        :key="order.order_id"
        type="button"
        class="row data"
        @click="openDetail(order.order_id)"
      >
        <span class="mono">{{ order.order_number }}</span>
        <span>{{ summaryText(order) }}</span>
        <span class="status">{{ statusLabel(order.status) }}</span>
        <span>{{ formatPrice(order.order_amount) }}</span>
        <span>{{ formatDate(order.created_at) }}</span>
      </button>
    </div>

    <div class="pagination">
      <button type="button" class="btn ghost" :disabled="!canGoPrev" @click="goPrev">이전</button>
      <span class="page">{{ page + 1 }} / {{ totalPages || 1 }}</span>
      <button type="button" class="btn ghost" :disabled="!canGoNext" @click="goNext">다음</button>
    </div>
  </section>
</template>

<style scoped>
.seller-orders {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.filter select {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 10px;
  background: var(--surface);
}

.count {
  font-size: 14px;
  color: var(--text-muted, #6b7280);
}

.empty {
  padding: 32px 12px;
  text-align: center;
  border: 1px dashed var(--border-color);
  border-radius: 12px;
  color: var(--text-muted, #6b7280);
}

.table {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

.row {
  display: grid;
  grid-template-columns: 1.4fr 2fr 1fr 1fr 1fr;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
}

.row.header {
  background: var(--surface);
  font-size: 13px;
  color: var(--text-muted, #6b7280);
}

.row.data {
  background: transparent;
  border: none;
  border-top: 1px solid var(--border-color);
  text-align: left;
  cursor: pointer;
  transition: background 0.2s ease;
}

.row.data:hover {
  background: rgba(17, 24, 39, 0.04);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
}

.status {
  font-weight: 600;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.btn {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 12px;
  background: transparent;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page {
  font-size: 14px;
  color: var(--text-muted, #6b7280);
}

@media (max-width: 768px) {
  .row {
    grid-template-columns: 1.2fr 1.6fr 1fr;
  }

  .row span:nth-child(4),
  .row span:nth-child(5) {
    display: none;
  }
}
</style>
