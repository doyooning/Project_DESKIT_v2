<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { getAuthUser, hydrateSessionUser } from '../lib/auth'
import { resolveViewerId } from '../lib/live/viewer'
import { SimpleStompClient } from '../lib/stomp-client'
import { resolveWsUrl } from '../lib/ws'

type ChatRole = 'user' | 'bot' | 'system'

type ChatMessage = {
  id: string
  role: ChatRole
  content: string
  sources?: string[]
}

type ChatHistoryItem = {
  messageId?: number
  type?: string
  content?: string
}

type ChatResponse = {
  answer?: string
  sources?: string[]
  escalated?: boolean
}

type DirectChatMessage = {
  messageId: number
  chatId: number
  sender: 'USER' | 'ADMIN' | 'SYSTEM'
  content: string
  createdAt: string
}

const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isSending = ref(false)
const isLocked = ref(false)
const statusLabel = ref<string>('BOT_ACTIVE')
const chatListRef = ref<HTMLDivElement | null>(null)
const chatId = ref<number | null>(null)
const isAdminChat = computed(() => statusLabel.value === 'ADMIN_ACTIVE')
const isDirectChat = computed(
  () => statusLabel.value === 'ADMIN_ACTIVE' || statusLabel.value === 'ESCALATED',
)
const isEscalated = computed(() => statusLabel.value === 'ESCALATED')
const isClosed = computed(() => statusLabel.value === 'CLOSED')
const statusLabelMap: Record<string, string> = {
  BOT_ACTIVE: '챗봇',
  ADMIN_ACTIVE: '관리자',
  ESCALATED: '관리자 연결 요청',
  CLOSED: '종료',
}
const displayStatusLabel = computed(() => statusLabelMap[statusLabel.value] ?? statusLabel.value)
const shouldShowClosedActions = computed(() => statusLabel.value === 'CLOSED')
const shouldShowAdminClose = computed(() => statusLabel.value === 'ADMIN_ACTIVE')
let stompClient: SimpleStompClient | null = null
let statusPoller: number | null = null

const memberId = ref<string | null>(null)
const resolveMemberId = async () => {
  const user = getAuthUser()
  const viewerId = resolveViewerId(user) ?? resolveViewerId(null)
  if (viewerId && /^\d+$/.test(viewerId)) {
    memberId.value = viewerId
    return
  }

  try {
    const response = await fetchWithCredentials(buildApiUrl('/api/my/member-id'))
    if (response.ok) {
      const data = (await response.json()) as { member_id?: number | string }
      if (data?.member_id !== null && data?.member_id !== undefined) {
        memberId.value = String(data.member_id)
        return
      }
    }
  } catch (error) {
    console.error('member id resolve failed', error)
  }

  memberId.value = null
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatListRef.value) {
    chatListRef.value.scrollTop = chatListRef.value.scrollHeight
  }
}

const renderMarkdown = (content: string) => {
  if (!content) return ''
  const parsed = marked.parse(content, { breaks: true, async: false }) as string
  return DOMPurify.sanitize(parsed)
}


const appendMessage = (role: ChatRole, content: string, sources?: string[]) => {
  messages.value.push({
    id: `${Date.now()}-${messages.value.length}`,
    role,
    content,
    sources,
  })
  scrollToBottom()
}

const loadChatHistory = async () => {
  if (!chatId.value) return
  try {
    const response = await fetchWithCredentials(`${apiBase}/chat/history/${chatId.value}`, {
      method: 'POST',
    })
    if (!response.ok) return
    const history = (await response.json()) as ChatHistoryItem[]
    messages.value = history.map((item, index) => ({
      id: `${item.messageId ?? index}`,
      role: item.type === 'USER' ? 'user' : 'bot',
      content: item.content ?? '',
    }))
    scrollToBottom()
  } catch (error) {
    console.error('chat history load failed', error)
  }
}

const loadDirectChatHistory = async () => {
  if (!chatId.value) return
  try {
    const response = await fetchWithCredentials(`${apiBase}/direct-chats/${chatId.value}/messages`)
    if (!response.ok) return
    const history = (await response.json()) as DirectChatMessage[]
    messages.value = history.map((item) => ({
      id: `${item.messageId}`,
      role: item.sender === 'USER' ? 'user' : item.sender === 'SYSTEM' ? 'system' : 'bot',
      content: item.content ?? '',
    }))
    scrollToBottom()
  } catch (error) {
    console.error('direct chat history load failed', error)
  }
}

const wsEndpoint = computed(() => resolveWsUrl(apiBase, '/ws'))

const fetchWithCredentials = (url: string, options: RequestInit = {}) =>
  fetch(url, { credentials: 'include', ...options })

const buildApiUrl = (path: string) => {
  const base = apiBase.replace(/\/+$/, '')
  const normalized = path.startsWith('/') ? path : `/${path}`
  if (!base) {
    return normalized
  }
  if (base.endsWith('/api') && normalized.startsWith('/api/')) {
    return `${base}${normalized.slice(4)}`
  }
  return `${base}${normalized}`
}

const stopStatusPolling = () => {
  if (statusPoller !== null) {
    window.clearInterval(statusPoller)
    statusPoller = null
  }
}

const startStatusPolling = () => {
  if (statusPoller !== null) return
  statusPoller = window.setInterval(async () => {
    await syncConversationStatus()
    if (statusLabel.value !== 'ESCALATED') {
      stopStatusPolling()
      if (statusLabel.value === 'ADMIN_ACTIVE' && chatId.value) {
        await loadDirectChatHistory()
        await connectDirectChat()
      }
    }
  }, 5000)
}

const applyStatus = (status: string) => {
  statusLabel.value = status
  isLocked.value = status === 'CLOSED'
  if (status === 'ADMIN_ACTIVE') {
    stopStatusPolling()
    return
  }
  if (status === 'ESCALATED') {
    startStatusPolling()
  } else {
    stopStatusPolling()
  }
  if (status === 'BOT_ACTIVE' && stompClient) {
    stompClient.disconnect()
    stompClient = null
    connectedChatId = null
  }
}

let connectedChatId: number | null = null

const connectDirectChat = async () => {
  if (!chatId.value) return
  if (connectedChatId === chatId.value && stompClient) {
    try {
      await stompClient.connect()
      return
    } catch (error) {
      console.error('direct chat reconnect failed', error)
      stompClient.disconnect()
      stompClient = null
      connectedChatId = null
      throw error
    }
  }
  if (stompClient) {
    stompClient.disconnect()
    stompClient = null
  }
  connectedChatId = chatId.value
  stompClient = new SimpleStompClient(wsEndpoint.value)
  try {
    await stompClient.connect()
    stompClient.subscribe(`/topic/direct-chats/${chatId.value}`, (body) => {
      try {
        const payload = JSON.parse(body) as DirectChatMessage
        messages.value.push({
          id: `${payload.messageId}`,
          role: payload.sender === 'USER' ? 'user' : payload.sender === 'SYSTEM' ? 'system' : 'bot',
          content: payload.content,
        })
        scrollToBottom()
        if (payload.sender === 'SYSTEM' && payload.content?.includes('상담이 종료되었습니다.')) {
          stompClient?.disconnect()
          stompClient = null
          connectedChatId = null
          applyStatus('CLOSED')
        }
      } catch (error) {
        console.error('direct chat message parse failed', error)
      }
    })
  } catch (error) {
    console.error('direct chat connect failed', error)
    stompClient.disconnect()
    stompClient = null
    connectedChatId = null
    throw error
  }
}

const syncConversationStatus = async () => {
  if (!memberId.value) return
  try {
    const response = await fetchWithCredentials(`${apiBase}/direct-chats/latest/${memberId.value}`)
    if (response.ok) {
      const data = (await response.json()) as { chatId?: number; status?: string }
      chatId.value = typeof data.chatId === 'number' ? data.chatId : chatId.value
      const nextStatus = data.status ?? 'BOT_ACTIVE'
      applyStatus(nextStatus)
      await ensureDirectChatId(nextStatus)
      await ensureBotChatId(nextStatus)
      await hydrateDirectChat(nextStatus)
      if (chatId.value || nextStatus === 'BOT_ACTIVE') {
        return
      }
    }
  } catch (error) {
    console.error('latest conversation load failed', error)
  }

  try {
    const response = await fetchWithCredentials(`${apiBase}/chat/status/${memberId.value}`)
    if (!response.ok) return
    const data = (await response.json()) as { status?: string }
    const nextStatus = data.status ?? 'BOT_ACTIVE'
    applyStatus(nextStatus)
    await ensureDirectChatId(nextStatus)
    await ensureBotChatId(nextStatus)
    await hydrateDirectChat(nextStatus)
  } catch (error) {
    console.error('status check failed', error)
  }
}

const ensureDirectChatId = async (status: string) => {
  if (!memberId.value || chatId.value) return
  if (status !== 'ESCALATED' && status !== 'ADMIN_ACTIVE') return
  try {
    const response = await fetchWithCredentials(`${apiBase}/direct-chats/start/${memberId.value}`, {
      method: 'POST',
    })
    if (!response.ok) return
    const data = (await response.json()) as { chatId?: number; status?: string }
    chatId.value = typeof data.chatId === 'number' ? data.chatId : chatId.value
    if (data.status) {
      applyStatus(data.status)
    }
  } catch (error) {
    console.error('direct chat fallback load failed', error)
  }
}

const ensureBotChatId = async (status: string) => {
  if (!memberId.value || chatId.value || status !== 'BOT_ACTIVE') return
  try {
    const response = await fetchWithCredentials(`${apiBase}/chat/latest/${memberId.value}`)
    if (!response.ok) return
    const data = (await response.json()) as { chatId?: number; status?: string }
    if (typeof data.chatId === 'number') {
      chatId.value = data.chatId
    }
    if (data.status) {
      applyStatus(data.status)
    }
    if (chatId.value && messages.value.length === 0) {
      await loadChatHistory()
    }
  } catch (error) {
    console.error('bot chat lookup failed', error)
  }
}

const hydrateDirectChat = async (status: string) => {
  if (!chatId.value) return
  if (status === 'ESCALATED' || status === 'ADMIN_ACTIVE' || status === 'CLOSED') {
    await loadDirectChatHistory()
  }
  if (status === 'ESCALATED' || status === 'ADMIN_ACTIVE') {
    await connectDirectChat()
  }
}

const startNewInquiry = async () => {
  if (isSending.value) return
  if (!memberId.value) return
  isSending.value = true
  try {
    const response = await fetchWithCredentials(`${apiBase}/direct-chats/start/${memberId.value}`, {
      method: 'POST',
    })
    if (!response.ok) {
      appendMessage('system', '문의 시작할 수 없습니다. 잠시 후 다시 시도해주세요.')
      return
    }
    const data = (await response.json()) as { chatId?: number; status?: string }
    stompClient?.disconnect()
    stompClient = null
    connectedChatId = null
    messages.value = []
    chatId.value = typeof data.chatId === 'number' ? data.chatId : chatId.value
    applyStatus(data.status ?? 'BOT_ACTIVE')
  } catch (error) {
    console.error('new inquiry start failed', error)
    appendMessage('system', '문의 시작할 수 없습니다. 잠시 후 다시 시도해주세요.')
  } finally {
    isSending.value = false
  }
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || isSending.value || isLocked.value) return
  if (isDirectChat.value) {
    isSending.value = true
    inputText.value = ''
    try {
      if (!chatId.value) {
        await syncConversationStatus()
      }
      const currentChatId = chatId.value
      if (!currentChatId) return
      await connectDirectChat()
      if (stompClient?.isConnected()) {
        stompClient.send(
          `/app/direct-chats/${currentChatId}`,
          JSON.stringify({ sender: 'USER', content: text }),
        )
      } else {
        await sendDirectMessageViaRest(currentChatId, text)
      }
    } catch (error) {
      const fallbackChatId = chatId.value
      if (fallbackChatId) {
        await sendDirectMessageViaRest(fallbackChatId, text, error)
      } else {
        console.error('direct chat send failed', error)
        appendMessage('system', '메시지 전송에 실패했습니다. 잠시 후 다시 시도해주세요.')
      }
    } finally {
      isSending.value = false
    }
    return
  }
  appendMessage('user', text)
  inputText.value = ''
  isSending.value = true
  try {
    const response = await fetchWithCredentials(`${apiBase}/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    })
    if (!response.ok) {
      appendMessage('system', '메시지 전송에 실패했어요. 다시 시도해 주세요.')
      return
    }

    const data = (await response.json()) as ChatResponse
    appendMessage('bot', data.answer ?? 'No response received.', data.sources)
    if (data.escalated) {
      applyStatus('ESCALATED')
      await ensureDirectChatId('ESCALATED')
      await hydrateDirectChat('ESCALATED')
      appendMessage('system', '채팅이 관리자로 이관되었어요. 관리자가 곧 답변 드릴 예정이에요.')
    }
  } catch (error) {
    console.error('chat send failed', error)
    appendMessage('system', '네트워크 오류. 다시 시도해 주세요.')
  } finally {
    isSending.value = false
  }
}

const sendDirectMessageViaRest = async (chatId: number, text: string, error?: unknown) => {
  try {
    const response = await fetchWithCredentials(`${apiBase}/direct-chats/${chatId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sender: 'USER', content: text }),
    })
    if (!response.ok) {
      appendMessage('system', '메시지 전송에 실패했습니다. 잠시 후 다시 시도해주세요.')
      return
    }
    const payload = (await response.json()) as DirectChatMessage
    messages.value.push({
      id: `${payload.messageId}`,
      role: payload.sender === 'USER' ? 'user' : payload.sender === 'SYSTEM' ? 'system' : 'bot',
      content: payload.content ?? '',
    })
    scrollToBottom()
  } catch (restError) {
    console.error('direct chat send failed', error ?? restError)
    appendMessage('system', '메시지 전송에 실패했습니다. 잠시 후 다시 시도해주세요.')
  }
}

const closeDirectChat = async () => {
  if (!chatId.value) return
  isSending.value = true
  try {
    const response = await fetchWithCredentials(`${apiBase}/direct-chats/${chatId.value}/close`, {
      method: 'POST',
    })
    if (!response.ok) {
      appendMessage('system', '상담 종료에 실패했습니다. 잠시 후 다시 시도해주세요.')
      return
    }
    stompClient?.disconnect()
    stompClient = null
    connectedChatId = null
    applyStatus('CLOSED')
    await loadDirectChatHistory()
  } catch (error) {
    console.error('direct chat close failed', error)
    appendMessage('system', '상담 종료에 실패했습니다. 잠시 후 다시 시도해주세요.')
  } finally {
    isSending.value = false
  }
}

onMounted(async () => {
  await hydrateSessionUser()
  await resolveMemberId()
  await syncConversationStatus()
  if (chatId.value) {
    if (isAdminChat.value || isEscalated.value || isClosed.value) {
      await loadDirectChatHistory()
    } else {
      await loadChatHistory()
    }
  }
  if (isAdminChat.value || isEscalated.value) {
    await connectDirectChat()
  }
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  stompClient?.disconnect()
  stompClient = null
  stopStatusPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

const viewPreviousChat = async () => {
  await syncConversationStatus()
  if (!chatId.value) {
    appendMessage('system', '이전 채팅을 불러올 수 없습니다. 잠시 후 다시 시도해주세요.')
    return
  }
  await loadDirectChatHistory()
}

const handleVisibilityChange = () => {
  if (document.visibilityState !== 'visible') return
  syncConversationStatus().catch((error) => {
    console.error('visibility sync failed', error)
  })
}
</script>

<template>
  <PageContainer>
    <PageHeader
      eyebrow="고객 지원"
      title="DESKIT AI"
      subtitle="궁금한 점이 있으시다면 DESKIT AI가 도와드릴게요."
    />

    <section class="chat-shell">
      <header class="chat-head">
        <div class="chat-meta">
          <span class="badge">상태:</span>
          <span class="status" :class="{ 'status--locked': isLocked }">
            {{ displayStatusLabel }}
          </span>
        </div>
        <p class="hint">
          관리자에게 연결된 경우, 입력창이 잠시 비활성화될 수 있습니다.
        </p>
        <div v-if="shouldShowAdminClose" class="chat-actions">
          <button type="button" class="btn ghost" @click="closeDirectChat">
            상담 종료
          </button>
        </div>
        <div v-if="shouldShowClosedActions" class="chat-actions">
          <button type="button" class="btn ghost" @click="viewPreviousChat">
            이전 채팅 보기
          </button>
          <button type="button" class="btn primary" @click="startNewInquiry">
            새 채팅
          </button>
        </div>
      </header>

      <div class="chat-body">
        <div ref="chatListRef" class="chat-list">
          <div v-if="messages.length === 0" class="chat-empty">
            DESKIT AI와 대화를 시작해보세요.
          </div>
          <div
            v-for="message in messages"
            :key="message.id"
            class="chat-message"
            :class="`chat-message--${message.role}`"
          >
            <div class="chat-text markdown" v-html="renderMarkdown(message.content)" />
            <div v-if="message.sources?.length" class="chat-sources">
              <span class="chat-source" v-for="(source, index) in message.sources" :key="`${message.id}-${index}`">
                {{ source }}
              </span>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <input
            v-model="inputText"
            type="text"
            placeholder="무엇이든 물어보세요"
            :disabled="isSending || isLocked"
            @keydown.enter.prevent="sendMessage"
          />
          <button type="button" class="btn primary" :disabled="isSending || isLocked" @click="sendMessage">
            {{ isSending ? '전송중...' : '전송' }}
          </button>
        </div>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.chat-shell {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  height: clamp(420px, calc(100dvh - 260px), 760px);
}

.chat-head {
  padding: 18px 20px;
  background: linear-gradient(135deg, rgba(209, 232, 208, 0.5), rgba(238, 244, 238, 0.8));
  border-bottom: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-head .btn {
  align-self: flex-start;
}

.chat-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chat-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.badge {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-soft);
}

.status {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(16, 163, 127, 0.12);
  color: #0f5132;
  font-weight: 800;
  font-size: 12px;
}

.status--locked {
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
}

.hint {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 600;
}

.chat-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.chat-list {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  background: radial-gradient(circle at top, rgba(233, 240, 231, 0.5), transparent 65%);
}

.chat-empty {
  color: var(--text-muted);
  font-weight: 700;
  text-align: center;
  margin-top: 80px;
}

.chat-message {
  max-width: 70%;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.5;
  background: #fff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.chat-message--user {
  align-self: flex-end;
  background: rgba(16, 163, 127, 0.12);
  color: #0f5132;
}

.chat-message--bot {
  align-self: flex-start;
  background: #fff;
  color: #111827;
}

.chat-message--system {
  align-self: center;
  max-width: 80%;
  background: rgba(59, 130, 246, 0.1);
  color: #1d4ed8;
  text-align: center;
}

.chat-text {
  margin: 0;
  font-weight: 600;
}

.chat-text :deep(p) {
  margin: 0 0 8px;
}

.chat-text :deep(p:last-child) {
  margin-bottom: 0;
}

.chat-text :deep(ul),
.chat-text :deep(ol) {
  margin: 0 0 8px 18px;
  padding: 0;
}

.chat-text :deep(h1),
.chat-text :deep(h2),
.chat-text :deep(h3),
.chat-text :deep(h4),
.chat-text :deep(h5),
.chat-text :deep(h6) {
  margin: 8px 0 6px;
  font-size: 1em;
  font-weight: 800;
}

.chat-text :deep(code) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  background: rgba(15, 23, 42, 0.06);
  padding: 2px 6px;
  border-radius: 6px;
}

.chat-text :deep(pre) {
  background: rgba(15, 23, 42, 0.06);
  padding: 10px 12px;
  border-radius: 10px;
  overflow-x: auto;
}

.chat-text :deep(pre code) {
  background: transparent;
  padding: 0;
}

.chat-sources {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chat-source {
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
}

.chat-input {
  padding: 16px 20px;
  border-top: 1px solid var(--border-color);
  display: flex;
  gap: 10px;
  align-items: center;
  background: var(--surface);
}

.chat-input input {
  flex: 1;
  min-height: 44px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 10px 12px;
  font-weight: 600;
  color: var(--text-strong);
}

.chat-input input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 8px 20px rgba(119, 136, 115, 0.18);
}

.chat-input input:disabled {
  background: var(--surface-weak);
}

.btn {
  border: none;
  border-radius: 12px;
  padding: 10px 18px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.btn.primary {
  background: #111827;
  color: #fff;
  box-shadow: 0 10px 24px rgba(17, 24, 39, 0.18);
}

.btn.ghost {
  background: #fff;
  color: #111827;
  border: 1px solid rgba(15, 23, 42, 0.16);
  box-shadow: none;
}

.btn.primary:disabled {
  background: rgba(17, 24, 39, 0.4);
  cursor: not-allowed;
  box-shadow: none;
}

.btn.primary:not(:disabled):hover {
  transform: translateY(-1px);
}

@media (max-width: 720px) {
  .chat-body {
    min-height: 0;
  }

  .chat-message {
    max-width: 85%;
  }

  .chat-input {
    flex-direction: column;
    align-items: stretch;
  }

  .btn.primary {
    width: 100%;
  }
}
</style>





