<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PageHeader from '../../../components/PageHeader.vue'
import StatsBarChart from '../../../components/stats/StatsBarChart.vue'
import StatsRankList from '../../../components/stats/StatsRankList.vue'
import { fetchSanctionStatistics } from '../../../lib/live/api'

type Metric = 'daily' | 'monthly' | 'yearly'
type ChartDatum = { label: string; value: number }
type RankItem = { rank: number; title: string; value: number }

const stopMetric = ref<Metric>('daily')
const viewerMetric = ref<Metric>('daily')
const rankMetric = ref<Metric>('daily')

const periodMap: Record<Metric, string> = {
  daily: 'DAILY',
  monthly: 'MONTHLY',
  yearly: 'YEARLY',
}

const stopChart = ref<ChartDatum[]>([])
const viewerChart = ref<ChartDatum[]>([])
const topSellerStops = ref<RankItem[]>([])
const topViewerSanctions = ref<RankItem[]>([])

const mapChart = (items: Array<{ label: string; count: number }> = []) =>
  items.map((item) => ({ label: item.label, value: Number(item.count ?? 0) }))

const mapSellerRanks = (items: Array<{ sellerName: string; sanctionCount: number }> = []) =>
  items.map((item, index) => ({
    rank: index + 1,
    title: item.sellerName,
    value: Number(item.sanctionCount ?? 0),
  }))

const mapViewerRanks = (items: Array<{ viewerId: string; name?: string; sanctionCount: number }> = []) =>
  items.map((item, index) => ({
    rank: index + 1,
    title: item.name || item.viewerId,
    value: Number(item.sanctionCount ?? 0),
  }))

const sumValues = (items: ChartDatum[]) => items.reduce((acc, item) => acc + item.value, 0)

const summaryCards = computed(() => {
  const totalStops = sumValues(stopChart.value)
  const totalViewers = sumValues(viewerChart.value)
  const lastStop = stopChart.value.length ? stopChart.value[stopChart.value.length - 1] : undefined
  const lastViewer = viewerChart.value.length ? viewerChart.value[viewerChart.value.length - 1] : undefined
  const recentLabel = lastStop?.label || lastViewer?.label || '-'
  return [
    { label: '총 송출 중지', value: `${totalStops.toLocaleString('ko-KR')}건` },
    { label: '시청자 제재', value: `${totalViewers.toLocaleString('ko-KR')}건` },
    { label: '최근 제재일', value: recentLabel },
  ]
})

const loadStopStats = async () => {
  try {
    const payload = await fetchSanctionStatistics(periodMap[stopMetric.value])
    stopChart.value = mapChart(payload.forceStopChart ?? [])
  } catch {
    stopChart.value = []
  }
}

const loadViewerStats = async () => {
  try {
    const payload = await fetchSanctionStatistics(periodMap[viewerMetric.value])
    viewerChart.value = mapChart(payload.viewerBanChart ?? [])
  } catch {
    viewerChart.value = []
  }
}

const loadRankStats = async () => {
  try {
    const payload = await fetchSanctionStatistics(periodMap[rankMetric.value])
    topSellerStops.value = mapSellerRanks(payload.worstSellers ?? [])
    topViewerSanctions.value = mapViewerRanks(payload.worstViewers ?? [])
  } catch {
    topSellerStops.value = []
    topViewerSanctions.value = []
  }
}

watch(stopMetric, () => {
  loadStopStats()
}, { immediate: true })

watch(viewerMetric, () => {
  loadViewerStats()
}, { immediate: true })

watch(rankMetric, () => {
  loadRankStats()
}, { immediate: true })
</script>

<template>
  <div>
    <PageHeader eyebrow="DESKIT" title="제재 통계" />

    <section class="stat-grid">
      <article v-for="card in summaryCards" :key="card.label" class="ds-surface stat-card">
        <p class="stat-label">{{ card.label }}</p>
        <p class="stat-value">{{ card.value }}</p>
      </article>
    </section>

    <section class="chart-grid">
      <article class="ds-surface panel">
        <header class="panel__head">
          <div>
            <h3>송출 중지 횟수</h3>
            <p class="ds-section-sub">기간별 송출 중지 추이</p>
          </div>
          <div class="toggle-group" role="tablist" aria-label="송출 중지 기간">
            <button type="button" class="toggle-btn" :class="{ 'toggle-btn--active': stopMetric === 'daily' }" @click="stopMetric = 'daily'">
              일별
            </button>
            <button type="button" class="toggle-btn" :class="{ 'toggle-btn--active': stopMetric === 'monthly' }" @click="stopMetric = 'monthly'">
              월별
            </button>
            <button type="button" class="toggle-btn" :class="{ 'toggle-btn--active': stopMetric === 'yearly' }" @click="stopMetric = 'yearly'">
              연도별
            </button>
          </div>
        </header>
        <StatsBarChart :data="stopChart" />
      </article>

      <article class="ds-surface panel">
        <header class="panel__head">
          <div>
            <h3>시청자 제재 횟수</h3>
            <p class="ds-section-sub">기간별 시청자 제재 추이</p>
          </div>
          <div class="toggle-group" role="tablist" aria-label="시청자 제재 기간">
            <button type="button" class="toggle-btn" :class="{ 'toggle-btn--active': viewerMetric === 'daily' }" @click="viewerMetric = 'daily'">
              일별
            </button>
            <button type="button" class="toggle-btn" :class="{ 'toggle-btn--active': viewerMetric === 'monthly' }" @click="viewerMetric = 'monthly'">
              월별
            </button>
            <button type="button" class="toggle-btn" :class="{ 'toggle-btn--active': viewerMetric === 'yearly' }" @click="viewerMetric = 'yearly'">
              연도별
            </button>
          </div>
        </header>
        <StatsBarChart :data="viewerChart" />
      </article>
    </section>

    <section class="ranks-grid">
      <div class="ranks-grid__head">
        <h3 class="ranks-grid__title">순위 리스트</h3>
        <div class="toggle-group" role="tablist" aria-label="순위 기간">
          <button
            type="button"
            class="toggle-btn"
            :class="{ 'toggle-btn--active': rankMetric === 'daily' }"
            @click="rankMetric = 'daily'"
          >
            일별
          </button>
          <button
            type="button"
            class="toggle-btn"
            :class="{ 'toggle-btn--active': rankMetric === 'monthly' }"
            @click="rankMetric = 'monthly'"
          >
            월별
          </button>
          <button
            type="button"
            class="toggle-btn"
            :class="{ 'toggle-btn--active': rankMetric === 'yearly' }"
            @click="rankMetric = 'yearly'"
          >
            연도별
          </button>
        </div>
      </div>
      <article class="ds-surface panel">
        <h3 class="panel__title">송출 중지 횟수 많은 판매자 TOP 5</h3>
        <StatsRankList :items="topSellerStops" />
      </article>
      <article class="ds-surface panel">
        <h3 class="panel__title">제재 당한 횟수 많은 시청자 TOP 5</h3>
        <StatsRankList :items="topViewerSanctions" />
      </article>
    </section>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.stat-card {
  padding: 14px;
  border-radius: 12px;
}

.stat-label {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-weight: 800;
}

.stat-value {
  margin: 0;
  font-size: 1.4rem;
  font-weight: 900;
  color: var(--text-strong);
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 14px;
}

.ranks-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 14px;
  margin-top: 14px;
}

.ranks-grid__head {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.ranks-grid__title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--text-strong);
}

.panel {
  padding: 16px;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
}

.panel__title {
  margin: 0 0 6px;
  font-weight: 900;
  color: var(--text-strong);
}

.toggle-group {
  display: inline-flex;
  gap: 8px;
}

.toggle-btn {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  border-radius: 999px;
  padding: 8px 12px;
  font-weight: 800;
  cursor: pointer;
}

.toggle-btn--active {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: rgba(var(--primary-rgb), 0.08);
}

@media (max-width: 640px) {
  .ranks-grid__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .toggle-group {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
