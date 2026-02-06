<script setup lang="ts">
import { computed, nextTick, ref, toRefs, watch } from 'vue'
import { Swiper, SwiperSlide } from 'swiper/vue'
import type { Swiper as SwiperClass } from 'swiper'
import type { NavigationOptions } from 'swiper/types'
import { Autoplay, Pagination, Navigation } from 'swiper/modules'

import LiveCard from './LiveCard.vue'
import type { LiveItem } from '../lib/live/types'

const props = defineProps<{
  items: LiveItem[]
}>()

const { items } = toRefs(props)
const modules = [Autoplay, Pagination, Navigation]
const prevEl = ref<HTMLButtonElement | null>(null)
const nextEl = ref<HTMLButtonElement | null>(null)
const swiperRef = ref<SwiperClass | null>(null)
const shouldLoop = computed(() => items.value.length > 2)

const refreshSwiper = async () => {
  await nextTick()
  if (!swiperRef.value || !items.value.length) return
  swiperRef.value.update()
  if (shouldLoop.value) {
    swiperRef.value.slideToLoop(0, 0)
  } else {
    swiperRef.value.slideTo(0, 0)
  }
}

const handleSwiper = (swiper: SwiperClass) => {
  swiperRef.value = swiper
  nextTick(() => {
    if (!prevEl.value || !nextEl.value) {
      return
    }

    const rawNavigation = swiper.params.navigation
    const navigationParams: NavigationOptions =
      typeof rawNavigation === 'object' && rawNavigation ? { ...rawNavigation } : {}

    navigationParams.prevEl = prevEl.value
    navigationParams.nextEl = nextEl.value
    swiper.params.navigation = navigationParams

    swiper.navigation?.destroy()
    swiper.navigation?.init()
    swiper.navigation?.update()
  })
  void refreshSwiper()
}

watch(() => items.value.length, () => {
  void refreshSwiper()
}, { immediate: true })
</script>

<template>
  <div class="live-carousel">
    <!-- 커스텀 네비 버튼 (기본 화살표는 숨김) -->
    <button ref="prevEl" class="nav-btn live-prev" aria-label="이전">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
        <path d="M15 18l-6-6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round" />
      </svg>
    </button>

    <button ref="nextEl" class="nav-btn live-next" aria-label="다음">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
        <path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round" />
      </svg>
    </button>

    <Swiper
      v-if="items.length"
      class="live-swiper"
      :modules="modules"
      :centered-slides="true"
      :slides-per-view="'auto'"
      :space-between="28"
      :loop="shouldLoop"
      :speed="880"
      :navigation="true"
      :autoplay="{
        delay: 3000,
        disableOnInteraction: false,
        pauseOnMouseEnter: true,
      }"
      :pagination="{ clickable: true }"
      @swiper="handleSwiper"
    >
      <SwiperSlide v-for="item in items" :key="item.id" class="live-slide">
        <LiveCard :item="item" />
      </SwiperSlide>
    </Swiper>
  </div>
</template>

<style scoped>
.live-carousel {
  position: relative;
  padding: 0 clamp(12px, 3vw, 48px) 40px;
  max-width: 100%;
  overflow-x: clip;
  overflow-y: visible;
  width: 100%;
}

/* 핵심: 옆 카드 “살짝 보이게” */
.live-swiper {
  overflow: visible;
  padding-bottom: 12px;
}

/* ✅ 한 번에 3장 보이는 느낌의 핵심: 슬라이드 폭을 크게 잡고 centeredSlides + overflow visible */
.live-slide {
  width: clamp(520px, 72vw, 860px);
}

@media (max-width: 520px) {
  .live-slide {
    width: 92vw;
  }
}

/* ✅ 가운데(Active)만 또렷, 양옆은 흐리고 살짝 덜 강조 */
:deep(.swiper-slide) {
  transition: opacity 0.65s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.65s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.65s cubic-bezier(0.22, 1, 0.36, 1);
}
:deep(.swiper-slide:not(.swiper-slide-active)) {
  opacity: 0.35;
  filter: blur(1px);
  transform: scale(0.96);
}
:deep(.swiper-slide-prev),
:deep(.swiper-slide-next) {
  opacity: 0.55;
  filter: blur(1px);
  transform: scale(0.975);
}
:deep(.swiper-slide-active) {
  opacity: 1;
  filter: none;
  transform: scale(1);
}

/* ✅ 기본 Swiper 화살표(파란 큰 거) 숨기기 */
:deep(.swiper-button-prev),
:deep(.swiper-button-next) {
  display: none;
}

:deep(.swiper-wrapper) {
  transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1) !important;
}

/* 커스텀 버튼 스타일 */
.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-strong);
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.12);
  display: grid;
  place-items: center;
  cursor: pointer;
  z-index: 10;
}

.live-prev {
  left: 10px;
}
.live-next {
  right: 10px;
}

/* pagination 점 예쁘게 */
:deep(.swiper-pagination) {
  position: relative !important;
  bottom: 0 !important;
  margin-top: 14px;
}
:deep(.swiper-pagination-bullet) {
  width: 8px;
  height: 8px;
  opacity: 0.3;
  background: var(--primary-color) !important;
}
:deep(.swiper-pagination-bullet-active) {
  opacity: 1;
  background: var(--primary-color) !important;
}
</style>
