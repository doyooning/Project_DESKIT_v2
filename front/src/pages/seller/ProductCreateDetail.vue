<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '../../components/PageContainer.vue'
import PageHeader from '../../components/PageHeader.vue'
import ProductTagInput from '../../components/seller/ProductTagInput.vue'
import {
  clearProductDraft,
  loadProductDraft,
  saveProductDraft,
  upsertProduct,
  type SellerProductDraft,
} from '../../composables/useSellerProducts'

const router = useRouter()
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'

const editorRef = ref<HTMLDivElement | null>(null)
const detailHtml = ref('')
const error = ref('')
const success = ref('')
const draft = ref<SellerProductDraft | null>(null)
const tags = ref<string[]>([])
const tagMap = ref<Map<string, number> | null>(null)

const buildAuthHeaders = (): Record<string, string> => {
  const access = localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
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

const saveDraftOnly = () => {
  if (!draft.value) return
  saveProductDraft({
    ...draft.value,
    detailHtml: detailHtml.value,
    tags: tags.value,
  })
}

const syncFromEditor = () => {
  detailHtml.value = editorRef.value?.innerHTML ?? ''
}

const syncFromEditorSoon = () => {
  // execCommand 직후에 DOM 반영 타이밍이 있어서 1tick 뒤에 동기화
  window.setTimeout(() => {
    syncFromEditor()
    saveDraftOnly()
  }, 0)
}

const handleInput = () => {
  syncFromEditor()
  saveDraftOnly()
}

const handleTagsUpdate = (next: string[]) => {
  tags.value = next
  saveDraftOnly()
}

// Draft-only helpers: stored previews are data URLs, so we rebuild files on submit.
const parseDataUrl = (dataUrl: string) => {
  const match = /^data:([^;]+);base64,(.+)$/.exec(dataUrl)
  if (!match) return null
  return { mime: match[1], data: match[2] }
}

const dataUrlToFile = (dataUrl: string, fileName: string) => {
  const parsed = parseDataUrl(dataUrl)
  if (!parsed || typeof parsed.data !== 'string') return null
  const binary = atob(parsed.data)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new File([bytes], fileName, { type: parsed.mime })
}

const buildImageUploadPayload = (images: string[]) =>
  images
    .map((preview, slotIndex) => {
      if (!preview) return null
      const file = dataUrlToFile(preview, `product-image-${slotIndex}.png`)
      if (!file) return null
      return {
        file,
        imageType: slotIndex === 0 ? 'THUMBNAIL' : 'GALLERY',
        slotIndex,
      }
    })
    .filter((item): item is { file: File; imageType: 'THUMBNAIL' | 'GALLERY'; slotIndex: number } =>
      Boolean(item),
    )
    .sort((a, b) => {
      if (a.imageType !== b.imageType) {
        return a.imageType === 'THUMBNAIL' ? -1 : 1
      }
      return a.slotIndex - b.slotIndex
    })

const uploadProductImages = async (productId: number, images: string[]) => {
  const payloads = buildImageUploadPayload(images)
  for (const payload of payloads) {
    // Upload sequentially to keep slot ordering stable and avoid server-side race issues.
    const formData = new FormData()
    formData.append('file', payload.file)
    formData.append('imageType', payload.imageType)
    formData.append('slotIndex', String(payload.slotIndex))

    const response = await fetch(`${apiBase}/seller/products/${productId}/images`, {
      method: 'POST',
      headers: {
        ...buildAuthHeaders(),
      },
      credentials: 'include',
      body: formData,
    })
    if (!response.ok) {
      throw new Error('upload failed')
    }
  }
}

const goBack = () => {
  saveDraftOnly()
  router.push('/seller/products/create').catch(() => {})
}

const normalizeTag = (value: string) => value.trim()

const handleSubmit = async () => {
  error.value = ''
  success.value = ''
  if (!draft.value) {
    error.value = '기본 정보를 먼저 입력해주세요.'
    return
  }

  const name = (draft.value.name || '').trim()
  const shortDesc = (draft.value.shortDesc || '').trim()

  const costPrice = Number(draft.value.costPrice)
  const price = Number(draft.value.price)
  const stock = Number(draft.value.stock)

  if (!name) {
    error.value = '상품명을 입력해주세요.'
    return
  }
  if (!Number.isFinite(costPrice) || costPrice < 0 || !Number.isFinite(price) || price < 0) {
    error.value = '원가/판매가를 올바르게 입력해주세요.'
    return
  }
  if (!Number.isFinite(stock) || stock < 0) {
    error.value = '재고를 올바르게 입력해주세요.'
    return
  }

  const productId = Number(draft.value.id)
  if (!Number.isFinite(productId)) {
    error.value = '상품 정보를 확인할 수 없습니다.'
    return
  }

  const authHeaders = buildAuthHeaders()

  // 마지막 동기화
  syncFromEditor()

  try {
    const normalizedTags = tags.value
      .map((tag) => normalizeTag(tag))
      .filter((tag) => tag.length > 0)
    const uniqueTags = Array.from(new Set(normalizedTags))

    if (!tagMap.value || tagMap.value.size === 0) {
      const tagResponse = await fetch(`${apiBase}/seller/tags`, {
        method: 'GET',
        headers: {
          ...authHeaders,
        },
        credentials: 'include',
      })
      if (!tagResponse.ok) {
        throw new Error('tag list failed')
      }
      const tagData = await tagResponse.json()
      const map = new Map<string, number>()
      if (Array.isArray(tagData)) {
        tagData.forEach((raw) => {
          if (!raw || typeof raw !== 'object') return
          const record = raw as Record<string, unknown>
          const tagId = typeof record.tag_id === 'number' ? record.tag_id : null
          const tagName = typeof record.tag_name === 'string' ? record.tag_name : null
          if (tagId == null || tagName == null) return
          map.set(normalizeTag(tagName), tagId)
        })
      }
      tagMap.value = map
    }

    const unknownTags = uniqueTags.filter((tag) => !tagMap.value?.has(tag))
    if (unknownTags.length > 0) {
      error.value = `등록되지 않은 태그입니다: ${unknownTags.join(', ')}`
      return
    }

    const tagIds = uniqueTags
      .map((tag) => tagMap.value?.get(tag))
      .filter((tagId): tagId is number => typeof tagId === 'number')

    const detailResponse = await fetch(`${apiBase}/seller/products/${productId}/detail`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders,
      },
      credentials: 'include',
      body: JSON.stringify({ detail_html: detailHtml.value }),
    })
    if (!detailResponse.ok) {
      throw new Error('detail update failed')
    }

    await uploadProductImages(productId, Array.isArray(draft.value.images) ? draft.value.images : [])

    const tagUpdateResponse = await fetch(`${apiBase}/seller/products/${productId}/tags`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders,
      },
      credentials: 'include',
      body: JSON.stringify({ tag_ids: tagIds }),
    })
    if (!tagUpdateResponse.ok) {
      throw new Error('tag update failed')
    }

    const completeResponse = await fetch(`${apiBase}/seller/products/${productId}/complete`, {
      method: 'PATCH',
      headers: {
        ...authHeaders,
      },
      credentials: 'include',
    })
    if (!completeResponse.ok) {
      throw new Error('complete failed')
    }
  } catch {
    // Image upload failure does not rolback product creation by design (no rollback yet).
    error.value = '상품 등록에 실패했습니다.'
    return
  }

  const now = new Date().toISOString()

  upsertProduct({
    id: draft.value.id || `new-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    name,
    shortDesc,
    costPrice,
    price,
    stock,
    images: Array.isArray(draft.value.images) ? draft.value.images : [],
    detailHtml: detailHtml.value,
    createdAt: now,
    updatedAt: now,
  })

  clearProductDraft()
  success.value = '상품이 등록되었습니다.'
  window.setTimeout(() => {
    router.push('/seller/products').catch(() => {})
  }, 300)
}

onMounted(() => {
  const loaded = loadProductDraft()
  if (!loaded) {
    draft.value = null
    error.value = '기본 정보를 먼저 입력해주세요.'
    return
  }
  draft.value = loaded
  detailHtml.value = loaded.detailHtml || ''
  tags.value = Array.isArray(loaded.tags) ? loaded.tags : []

  // contenteditable 초기 값 주입
  window.setTimeout(() => {
    if (!editorRef.value) return
    editorRef.value.innerHTML = detailHtml.value || ''
  }, 0)
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="상품 등록 - 상세정보 작성" />

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
      <p v-else-if="success" class="hint">{{ success }}</p>

      <ProductTagInput :tags="tags" @update:tags="handleTagsUpdate" />

      <div class="actions">
        <button type="button" class="btn" @click="goBack">이전</button>
        <button type="button" class="btn primary" @click="handleSubmit">생성</button>
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
