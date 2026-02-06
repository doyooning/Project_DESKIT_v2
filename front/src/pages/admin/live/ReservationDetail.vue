<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QCardModal from '../../../components/QCardModal.vue'
import {
  cancelAdminBroadcast,
  fetchAdminBroadcastDetail,
  type BroadcastDetailResponse,
} from '../../../lib/live/api'
import { getBroadcastStatusLabel, normalizeBroadcastStatus } from '../../../lib/broadcastStatus'

const route = useRoute()
const router = useRouter()

type AdminReservationDetail = {
  id: string
  title: string
  status: string
  datetime: string
  category: string
  sellerName: string
  notice: string
  thumb: string
  standbyThumb?: string
  products: Array<{ id: string; name: string; price: string; salePrice: string; qty: number; stock: number }>
  cueQuestions?: string[]
  cancelReason?: string
}

const detail = ref<AdminReservationDetail | null>(null)
const isLoading = ref(false)
const showCueCard = ref(false)
const cueIndex = ref(0)

const reservationId = computed(() => (typeof route.params.reservationId === 'string' ? route.params.reservationId : ''))

const cancelReason = ref('')
const cancelDetail = ref('')
const showCancelModal = ref(false)
const cancelError = ref('')

const cancelReasonOptions = ['운영 정책 위반', '상품 준비 지연', '기타']

const formatScheduledAt = (scheduledAt?: string) => {
  if (!scheduledAt) return ''
  return scheduledAt.replace(/-/g, '.').replace('T', ' ').slice(0, 16)
}

const formatCurrency = (value: number) => `₩${value.toLocaleString('ko-KR')}`

const mapDetail = (payload: BroadcastDetailResponse): AdminReservationDetail => ({
  id: String(payload.broadcastId),
  title: payload.title ?? '',
  status: payload.status ?? '',
  datetime: formatScheduledAt(payload.scheduledAt),
  category: payload.categoryName ?? '',
  sellerName: payload.sellerName ?? '',
  notice: payload.notice ?? '',
  thumb: payload.thumbnailUrl ?? '',
  standbyThumb: payload.waitScreenUrl ?? payload.thumbnailUrl ?? '',
  products: (payload.products ?? []).map((item) => ({
    id: String(item.productId),
    name: item.name,
    price: formatCurrency(item.originalPrice ?? 0),
    salePrice: formatCurrency(item.bpPrice ?? item.originalPrice ?? 0),
    qty: item.bpQuantity ?? 0,
    stock: item.productStockQty ?? item.stockQty ?? item.bpQuantity ?? 0,
  })),
  cueQuestions: (payload.qcards ?? []).map((card) => card.question),
  cancelReason: payload.stoppedReason ?? undefined,
})

const loadDetail = async () => {
  if (!reservationId.value) {
    detail.value = null
    return
  }
  const idValue = Number(reservationId.value)
  if (Number.isNaN(idValue)) {
    detail.value = null
    return
  }
  isLoading.value = true
  try {
    const payload = await fetchAdminBroadcastDetail(idValue)
    detail.value = mapDetail(payload)
  } catch {
    detail.value = null
  } finally {
    isLoading.value = false
  }
}

const goBack = () => {
  router.back()
}

const goToList = () => {
  router.push('/admin/live?tab=scheduled').catch(() => {})
}

const openCueCard = () => {
  if (!detail.value?.cueQuestions?.length) return
  showCueCard.value = true
}

const openCancelModal = () => {
  if (!detail.value || normalizeBroadcastStatus(detail.value.status) === 'CANCELED') return
  showCancelModal.value = true
  cancelReason.value = ''
  cancelDetail.value = ''
  cancelError.value = ''
}

const closeCancelModal = () => {
  showCancelModal.value = false
  cancelReason.value = ''
  cancelDetail.value = ''
  cancelError.value = ''
}

const saveCancel = () => {
  if (!cancelReason.value) {
    cancelError.value = '취소 사유를 선택해주세요.'
    return
  }
  if (cancelReason.value === '기타' && !cancelDetail.value.trim()) {
    cancelError.value = '기타 사유를 입력해주세요.'
    return
  }
  const ok = window.confirm('예약을 취소하시겠습니까?')
  if (!ok) return
  if (!detail.value) return
  const reason = cancelReason.value === '기타' ? cancelDetail.value.trim() : cancelReason.value
  cancelAdminBroadcast(Number(detail.value.id), reason)
    .then(() => {
      if (detail.value) {
        detail.value = {
          ...detail.value,
          status: 'CANCELED',
          cancelReason: reason,
        }
      }
    })
    .finally(() => {
      closeCancelModal()
    })
}

const isCancelled = computed(() => normalizeBroadcastStatus(detail.value?.status) === 'CANCELED')
const standbyImage = computed(() => detail.value?.standbyThumb || detail.value?.thumb)
const statusLabel = computed(() => getBroadcastStatusLabel(detail.value?.status))
const scheduledWindow = computed(() => {
  if (!detail.value) return ''
  const raw = detail.value.datetime
  const start = new Date(raw.replace(/\./g, '-').replace(' ', 'T'))
  const end = new Date(start.getTime() + 30 * 60 * 1000)
  const fmt = (d: Date) => `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
  return `${raw} ~ ${fmt(end)}`
})

const cancelReasonText = computed(() => {
  if (!isCancelled.value) return ''
  return detail.value?.cancelReason || '사유가 등록되지 않았습니다.'
})

watch(reservationId, loadDetail, { immediate: true })
</script>

<template>
  <div v-if="detail" class="detail-wrap">
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
        <h3>{{ detail.title }}</h3>
        <span class="status-pill" :class="{ cancelled: isCancelled }">{{ statusLabel }}</span>
      </div>
      <div class="detail-meta">
        <p><span>방송 예정 시간</span>{{ scheduledWindow }}</p>
        <p><span>카테고리</span>{{ detail.category }}</p>
        <p><span>판매자</span>{{ detail.sellerName }}</p>
        <p v-if="isCancelled" class="cancel-row">
          <span>취소 사유</span>
          <span class="cancel-value">{{ cancelReasonText }}</span>
        </p>
      </div>
    </section>

    <section class="detail-card ds-surface notice-box">
      <h4>공지사항</h4>
      <p>{{ detail.notice }}</p>
    </section>

    <section class="detail-card ds-surface">
      <div class="card-head">
        <h4>방송 이미지</h4>
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
        <h4>판매 상품 리스트</h4>
      </div>
      <div class="table-wrap">
        <table class="product-table">
          <thead>
            <tr>
              <th>상품명</th>
              <th>정가</th>
              <th>할인가</th>
              <th>수량</th>
              <th>재고</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in detail.products" :key="item.id">
              <td>{{ item.name }}</td>
              <td>{{ item.price }}</td>
              <td class="sale">{{ item.salePrice }}</td>
              <td>{{ item.qty }}</td>
              <td>{{ item.stock }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div class="detail-footer">
      <div class="footer-actions">
        <button
          type="button"
          class="btn danger"
          :disabled="isCancelled"
          @click="openCancelModal"
        >
          {{ isCancelled ? '취소됨' : '예약 취소' }}
        </button>
      </div>
    </div>

    <QCardModal
      v-model="showCueCard"
      :q-cards="detail.cueQuestions || []"
      :initial-index="cueIndex"
      @update:initialIndex="cueIndex = $event"
    />

    <div v-if="showCancelModal" class="stop-modal">
      <div class="stop-modal__backdrop" @click="closeCancelModal"></div>
      <div class="stop-modal__card ds-surface">
        <header class="stop-modal__head">
          <h3>예약 취소</h3>
          <button type="button" class="close-btn" @click="closeCancelModal">×</button>
        </header>
        <div class="stop-modal__body">
          <label class="field">
            <span class="field__label">취소 사유</span>
            <select v-model="cancelReason" class="field-input">
              <option value="">선택해주세요</option>
              <option v-for="reason in cancelReasonOptions" :key="reason" :value="reason">{{ reason }}</option>
            </select>
          </label>
          <label v-if="cancelReason === '기타'" class="field">
            <span class="field__label">기타 사유 입력</span>
            <textarea v-model="cancelDetail" class="field-input" rows="4" placeholder="사유를 입력해주세요."></textarea>
          </label>
          <p v-if="cancelError" class="error">{{ cancelError }}</p>
        </div>
        <div class="stop-modal__actions">
          <button type="button" class="btn ghost" @click="closeCancelModal">취소</button>
          <button type="button" class="btn primary" @click="saveCancel">저장</button>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="detail-wrap">
    <h2 class="page-title">예약 상세</h2>
    <section class="detail-card ds-surface empty-state">
      <p>{{ isLoading ? '예약 정보를 불러오는 중입니다.' : '예약 정보를 찾을 수 없습니다.' }}</p>
      <button type="button" class="btn" @click="goToList">목록으로</button>
    </section>
  </div>
</template>

<style scoped>
.detail-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 900;
  color: var(--text-strong);
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
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

.detail-card {
  padding: 18px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-title h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 900;
  color: var(--text-strong);
}

.status-pill {
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--surface-weak);
  color: var(--text-strong);
  font-weight: 800;
  font-size: 0.85rem;
}

.status-pill.cancelled {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-weight: 700;
  color: var(--text-muted);
}

.detail-meta span {
  display: inline-block;
  min-width: 100px;
  color: var(--text-strong);
  font-weight: 800;
  margin-right: 4px;
}

.cancel-row {
  align-items: center;
}

.cancel-value {
  color: #ef4444;
  font-weight: 900;
}

.notice-box h4 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.notice-box p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.card-head h4 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
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

.table-wrap {
  overflow-x: auto;
}

.product-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 520px;
}

.product-table th,
.product-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
  text-align: left;
  font-weight: 700;
  color: var(--text-strong);
  white-space: nowrap;
}

.product-table th {
  font-size: 0.9rem;
  color: var(--text-muted);
}

.sale {
  color: #ef4444;
  font-weight: 900;
}

.detail-footer {
  display: flex;
  justify-content: flex-end;
}

.footer-actions {
  display: flex;
  gap: 10px;
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

.btn.danger {
  background: #ef4444;
  border-color: transparent;
  color: #fff;
}

.btn.ghost {
  border-color: var(--border-color);
  color: var(--text-muted);
  background: transparent;
}

.btn.primary {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.stop-modal {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
}

.stop-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
}

.stop-modal__card {
  position: relative;
  width: min(520px, 92vw);
  border-radius: 16px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  z-index: 1;
}

.stop-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.stop-modal__head h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.close-btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-muted);
  width: 32px;
  height: 32px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
}

.stop-modal__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.field-input {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.stop-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.error {
  margin: 0;
  color: #ef4444;
  font-weight: 700;
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
}
</style>
