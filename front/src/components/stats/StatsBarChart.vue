<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  data: Array<{ label: string; value: number }>
  valueFormatter?: (value: number) => string
}>()

const dataLength = computed(() => props.data.length || 1)

const maxValue = computed(() => {
  if (!props.data.length) return 0
  return Math.max(...props.data.map((item) => item.value))
})

// Y축 눈금 간격 계산 (기존 로직 유지하되 안전장치 추가)
const getStep = (max: number) => {
  if (max <= 0) return 1
  // 5~6개 정도의 눈금이 나오도록 조정 (너무 촘촘하면 보기 힘듦)
  const roughStep = max / 5
  const magnitude = 10 ** Math.floor(Math.log10(roughStep))
  const step = Math.ceil(roughStep / magnitude) * magnitude
  return step || 1
}

const yTicks = computed(() => {
  if (!maxValue.value) return [0]
  const step = getStep(maxValue.value)

  // 최대값보다 조금 더 넉넉하게 상단 설정
  const top = Math.ceil(maxValue.value / step) * step

  const ticks: number[] = []
  // top부터 0까지 step만큼 감소
  for (let val = top; val >= 0; val -= step) {
    ticks.push(val)
  }
  // 0이 포함되지 않았다면 강제로 추가 (부동소수점 오차 방지)
  if (ticks[ticks.length - 1] !== 0) ticks.push(0)

  return ticks
})

// 차트의 가장 높은 눈금 값 (이 값이 100% 높이가 됨)
const topTick = computed(() => (yTicks.value.length ? yTicks.value[0] : maxValue.value || 1))

const formatValue = (value: number) => {
  if (props.valueFormatter) return props.valueFormatter(value)
  return value.toLocaleString('ko-KR')
}

const toHeight = (value: number) => {
  if (!topTick.value) return '0%'
  // 데이터 값 / 전체 눈금 최대값 * 100
  const percentage = (value / topTick.value) * 100
  return `${Math.max(0, Math.min(100, percentage))}%`
}

// 막대 너비 및 간격 계산
const barGap = computed(() => {
  // 데이터가 많으면 간격을 좁히고, 적으면 넓힘
  return `${Math.max(4, Math.min(20, 40 / dataLength.value))}px`
})

const barLayoutStyle = computed(() => ({
  gap: barGap.value,
  // minmax(0, 1fr)은 그리드 아이템이 넘치지 않게 함
  gridTemplateColumns: `repeat(${dataLength.value}, minmax(0, 1fr))`,
}))
</script>

<template>
  <div class="bar-chart">
    <div v-if="data.length" class="bar-chart__grid">
      <div class="bar-chart__y-axis" aria-hidden="true">
        <span v-for="tick in yTicks" :key="tick" class="bar-chart__y-label">
          {{ formatValue(tick) }}
        </span>
      </div>

      <div class="bar-chart__bars" :style="barLayoutStyle">
        <div class="bar-chart__lines">
          <div v-for="tick in yTicks" :key="`line-${tick}`" class="bar-chart__line"></div>
        </div>

        <div v-for="(item, index) in data" :key="`${item.label}-${index}`" class="bar-chart__item">
          <div class="bar-chart__bar-wrapper">
            <div
                class="bar-chart__bar"
                :style="{ height: toHeight(item.value) }"
                :data-value="formatValue(item.value)"
                tabindex="0"
            ></div>
          </div>
          <span class="bar-chart__label">{{ item.label }}</span>
        </div>
      </div>
    </div>
    <p v-else class="empty">데이터가 없습니다.</p>
  </div>
</template>

<style scoped>
.bar-chart {
  display: block;
  width: 100%;
}

.bar-chart__grid {
  display: grid;
  /* 중요: Y축(auto)과 막대영역(1fr) 정의 */
  grid-template-columns: auto 1fr;
  align-items: stretch;
  gap: 12px;
  width: 100%;
  height: 240px; /* 전체 차트 높이 고정 */
}

/* Y축 스타일 */
.bar-chart__y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between; /* 위아래 균등 배치 */
  text-align: right;
  /* 중요: 라벨 텍스트 높이의 절반만큼 마진을 조정하여 라인과 텍스트 중심을 맞춤 */
  margin: -0.6em 0;
  padding: 0.6em 0;
  z-index: 1;
}

.bar-chart__y-label {
  font-size: 0.8rem;
  color: var(--text-muted, #888);
  font-weight: 500;
  white-space: nowrap;
  line-height: 1.2;
}

/* 막대 영역 스타일 */
.bar-chart__bars {
  position: relative;
  display: grid;
  align-items: end; /* 아래 정렬 */
  height: 100%;
  width: 100%;
}

/* 배경 가로선 (옵션) */
.bar-chart__lines {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
  z-index: 0;
}
.bar-chart__line {
  width: 100%;
  height: 1px;
  background-color: var(--border-color, #eee);
  opacity: 0.5;
}
.bar-chart__line:last-child {
  background-color: var(--border-color, #ddd); /* 바닥선은 조금 진하게 */
  opacity: 1;
}


/* 개별 막대 아이템 (막대 + 라벨) */
.bar-chart__item {
  display: flex;
  flex-direction: column;
  justify-content: flex-end; /* 아래쪽 정렬 */
  height: 100%;
  z-index: 1; /* 가로선보다 위에 오게 */
}

.bar-chart__bar-wrapper {
  flex: 1; /* 남은 공간 모두 차지 */
  display: flex;
  align-items: flex-end; /* 막대를 바닥에 붙임 */
  width: 100%;
  /* Y축 텍스트 중심과 맞추기 위해 위아래 패딩 조정 (선택 사항) */
  padding-top: 10px;
}

.bar-chart__bar {
  position: relative;
  width: 100%;
  /* 최소 높이를 주되 0일때는 안보이게 처리하거나 유지 */
  min-height: 2px;
  background: linear-gradient(180deg, rgba(var(--primary-rgb, 59, 130, 246), 0.9), rgba(var(--primary-rgb, 59, 130, 246), 0.6));
  border-radius: 6px 6px 0 0;
  transition: height 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

/* 툴팁 (Hover 값 표시) */
.bar-chart__bar::after {
  content: attr(data-value);
  position: absolute;
  left: 50%;
  bottom: 100%;
  transform: translate(-50%, -4px);
  background: var(--surface, #fff);
  color: var(--text-strong, #333);
  border: 1px solid var(--border-color, #ddd);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 700;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  opacity: 0;
  pointer-events: none;
  transition: all 0.2s ease;
  z-index: 10;
}

.bar-chart__bar:hover::after,
.bar-chart__bar:focus-visible::after {
  opacity: 1;
  transform: translate(-50%, -8px);
}

.bar-chart__label {
  margin-top: 8px;
  text-align: center;
  font-size: 0.8rem;
  color: var(--text-muted, #666);
  font-weight: 600;
  line-height: 1.2;
  min-height: 2.4em;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
  width: 100%;
}

.empty {
  text-align: center;
  padding: 40px;
  color: var(--text-muted);
}
</style>
