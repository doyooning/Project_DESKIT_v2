<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import PageContainer from '../../components/PageContainer.vue'
import PageHeader from '../../components/PageHeader.vue'
import ProductBasicFields from '../../components/seller/ProductBasicFields.vue'
import LiveImageCropModal from '../../components/LiveImageCropModal.vue'
import {clearProductDraft, loadProductDraft, saveProductDraft} from '../../composables/useSellerProducts'

type ImageSlot = {
  slot: number
  file?: File
  preview?: string
  uploading?: boolean
}

const route = useRoute()
const router = useRouter()
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'

const name = ref('')
const shortDesc = ref('')
const costPrice = ref(0)
const price = ref(0)
const stock = ref(0)
const images = ref<ImageSlot[]>([])
const imageKeys = ref<string[]>(Array.from({ length: 5 }, () => ''))
const error = ref('')
const isSaving = ref(false)
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const cropperOpen = ref(false)
const cropperSource = ref('')
const cropperFileName = ref('')
const cropperIndex = ref<number | null>(null)
const cropperInputRef = ref<HTMLInputElement | null>(null)

const buildAuthHeaders = (): Record<string, string> => {
  const access = localStorage.getItem('access') || sessionStorage.getItem('access')
  if (!access) return {}
  return {Authorization: `Bearer ${access}`}
}

const loadDraft = () => {
  const draft = loadProductDraft()
  if (!draft) return
  name.value = draft.name
  shortDesc.value = draft.shortDesc
  costPrice.value = draft.costPrice
  price.value = draft.price
  stock.value = draft.stock
  const previews = Array.isArray(draft.images) ? [...draft.images].slice(0, 5) : []
  images.value = buildImageSlots(previews)
  imageKeys.value = Array.from({ length: 5 }, () => '')
}

const saveDraft = (productId?: number) => {
  saveProductDraft({
    id: productId ? String(productId) : undefined,
    name: name.value.trim(),
    shortDesc: shortDesc.value.trim(),
    costPrice: costPrice.value,
    price: price.value,
    stock: stock.value,
    images: images.value.map((slot) => slot.preview ?? ''),
    detailHtml: '',
  })
}

const buildImageSlots = (previews: string[] = []) => {
  const slots: ImageSlot[] = []
  for (let i = 0; i < 5; i += 1) {
    const preview = previews[i] ?? ''
    slots.push({slot: i, preview: preview || undefined, uploading: false})
  }
  return slots
}

const updateSlot = (index: number, patch: Partial<ImageSlot>) => {
  images.value = images.value.map((slot) =>
    slot.slot === index ? { ...slot, ...patch, slot: index } : slot,
  )
}

const resetCropperState = () => {
  cropperSource.value = ''
  cropperFileName.value = ''
  cropperIndex.value = null
  cropperInputRef.value = null
}

const openCropper = (file: File, index: number, input: HTMLInputElement) => {
  const reader = new FileReader()
  reader.onload = () => {
    cropperSource.value = typeof reader.result === 'string' ? reader.result : ''
    cropperFileName.value = file.name
    cropperIndex.value = index
    cropperInputRef.value = input
    cropperOpen.value = true
  }
  reader.readAsDataURL(file)
}

const dataUrlToFile = (dataUrl: string, fileName: string) => {
  const [header, base64] = dataUrl.split(',')
  if (!header || !base64) return null
  const mimeMatch = header.match(/data:(.*?);base64/)
  const mimeType = mimeMatch?.[1] ?? 'image/jpeg'
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new File([bytes], fileName, { type: mimeType })
}

const uploadImageFile = async (index: number, file: File) => {
  error.value = ''
  updateSlot(index, { uploading: true })
  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch(`${apiBase}/seller/products/images/upload`, {
      method: 'POST',
      headers: {
        ...buildAuthHeaders(),
      },
      credentials: 'include',
      body: formData,
    })
    if (response.status === 401 || response.status === 403) {
      error.value = '권한이 없습니다. 다시 로그인해주세요.'
      return
    }
    if (response.status === 413) {
      error.value = '파일 크기가 제한을 초과했습니다.'
      return
    }
    if (!response.ok) {
      error.value = '이미지 업로드에 실패했습니다.'
      return
    }
    const data = (await response.json()) as { url?: string; key?: string }
    if (!data.url) {
      error.value = '이미지 업로드에 실패했습니다.'
      return
    }
    const nextKeys = [...imageKeys.value]
    nextKeys[index] = data.key ?? ''
    imageKeys.value = nextKeys
    updateSlot(index, { preview: data.url, file: undefined })
  } catch {
    error.value = '이미지 업로드에 실패했습니다.'
    updateSlot(index, { file: undefined })
  } finally {
    updateSlot(index, { uploading: false })
    if (cropperInputRef.value) {
      cropperInputRef.value.value = ''
    }
  }
}

const handleCropConfirm = async (payload: { dataUrl: string; fileName: string }) => {
  const index = cropperIndex.value
  if (index === null) return
  const file = dataUrlToFile(payload.dataUrl, payload.fileName)
  if (!file) {
    error.value = '이미지 업로드에 실패했습니다.'
    cropperOpen.value = false
    return
  }
  if (file.size > MAX_IMAGE_BYTES) {
    error.value = '파일 크기가 제한을 초과했습니다.'
    cropperOpen.value = false
    return
  }
  await uploadImageFile(index, file)
  cropperOpen.value = false
}

const setImageAt = async (index: number, event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type || !file.type.startsWith('image/')) {
    error.value = '이미지 파일만 업로드할 수 있습니다.'
    input.value = ''
    return
  }
  if (file.size > MAX_IMAGE_BYTES) {
    error.value = '파일 크기가 제한을 초과했습니다.'
    input.value = ''
    return
  }

  error.value = ''
  openCropper(file, index, input)
}

const clearImageAt = (index: number) => {
  updateSlot(index, { preview: undefined, file: undefined, uploading: false })
  const nextKeys = [...imageKeys.value]
  nextKeys[index] = ''
  imageKeys.value = nextKeys
}

const goNext = async () => {
  if (isSaving.value) return
  error.value = ''
  if (!name.value.trim() || !shortDesc.value.trim()) {
    error.value = '상품명과 한 줄 소개를 입력해주세요.'
    return
  }
  if (!images.value[0]?.preview) {
    error.value = '썸네일 이미지를 등록해주세요.'
    return
  }
  if (images.value.some((slot) => slot.uploading)) {
    error.value = '이미지 업로드가 완료될 때까지 기다려주세요.'
    return
  }
  isSaving.value = true
  try {
    const response = await fetch(`${apiBase}/seller/products`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...buildAuthHeaders(),
      },
      credentials: 'include',
      body: JSON.stringify({
        product_name: name.value.trim(),
        short_desc: shortDesc.value.trim(),
        detail_html: '',
        price: price.value,
        stock_qty: stock.value,
        cost_price: costPrice.value,
      }),
    })
    if (response.status === 401 || response.status === 403) {
      error.value = '권한이 없습니다. 다시 로그인해주세요.'
      return
    }
    if (!response.ok) {
      error.value = '상품 등록에 실패했습니다.'
      return
    }
    const payload = (await response.json().catch(() => null)) as {product_id?: number} | null
    if (!payload?.product_id) {
      error.value = '상품 등록에 실패했습니다.'
      return
    }
    const imageUrls = images.value.map((slot) => slot.preview ?? '')
    const normalizedKeys = Array.from({ length: 5 }, (_, idx) => imageKeys.value[idx] ?? '')
    const imageResponse = await fetch(`${apiBase}/seller/products/${payload.product_id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...buildAuthHeaders(),
      },
      credentials: 'include',
      body: JSON.stringify({ image_urls: imageUrls, image_keys: normalizedKeys }),
    })
    if (imageResponse.status === 401 || imageResponse.status === 403) {
      error.value = '권한이 없습니다. 다시 로그인해주세요.'
      return
    }
    if (imageResponse.status === 404) {
      error.value = '상품을 찾을 수 없습니다.'
      return
    }
    if (imageResponse.status === 413) {
      error.value = '파일 크기가 제한을 초과했습니다.'
      return
    }
    if (!imageResponse.ok) {
      error.value = '이미지 저장에 실패했습니다.'
      return
    }
    saveDraft(payload.product_id)
    router.push('/seller/products/create/detail').catch(() => {
    })
  } catch {
    error.value = '상품 등록에 실패했습니다.'
  } finally {
    isSaving.value = false
  }
}

const cancel = () => {
  router.push('/seller/products').catch(() => {
  })
}

onMounted(() => {
  const resume = route.query.resume
  const shouldResume = resume === '1' || (Array.isArray(resume) && resume[0] === '1')
  if (shouldResume) {
    loadDraft()
    return
  }
  clearProductDraft()
  name.value = ''
  shortDesc.value = ''
  costPrice.value = 0
  price.value = 0
  stock.value = 0
  images.value = buildImageSlots()
  imageKeys.value = Array.from({ length: 5 }, () => '')
})

watch(cropperOpen, (open, wasOpen) => {
  if (!open && wasOpen) {
    if (cropperInputRef.value) {
      cropperInputRef.value.value = ''
    }
    resetCropperState()
  }
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="상품 등록 - 기본 정보"/>
    <LiveImageCropModal
      v-model="cropperOpen"
      :image-src="cropperSource"
      :file-name="cropperFileName"
      title="1:1 이미지 자르기"
      :frame-width-ratio="1"
      :frame-height-ratio="1"
      :output-width="1024"
      :output-height="1024"
      @confirm="handleCropConfirm"
    />
    <section class="create-card ds-surface">
      <ProductBasicFields
        v-model:name="name"
        v-model:shortDesc="shortDesc"
        v-model:price="price"
        v-model:stock="stock"
      >
        <template #extra-fields>
          <label class="field">
            <span class="field__label">원가</span>
            <input v-model.number="costPrice" type="number" min="0" class="basic-input"/>
          </label>
        </template>
      </ProductBasicFields>
      <div class="section-block">
        <div class="section-head">
          <h3>상품 이미지</h3>
        </div>
        <div class="image-slots">
          <div v-for="slot in images" :key="slot.slot" class="image-slot">
            <div class="image-slot__label">{{ slot.slot === 0 ? '썸네일' : String(slot.slot) }}</div>
            <div class="image-slot__preview">
              <img v-if="slot.preview" :src="slot.preview" :alt="`상품 이미지 ${slot.slot}`"/>
              <label v-if="!slot.preview" class="btn ghost image-slot__upload">
                {{ slot.uploading ? '업로드 중' : '업로드' }}
                <input
                  type="file"
                  accept="image/*"
                  :disabled="slot.uploading"
                  @change="setImageAt(slot.slot, $event)"
                  hidden
                />
              </label>
            </div>
            <div class="image-slot__actions">
              <button v-if="slot.preview" type="button" class="btn ghost" @click="clearImageAt(slot.slot)">
                삭제
              </button>
            </div>
          </div>
        </div>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="actions">
        <button type="button" class="btn" @click="cancel">취소</button>
        <button type="button" class="btn primary" :disabled="isSaving" @click="goNext">상세 작성</button>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.create-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
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

.field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.section-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.empty-hint {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.image-slots {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.image-slot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px;
  background: var(--surface);
}

.image-slot__label {
  font-weight: 800;
  color: var(--text-strong);
  font-size: 0.9rem;
}

.image-slot__preview {
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-weak);
  height: 140px;
  display: grid;
  place-items: center;
}

.image-slot__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.image-slot__placeholder {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.image-slot__actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  min-height: 28px;
}

.image-slot__actions .btn,
.image-slot__upload {
  padding: 6px 12px;
  line-height: 1.1;
  font-size: 0.85rem;
}

.image-slot__upload {
  padding: 6px 12px;
}

input {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn {
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 900;
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  cursor: pointer;
}

.btn.primary {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.btn.ghost {
  border-color: var(--border-color);
  color: var(--text-muted);
}

.error {
  margin: 0;
  color: #ef4444;
  font-weight: 700;
}

@media (max-width: 720px) {
  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
