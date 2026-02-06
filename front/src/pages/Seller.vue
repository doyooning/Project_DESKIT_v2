<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { exportSellerDashboardExcel } from '../utils/exportExcel'

const periodSales = ref<'daily' | 'monthly' | 'yearly'>('daily')
const periodRevenue = ref<'daily' | 'monthly' | 'yearly'>('daily')
const route = useRoute()
const isHome = computed(() => route.path === '/seller')

const salesChartByPeriod = {
  daily: [
    { label: '오피스', value: 42 },
    { label: '게이밍', value: 33 },
    { label: '미니멀', value: 28 },
    { label: '홈카페', value: 22 },
    { label: '스탠딩', value: 18 },
  ],
  monthly: [
    { label: '오피스', value: 980 },
    { label: '게이밍', value: 840 },
    { label: '미니멀', value: 760 },
    { label: '홈카페', value: 640 },
    { label: '스탠딩', value: 520 },
  ],
  yearly: [
    { label: '오피스', value: 11200 },
    { label: '게이밍', value: 9800 },
    { label: '미니멀', value: 8600 },
    { label: '홈카페', value: 7400 },
    { label: '스탠딩', value: 6200 },
  ],
} as const

const revenueChartByPeriod = {
  daily: [
    { label: '오피스', value: 1200 },
    { label: '게이밍', value: 980 },
    { label: '미니멀', value: 860 },
    { label: '홈카페', value: 740 },
    { label: '스탠딩', value: 620 },
  ],
  monthly: [
    { label: '오피스', value: 35800 },
    { label: '게이밍', value: 31200 },
    { label: '미니멀', value: 28600 },
    { label: '홈카페', value: 24800 },
    { label: '스탠딩', value: 21400 },
  ],
  yearly: [
    { label: '오피스', value: 412000 },
    { label: '게이밍', value: 368000 },
    { label: '미니멀', value: 332000 },
    { label: '홈카페', value: 286000 },
    { label: '스탠딩', value: 248000 },
  ],
} as const

const salesChart = computed(() => salesChartByPeriod[periodSales.value])
const revenueChart = computed(() => revenueChartByPeriod[periodRevenue.value])

const top5Items = [
  { name: '모던 스탠딩 데스크', value: '1,240개' },
  { name: '게이밍 데스크 매트 XL', value: '980개' },
  { name: '알루미늄 모니터암', value: '860개' },
  { name: '미니멀 수납 선반', value: '740개' },
  { name: '데스크 램프 2세대', value: '620개' },
]

const kpis = [
  { label: '일일 총 판매량', value: '1,120', sub: '오늘 기준' },
  { label: '일일 총 판매액', value: '₩28,400,000', sub: '오늘 기준' },
]

const maxSales = computed(() => Math.max(...salesChart.value.map((item) => item.value)))
const maxRevenue = computed(() => Math.max(...revenueChart.value.map((item) => item.value)))

const handleExport = () => {
  exportSellerDashboardExcel({
    periodSales: periodSales.value,
    periodRevenue: periodRevenue.value,
    salesChart: salesChart.value.map((item) => ({ ...item })),
    revenueChart: revenueChart.value.map((item) => ({ ...item })),
    top5Items,
    kpis,
  })
}
</script>

<template>
  <PageContainer>
    <PageHeader v-if="isHome" eyebrow="DESKIT" title="홈" />
    <template v-if="isHome">
      <header class="dashboard-header">
        <div>
          <h2 class="section-title">판매자 대시보드</h2>
          <p class="ds-section-sub">판매 현황과 운영 지표를 한눈에 확인하세요.</p>
        </div>
        <button type="button" class="excel-btn" @click="handleExport">Excel 추출</button>
      </header>

      <section class="dashboard-grid">
        <div class="dashboard-main">
          <article class="card card--sales ds-surface">
            <header class="card-head">
              <div>
                <h3>판매자 상품 판매 현황 (상품 종류)</h3>
                <p class="card-sub">막대 그래프</p>
              </div>
              <div class="segmented" role="tablist" aria-label="기간 선택">
                <button
                    type="button"
                    class="segmented__btn"
                    :class="{ 'segmented__btn--active': periodSales === 'daily' }"
                    @click="periodSales = 'daily'"
                >
                  일별
                </button>
                <button
                    type="button"
                    class="segmented__btn"
                    :class="{ 'segmented__btn--active': periodSales === 'monthly' }"
                    @click="periodSales = 'monthly'"
                >
                  월별
                </button>
                <button
                    type="button"
                    class="segmented__btn"
                    :class="{ 'segmented__btn--active': periodSales === 'yearly' }"
                    @click="periodSales = 'yearly'"
                >
                  연도별
                </button>
              </div>
            </header>
            <div class="chart-placeholder">
              <div class="bar-chart" role="img" aria-label="상품 종류별 판매 현황">
                <div v-for="item in salesChart" :key="item.label" class="bar-item">
                  <div class="bar-value">{{ item.value }}개</div>
                  <div class="bar-area">
                    <div class="bar" :style="{ height: `${(item.value / maxSales) * 100}%` }"></div>
                  </div>
                  <div class="bar-label">{{ item.label }}</div>
                </div>
              </div>
            </div>
          </article>

          <article class="card card--top5 ds-surface">
            <header class="card-head">
              <div>
                <h3>가장 많이 판매된 상품 TOP5</h3>
                <p class="card-sub">최근 판매 기준</p>
              </div>
            </header>
            <ol class="top-list">
              <li v-for="(item, index) in top5Items" :key="item.name" class="top-item">
                <span class="rank">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="label">{{ item.name }}</span>
                <span class="value">{{ item.value }}</span>
              </li>
            </ol>
          </article>

          <div class="dashboard-divider" aria-hidden="true"></div>

          <article class="card card--revenue ds-surface">
            <header class="card-head">
              <div>
                <h3>판매자 상품 판매 현황 (매출)</h3>
                <p class="card-sub">막대 그래프</p>
              </div>
              <div class="segmented" role="tablist" aria-label="기간 선택">
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
              <div class="bar-chart" role="img" aria-label="상품 종류별 매출 현황">
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
            <article v-for="kpi in kpis" :key="kpi.label" class="kpi-card ds-surface">
              <p class="kpi-label">{{ kpi.label }}</p>
              <p class="kpi-value">{{ kpi.value }}</p>
              <p class="kpi-sub">{{ kpi.sub }}</p>
            </article>
          </div>
        </div>
      </section>
    </template>
    <router-view v-else />
  </PageContainer>
</template>

<style scoped>
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

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
}

.dashboard-main {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-areas:
    'sales top5'
    'divider divider'
    'revenue kpi';
}

.card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 220px;
}

.card--sales {
  grid-area: sales;
}

.card--top5 {
  grid-area: top5;
}

.card--revenue {
  grid-area: revenue;
}

.dashboard-divider {
  grid-area: divider;
  height: 1px;
  background: var(--border-color);
  opacity: 0.6;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.chart-placeholder {
  display: block;
  flex: 0 0 260px;
  height: 260px;
  padding: 10px 12px 12px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: #f5f6f8;
  overflow: visible;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  width: 100%;
  height: 100%;
  min-height: 0;
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

.top-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
}

.top-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--surface-weak);
  color: var(--text-strong);
  font-weight: 600;
}

.rank {
  color: var(--primary-color);
  font-weight: 800;
}

.label {
  color: var(--text-strong);
}

.value {
  color: var(--text-soft);
  font-weight: 700;
}

.kpi-grid {
  grid-area: kpi;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.kpi-card {
  padding: 18px;
  min-height: 140px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.kpi-label {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.kpi-value {
  margin: 0;
  font-size: 1.6rem;
  font-weight: 900;
  color: var(--text-strong);
}

.kpi-sub {
  margin: 0;
  color: var(--text-soft);
  font-size: 0.9rem;
}

@media (max-width: 720px) {
  .dashboard-header {
    flex-direction: column;
    align-items: stretch;
  }

  .excel-btn {
    align-self: flex-start;
  }

  .dashboard-main {
    grid-template-columns: 1fr;
    grid-template-areas:
      'sales'
      'top5'
      'divider'
      'revenue'
      'kpi';
  }

  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>