<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../../../components/PageContainer.vue'
import ConfirmModal from '../../../components/ConfirmModal.vue'
import {
  fetchRecentLiveChats,
  fetchSellerBroadcastDetail,
  fetchSellerBroadcastReport,
  deleteSellerVod,
  type BroadcastDetailResponse,
  type BroadcastResult,
  updateSellerVodVisibility,
} from '../../../lib/live/api'
import { getBroadcastStatusLabel } from '../../../lib/broadcastStatus'
import { createImageErrorHandler } from '../../../lib/images/productImages'

const route = useRoute()
const router = useRouter()

const { handleImageError } = createImageErrorHandler()

const vodId = computed(() => (typeof route.params.vodId === 'string' ? route.params.vodId : ''))

type SellerVodDetail = {
  id: string
  title: string
  startedAt: string
  endedAt: string
  statusLabel: string
  stopReason?: string
  thumb: string
  metrics: {
    totalViews: number
    maxViewers: number
    maxViewerTime?: string
    reports: number
    sanctions: number
    likes: number
    totalRevenue: number
  }
  vod: { url?: string; visibility: string; adminLock?: boolean }
  productResults: Array<{ id: string; name: string; price: number; soldQty: number; revenue: number }>
}

const detail = ref<SellerVodDetail | null>(null)
const isLoading = ref(false)
const isVodPlayable = computed(() => !!detail.value?.vod?.url)
const isVodPublic = computed(() => detail.value?.vod.visibility === '공개')
const isVodVisibilityLocked = computed(() => detail.value?.vod.adminLock === true)
const showDeleteConfirm = ref(false)
const statusLabel = computed(() => getBroadcastStatusLabel(detail.value?.statusLabel))

const goBack = () => {
  router.back()
}

const goToList = () => {
  router.push('/seller/live?tab=vod').catch(() => {})
}

const toggleVisibility = async () => {
  if (!detail.value) return
  const nextStatus = detail.value.vod.visibility === '공개' ? 'PRIVATE' : 'PUBLIC'
  if (isVodVisibilityLocked.value && nextStatus === 'PUBLIC') {
    window.alert('관리자에 의해 비공개 처리된 VOD는 공개로 전환할 수 없습니다.')
    return
  }
  try {
    await updateSellerVodVisibility(Number(detail.value.id), nextStatus)
    const nextLabel = nextStatus === 'PUBLIC' ? '공개' : '비공개'
    detail.value = { ...detail.value, vod: { ...detail.value.vod, visibility: nextLabel } }
  } catch {
    return
  }
}

const handleDownload = async () => {
  if (!detail.value?.vod?.url) return
  window.alert('VOD 파일 다운로드를 시작합니다.')
  const fileName = buildDownloadName(detail.value.title)
  if (await saveVodFile(detail.value.vod.url, fileName)) {
    return
  }
  const link = document.createElement('a')
  link.href = detail.value.vod.url
  link.download = fileName
  link.rel = 'noopener'
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  link.remove()
}

const handleDelete = () => {
  showDeleteConfirm.value = true
}

const confirmDelete = async () => {
  if (!detail.value) return
  try {
    await deleteSellerVod(Number(detail.value.id))
    window.alert('VOD가 삭제되었습니다.')
    goToList()
  } catch {
    return
  }
}

const showChat = ref(false)
const chatText = ref('')
const chatMessages = ref<{ id: string; user: string; text: string; time: string }[]>([])

const sendChat = () => {
  if (!chatText.value.trim()) return
  chatMessages.value = [...chatMessages.value, { id: `c-${Date.now()}`, user: '관리자', text: chatText.value, time: '방금' }]
  chatText.value = ''
}

watch(isVodPlayable, (playable) => {
  if (!playable) {
    showChat.value = false
  }
})

const formatDateTime = (value?: string) => (value ? value.replace('T', ' ') : '')

const formatVisibility = (vodStatus?: string) => (vodStatus === 'PUBLIC' ? '공개' : '비공개')

const toNumber = (value: number | string | undefined) => {
  if (typeof value === 'number') return value
  if (typeof value === 'string') {
    const parsed = Number(value)
    return Number.isNaN(parsed) ? 0 : parsed
  }
  return 0
}

const formatChatTime = (timestamp?: number) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const hours = date.getHours()
  const displayHour = hours % 12 || 12
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours >= 12 ? '오후' : '오전'} ${displayHour}:${minutes}`
}

const buildDownloadName = (title?: string) => {
  const base = title?.trim() || 'vod'
  const safe = base.replace(/[\\/:*?"<>|]/g, '_')
  return `${safe}.mp4`
}

const saveVodFile = async (url: string, fileName: string) => {
  const picker = (window as Window & { showSaveFilePicker?: (options?: { suggestedName?: string }) => Promise<FileSystemFileHandle> })
    .showSaveFilePicker
  if (!picker) return false
  try {
    const handle = await picker({ suggestedName: fileName })
    const response = await fetch(url)
    if (!response.ok) return false
    const writable = await handle.createWritable()
    if (response.body) {
      await response.body.pipeTo(writable)
    } else {
      const blob = await response.blob()
      await writable.write(blob)
      await writable.close()
    }
    return true
  } catch {
    return false
  }
}

const buildDetail = (broadcast: BroadcastDetailResponse, report: BroadcastResult): SellerVodDetail => ({
  id: String(report.broadcastId),
  title: report.title ?? broadcast.title ?? '',
  startedAt: formatDateTime(report.startAt ?? broadcast.startedAt),
  endedAt: formatDateTime(report.endAt),
  statusLabel: report.status ?? broadcast.status ?? '',
  stopReason: report.stoppedReason ?? broadcast.stoppedReason ?? undefined,
  thumb: broadcast.thumbnailUrl ?? '',
  metrics: {
    totalViews: report.totalViews ?? 0,
    maxViewers: report.maxViewers ?? 0,
    maxViewerTime: formatDateTime(report.maxViewerTime),
    reports: report.reportCount ?? 0,
    sanctions: report.sanctionCount ?? 0,
    likes: report.totalLikes ?? 0,
    totalRevenue: toNumber(report.totalSales),
  },
  vod: {
    url: report.vodUrl ?? undefined,
    visibility: formatVisibility(report.vodStatus),
    adminLock: report.vodAdminLock ?? false,
  },
  productResults: (report.productStats ?? []).map((item) => ({
    id: String(item.productId),
    name: item.productName,
    price: item.price ?? 0,
    soldQty: item.salesQuantity ?? 0,
    revenue: toNumber(item.salesAmount),
  })),
})

const loadDetail = async () => {
  const idValue = Number(vodId.value)
  if (!vodId.value || Number.isNaN(idValue)) {
    detail.value = null
    return
  }
  isLoading.value = true
  try {
    const [broadcast, report, chats] = await Promise.all([
      fetchSellerBroadcastDetail(idValue),
      fetchSellerBroadcastReport(idValue),
      fetchRecentLiveChats(idValue, 3600).catch(() => []),
    ])
    detail.value = buildDetail(broadcast, report)
    chatMessages.value = chats.map((item) => ({
      id: `${item.sentAt}-${item.sender}`,
      user: item.sender || item.memberEmail || '시청자',
      text: item.content,
      time: formatChatTime(item.sentAt),
    }))
  } catch {
    detail.value = null
    chatMessages.value = []
  } finally {
    isLoading.value = false
  }
}

watch(vodId, () => {
  loadDetail()
}, { immediate: true })
</script>

<template>
  <PageContainer v-if="detail">
    <header class="detail-header">
      <button type="button" class="back-link" @click="goBack">← 뒤로 가기</button>
      <button type="button" class="btn ghost" @click="goToList">목록으로</button>
    </header>

    <h2 class="page-title">방송 결과 리포트</h2>

    <section class="detail-card ds-surface">
      <div class="info-grid">
        <div class="thumb-box">
          <img :src="detail.thumb" :alt="detail.title" @error="handleImageError" />
        </div>
        <div class="info-meta">
          <h3>{{ detail.title }}</h3>
          <p><span>방송 시작 시간</span>{{ detail.startedAt }}</p>
          <p><span>방송 종료 시간</span>{{ detail.endedAt }}</p>
          <p><span>상태</span>{{ statusLabel }}</p>
          <p v-if="detail.statusLabel === 'STOPPED' && detail.stopReason"><span>중지 사유</span>{{ detail.stopReason }}</p>
        </div>
      </div>
    </section>

    <section class="kpi-grid">
      <article class="kpi-card ds-surface">
        <p class="kpi-label">누적 조회수</p>
        <p class="kpi-value">{{ detail.metrics.totalViews.toLocaleString('ko-KR') }}회</p>
      </article>
      <article class="kpi-card ds-surface">
        <p class="kpi-label">방송 중 최대 시청자 수</p>
        <p class="kpi-value">{{ detail.metrics.maxViewers.toLocaleString('ko-KR') }}</p>
        <p v-if="detail.metrics.maxViewerTime" class="kpi-sub">{{ detail.metrics.maxViewerTime }} 기준</p>
      </article>
      <article class="kpi-card ds-surface">
        <p class="kpi-label">신고 건수</p>
        <p class="kpi-value">{{ detail.metrics.reports }}</p>
      </article>
      <article class="kpi-card ds-surface">
        <p class="kpi-label">시청자 제재 건수</p>
        <p class="kpi-value">{{ detail.metrics.sanctions }}</p>
      </article>
      <article class="kpi-card ds-surface">
        <p class="kpi-label">좋아요</p>
        <p class="kpi-value">{{ detail.metrics.likes.toLocaleString('ko-KR') }}</p>
      </article>
      <article class="kpi-card ds-surface">
        <p class="kpi-label">총 매출</p>
        <p class="kpi-value">₩{{ detail.metrics.totalRevenue.toLocaleString('ko-KR') }}</p>
      </article>
    </section>

    <section class="detail-card ds-surface">
      <div class="card-head">
        <h3>VOD</h3>
        <div class="vod-actions" v-if="isVodPlayable">
          <div class="visibility-toggle" aria-label="VOD 공개 설정">
            <svg aria-hidden="true" class="icon muted" viewBox="0 0 24 24" focusable="false">
              <path
                d="M14.12 14.12a3 3 0 0 1-4.24-4.24m6.83 6.83A9.6 9.6 0 0 1 12 17c-5 0-9-5-9-5a15.63 15.63 0 0 1 5.12-4.88m3.41-1.5A9.4 9.4 0 0 1 12 7c5 0 9 5 9 5a15.78 15.78 0 0 1-2.6 2.88"
              />
              <path d="m3 3 18 18" />
            </svg>
            <span class="visibility-label">비공개</span>
            <label class="vod-switch">
              <input type="checkbox" :checked="isVodPublic" :disabled="isVodVisibilityLocked" @change="toggleVisibility" />
              <span class="switch-track"><span class="switch-thumb"></span></span>
            </label>
            <span class="visibility-label">공개</span>
            <svg aria-hidden="true" class="icon muted" viewBox="0 0 24 24" focusable="false">
              <path d="M1 12s4.5-7 11-7 11 7 11 7-4.5 7-11 7S1 12 1 12Z" />
              <circle cx="12" cy="12" r="3.5" />
            </svg>
          </div>
          <p v-if="isVodVisibilityLocked" class="vod-lock-note">관리자에 의해 비공개 처리된 VOD는 공개로 전환할 수 없습니다.</p>
          <div class="vod-icon-actions">
            <button type="button" class="icon-pill" @click="handleDownload" title="다운로드">
              <svg aria-hidden="true" class="icon" viewBox="0 0 24 24" focusable="false">
                <path d="M12 3v12" />
                <path d="m6 11 6 6 6-6" />
                <path d="M5 19h14" />
              </svg>
            </button>
            <button type="button" class="icon-pill danger" @click="handleDelete" title="삭제">
              <svg aria-hidden="true" class="icon" viewBox="0 0 24 24" focusable="false">
                <path d="M3 6h18" />
                <path d="M19 6v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                <line x1="10" x2="10" y1="11" y2="17" />
                <line x1="14" x2="14" y1="11" y2="17" />
              </svg>
            </button>
          </div>
        </div>
      </div>
      <div class="vod-player" :class="{ 'with-chat': showChat && isVodPlayable }">
        <div class="player-shell">
          <div class="player-frame">
            <video
              v-if="isVodPlayable"
              :src="detail.vod.url"
              controls
            ></video>
            <div v-else class="vod-placeholder">
              <span>재생할 VOD가 없습니다.</span>
            </div>
            <div v-if="isVodPlayable" class="player-overlay">
              <div class="overlay-right">
                <div class="chat-pill">
                  <span class="chat-count">{{ chatMessages.length }}</span>
                  <span class="chat-label">채팅 기록</span>
                </div>
                <button
                  type="button"
                  class="icon-circle"
                  :class="{ active: showChat }"
                  @click="showChat = !showChat"
                  :title="showChat ? '채팅 닫기' : '채팅 열기'"
                >
                  <svg aria-hidden="true" class="icon" viewBox="0 0 24 24" focusable="false">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2Z" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
        <aside v-if="showChat && isVodPlayable" class="chat-panel ds-surface">
          <header class="chat-head">
            <h4>채팅</h4>
            <button type="button" class="icon-circle ghost" @click="showChat = false" title="채팅 닫기">
              <svg aria-hidden="true" class="icon" viewBox="0 0 24 24" focusable="false">
                <path d="M18 6 6 18" />
                <path d="m6 6 12 12" />
              </svg>
            </button>
          </header>
          <div class="chat-list">
            <div v-for="msg in chatMessages" :key="msg.id" class="chat-row">
              <div class="chat-meta">
                <span class="chat-user">{{ msg.user }}</span>
                <span class="chat-time">{{ msg.time }}</span>
              </div>
              <p class="chat-text">{{ msg.text }}</p>
            </div>
          </div>
          <div class="chat-input">
            <input v-model="chatText" type="text" placeholder="메시지 입력" />
            <button type="button" class="btn primary" @click="sendChat">전송</button>
          </div>
        </aside>
      </div>
    </section>

    <section class="detail-card ds-surface">
      <div class="card-head">
        <h3>상품별 성과</h3>
      </div>
      <div class="table-wrap">
        <table class="product-table">
          <thead>
            <tr>
              <th>상품명</th>
              <th>가격</th>
              <th>판매 수량</th>
              <th>매출</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in detail.productResults" :key="item.id">
              <td>{{ item.name }}</td>
              <td>₩{{ item.price.toLocaleString('ko-KR') }}</td>
              <td>{{ item.soldQty }}</td>
              <td>₩{{ item.revenue.toLocaleString('ko-KR') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <ConfirmModal
      v-model="showDeleteConfirm"
      title="VOD 삭제"
      description="VOD를 삭제하시겠습니까? 영구 삭제되어 복구할 수 없습니다."
      confirm-text="삭제"
      @confirm="confirmDelete"
    />
  </PageContainer>
  <PageContainer v-else>
    <header class="detail-header">
      <button type="button" class="back-link" @click="goBack">← 뒤로 가기</button>
      <button type="button" class="btn ghost" @click="goToList">목록으로</button>
    </header>

    <h2 class="page-title">방송 결과 리포트</h2>

    <section class="detail-card ds-surface empty-state">
      <p>{{ isLoading ? '방송 정보를 불러오는 중입니다.' : '방송 정보를 찾을 수 없습니다.' }}</p>
    </section>
  </PageContainer>
</template>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.page-title {
  margin: 0 0 16px;
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

.detail-card {
  padding: 18px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.thumb-box {
  width: 180px;
  height: 120px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
}

.thumb-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.info-meta h3 {
  margin: 0 0 10px;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.info-meta p {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-weight: 700;
  display: flex;
  gap: 12px;
}

.info-meta span {
  min-width: 120px;
  color: var(--text-strong);
  font-weight: 800;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  padding: 16px;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kpi-label {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.kpi-value {
  margin: 0;
  color: var(--text-strong);
  font-weight: 900;
  font-size: 1.2rem;
}

.kpi-sub {
  margin: 0;
  font-size: 0.85rem;
  color: var(--text-muted);
  font-weight: 600;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.card-head h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 900;
  color: var(--text-strong);
}

.vod-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.visibility-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.08), rgba(15, 23, 42, 0.02));
}

.vod-lock-note {
  margin: 4px 0 0;
  font-size: 0.85rem;
  color: var(--text-muted);
  font-weight: 600;
}

.vod-switch input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.vod-switch .switch-track {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: 44px;
  height: 22px;
  background: var(--border-color);
  border-radius: 999px;
  transition: background 0.2s ease;
}

.vod-switch .switch-thumb {
  position: absolute;
  left: 3px;
  top: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  transition: transform 0.2s ease;
}

.vod-switch input:checked + .switch-track {
  background: #22c55e;
}

.vod-switch input:checked + .switch-track .switch-thumb {
  transform: translateX(22px);
}

.vod-switch input:disabled + .switch-track {
  opacity: 0.5;
}

.visibility-label {
  font-weight: 900;
  color: var(--text-strong);
  font-size: 0.9rem;
}

.vod-icon-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.icon-pill {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: transform 0.1s ease, box-shadow 0.1s ease, background 0.2s ease, border-color 0.2s ease;
}

.icon-pill:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.12);
}

.icon-pill.danger {
  border-color: rgba(239, 68, 68, 0.28);
  color: #ef4444;
  background: rgba(239, 68, 68, 0.05);
}

.icon-circle {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid var(--border-color);
  background: var(--surface);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: transform 0.1s ease, box-shadow 0.1s ease, background 0.2s ease;
}

.icon-circle.ghost {
  background: rgba(0, 0, 0, 0.55);
  border-color: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.icon-circle.danger {
  background: #fee2e2;
  border-color: #fecdd3;
}

.icon-circle.active {
  background: #e0f2fe;
  border-color: #bae6fd;
}

.icon-circle:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.icon {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.icon.muted {
  color: var(--text-muted);
}

.play-toggle .icon {
  fill: currentColor;
  stroke: none;
}

.vod-player {
  border-radius: 14px;
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.vod-player.with-chat {
  grid-template-columns: minmax(0, 1.65fr) 340px;
}

.player-shell {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  background: #000;
  aspect-ratio: 16 / 9;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.18);
}

.player-frame {
  position: relative;
  width: 100%;
  height: 100%;
}

.vod-player video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000;
}

.vod-placeholder {
  color: var(--text-muted);
  font-weight: 700;
  display: grid;
  place-items: center;
  height: 100%;
  background: linear-gradient(135deg, #0b1220, #0f172a);
}

.player-poster {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
}

.player-poster img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  filter: brightness(0.75);
}

.play-toggle {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  color: #111827;
  display: grid;
  place-items: center;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.2s ease;
}

.play-toggle:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 14px 38px rgba(0, 0, 0, 0.45);
}

.play-toggle .icon {
  width: 26px;
  height: 26px;
  stroke: none;
}

.player-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 14px;
}

.overlay-right {
  display: flex;
  align-items: center;
  gap: 10px;
  pointer-events: auto;
}

.chat-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-weight: 800;
}

.chat-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
}

.chat-label {
  font-size: 0.85rem;
}

.chat-panel {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}

.chat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.chat-list {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}

.chat-row {
  background: var(--surface-weak);
  border-radius: 10px;
  padding: 10px;
  border: 1px solid var(--border-color);
}

.chat-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-weight: 800;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.chat-text {
  margin: 0;
  font-weight: 700;
  color: var(--text-strong);
}

.chat-input {
  display: flex;
  gap: 8px;
}

.chat-input input {
  flex: 1;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 10px;
}

.table-wrap {
  overflow-x: auto;
}

.product-table {
  width: 100%;
  min-width: 560px;
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

@media (max-width: 960px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .vod-player {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .detail-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .thumb-box {
    width: 100%;
    height: 160px;
  }

  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
