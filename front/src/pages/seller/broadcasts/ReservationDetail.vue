<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../../../components/PageContainer.vue'
import QCardModal from '../../../components/QCardModal.vue'
import { cancelSellerBroadcast, fetchSellerBroadcastDetail, type BroadcastDetailResponse } from '../../../lib/live/api'
import { getBroadcastStatusLabel, normalizeBroadcastStatus } from '../../../lib/broadcastStatus'

const route = useRoute()
const router = useRouter()

const reservationId = computed(() => (typeof route.params.reservationId === 'string' ? route.params.reservationId : ''))

type SellerReservationDetail = {
  id: string
  title: string
  status: string
  datetime: string
  category: string
  notice: string
  thumb: string
  standbyThumb?: string
  products: Array<{ id: string; name: string; price: string; salePrice: string; stock: number; qty: number }>
  cueQuestions?: string[]
  cancelReason?: string
}

const detail = ref<SellerReservationDetail | null>(null)
const isLoading = ref(false)
const qCardIndex = ref(0)

const goBack = () => {
  router.back()
}

const goToList = () => {
  router.push('/seller/live?tab=scheduled').catch(() => {})
}

const openCueCard = () => {
  if (!detail.value?.cueQuestions?.length) return
  showCueCard.value = true
}

const showCueCard = ref(false)

const handleEdit = () => {
  router.push({ path: '/seller/live/create', query: { mode: 'edit', reservationId: reservationId.value } }).catch(() => {})
}

const handleCancel = () => {
  const ok = window.confirm('예약을 취소하시겠습니까?')
  if (!ok) return
  if (!detail.value) return
  cancelSellerBroadcast(Number(detail.value.id))
    .then(() => {
      if (detail.value) {
        detail.value.status = 'CANCELED'
      }
      window.alert('예약이 취소되었습니다.')
      router.replace({ path: '/seller/live', query: { tab: 'scheduled' } }).catch(() => {})
    })
    .catch(() => {})
}

onBeforeUnmount(() => {
})

const scheduledWindow = computed(() => {
  if (!detail.value?.datetime) return ''
  const raw = detail.value.datetime
  const start = new Date(raw.replace(/\./g, '-').replace(' ', 'T'))
  const end = new Date(start.getTime() + 30 * 60 * 1000)
  const fmt = (d: Date) =>
    `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
  return `${raw} ~ ${fmt(end)}`
})

const cancelReason = computed(() => detail.value?.cancelReason ?? '사유가 등록되지 않았습니다.')
const isCancelled = computed(() => normalizeBroadcastStatus(detail.value?.status) === 'CANCELED')
const standbyImage = computed(() => detail.value?.standbyThumb || detail.value?.thumb || '')
const displayedCancelReason = computed(() =>
  isCancelled.value ? cancelReason.value : '',
)
const statusLabel = computed(() => getBroadcastStatusLabel(detail.value?.status))

const formatScheduledAt = (value?: string) => (value ? value.replace(/-/g, '.').replace('T', ' ').slice(0, 16) : '')

const formatCurrency = (amount: number) => `₩${amount.toLocaleString('ko-KR')}`

const mapDetail = (payload: BroadcastDetailResponse): SellerReservationDetail => ({
  id: String(payload.broadcastId),
  title: payload.title ?? '',
  status: payload.status ?? '',
  datetime: formatScheduledAt(payload.scheduledAt),
  category: payload.categoryName ?? '',
  notice: payload.notice ?? '',
  thumb: payload.thumbnailUrl ?? '',
  standbyThumb: payload.waitScreenUrl ?? payload.thumbnailUrl ?? '',
  products: (payload.products ?? []).map((item) => ({
    id: String(item.productId),
    name: item.name,
    price: formatCurrency(item.originalPrice ?? 0),
    salePrice: formatCurrency(item.bpPrice ?? item.originalPrice ?? 0),
    stock: item.productStockQty ?? item.stockQty ?? item.bpQuantity ?? 0,
    qty: item.bpQuantity ?? 0,
  })),
  cueQuestions: (payload.qcards ?? []).map((card) => card.question),
  cancelReason: payload.stoppedReason ?? undefined,
})

const loadDetail = async () => {
  const idValue = Number(reservationId.value)
  if (!reservationId.value || Number.isNaN(idValue)) {
    detail.value = null
    return
  }
  isLoading.value = true
  try {
    const payload = await fetchSellerBroadcastDetail(idValue)
    detail.value = mapDetail(payload)
  } catch {
    detail.value = null
  } finally {
    isLoading.value = false
  }
}

watch(reservationId, () => {
  loadDetail()
}, { immediate: true })
</script>

<template>
  <PageContainer v-if="detail">
    <h2 class="page-title">예약 상세</h2>
    <header class="detail-header">
      <button type="button" class="back-link" @click="goBack">← 뒤로 가기</button>
      <div class="detail-actions">
        <button type="button" class="btn ghost" :disabled="!(detail.cueQuestions?.length)" @click="openCueCard">
          큐카드 보기
        </button>
        <button type="button" class="btn" @click="goToList">목록으로</button>
      </div>
    </header>

    <section class="detail-card ds-surface">
      <div class="detail-title">
        <h2>{{ detail.title }}</h2>
        <span class="status-pill">{{ statusLabel }}</span>
      </div>
      <div class="detail-meta">
        <p><span>방송 예정 시간</span>{{ scheduledWindow }}</p>
        <p><span>카테고리</span>{{ detail.category }}</p>
        <p v-if="isCancelled" class="cancel-row">
          <span>취소 사유</span>
          <span :class="['cancel-value', { cancelled: isCancelled }]">{{ displayedCancelReason }}</span>
        </p>
      </div>
    </section>

    <section class="detail-card ds-surface notice-box">
      <h3>공지사항</h3>
      <p>{{ detail.notice }}</p>
    </section>

    <section class="detail-card ds-surface">
      <div class="card-head">
        <h3>방송 이미지</h3>
      </div>
      <div class="upload-grid">
        <div class="upload-col">
          <p class="upload-label">썸네일</p>
          <div class="upload-preview">
            <img :src="detail.thumb" :alt="detail.title" />
          </div>
        </div>
        <div class="upload-col">
          <p class="upload-label">대기화면</p>
          <div class="upload-preview">
            <img :src="standbyImage" :alt="`${detail.title} 대기화면`" />
          </div>
        </div>
      </div>
    </section>

    <section class="detail-card ds-surface">
      <div class="card-head">
        <h3>판매 상품 리스트</h3>
      </div>
      <div class="table-wrap">
        <table class="product-table">
          <thead>
            <tr>
              <th>상품명</th>
              <th>정가</th>
              <th>할인가</th>
              <th>재고</th>
              <th>판매 수량</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in detail.products" :key="item.id">
              <td>{{ item.name }}</td>
              <td>{{ item.price }}</td>
              <td class="sale">{{ item.salePrice }}</td>
              <td>{{ item.stock }}</td>
              <td>{{ item.qty }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div class="detail-footer">
      <div class="footer-actions">
        <button type="button" class="btn primary" @click="handleEdit">예약 수정</button>
        <button type="button" class="btn danger" @click="handleCancel">예약 취소</button>
      </div>
    </div>

    <QCardModal
      v-model="showCueCard"
      :q-cards="detail.cueQuestions || []"
      :initial-index="qCardIndex"
      @update:initialIndex="qCardIndex = $event"
    />
  </PageContainer>
  <PageContainer v-else>
    <h2 class="page-title">예약 상세</h2>
    <section class="detail-card ds-surface empty-state">
      <p>{{ isLoading ? '예약 정보를 불러오는 중입니다.' : '예약 정보를 찾을 수 없습니다.' }}</p>
      <button type="button" class="btn" @click="goToList">목록으로</button>
    </section>
  </PageContainer>
</template>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 8px;
  font-size: 1.5rem;
  font-weight: 900;
  color: var(--text-strong);
}

.back-link {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-weight: 800;
  cursor: pointer;
  padding: 6px 0;
}

.detail-actions {
  display: flex;
  gap: 10px;
  align-items: center;
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

.btn.ghost {
  background: transparent;
  color: var(--text-muted);
}

.btn.primary {
  background: var(--primary-color);
  color: #fff;
  border-color: transparent;
}

.btn.danger {
  background: #ef4444;
  color: #fff;
  border-color: transparent;
}

.detail-card {
  padding: 18px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
}

.modal__card {
  position: relative;
  width: min(480px, 92vw);
  padding: 18px;
  border-radius: 14px;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modal__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal__content {
  margin: 0;
  color: var(--text-strong);
  font-weight: 700;
}

.detail-title h2 {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 900;
  color: var(--text-strong);
}

.status-pill {
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 4px 10px;
  font-weight: 800;
  color: var(--primary-color);
  background: var(--surface-weak);
}

.detail-meta p {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-weight: 700;
  display: flex;
  gap: 12px;
}

.detail-meta span {
  min-width: 120px;
  color: var(--text-strong);
  font-weight: 800;
}

.cancel-row {
  align-items: center;
}

.cancel-value {
  color: var(--text-muted);
}

.cancel-value.cancelled {
  color: #ef4444;
}

.notice-box h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 900;
  color: var(--text-strong);
}

.notice-box p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  line-height: 1.5;
}

.upload-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.upload-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-label {
  margin: 0;
  font-weight: 800;
  color: var(--text-strong);
}

.upload-preview {
  position: relative;
  height: auto;
  aspect-ratio: 16 / 9;
  min-height: 180px;
  border-radius: 12px;
  background: var(--surface-weak);
  border: 1px solid var(--border-color);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.upload-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.upload-help {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.card-head h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 900;
  color: var(--text-strong);
}

.table-wrap {
  overflow-x: auto;
}

.product-table {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
}

.product-table th,
.product-table td {
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
  text-align: left;
  font-weight: 700;
  color: var(--text-strong);
}

.product-table thead th {
  background: var(--surface-weak);
  font-weight: 900;
}

.sale {
  color: #ef4444;
  font-weight: 900;
}

.detail-footer {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 16px;
}

.footer-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.empty-state {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 32px;
  color: var(--text-muted);
}

@media (max-width: 720px) {
  .detail-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .upload-grid {
    grid-template-columns: 1fr;
  }

  .footer-actions {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }
}
</style>
