<script setup lang="ts">
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Autoplay, Navigation, Pagination } from 'swiper/modules'

import 'swiper/css'
import 'swiper/css/navigation'
import 'swiper/css/pagination'

import SetupCard from './SetupCard.vue'
import type { SetupItem } from '../lib/home-data'

const props = defineProps<{
  items: SetupItem[]
}>()

const modules = [Autoplay, Navigation, Pagination]
</script>

<template>
  <div class="carousel">
    <button class="nav-btn setup-prev" aria-label="이전">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
        <path d="M15 18l-6-6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round" />
      </svg>
    </button>

    <button class="nav-btn setup-next" aria-label="다음">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
        <path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round" />
      </svg>
    </button>

    <div class="viewport">
      <Swiper
        v-if="props.items.length"
        class="swiper-shell"
        :modules="modules"
        :loop="true"
        :slides-per-view="2"
        :space-between="22"
        :centered-slides="false"
        :navigation="{
          nextEl: '.setup-next',
          prevEl: '.setup-prev',
        }"
        :pagination="{ clickable: true }"
        :watch-overflow="true"
        :autoplay="false"
        :breakpoints="{
        0: { slidesPerView: 1.05, spaceBetween: 14 },
        640: { slidesPerView: 1.35, spaceBetween: 18 },
        900: { slidesPerView: 2, spaceBetween: 20 },
        1200: { slidesPerView: 2, spaceBetween: 22 },
      }"
      >
        <SwiperSlide v-for="item in props.items" :key="item.id">
          <SetupCard v-bind="item" />
        </SwiperSlide>
      </Swiper>
    </div>
  </div>
</template>

<style scoped>
.carousel {
  position: relative;
  padding: 8px clamp(12px, 3vw, 48px) 40px;
  max-width: 100%;
  overflow-x: clip;
}

.viewport {
  overflow: hidden;
  border-radius: 14px;
}

.swiper-shell {
  overflow: hidden;
  padding-bottom: 12px;
}

:deep(.swiper-wrapper) {
  transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1) !important;
}

:deep(.swiper-slide) {
  height: auto;
}

:deep(.swiper-button-prev),
:deep(.swiper-button-next) {
  display: none;
}

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

.setup-prev {
  left: 12px;
}

.setup-next {
  right: 12px;
}

:deep(.swiper-pagination) {
  position: relative !important;
  bottom: 0px !important;
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
