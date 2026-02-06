<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../../components/PageContainer.vue'
import PageHeader from '../../components/PageHeader.vue'

const router = useRouter()
const route = useRoute()
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const editorRef = ref<HTMLDivElement | null>(null)
const detailHtml = ref('')
const error = ref('')
const isSaving = ref(false)

const buildAuthHeaders = (): Record<string, string> => {
  const access = localStorage.getItem('access') || sessionStorage.getItem('access')
  if (!access) return {}
  return { Authorization: `Bearer ${access}` }
}

const exec = (command: string, value?: string) => {
  document.execCommand(command, false, value)
  syncFromEditorSoon()
}

const addLink = () => {
  const url = window.prompt('링크 주소를 입력하세요.')
  if (!url) return
  exec('createLink', url)
}

const addImage = () => {
  const url = window.prompt('이미지 주소(URL)를 입력하세요.')
  if (!url) return
  exec('insertImage', url)
}

const formatBlock = (tag: 'p' | 'h1' | 'h2' | 'h3') => {
  exec('formatBlock', `<${tag}>`)
}

const syncFromEditor = () => {
  detailHtml.value = editorRef.value?.innerHTML ?? ''
}

const syncFromEditorSoon = () => {
  window.setTimeout(() => {
    syncFromEditor()
  }, 0)
}

const handleInput = () => {
  syncFromEditor()
}

const goBack = () => {
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  router.push({ name: 'seller-products-edit', params: { id } }).catch(() => {})
}

const handleSubmit = async () => {
  error.value = ''
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  if (!id) {
    error.value = '상품 정보를 확인할 수 없습니다.'
    return
  }

  syncFromEditor()
  isSaving.value = true
  try {
    const response = await fetch(`${apiBase}/seller/products/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...buildAuthHeaders(),
      },
      credentials: 'include',
      body: JSON.stringify({ detail_html: detailHtml.value }),
    })
    if (response.status === 401 || response.status === 403) {
      error.value = '권한이 없습니다. 다시 로그인해주세요.'
      return
    }
    if (response.status === 404) {
      window.alert('이미 삭제되었거나 상품을 찾을 수 없습니다.')
      router.push({ name: 'seller-products' }).catch(() => {})
      return
    }
    if (!response.ok) {
      error.value = '상세 정보를 저장하지 못했습니다.'
      return
    }
    router.push({ name: 'seller-products' }).catch(() => {})
  } catch {
    error.value = '상세 정보를 저장하지 못했습니다.'
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  if (!id) {
    error.value = '상품 정보를 확인할 수 없습니다.'
    return
  }

  const load = async () => {
    try {
      const response = await fetch(`${apiBase}/seller/products/${id}`, {
        method: 'GET',
        headers: {
          ...buildAuthHeaders(),
        },
        credentials: 'include',
      })
      if (response.status === 401 || response.status === 403) {
        error.value = '권한이 없습니다. 다시 로그인해주세요.'
        return
      }
      if (response.status === 404) {
        window.alert('이미 삭제되었거나 상품을 찾을 수 없습니다.')
        router.push({ name: 'seller-products' }).catch(() => {})
        return
      }
      if (!response.ok) {
        error.value = '상세 정보를 불러올 수 없습니다.'
        return
      }
      const data = (await response.json()) as { detail_html?: string | null }
      detailHtml.value = data.detail_html ?? ''
      window.setTimeout(() => {
        if (!editorRef.value) return
        editorRef.value.innerHTML = detailHtml.value || ''
      }, 0)
    } catch {
      error.value = '상세 정보를 불러올 수 없습니다.'
    }
  }

  load()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="상품 수정 - 상세정보 작성" />

    <section class="detail-surface ds-surface">
      <div class="toolbar" role="toolbar" aria-label="상세 설명 편집 툴바">
        <div class="toolbar__group">
          <button type="button" class="tool-btn" @click="exec('bold')" aria-label="굵게"><span aria-hidden="true">B</span></button>
          <button type="button" class="tool-btn" @click="exec('italic')" aria-label="기울임"><span aria-hidden="true">I</span></button>
          <button type="button" class="tool-btn" @click="exec('underline')" aria-label="밑줄"><span aria-hidden="true">U</span></button>
          <button type="button" class="tool-btn" @click="exec('removeFormat')" aria-label="서식 제거">서식</button>
        </div>

        <div class="toolbar__group">
          <button type="button" class="tool-btn" @click="formatBlock('h1')">H1</button>
          <button type="button" class="tool-btn" @click="formatBlock('h2')">H2</button>
          <button type="button" class="tool-btn" @click="formatBlock('h3')">H3</button>
          <button type="button" class="tool-btn" @click="formatBlock('p')">P</button>
        </div>

        <div class="toolbar__group">
          <button type="button" class="tool-btn" @click="exec('insertUnorderedList')">• 리스트</button>
          <button type="button" class="tool-btn" @click="exec('insertOrderedList')">1. 리스트</button>
        </div>

        <div class="toolbar__group">
          <button type="button" class="tool-btn" @click="addLink">링크</button>
          <button type="button" class="tool-btn" @click="addImage">이미지</button>
        </div>
      </div>

      <div class="editor-wrap">
        <div
          ref="editorRef"
          class="editor"
          contenteditable="true"
          spellcheck="false"
          aria-label="상세 설명 편집 영역"
          @input="handleInput"
        ></div>

        <p class="hint">* 간단 에디터(목업)입니다. 저장은 로컬스토리지 기반이며, 실제 서버 연동 전까지 임시로 사용합니다.</p>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions">
        <button type="button" class="btn" @click="goBack">이전</button>
        <button type="button" class="btn primary" :disabled="isSaving" @click="handleSubmit">저장</button>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.detail-surface {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border-radius: 14px;
  background: var(--surface-weak);
  border: 1px solid var(--border-color);
}

.toolbar__group {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.tool-btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 8px 10px;
  font-weight: 900;
  cursor: pointer;
  line-height: 1;
}

.editor-wrap {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.editor {
  min-height: 320px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  outline: none;
}

.hint {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.error {
  margin: 0;
  color: #b91c1c;
  font-weight: 800;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 16px;
  font-weight: 900;
  cursor: pointer;
}

.btn.primary {
  background: var(--text-strong);
  color: var(--surface);
  border-color: transparent;
}
</style>
