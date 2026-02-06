<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CustomerCenterTabs from '../../components/CustomerCenterTabs.vue'
import PageHeader from '../../components/PageHeader.vue'
import { getAuthUser } from '../../lib/auth'
import { SimpleStompClient } from '../../lib/stomp-client'
import { resolveWsUrl } from '../../lib/ws'

type CustomerCenterTab = 'sellerApproval' | 'inquiries'

type AiEvaluationSummary = {
  aiEvalId: number
  sellerId: number
  registerId: number
  sellerName: string
  companyName: string
  description: string
  totalScore: number
  sellerGrade: string
  summary: string
  createdAt: string
  finalized: boolean
}

type AdminEvaluationDetail = {
  adminEvalId: number
  gradeRecommended: string
  adminComment: string
  createdAt: string
}

type AiEvaluationDetail = {
  aiEvalId: number
  sellerId: number
  registerId: number
  companyName: string
  description: string
  businessStability: number
  productCompetency: number
  liveSuitability: number
  operationCoop: number
  growthPotential: number
  totalScore: number
  sellerGrade: string
  summary: string
  createdAt: string
  sellerEmail: string
  adminEvaluation?: AdminEvaluationDetail | null
}

type DirectChatSummary = {
  chatId: number
  memberId: number
  loginId?: string | null
  status: string
  createdAt: string
  assignedAdminId?: number | null
  handoffStatus?: string | null
}

type DirectChatMessage = {
  messageId: number
  chatId: number
  sender: 'USER' | 'ADMIN' | 'SYSTEM'
  content: string
  createdAt: string
}

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const route = useRoute()
const router = useRouter()
const activeTab = ref<CustomerCenterTab>('sellerApproval')
const evaluations = ref<AiEvaluationSummary[]>([])
const selected = ref<AiEvaluationDetail | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const error = ref('')
const showModal = ref(false)
const escalatedChats = ref<DirectChatSummary[]>([])
const activeChats = ref<DirectChatSummary[]>([])
const selectedChat = ref<DirectChatSummary | null>(null)
const directMessages = ref<DirectChatMessage[]>([])
const directInput = ref('')
const directLoading = ref(false)
const directError = ref('')
const adminId = computed(() => {
  const user = getAuthUser()
  const rawId = user?.id ?? user?.userId ?? user?.user_id ?? user?.sellerId ?? user?.seller_id
  return typeof rawId === 'number' ? rawId : 1
})
let stompClient: SimpleStompClient | null = null
let connectedChatId: number | null = null

const form = reactive({
  gradeRecommended: '',
  adminComment: '',
  message: '',
  submitting: false,
  hasError: false,
})

const isFinalized = computed(() => Boolean(selected.value?.adminEvaluation))

const formatDateTime = (value: string) => {
  const normalized = (value || '').trim()
  if (!normalized) return '-'
  const parsed = new Date(normalized)
  if (Number.isNaN(parsed.getTime())) return normalized
  const yyyy = parsed.getFullYear()
  const mm = String(parsed.getMonth() + 1).padStart(2, '0')
  const dd = String(parsed.getDate()).padStart(2, '0')
  const hh = String(parsed.getHours()).padStart(2, '0')
  const mi = String(parsed.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

const normalizeTab = (value: unknown): CustomerCenterTab | null => {
  const tabValue = Array.isArray(value) ? value[0] : value
  if (tabValue === 'sellerApproval' || tabValue === 'inquiries') {
    return tabValue
  }
  return null
}

const syncTabFromQuery = () => {
  const resolved = normalizeTab(route.query.tab)
  if (resolved) {
    activeTab.value = resolved
    return
  }

  activeTab.value = 'sellerApproval'
  if (route.query.tab !== 'sellerApproval') {
    router.replace({ query: { ...route.query, tab: 'sellerApproval' } }).catch(() => {})
  }
}

const setActiveTab = (tab: CustomerCenterTab) => {
  if (activeTab.value === tab && route.query.tab === tab) return
  activeTab.value = tab
  if (route.query.tab === tab) return
  router.replace({ query: { ...route.query, tab } }).catch(() => {})
}

const loadEvaluations = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await fetch(`${apiBase}/admin/evaluations`, {
      credentials: 'include',
    })
    if (!response.ok) {
      const message = await response.text()
      error.value = message || '심사 목록을 불러오지 못했습니다.'
      evaluations.value = []
      return
    }
    evaluations.value = (await response.json()) as AiEvaluationSummary[]
  } catch (err) {
    error.value = err instanceof Error ? err.message : '심사 목록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

const loadEvaluationDetail = async (aiEvalId: number) => {
  detailLoading.value = true
  form.message = ''
  form.hasError = false
  try {
    const response = await fetch(`${apiBase}/admin/evaluations/${aiEvalId}`, {
      credentials: 'include',
    })
    if (!response.ok) {
      const message = await response.text()
      form.message = message || '심사 상세 정보를 불러오지 못했습니다.'
      form.hasError = true
      selected.value = null
      return
    }
    selected.value = (await response.json()) as AiEvaluationDetail
    form.gradeRecommended = selected.value.adminEvaluation?.gradeRecommended ?? selected.value.sellerGrade
    form.adminComment = selected.value.adminEvaluation?.adminComment ?? ''
  } catch (err) {
    form.message = err instanceof Error ? err.message : '심사 상세 정보를 불러오지 못했습니다.'
    form.hasError = true
    selected.value = null
  } finally {
    detailLoading.value = false
  }
}

const openEvaluation = async (aiEvalId: number) => {
  showModal.value = true
  await loadEvaluationDetail(aiEvalId)
}

const closeModal = () => {
  showModal.value = false
  selected.value = null
  form.gradeRecommended = ''
  form.adminComment = ''
  form.message = ''
  form.hasError = false
}

const finalizeEvaluation = async () => {
  if (!selected.value || isFinalized.value) return
  if (!form.gradeRecommended) {
    form.message = '최종 등급을 선택해주세요.'
    form.hasError = true
    return
  }

  form.submitting = true
  form.message = ''
  form.hasError = false
  try {
    const response = await fetch(`${apiBase}/admin/evaluations/${selected.value.aiEvalId}/finalize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        gradeRecommended: form.gradeRecommended,
        adminComment: form.adminComment,
      }),
    })
    if (!response.ok) {
      const message = await response.text()
      form.message = message || '최종 심사 처리에 실패했습니다.'
      form.hasError = true
      return
    }
    form.message = '최종 심사가 완료되었습니다.'
    await loadEvaluationDetail(selected.value.aiEvalId)
    await loadEvaluations()
  } catch (err) {
    form.message = err instanceof Error ? err.message : '최종 심사 처리에 실패했습니다.'
    form.hasError = true
  } finally {
    form.submitting = false
  }
}

const wsEndpoint = computed(() => resolveWsUrl(apiBase, '/ws'))

const loadDirectChats = async () => {
  directLoading.value = true
  directError.value = ''
  try {
    const [activeResponse, escalatedResponse] = await Promise.all([
      fetch(`${apiBase}/admin/direct-chats/active?adminId=${adminId.value}`, {
        credentials: 'include',
      }),
      fetch(`${apiBase}/admin/direct-chats/escalated`, {
        credentials: 'include',
      }),
    ])
    if (!activeResponse.ok || !escalatedResponse.ok) {
      const message = !(activeResponse.ok && escalatedResponse.ok)
        ? await (activeResponse.ok ? escalatedResponse.text() : activeResponse.text())
        : ''
      directError.value = message || '문의 목록을 불러오지 못했습니다.'
      escalatedChats.value = []
      activeChats.value = []
      return
    }
    activeChats.value = (await activeResponse.json()) as DirectChatSummary[]
    escalatedChats.value = (await escalatedResponse.json()) as DirectChatSummary[]
  } catch (err) {
    directError.value = err instanceof Error ? err.message : '문의 목록을 불러오지 못했습니다.'
  } finally {
    directLoading.value = false
  }
}

const loadDirectChatHistory = async (chatId: number) => {
  directLoading.value = true
  try {
    const response = await fetch(`${apiBase}/direct-chats/${chatId}/messages`, {
      credentials: 'include',
    })
    if (!response.ok) {
      directMessages.value = []
      return
    }
    directMessages.value = (await response.json()) as DirectChatMessage[]
  } catch (err) {
    directError.value = err instanceof Error ? err.message : '채팅 내역을 불러오지 못했습니다.'
  } finally {
    directLoading.value = false
  }
}

const connectDirectChat = async (chatId: number) => {
  if (connectedChatId === chatId && stompClient) {
    try {
      await stompClient.connect()
      return
    } catch (error) {
      console.error('direct chat reconnect failed', error)
      stompClient.disconnect()
      stompClient = null
    }
  }
  if (stompClient) {
    stompClient.disconnect()
    stompClient = null
  }
  connectedChatId = chatId
  stompClient = new SimpleStompClient(wsEndpoint.value)
  try {
    await stompClient.connect()
    stompClient.subscribe(`/topic/direct-chats/${chatId}`, (body) => {
      try {
        const payload = JSON.parse(body) as DirectChatMessage
        directMessages.value.push(payload)
        if (payload.sender === 'SYSTEM' && payload.content?.includes('상담이 종료되었습니다.')) {
          if (selectedChat.value?.chatId === chatId) {
            selectedChat.value = {
              ...selectedChat.value,
              status: 'CLOSED',
            }
          }
          activeChats.value = activeChats.value.filter((item) => item.chatId !== chatId)
          if (connectedChatId === chatId) {
            stompClient?.disconnect()
            stompClient = null
            connectedChatId = null
          }
        }
      } catch (error) {
        console.error('direct chat parse failed', error)
      }
    })
  } catch (error) {
    console.error('direct chat connect failed', error)
  }
}

const selectDirectChat = async (chat: DirectChatSummary) => {
  selectedChat.value = chat
  directMessages.value = []
  directError.value = ''
  await loadDirectChatHistory(chat.chatId)
  if (chat.status === 'ADMIN_ACTIVE') {
    await connectDirectChat(chat.chatId)
  }
}

const acceptDirectChat = async (chatId: number) => {
  directLoading.value = true
  try {
    const response = await fetch(`${apiBase}/admin/direct-chats/${chatId}/accept`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ adminId: adminId.value }),
    })
    if (!response.ok) {
      const message = await response.text()
      directError.value = message || '상담 연결에 실패했습니다.'
      return
    }
    const updated = (await response.json()) as DirectChatSummary
    selectedChat.value = updated
    escalatedChats.value = escalatedChats.value.filter((item) => item.chatId !== updated.chatId)
    activeChats.value = [
      updated,
      ...activeChats.value.filter((item) => item.chatId !== updated.chatId),
    ]
    await loadDirectChatHistory(updated.chatId)
    await connectDirectChat(updated.chatId)
  } catch (err) {
    directError.value = err instanceof Error ? err.message : '상담 연결에 실패했습니다.'
  } finally {
    directLoading.value = false
  }
}

const closeDirectChat = async (chatId: number) => {
  directLoading.value = true
  try {
    const response = await fetch(`${apiBase}/admin/direct-chats/${chatId}/close`, {
      method: 'POST',
      credentials: 'include',
    })
    if (!response.ok) {
      const message = await response.text()
      directError.value = message || '상담 종료에 실패했습니다.'
      return
    }
    if (selectedChat.value?.chatId === chatId) {
      selectedChat.value = {
        ...selectedChat.value,
        status: 'CLOSED',
      }
    }
    if (connectedChatId === chatId) {
      stompClient?.disconnect()
      stompClient = null
      connectedChatId = null
    }
    activeChats.value = activeChats.value.filter((item) => item.chatId !== chatId)
    await loadDirectChatHistory(chatId)
  } catch (err) {
    directError.value = err instanceof Error ? err.message : '상담 종료에 실패했습니다.'
  } finally {
    directLoading.value = false
  }
}

const sendDirectMessage = async () => {
  const text = directInput.value.trim()
  if (!text || !selectedChat.value) return
  try {
    await connectDirectChat(selectedChat.value.chatId)
    if (stompClient?.isConnected()) {
      stompClient.send(
        `/app/direct-chats/${selectedChat.value.chatId}`,
        JSON.stringify({ sender: 'ADMIN', content: text }),
      )
      directInput.value = ''
      return
    }
    await sendDirectMessageViaRest(selectedChat.value.chatId, text)
  } catch (error) {
    await sendDirectMessageViaRest(selectedChat.value.chatId, text, error)
  }
}

const sendDirectMessageViaRest = async (chatId: number, text: string, error?: unknown) => {
  try {
    const response = await fetch(`${apiBase}/direct-chats/${chatId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ sender: 'ADMIN', content: text }),
    })
    if (!response.ok) {
      const message = await response.text()
      directError.value = message || '메시지 전송에 실패했습니다.'
      return
    }
    const payload = (await response.json()) as DirectChatMessage
    directMessages.value.push(payload)
    directInput.value = ''
  } catch (restError) {
    const fallback = error ?? restError
    directError.value = fallback instanceof Error ? fallback.message : '메시지 전송에 실패했습니다.'
  }
}

watch(
  () => route.query.tab,
  () => syncTabFromQuery(),
  { immediate: true },
)

watch(
  activeTab,
  (tab) => {
    if (tab === 'sellerApproval') {
      loadEvaluations()
    }
    if (tab === 'inquiries') {
      loadDirectChats()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stompClient?.disconnect()
  stompClient = null
  connectedChatId = null
})
</script>

<template>
  <div>
    <PageHeader eyebrow="DESKIT" title="고객센터" />

    <CustomerCenterTabs :model-value="activeTab" @update:model-value="setActiveTab" />

    <section v-if="activeTab === 'sellerApproval'" class="live-section">
      <div class="live-section__head">
        <h3>판매자 등록 승인</h3>
        <p class="ds-section-sub">판매자 등록 신청서를 확인하고 승인합니다.</p>
      </div>

      <section class="support-card ds-surface">
        <div class="support-card__head">
          <div>
            <h4>AI 심사 이력</h4>
            <p>사업계획서 AI 심사 결과를 확인하고 최종 심사를 진행해주세요.</p>
          </div>
          <button type="button" class="btn ghost" :disabled="loading" @click="loadEvaluations">
            새로고침
          </button>
        </div>

        <p v-if="loading" class="state-text">심사 목록을 불러오는 중입니다.</p>
        <p v-else-if="error" class="state-text error">{{ error }}</p>
        <p v-else-if="!evaluations.length" class="state-text">AI 심사 이력이 없습니다.</p>

        <div v-else class="table-wrap">
          <table class="admin-table">
            

<thead>
              <tr>
                <th>번호</th>
                <th>판매자명</th>
                <th>회사명</th>
                <th>총점</th>
                <th>등급</th>
                <th>결과 요약</th>
                <th>신청일</th>
                <th>상태</th>
                <th>결과</th>
              </tr>
            </thead>


            <tbody>
              <tr v-for="(item, index) in evaluations" :key="item.aiEvalId">
                <td>{{ index + 1 }}</td>
                <td>{{ item.sellerName || '-' }}</td>
                <td>{{ item.companyName }}</td>
                <td>{{ item.totalScore }}</td>
                <td>{{ item.sellerGrade }}</td>
                <td class="summary-cell">{{ item.summary }}</td>
                <td>{{ formatDateTime(item.createdAt) }}</td>
                <td>
                  <span class="status-pill" :class="item.finalized ? 'is-final' : 'is-pending'">
                    {{ item.finalized ? '완료' : '대기' }}
                  </span>
                </td>
                <td>
                  <button type="button" class="btn ghost" @click="openEvaluation(item.aiEvalId)">
                    결과 보기
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>

    <section v-else class="live-section">
      <div class="live-section__head">
        <h3>문의사항 확인</h3>
        <p class="ds-section-sub">고객 문의 이력을 확인합니다.</p>
      </div>

      <section class="support-card ds-surface">
        <div class="support-card__head">
          <div>
            <h4>1:1 문의 목록</h4>
            <p>관리자 이관 요청을 확인하고 상담을 시작하세요.</p>
          </div>
          <button type="button" class="btn ghost" :disabled="directLoading" @click="loadDirectChats">새로고침</button>
        </div>

        <p v-if="directLoading" class="state-text">문의 목록을 불러오는 중입니다.</p>
        <p v-else-if="directError" class="state-text error">{{ directError }}</p>
        <p v-else-if="!activeChats.length && !escalatedChats.length" class="state-text">대기중인 문의가 없습니다.</p>

        <div v-else class="direct-chat-grid">
          <div class="direct-chat-list">
            <div v-if="activeChats.length" class="direct-chat-group">
              <p class="direct-chat-group__title">상담중</p>
              <button
                v-for="chat in activeChats"
                :key="`active-${chat.chatId}`"
                type="button"
                class="direct-chat-item"
                :class="{ active: selectedChat?.chatId === chat.chatId }"
                @click="selectDirectChat(chat)"
              >
                <span class="direct-chat-item__id">Chat #{{ chat.chatId }}</span>
                <span class="direct-chat-item__meta">로그인ID {{ chat.loginId || '-' }}</span>
                <span class="direct-chat-item__status">{{ chat.status }}</span>
              </button>
            </div>
            <div v-if="escalatedChats.length" class="direct-chat-group">
              <p class="direct-chat-group__title">대기중</p>
              <button
                v-for="chat in escalatedChats"
                :key="`escalated-${chat.chatId}`"
                type="button"
                class="direct-chat-item"
                :class="{ active: selectedChat?.chatId === chat.chatId }"
                @click="selectDirectChat(chat)"
              >
                <span class="direct-chat-item__id">Chat #{{ chat.chatId }}</span>
                <span class="direct-chat-item__meta">로그인ID {{ chat.loginId || '-' }}</span>
                <span class="direct-chat-item__status">{{ chat.status }}</span>
              </button>
            </div>
          </div>
          <div class="direct-chat-panel">
            <div v-if="!selectedChat" class="state-text">문의 항목을 선택해주세요.</div>
            <div v-else class="direct-chat-card">
              <header class="direct-chat-card__head">
                <div>
                  <h4>Chat #{{ selectedChat.chatId }}</h4>
                  <p>로그인ID {{ selectedChat.loginId || '-' }} · {{ selectedChat.status }}</p>
                </div>
                <div class="direct-chat-actions">
                  <button
                    v-if="selectedChat.status === 'ESCALATED'"
                    type="button"
                    class="btn primary"
                    :disabled="directLoading"
                    @click="acceptDirectChat(selectedChat.chatId)"
                  >상담 연결</button>
                  <button
                    v-else-if="selectedChat.status === 'ADMIN_ACTIVE'"
                    type="button"
                    class="btn ghost"
                    :disabled="directLoading"
                    @click="closeDirectChat(selectedChat.chatId)"
                  >상담 종료</button>
                </div>
              </header>
              <div class="direct-chat-messages">
                <p v-if="!directMessages.length" class="state-text">채팅 내역이 없습니다.</p>
                <div
                  v-for="message in directMessages"
                  :key="message.messageId"
                  class="direct-chat-message"
                  :class="`is-${message.sender.toLowerCase()}`"
                >
                  <p class="direct-chat-text">{{ message.content }}</p>
                </div>
              </div>
              <div class="direct-chat-input">
                <input
                  v-model="directInput"
                  type="text"
                  placeholder="메시지를 입력하세요."
                  :disabled="selectedChat.status !== 'ADMIN_ACTIVE'"
                  @keydown.enter.prevent="sendDirectMessage"
                />
                <button
                  type="button"
                  class="btn primary"
                  :disabled="selectedChat.status !== 'ADMIN_ACTIVE'"
                  @click="sendDirectMessage"
                >
                  전송
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </section>

    <div v-if="showModal" class="evaluation-modal" role="dialog" aria-modal="true" aria-label="심사 상세">
      <div class="evaluation-modal__backdrop" @click="closeModal"></div>
      <div class="evaluation-modal__card ds-surface">
        <div class="evaluation-modal__head">
          <div>
            <h3>AI 심사 상세</h3>
            <p>AI 심사 결과를 확인하고 최종 심사를 진행합니다.</p>
          </div>
          <button type="button" class="btn ghost" @click="closeModal">닫기</button>
        </div>

        <div v-if="detailLoading" class="state-text">상세 정보를 불러오는 중입니다.</div>
        <div v-else-if="!selected" class="state-text error">심사 상세 정보를 불러오지 못했습니다.</div>
        <div v-else class="evaluation-detail">
          

<dl class="detail-grid">
            <div class="detail-item">
              <dt>Seller ID</dt>
              <dd>{{ selected.sellerId }}</dd>
            </div>
            <div class="detail-item">
              <dt>Register ID</dt>
              <dd>{{ selected.registerId }}</dd>
            </div>
            <div class="detail-item">
              <dt>신청일</dt>
              <dd>{{ formatDateTime(selected.createdAt) }}</dd>
            </div>
            <div class="detail-item">
              <dt>회사명</dt>
              <dd>{{ selected.companyName }}</dd>
            </div>
            <div class="detail-item">
              <dt>담당 이메일</dt>
              <dd>{{ selected.sellerEmail }}</dd>
            </div>
            <div class="detail-item">
              <dt>사업 안정성</dt>
              <dd>{{ selected.businessStability }}</dd>
            </div>
            <div class="detail-item">
              <dt>상품 경쟁력</dt>
              <dd>{{ selected.productCompetency }}</dd>
            </div>
            <div class="detail-item">
              <dt>라이브 적합성</dt>
              <dd>{{ selected.liveSuitability }}</dd>
            </div>
            <div class="detail-item">
              <dt>운영 협업</dt>
              <dd>{{ selected.operationCoop }}</dd>
            </div>
            <div class="detail-item">
              <dt>성장 가능성</dt>
              <dd>{{ selected.growthPotential }}</dd>
            </div>
            <div class="detail-item">
              <dt>총점</dt>
              <dd>{{ selected.totalScore }}</dd>
            </div>
            <div class="detail-item">
              <dt>AI 권장 등급</dt>
              <dd>{{ selected.sellerGrade }}</dd>
            </div>
            <div class="detail-item full">
              <dt>요약</dt>
              <dd>{{ selected.summary }}</dd>
            </div>
            <div class="detail-item full">
              <dt>설명</dt>
              <dd>{{ selected.description || '-' }}</dd>
            </div>
          </dl>



                    <section class="final-section">
            <div class="final-head">
              <h4>최종 심사</h4>
              <span v-if="isFinalized" class="status-pill is-final">완료</span>
            </div>
            <p class="final-desc">
              최종 등급과 관리자 코멘트를 입력하면 판매자 이메일로 결과가 전송됩니다.
            </p>

            <div class="final-form">
              <label class="field">
                <span class="field__label">최종 등급</span>
                <select v-model="form.gradeRecommended" :disabled="isFinalized || detailLoading" class="field-input">
                  <option disabled value="">등급 선택</option>
                  <option value="A">A</option>
                  <option value="B">B</option>
                  <option value="C">C</option>
                  <option value="REJECTED">REJECTED</option>
                </select>
              </label>
              <label class="field">
                <span class="field__label">관리자 코멘트</span>
                <textarea
                  v-model="form.adminComment"
                  class="field-input textarea"
                  :disabled="isFinalized || detailLoading"
                  placeholder="최종 심사 코멘트를 입력하세요."
                ></textarea>
              </label>
              <div class="final-actions">
                <button
                  type="button"
                  class="btn primary"
                  :disabled="isFinalized || form.submitting"
                  @click="finalizeEvaluation"
                >
                  {{ form.submitting ? '처리 중...' : '최종 심사 완료' }}
                </button>
              </div>
              <p v-if="form.message" class="form-message" :class="{ error: form.hasError }">
                {{ form.message }}
              </p>
            </div>

            <div v-if="selected.adminEvaluation" class="final-info">
              <p>최종 등급: {{ selected.adminEvaluation.gradeRecommended }}</p>
              <p>관리자 코멘트: {{ selected.adminEvaluation.adminComment || '-' }}</p>
              <p>관리자 평가일: {{ formatDateTime(selected.adminEvaluation.createdAt) }}</p>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.live-section {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.live-section:first-of-type {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.live-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.live-section__head h3 {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 900;
  color: var(--text-strong);
}

.support-card {
  padding: 18px;
  border-radius: 14px;
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.support-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.support-card__head h4 {
  margin: 0 0 6px;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.support-card__head p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.state-text {
  margin: 0;
  font-weight: 700;
  color: var(--text-muted);
}

.state-text.error {
  color: #b91c1c;
}

.table-wrap {
  overflow-x: auto;
}

.admin-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 900px;
}

.admin-table th,
.admin-table td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
  font-weight: 700;
  color: var(--text-strong);
  font-size: 0.88rem;
}

.admin-table thead th {
  background: var(--surface-weak);
  font-weight: 900;
}

.summary-cell {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 800;
  font-size: 0.8rem;
}

.status-pill.is-pending {
  background: var(--surface-weak);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

.status-pill.is-final {
  background: #111827;
  color: #fff;
}

.btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  padding: 8px 12px;
  border-radius: 10px;
  font-weight: 800;
  cursor: pointer;
}

.btn.primary {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #fff;
}

.btn.ghost {
  background: transparent;
}

.evaluation-modal {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  padding: 24px;
}

.evaluation-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
}

.evaluation-modal__card {
  position: relative;
  z-index: 1;
  width: min(840px, 100%);
  max-height: 90vh;
  overflow-y: auto;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.18);
}

.evaluation-modal__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.evaluation-modal__head h3 {
  margin: 0 0 6px;
  font-size: 1.2rem;
  font-weight: 900;
  color: var(--text-strong);
}

.evaluation-modal__head p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.evaluation-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-grid {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-item.full {
  grid-column: span 2;
}

.detail-item dt {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.detail-item dd {
  margin: 0;
  color: var(--text-strong);
  font-weight: 800;
}

.final-section {
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.final-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.final-head h4 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 900;
  color: var(--text-strong);
}

.final-desc {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.final-form {
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
  font-size: 0.9rem;
}

.field-input {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.textarea {
  min-height: 90px;
  resize: vertical;
}

.final-actions {
  display: flex;
  justify-content: flex-end;
}

.form-message {
  margin: 0;
  font-weight: 700;
  color: #15803d;
}

.form-message.error {
  color: #b91c1c;
}

.final-info {
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  padding: 12px;
  font-weight: 700;
  color: var(--text-strong);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.direct-chat-grid {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: 16px;
}

.direct-chat-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.direct-chat-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.direct-chat-group__title {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 900;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.direct-chat-item {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--surface);
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: left;
  font-weight: 700;
  cursor: pointer;
}

.direct-chat-item.active {
  border-color: var(--primary-color);
  background: var(--surface-weak);
}

.direct-chat-item__id {
  color: var(--text-strong);
  font-size: 0.95rem;
  font-weight: 900;
}

.direct-chat-item__meta {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.direct-chat-item__status {
  color: var(--text-strong);
  font-size: 0.8rem;
}

.direct-chat-panel {
  min-height: 360px;
}

.direct-chat-card {
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface);
  display: flex;
  flex-direction: column;
  height: 100%;
}

.direct-chat-card__head {
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--border-color);
}

.direct-chat-card__head h4 {
  margin: 0 0 4px;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.direct-chat-card__head p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.direct-chat-actions {
  display: flex;
  gap: 8px;
}

.direct-chat-messages {
  flex: 1;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--surface-weak);
  overflow-y: auto;
}

.direct-chat-message {
  max-width: 70%;
  padding: 10px 12px;
  border-radius: 12px;
  font-weight: 700;
  line-height: 1.4;
}

.direct-chat-message.is-user {
  align-self: flex-start;
  background: #fff;
  color: var(--text-strong);
}

.direct-chat-message.is-admin {
  align-self: flex-end;
  background: rgba(17, 24, 39, 0.08);
  color: #111827;
}

.direct-chat-message.is-system {
  align-self: center;
  background: rgba(59, 130, 246, 0.1);
  color: #1d4ed8;
}

.direct-chat-text {
  margin: 0;
}

.direct-chat-input {
  padding: 12px 14px;
  display: flex;
  gap: 10px;
  border-top: 1px solid var(--border-color);
}

.direct-chat-input input {
  flex: 1;
  min-height: 40px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 8px 10px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.direct-chat-input input:disabled {
  background: var(--surface-weak);
}

@media (max-width: 720px) {
  .support-card__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .direct-chat-grid {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-item.full {
    grid-column: span 1;
  }
}
</style>











