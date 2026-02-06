<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    imageSrc: string
    fileName: string
    frameWidthRatio?: number
    frameHeightRatio?: number
    outputWidth?: number
    outputHeight?: number
    title?: string
  }>(),
  {
    frameWidthRatio: 16,
    frameHeightRatio: 9,
    outputWidth: 1280,
    outputHeight: 720,
    title: '16:9 이미지 자르기',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', payload: { dataUrl: string; fileName: string }): void
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const frameRef = ref<HTMLDivElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)
const imageElement = ref<HTMLImageElement | null>(null)
const scale = ref(1)
const offset = ref({ x: 0, y: 0 })
const dragState = ref<{ active: boolean; startX: number; startY: number; originX: number; originY: number }>({
  active: false,
  startX: 0,
  startY: 0,
  originX: 0,
  originY: 0,
})

const frameRect = ref({ x: 0, y: 0, width: 0, height: 0 })
const frameSize = ref({ width: 0, height: 0 })

const isOpen = computed(() => props.modelValue)

const close = () => emit('update:modelValue', false)

const syncFrameRect = async () => {
  if (!frameRef.value || !containerRef.value || !canvasRef.value) return
  const containerBox = containerRef.value.getBoundingClientRect()
  canvasRef.value.width = containerBox.width
  canvasRef.value.height = containerBox.height
  const ratio = props.frameWidthRatio / props.frameHeightRatio
  const maxWidth = containerBox.width * 0.9
  const maxHeight = containerBox.height * 0.9
  let frameWidth = Math.min(maxWidth, 640)
  let frameHeight = frameWidth / ratio
  if (frameHeight > maxHeight) {
    frameHeight = maxHeight
    frameWidth = frameHeight * ratio
  }
  frameSize.value = { width: frameWidth, height: frameHeight }
  await nextTick()
  const frameBox = frameRef.value.getBoundingClientRect()
  frameRect.value = {
    x: frameBox.left - containerBox.left,
    y: frameBox.top - containerBox.top,
    width: frameBox.width,
    height: frameBox.height,
  }
}

const loadImage = async () => {
  if (!props.imageSrc) return
  const img = new Image()
  img.src = props.imageSrc
  await new Promise<void>((resolve, reject) => {
    img.onload = () => resolve()
    img.onerror = () => reject(new Error('failed to load'))
  })
  imageElement.value = img
  scale.value = 1
  offset.value = { x: 0, y: 0 }
  await nextTick()
  await syncFrameRect()
  drawPreview()
}

const getBaseScale = () => {
  if (!imageElement.value) return 1
  const { width, height } = frameRect.value
  if (!width || !height) return 1
  return Math.min(1, width / imageElement.value.width, height / imageElement.value.height)
}

const drawPreview = () => {
  const canvas = canvasRef.value
  const img = imageElement.value
  if (!canvas || !img) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const { width, height } = canvas
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = '#000'
  ctx.fillRect(0, 0, width, height)

  const baseScale = getBaseScale()
  const drawScale = baseScale * scale.value
  const targetWidth = img.width * drawScale
  const targetHeight = img.height * drawScale
  const centerX = frameRect.value.x + frameRect.value.width / 2 + offset.value.x
  const centerY = frameRect.value.y + frameRect.value.height / 2 + offset.value.y
  const drawX = centerX - targetWidth / 2
  const drawY = centerY - targetHeight / 2
  ctx.drawImage(img, drawX, drawY, targetWidth, targetHeight)
}

const handlePointerDown = (event: PointerEvent) => {
  if (!isOpen.value) return
  dragState.value = {
    active: true,
    startX: event.clientX,
    startY: event.clientY,
    originX: offset.value.x,
    originY: offset.value.y,
  }
}

const handlePointerMove = (event: PointerEvent) => {
  if (!dragState.value.active) return
  offset.value = {
    x: dragState.value.originX + (event.clientX - dragState.value.startX),
    y: dragState.value.originY + (event.clientY - dragState.value.startY),
  }
  drawPreview()
}

const handlePointerUp = () => {
  dragState.value.active = false
}

const handleScaleInput = (value: number) => {
  scale.value = value
  drawPreview()
}

const confirmCrop = () => {
  const img = imageElement.value
  if (!img) return
  const outputWidth = props.outputWidth
  const outputHeight = props.outputHeight
  const outputCanvas = document.createElement('canvas')
  outputCanvas.width = outputWidth
  outputCanvas.height = outputHeight
  const ctx = outputCanvas.getContext('2d')
  if (!ctx) return
  ctx.fillStyle = '#000'
  ctx.fillRect(0, 0, outputWidth, outputHeight)

  const baseScale = getBaseScale()
  const ratio = outputWidth / frameRect.value.width
  const drawScale = baseScale * scale.value * ratio
  const targetWidth = img.width * drawScale
  const targetHeight = img.height * drawScale
  const centerX = outputWidth / 2 + offset.value.x * ratio
  const centerY = outputHeight / 2 + offset.value.y * ratio
  const drawX = centerX - targetWidth / 2
  const drawY = centerY - targetHeight / 2
  ctx.drawImage(img, drawX, drawY, targetWidth, targetHeight)

  emit('confirm', { dataUrl: outputCanvas.toDataURL('image/jpeg', 0.9), fileName: props.fileName })
  close()
}

watch(
  () => [props.modelValue, props.imageSrc],
  ([open]) => {
    if (open) {
      void loadImage()
    }
  },
)

onMounted(() => {
  window.addEventListener('pointerup', handlePointerUp)
  window.addEventListener('pointermove', handlePointerMove)
})

onUnmounted(() => {
  window.removeEventListener('pointerup', handlePointerUp)
  window.removeEventListener('pointermove', handlePointerMove)
})
</script>

<template>
  <div v-if="modelValue" class="ds-modal" role="dialog" aria-modal="true">
    <div class="ds-modal__backdrop" @click="close"></div>
    <div class="ds-modal__card ds-surface">
      <header class="ds-modal__head">
        <div>
          <p class="ds-modal__eyebrow">이미지 편집</p>
          <h3 class="ds-modal__title">{{ title }}</h3>
        </div>
        <button type="button" class="ds-modal__close" aria-label="닫기" @click="close">×</button>
      </header>

      <div class="cropper" ref="containerRef">
        <canvas ref="canvasRef" class="cropper__canvas" width="640" height="420" @pointerdown="handlePointerDown"></canvas>
        <div
          ref="frameRef"
          class="cropper__frame"
          :style="{
            width: frameSize.width ? `${frameSize.width}px` : undefined,
            height: frameSize.height ? `${frameSize.height}px` : undefined,
          }"
        ></div>
      </div>

      <div class="cropper__controls">
        <label class="field">
          <span class="field__label">확대/축소</span>
          <input
            type="range"
            min="0.1"
            max="3"
            step="0.01"
            :value="scale"
            class="field__input"
            @input="handleScaleInput(Number(($event.target as HTMLInputElement).value))"
          />
        </label>
      </div>

      <footer class="ds-modal__actions">
        <button type="button" class="ds-btn ghost" @click="close">취소</button>
        <button type="button" class="ds-btn primary" @click="confirmCrop">적용</button>
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
  z-index: 1400;
}

.ds-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}

.ds-modal__card {
  position: relative;
  width: min(760px, 94vw);
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

.ds-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.cropper {
  position: relative;
  width: min(680px, 94vw);
  height: 420px;
  margin: 0 auto;
  border-radius: 16px;
  overflow: hidden;
  background: #000;
}

.cropper__canvas {
  width: 100%;
  height: 100%;
  display: block;
  touch-action: none;
  cursor: grab;
}

.cropper__canvas:active {
  cursor: grabbing;
}

.cropper__frame {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  border: 2px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.55);
  pointer-events: none;
}

.cropper__controls {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
