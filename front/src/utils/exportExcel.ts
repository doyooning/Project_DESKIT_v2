import * as XLSX from 'xlsx'

type ChartItem = { label: string; value: number }
type TopItem = { name: string; value: string }
type KpiItem = { label: string; value: string; sub: string }

type ExportPayload = {
  periodSales: string
  periodRevenue: string
  salesChart: ChartItem[]
  revenueChart: ChartItem[]
  top5Items: TopItem[]
  kpis: KpiItem[]
}

export const exportSellerDashboardExcel = (payload: ExportPayload) => {
  const workbook = XLSX.utils.book_new()

  const salesRows = [
    ['기간', '구분', '값'],
    ...payload.salesChart.map((item) => [payload.periodSales, item.label, item.value]),
  ]

  const revenueRows = [
    ['기간', '구분', '매출액'],
    ...payload.revenueChart.map((item) => [payload.periodRevenue, item.label, item.value]),
  ]

  const topRows = [
    ['순위', '상품명', '판매량'],
    ...payload.top5Items.map((item, index) => [index + 1, item.name, item.value]),
  ]

  const kpiRows = [
    ['지표', '값', '비고'],
    ...payload.kpis.map((item) => [item.label, item.value, item.sub]),
  ]

  XLSX.utils.book_append_sheet(workbook, XLSX.utils.aoa_to_sheet(salesRows), '판매량')
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.aoa_to_sheet(revenueRows), '매출액')
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.aoa_to_sheet(topRows), 'TOP5')
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.aoa_to_sheet(kpiRows), '핵심지표')

  const dateStamp = new Date().toISOString().slice(0, 10)
  XLSX.writeFile(workbook, `seller-dashboard-${dateStamp}.xlsx`, {
    bookType: 'xlsx',
    compression: true,
  })
}

