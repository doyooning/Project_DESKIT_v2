<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
  modelValue: boolean
  broadcastTitle?: string
  initialCameraId?: string
  initialMicId?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'apply', value: { cameraId: string; microphoneId: string }): void
  (e: 'start'): void
}>()

const selectedCamera = ref('')
const selectedMic = ref('')
const volumeLevel = ref(70)
const deviceCameras = ref<MediaDeviceInfo[]>([])
const deviceMics = ref<MediaDeviceInfo[]>([])
const deviceError = ref<string | null>(null)
const videoRef = ref<HTMLVideoElement | null>(null)
const mediaStream = ref<MediaStream | null>(null)
const audioContext = ref<AudioContext | null>(null)
const analyser = ref<AnalyserNode | null>(null)
const meterFrame = ref<number | null>(null)
const isSyncingDevices = ref(false)

const stopMeter = () => {
  if (meterFrame.value !== null) {
    cancelAnimationFrame(meterFrame.value)
    meterFrame.value = null
  }
}

const stopPreview = () => {
  stopMeter()
  if (audioContext.value) {
    audioContext.value.close()
    audioContext.value = null
  }
  analyser.value = null
  if (mediaStream.value) {
    mediaStream.value.getTracks().forEach((track) => track.stop())
    mediaStream.value = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
}

const hydrateSelection = () => {
  selectedCamera.value = props.initialCameraId ?? ''
  selectedMic.value = props.initialMicId ?? ''
}

const startMeter = (stream: MediaStream) => {
  const audioTracks = stream.getAudioTracks()
  if (!audioTracks.length) {
    volumeLevel.value = 0
    return
  }
  const context = new AudioContext()
  const source = context.createMediaStreamSource(stream)
  const analyserNode = context.createAnalyser()
  analyserNode.fftSize = 512
  source.connect(analyserNode)
  audioContext.value = context
  analyser.value = analyserNode
  const buffer = new Uint8Array(analyserNode.fftSize)
  const update = () => {
    analyserNode.getByteTimeDomainData(buffer)
    let sum = 0
    for (const sample of buffer) {
      const normalized = (sample - 128) / 128
      sum += normalized * normalized
    }
    const rms = Math.sqrt(sum / buffer.length)
    volumeLevel.value = Math.min(100, Math.round(rms * 140))
    meterFrame.value = requestAnimationFrame(update)
  }
  update()
}

const loadDevices = async () => {
  if (!navigator.mediaDevices?.enumerateDevices) {
    deviceError.value = '디바이스 정보를 가져올 수 없습니다.'
    return
  }
  const devices = await navigator.mediaDevices.enumerateDevices()
  deviceCameras.value = devices.filter((device) => device.kind === 'videoinput')
  deviceMics.value = devices.filter((device) => device.kind === 'audioinput')
  if (!selectedCamera.value && deviceCameras.value.length > 0) {
    const firstCamera = deviceCameras.value[0]
    if (firstCamera) {
      selectedCamera.value = firstCamera.deviceId
    }
  }
  if (!selectedMic.value && deviceMics.value.length > 0) {
    const firstMic = deviceMics.value[0]
    if (firstMic) {
      selectedMic.value = firstMic.deviceId
    }
  }
}

const startPreview = async () => {
  if (!navigator.mediaDevices?.getUserMedia) {
    deviceError.value = '미디어 스트림을 사용할 수 없습니다.'
    return
  }
  stopPreview()
  deviceError.value = null
  try {
    const constraints: MediaStreamConstraints = {
      video: selectedCamera.value ? { deviceId: { exact: selectedCamera.value } } : true,
      audio: selectedMic.value ? { deviceId: { exact: selectedMic.value } } : true,
    }
    let stream: MediaStream | null = null
    try {
      stream = await navigator.mediaDevices.getUserMedia(constraints)
    } catch (error) {
      if (selectedCamera.value || selectedMic.value) {
        stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
      } else {
        deviceError.value = '카메라 또는 마이크 접근 권한이 필요합니다.'
        return
      }
    }
    if (!stream) {
      deviceError.value = '카메라 또는 마이크 접근 권한이 필요합니다.'
      return
    }
    mediaStream.value = stream
    const [videoTrack] = stream.getVideoTracks()
    const [audioTrack] = stream.getAudioTracks()
    if (videoTrack) {
      const deviceId = videoTrack.getSettings().deviceId
      if (deviceId) {
        isSyncingDevices.value = true
        selectedCamera.value = deviceId
      }
    }
    if (audioTrack) {
      const deviceId = audioTrack.getSettings().deviceId
      if (deviceId) {
        isSyncingDevices.value = true
        selectedMic.value = deviceId
      }
    }
    if (isSyncingDevices.value) {
      await nextTick()
      isSyncingDevices.value = false
    }
    if (videoRef.value) {
      videoRef.value.srcObject = stream
      await videoRef.value.play()
    }
    startMeter(stream)
    await loadDevices()
  } catch (error) {
    deviceError.value = '카메라 또는 마이크 접근 권한이 필요합니다.'
    stopPreview()
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      hydrateSelection()
      volumeLevel.value = 0
      loadDevices()
      startPreview()
    } else {
      stopPreview()
    }
  },
)

watch([selectedCamera, selectedMic], () => {
  if (props.modelValue && !isSyncingDevices.value) {
    startPreview()
  }
})

onBeforeUnmount(() => {
  stopPreview()
})

const close = () => emit('update:modelValue', false)

const handleStart = () => {
  emit('apply', { cameraId: selectedCamera.value, microphoneId: selectedMic.value })
  emit('start')
  close()
}

const modalTitle = computed(() => (props.broadcastTitle ? `${props.broadcastTitle} 장치 설정` : '방송 장치 설정'))
</script>

<template>
  <div v-if="modelValue" class="ds-modal" role="dialog" aria-modal="true">
    <div class="ds-modal__backdrop" @click="close"></div>
    <div class="ds-modal__card ds-surface">
      <header class="ds-modal__head">
        <div>
          <p class="ds-modal__eyebrow">방송 준비</p>
          <h3 class="ds-modal__title">{{ modalTitle }}</h3>
        </div>
        <button type="button" class="ds-modal__close" aria-label="닫기" @click="close">×</button>
      </header>

      <div class="ds-modal__body">
        <div class="preview-box">
          <video v-if="mediaStream" ref="videoRef" class="preview-video" muted playsinline autoplay></video>
          <div v-else class="preview-placeholder">
            <span class="preview-icon">📷</span>
            <p class="preview-label">카메라 미리보기</p>
          </div>
        </div>
        <p v-if="deviceError" class="device-error">{{ deviceError }}</p>

        <div class="control-grid">
          <label class="field">
            <span class="field__label">카메라 선택</span>
            <select v-model="selectedCamera" class="field__input">
              <option v-if="deviceCameras.length === 0" value="" disabled>카메라 없음</option>
              <option v-for="(camera, index) in deviceCameras" :key="camera.deviceId" :value="camera.deviceId">
                {{ camera.label || `카메라 ${index + 1}` }}
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field__label">마이크 선택</span>
            <select v-model="selectedMic" class="field__input">
              <option v-if="deviceMics.length === 0" value="" disabled>마이크 없음</option>
              <option v-for="(mic, index) in deviceMics" :key="mic.deviceId" :value="mic.deviceId">
                {{ mic.label || `마이크 ${index + 1}` }}
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field__label">입력 볼륨</span>
            <div class="volume-meter" role="progressbar" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="volumeLevel">
              <div class="volume-meter__fill" :style="{ width: `${volumeLevel}%` }"></div>
            </div>
            <p class="volume-meter__hint">소리가 입력되고 있는지 확인하세요.</p>
          </label>
        </div>
      </div>

      <footer class="ds-modal__actions">
        <button type="button" class="btn ghost" @click="close">취소</button>
        <button type="button" class="btn primary" @click="handleStart">방송 스튜디오 입장</button>
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
  z-index: 1000;
}

.ds-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}

.ds-modal__card {
  position: relative;
  width: min(720px, 92vw);
  max-height: 90vh;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  z-index: 1;
}

.ds-modal__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
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
  border-radius: 10px;
  width: 36px;
  height: 36px;
  font-size: 1.2rem;
  font-weight: 900;
  cursor: pointer;
}

.ds-modal__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.preview-box {
  width: 100%;
  aspect-ratio: 16 / 9;
  border: 1px dashed var(--border-color);
  border-radius: 14px;
  overflow: hidden;
  background: var(--surface-weak);
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-placeholder {
  text-align: center;
  color: var(--text-muted);
}

.preview-icon {
  font-size: 2rem;
  display: block;
}

.preview-label {
  margin: 6px 0 0;
  font-weight: 800;
}

.control-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
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

.field__input {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.volume-meter {
  position: relative;
  height: 12px;
  border-radius: 999px;
  background: var(--surface-weak);
  overflow: hidden;
}

.volume-meter__fill {
  height: 100%;
  background: linear-gradient(90deg, #22c55e, #3b82f6);
  transition: width 0.2s ease;
}

.volume-meter__hint {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.device-error {
  margin: 0;
  color: #ef4444;
  font-weight: 700;
}

.ds-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn {
  border-radius: 10px;
  padding: 10px 14px;
  font-weight: 900;
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-strong);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(var(--primary-rgb), 0.14);
  border-color: var(--primary-color);
}

.btn.primary {
  background: var(--primary-color);
  color: #fff;
  border-color: var(--primary-color);
}

.btn.ghost {
  background: var(--surface);
}

@media (max-width: 640px) {
  .ds-modal__card {
    padding: 16px;
  }
}
</style>
