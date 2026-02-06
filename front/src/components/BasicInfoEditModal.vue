<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import LiveImageCropModal from './LiveImageCropModal.vue'
import { deleteSellerImage, fetchCategories, type BroadcastCategory, uploadSellerImage, type UploadImageType } from '../lib/live/api'

type BroadcastInfo = {
  title: string
  category: string
  notice?: string
  thumbnail?: string
  waitingScreen?: string
}

const props = defineProps<{
  modelValue: boolean
  broadcast: BroadcastInfo | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', value: BroadcastInfo): void
}>()

const title = ref('')
const category = ref('')
const notice = ref('')
const thumbnailPreview = ref('')
const waitingPreview = ref('')
const categories = ref<BroadcastCategory[]>([])
const cropperOpen = ref(false)
const cropperSource = ref('')
const cropperFileName = ref('')
const cropTarget = ref<'thumbnail' | 'waiting' | null>(null)
const thumbnailName = ref('')
const waitingName = ref('')
const thumbnailStoredName = ref('')
const waitingStoredName = ref('')
const thumbInputRef = ref<HTMLInputElement | null>(null)
const waitingInputRef = ref<HTMLInputElement | null>(null)

const categoryOptions = computed(() => {
  const names = categories.value.map((item) => item.name)
  if (category.value && !names.includes(category.value)) {
    return [{ id: -1, name: category.value }, ...categories.value]
  }
  return categories.value
})

const isOpen = computed(() => props.modelValue)

const extractFileName = (source: string) => {
  if (!source || source.startsWith('data:')) return ''
  const [path = ''] = source.split('?')
  const segments = path.split('/')
  const last = segments[segments.length - 1] ?? ''
  return decodeURIComponent(last)
}

const extractStoredName = (source: string) => {
  if (!source) return ''
  try {
    const url = new URL(source)
    return (url.pathname ?? '').replace(/^\//, '')
  } catch {
    return source.replace(/^\//, '')
  }
}

const thumbnailDisplayName = computed(() => thumbnailName.value || extractFileName(thumbnailPreview.value))
const waitingDisplayName = computed(() => waitingName.value || extractFileName(waitingPreview.value))

const hydrateFromBroadcast = () => {
  if (!props.broadcast) return
  title.value = props.broadcast.title
  category.value = props.broadcast.category
  notice.value = props.broadcast.notice ?? ''
  thumbnailPreview.value = props.broadcast.thumbnail ?? ''
  waitingPreview.value = props.broadcast.waitingScreen ?? ''
}

watch(isOpen, (open) => {
  if (open) {
    hydrateFromBroadcast()
  }
})

watch(
  () => props.broadcast,
  (next) => {
    if (props.modelValue && next) {
      hydrateFromBroadcast()
    }
  },
  { deep: true },
)

onMounted(() => {
  if (props.broadcast) hydrateFromBroadcast()
  void loadCategories()
})

const close = () => emit('update:modelValue', false)

const loadCategories = async () => {
  try {
    categories.value = await fetchCategories()
  } catch (error) {
    console.error('Failed to load categories', error)
  }
}

const openCropper = (file: File, target: 'thumbnail' | 'waiting') => {
  const reader = new FileReader()
  reader.onloadend = () => {
    cropperSource.value = typeof reader.result === 'string' ? reader.result : ''
    cropperFileName.value = file.name
    cropTarget.value = target
    cropperOpen.value = true
  }
  reader.readAsDataURL(file)
}

const handleFile = (event: Event, target: 'thumbnail' | 'waiting') => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    input.value = ''
    return
  }
  openCropper(file, target)
}

const applyCroppedImage = (payload: { dataUrl: string; fileName: string }) => {
  const target = cropTarget.value
  if (!target) return
  const uploadTarget = target === 'thumbnail' ? 'THUMBNAIL' : 'WAIT_SCREEN'
  const existingUrl = target === 'thumbnail' ? thumbnailPreview.value : waitingPreview.value
  const existingStored = target === 'thumbnail' ? thumbnailStoredName.value : waitingStoredName.value
  const prevStoredName = existingStored || extractStoredName(existingUrl)
  const [header, base64] = payload.dataUrl.split(',')
  if (!header || !base64) return
  const mimeMatch = header.match(/data:(.*?);base64/)
  const mimeType = mimeMatch?.[1] ?? 'image/jpeg'
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  const file = new File([bytes], payload.fileName, { type: mimeType })
  uploadSellerImage(uploadTarget as UploadImageType, file)
      .then((response) => {
        if (target === 'thumbnail') {
          thumbnailPreview.value = response.fileUrl
          thumbnailName.value = response.originalFileName
          thumbnailStoredName.value = response.storedFileName
        }
        if (target === 'waiting') {
          waitingPreview.value = response.fileUrl
          waitingName.value = response.originalFileName
          waitingStoredName.value = response.storedFileName
        }
        if (prevStoredName && prevStoredName !== response.storedFileName) {
          void deleteSellerImage(prevStoredName)
        }
      })
      .catch(() => {
        alert('이미지 업로드에 실패했습니다.')
      })
}

const clearThumbnail = () => {
  const storedName = thumbnailStoredName.value || extractStoredName(thumbnailPreview.value)
  if (storedName) {
    void deleteSellerImage(storedName)
  }
  thumbnailPreview.value = ''
  thumbnailName.value = ''
  thumbnailStoredName.value = ''
  if (thumbInputRef.value) thumbInputRef.value.value = ''
}

const clearWaiting = () => {
  const storedName = waitingStoredName.value || extractStoredName(waitingPreview.value)
  if (storedName) {
    void deleteSellerImage(storedName)
  }
  waitingPreview.value = ''
  waitingName.value = ''
  waitingStoredName.value = ''
  if (waitingInputRef.value) waitingInputRef.value.value = ''
}

const handleThumbnailError = () => {
  clearThumbnail()
}

const handleWaitingError = () => {
  clearWaiting()
}

const handleSave = () => {
  if (!props.broadcast) return close()
  const payload: BroadcastInfo = {
    title: title.value.trim() || props.broadcast.title,
    category: category.value,
    notice: notice.value,
    thumbnail: thumbnailPreview.value,
    waitingScreen: waitingPreview.value,
  }
  emit('save', payload)
  alert('기본정보가 수정되었습니다.')
  close()
}
</script>

<template>
  <div v-if="modelValue" class="ds-modal" role="dialog" aria-modal="true">
    <div class="ds-modal__backdrop" @click="close"></div>
    <div class="ds-modal__card ds-surface">
      <LiveImageCropModal
        v-model="cropperOpen"
        :image-src="cropperSource"
        :file-name="cropperFileName"
        @confirm="applyCroppedImage"
      />
      <header class="ds-modal__head">
        <div>
          <p class="ds-modal__eyebrow">방송 관리</p>
          <h3 class="ds-modal__title">기본정보 수정</h3>
        </div>
        <button type="button" class="ds-modal__close" aria-label="닫기" @click="close">×</button>
      </header>

      <div class="ds-modal__body">
        <label class="field">
          <span class="field__label">방송 제목</span>
          <input v-model="title" type="text" maxlength="30" class="field__input" placeholder="방송 제목을 입력하세요" />
          <span class="field__hint">{{ title.length }}/30</span>
        </label>

        <label class="field">
          <span class="field__label">카테고리</span>
          <select v-model="category" class="field__input">
            <option v-for="item in categoryOptions" :key="item.id" :value="item.name">{{ item.name }}</option>
          </select>
        </label>

        <label class="field">
          <span class="field__label">공지사항</span>
          <textarea
            v-model="notice"
            rows="3"
            maxlength="50"
            class="field__input"
            placeholder="공지사항을 입력하세요"
          ></textarea>
          <span class="field__hint">{{ notice.length }}/50</span>
        </label>

        <div class="upload-grid">
          <label class="field">
            <span class="field__label">썸네일</span>
            <label class="upload-tile">
              <input
                ref="thumbInputRef"
                type="file"
                accept="image/*"
                class="upload-input"
                @change="(event) => handleFile(event, 'thumbnail')"
              />
              <div class="upload-preview">
                <img v-if="thumbnailPreview" :src="thumbnailPreview" alt="썸네일" @error="handleThumbnailError" />
                <div v-else class="upload-placeholder">
                  <span class="upload-icon">⬆</span>
                  <p class="upload-label">클릭하여 업로드</p>
                </div>
              </div>
            </label>
            <p class="upload-filename">{{ thumbnailDisplayName || '선택된 파일 없음' }}</p>
            <button type="button" class="ds-btn ghost upload-clear" @click="clearThumbnail">이미지 삭제</button>
          </label>

          <label class="field">
            <span class="field__label">대기화면</span>
            <label class="upload-tile">
              <input
                ref="waitingInputRef"
                type="file"
                accept="image/*"
                class="upload-input"
                @change="(event) => handleFile(event, 'waiting')"
              />
              <div class="upload-preview">
                <img v-if="waitingPreview" :src="waitingPreview" alt="대기화면" @error="handleWaitingError" />
                <div v-else class="upload-placeholder">
                  <span class="upload-icon">⬆</span>
                  <p class="upload-label">클릭하여 업로드</p>
                </div>
              </div>
            </label>
            <p class="upload-filename">{{ waitingDisplayName || '선택된 파일 없음' }}</p>
            <button type="button" class="ds-btn ghost upload-clear" @click="clearWaiting">이미지 삭제</button>
          </label>
        </div>
      </div>

      <footer class="ds-modal__actions">
        <button type="button" class="ds-btn ghost" @click="close">취소</button>
        <button type="button" class="ds-btn primary" @click="handleSave">저장</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.ds-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1300;
}

.ds-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}

.ds-modal__card {
  position: relative;
  width: min(720px, 94vw);
  max-height: 92vh;
  padding: 20px;
  border-radius: 16px;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ds-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.ds-modal__eyebrow {
  margin: 0 0 4px;
  color: var(--text-muted);
  font-weight: 800;
  letter-spacing: 0.04em;
}

.ds-modal__title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 900;
  color: var(--text-strong);
}

.ds-modal__close {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  width: 36px;
  height: 36px;
  border-radius: 10px;
  font-size: 1.1rem;
  font-weight: 900;
  cursor: pointer;
}

.ds-modal__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field__label {
  font-weight: 800;
  color: var(--text-strong);
}

.field__input {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 10px 12px;
  font-weight: 700;
  font-size: 0.95rem;
}

.field__input:focus {
  outline: 2px solid var(--primary-color);
  outline-offset: 1px;
}

.field__hint {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.8rem;
}

.upload-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.upload-tile {
  display: block;
  border: 2px dashed var(--border-color);
  border-radius: 12px;
  padding: 10px;
  cursor: pointer;
  background: var(--surface-weak);
}

.upload-input {
  display: none;
}

.upload-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: 10px;
}

.upload-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}

.upload-placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  text-align: center;
  color: var(--text-muted);
  gap: 6px;
}

.upload-icon {
  font-size: 1.6rem;
}

.upload-label {
  margin: 0;
  font-weight: 800;
}

.upload-filename {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 0.85rem;
  font-weight: 600;
}

.upload-clear {
  margin-top: 6px;
}

.ds-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.ds-btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 10px;
  padding: 10px 16px;
  font-weight: 800;
  cursor: pointer;
}

.ds-btn.primary {
  background: var(--primary-color);
  color: #fff;
  border-color: transparent;
}

.ds-btn.ghost {
  background: transparent;
  color: var(--text-muted);
}
</style>
