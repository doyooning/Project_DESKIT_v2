<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '../../components/PageHeader.vue'

type Period = 'daily' | 'monthly' | 'yearly'
type ChartItem = { label: string; value: number }

const periodSignups = ref<Period>('daily')
const periodVisitors = ref<Period>('daily')
const periodRevenue = ref<Period>('daily')

const signupsByPeriod: Record<Period, ChartItem[]> = {
  daily: [
    { label: '월', value: 120 },
    { label: '화', value: 180 },
    { label: '수', value: 140 },
    { label: '목', value: 220 },
    { label: '금', value: 260 },
  ],
  monthly: [
    { label: '1월', value: 3200 },
    { label: '2월', value: 4100 },
    { label: '3월', value: 3800 },
    { label: '4월', value: 4600 },
    { label: '5월', value: 5200 },
  ],
  yearly: [
    { label: '2021', value: 32000 },
    { label: '2022', value: 41000 },
    { label: '2023', value: 52000 },
    { label: '2024', value: 61000 },
    { label: '2025', value: 68000 },
  ],
}

const visitorsByPeriod: Record<Period, ChartItem[]> = {
  daily: [
    { label: '월', value: 1240 },
    { label: '화', value: 980 },
    { label: '수', value: 1420 },
    { label: '목', value: 1680 },
    { label: '금', value: 1900 },
  ],
  monthly: [
    { label: '1월', value: 38400 },
    { label: '2월', value: 41000 },
    { label: '3월', value: 45200 },
    { label: '4월', value: 49800 },
    { label: '5월', value: 53200 },
  ],
  yearly: [
    { label: '2021', value: 410000 },
    { label: '2022', value: 520000 },
    { label: '2023', value: 610000 },
    { label: '2024', value: 720000 },
    { label: '2025', value: 820000 },
  ],
}

const revenueByPeriod: Record<Period, ChartItem[]> = {
  daily: [
    { label: '월', value: 2200 },
    { label: '화', value: 3200 },
    { label: '수', value: 2800 },
    { label: '목', value: 4100 },
    { label: '금', value: 5200 },
  ],
  monthly: [
    { label: '1월', value: 62000 },
    { label: '2월', value: 71000 },
    { label: '3월', value: 68000 },
    { label: '4월', value: 76000 },
    { label: '5월', value: 82000 },
  ],
  yearly: [
    { label: '2021', value: 720000 },
    { label: '2022', value: 840000 },
    { label: '2023', value: 920000 },
    { label: '2024', value: 1080000 },
    { label: '2025', value: 1240000 },
  ],
}

const signupsChart = computed(() => signupsByPeriod[periodSignups.value])
const visitorsChart = computed(() => visitorsByPeriod[periodVisitors.value])
const revenueChart = computed(() => revenueByPeriod[periodRevenue.value])

const maxSignups = computed(() => Math.max(...signupsChart.value.map((item) => item.value), 1))
const maxVisitors = computed(() => Math.max(...visitorsChart.value.map((item) => item.value), 1))
const maxRevenue = computed(() => Math.max(...revenueChart.value.map((item) => item.value), 1))

const handleExport = () => {
}
</script>

<template>
  <section class="admin-dashboard">
    <PageHeader eyebrow="DESKIT" title="대시보드" />
    <header class="dashboard-header">
      <div>
        <h2 class="section-title">관리자 대시보드</h2>
        <p class="ds-section-sub">운영 지표와 방문/매출 현황을 확인하세요.</p>
      </div>
      <button type="button" class="excel-btn" @click="handleExport">Excel 추출</button>
    </header>

    <div class="dashboard-main">
      <article class="card card--signups ds-surface">
        <header class="card-head">
          <div>
            <h3>가입자수</h3>
            <p class="card-sub">기간별 가입 지표</p>
          </div>
          <div class="segmented" role="tablist" aria-label="가입자수 기간">
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodSignups === 'daily' }"
              @click="periodSignups = 'daily'"
            >
              일별
            </button>
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodSignups === 'monthly' }"
              @click="periodSignups = 'monthly'"
            >
              월별
            </button>
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodSignups === 'yearly' }"
              @click="periodSignups = 'yearly'"
            >
              연도별
            </button>
          </div>
        </header>
        <div class="chart-placeholder">
          <div class="bar-chart" role="img" aria-label="가입자수 차트">
            <div v-for="item in signupsChart" :key="item.label" class="bar-item">
              <div class="bar-value">{{ item.value.toLocaleString('ko-KR') }}</div>
              <div class="bar-area">
                <div class="bar" :style="{ height: `${(item.value / maxSignups) * 100}%` }"></div>
              </div>
              <div class="bar-label">{{ item.label }}</div>
            </div>
          </div>
        </div>
      </article>

      <article class="card card--visitors ds-surface">
        <header class="card-head">
          <div>
            <h3>방문자수</h3>
            <p class="card-sub">기간별 방문 지표</p>
          </div>
          <div class="segmented" role="tablist" aria-label="방문자수 기간">
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodVisitors === 'daily' }"
              @click="periodVisitors = 'daily'"
            >
              일별
            </button>
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodVisitors === 'monthly' }"
              @click="periodVisitors = 'monthly'"
            >
              월별
            </button>
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodVisitors === 'yearly' }"
              @click="periodVisitors = 'yearly'"
            >
              연도별
            </button>
          </div>
        </header>
        <div class="chart-placeholder">
          <div class="bar-chart" role="img" aria-label="방문자수 차트">
            <div v-for="item in visitorsChart" :key="item.label" class="bar-item">
              <div class="bar-value">{{ item.value.toLocaleString('ko-KR') }}</div>
              <div class="bar-area">
                <div class="bar" :style="{ height: `${(item.value / maxVisitors) * 100}%` }"></div>
              </div>
              <div class="bar-label">{{ item.label }}</div>
            </div>
          </div>
        </div>
      </article>

      <article class="card card--revenue ds-surface">
        <header class="card-head">
          <div>
            <h3>매출</h3>
            <p class="card-sub">기간별 매출 지표</p>
          </div>
          <div class="segmented" role="tablist" aria-label="매출 기간">
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodRevenue === 'daily' }"
              @click="periodRevenue = 'daily'"
            >
              일별
            </button>
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodRevenue === 'monthly' }"
              @click="periodRevenue = 'monthly'"
            >
              월별
            </button>
            <button
              type="button"
              class="segmented__btn"
              :class="{ 'segmented__btn--active': periodRevenue === 'yearly' }"
              @click="periodRevenue = 'yearly'"
            >
              연도별
            </button>
          </div>
        </header>
        <div class="chart-placeholder">
          <div class="bar-chart" role="img" aria-label="매출 차트">
            <div v-for="item in revenueChart" :key="item.label" class="bar-item">
              <div class="bar-value">₩{{ item.value.toLocaleString('ko-KR') }}</div>
              <div class="bar-area">
                <div class="bar" :style="{ height: `${(item.value / maxRevenue) * 100}%` }"></div>
              </div>
              <div class="bar-label">{{ item.label }}</div>
            </div>
          </div>
        </div>
      </article>

      <div class="kpi-grid">
        <article class="kpi-card ds-surface">
          <p class="kpi-label">누적 방문자</p>
          <p class="kpi-value">1,248,900</p>
          <p class="kpi-sub">누적 기준</p>
        </article>
        <article class="kpi-card ds-surface">
          <p class="kpi-label">실시간 방문자 수</p>
          <p class="kpi-value">1,284</p>
          <p class="kpi-sub">현재 기준</p>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.admin-dashboard {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dashboard-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.excel-btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.excel-btn:hover {
  border-color: var(--primary-color);
  box-shadow: 0 8px 18px rgba(var(--primary-rgb), 0.12);
  transform: translateY(-1px);
}

.dashboard-main {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-areas:
    'signups visitors'
    'revenue kpi';
}

.card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 220px;
}

.card--signups {
  grid-area: signups;
}

.card--visitors {
  grid-area: visitors;
}

.card--revenue {
  grid-area: revenue;
}

.kpi-grid {
  grid-area: kpi;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.kpi-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kpi-label {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.kpi-value {
  margin: 0;
  font-size: 1.4rem;
  font-weight: 900;
  color: var(--text-strong);
}

.kpi-sub {
  margin: 0;
  color: var(--text-soft);
  font-weight: 700;
  font-size: 0.9rem;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.card-head h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--text-strong);
}

.card-sub {
  margin: 6px 0 0;
  color: var(--text-soft);
  font-size: 0.9rem;
}

.segmented {
  display: inline-flex;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--surface-weak);
  padding: 4px;
  gap: 4px;
}

.segmented__btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  padding: 6px 12px;
  border-radius: 999px;
  font-weight: 700;
  cursor: pointer;
}

.segmented__btn--active {
  background: var(--surface);
  color: var(--primary-color);
  box-shadow: 0 6px 16px rgba(var(--primary-rgb), 0.12);
}

.chart-placeholder {
  display: block;
  padding: 10px 12px 12px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: #f5f6f8;
  overflow: visible;
  flex: 1;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  width: 100%;
  height: auto;
  min-height: 220px;
  padding-top: 14px;
  overflow-x: auto;
  overflow-y: visible;
  padding-bottom: 6px;
  position: relative;
}

.bar-chart::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 28px;
  height: 1px;
  background: rgba(15, 23, 42, 0.12);
  pointer-events: none;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-weight: 800;
  color: var(--text-strong);
  font-size: 0.85rem;
  flex: 0 0 88px;
  max-width: 90px;
  padding-top: 6px;
}

.bar-label {
  color: var(--text-muted);
}

.bar-value {
  color: var(--text-strong);
  font-weight: 900;
  font-size: 0.78rem;
  white-space: nowrap;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.1;
  margin-bottom: 4px;
}

.bar-area {
  width: 20px;
  height: 140px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(15, 23, 42, 0.04);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
  padding: 2px;
  box-sizing: border-box;
}

.bar {
  width: 100%;
  min-height: 6px;
  border-radius: 8px 8px 0 0;
  background: rgba(120, 170, 255, 0.85);
  transition: height 0.2s ease;
}

@media (max-width: 960px) {
  .dashboard-main {
    grid-template-columns: 1fr;
    grid-template-areas:
      'signups'
      'visitors'
      'revenue'
      'kpi';
  }
}

@media (max-width: 720px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
