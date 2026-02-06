<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { type DbProduct } from '../../lib/products-data'
import { deleteProduct, listProducts } from '../../api/products'
import { USE_MOCK_API } from '../../api/config'

type ProductStatus = 'selling' | 'soldout' | 'hidden'

type StatusFilter = 'all' | ProductStatus

type SortOption = 'name' | 'status'

const STATUS_KEY = 'deskit_seller_product_status_v1'

const statusFilter = ref<StatusFilter>('all')
const sortOption = ref<SortOption>('name')
const searchQuery = ref('')
const statusMap = ref<Record<string, ProductStatus>>({})
const baseProducts = ref<DbProduct[]>([])
const isLoading = ref(false)
const errorMessage = ref('')
const deletingKey = ref<string | null>(null)
const mockEventName = ref<string | null>(null)

const statusLabelMap: Record<ProductStatus, string> = {
  selling: '판매중',
  soldout: '품절',
  hidden: '숨김',
}

const loadStatusMap = () => {
  const raw = localStorage.getItem(STATUS_KEY)
  if (!raw) return
  try {
    const parsed = JSON.parse(raw) as Record<string, string>
    const next: Record<string, ProductStatus> = {}
    Object.entries(parsed).forEach(([key, value]) => {
      if (value === 'selling' || value === 'soldout' || value === 'hidden') {
        next[key] = value
      }
    })
    statusMap.value = next
  } catch {
    return
  }
}

const getProductKey = (product: any) => {
  return String(product?.product_id ?? product?.id ?? '')
}

const getStatus = (productKey: string | number): ProductStatus => {
  return statusMap.value[String(productKey)] || 'selling'
}

const refreshProducts = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    baseProducts.value = await listProducts()
  } catch {
    errorMessage.value = '상품 목록을 불러오지 못했어요.'
  } finally {
    isLoading.value = false
  }
}

const onProductsChanged = () => {
  void refreshProducts()
}

const filteredProducts = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  const filtered = baseProducts.value.filter((product: any) => {
    const name = (product.name || '').toLowerCase()
    const desc = (product.short_desc ?? product.shortDesc ?? '').toLowerCase()
    const match = !q || name.includes(q) || desc.includes(q)
    if (!match) return false
    const status = getStatus(getProductKey(product))
    if (statusFilter.value !== 'all' && statusFilter.value !== status) return false
    return true
  })

  if (sortOption.value === 'name') {
    return filtered.slice().sort((a, b) => a.name.localeCompare(b.name))
  }
  if (sortOption.value === 'status') {
    const order: Record<ProductStatus, number> = { selling: 0, soldout: 1, hidden: 2 }
    return filtered.slice().sort((a, b) => order[getStatus(getProductKey(a))] - order[getStatus(getProductKey(b))])
  }
  return filtered
})

const keyedProducts = computed(() => {
  return filteredProducts.value.map((product) => ({
    product,
    key: getProductKey(product),
  }))
})

const formatPrice = (value: number) => `${value.toLocaleString('ko-KR')}원`

const getDiscountPercent = (product: DbProduct | any) => {
  const cost = product.cost_price ?? product.costPrice ?? 0
  const price = product.price ?? 0
  if (!cost || cost <= price) return 0
  return Math.round(((cost - price) / cost) * 100)
}

const getStockCount = (product: DbProduct | any) => {
  if (typeof product.stock === 'number') return product.stock
  const base = product.salesVolume ?? product.product_id * 7
  return Math.max(0, 200 - (base % 200))
}

const resolveSellerId = (product: any) => {
  const candidates = [
    product?.seller_id,
    product?.sellerId,
    product?.owner_id,
    product?.ownerId,
    product?.user_id,
    product?.userId,
  ]
  for (const value of candidates) {
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (typeof value === 'string') {
      const parsed = Number.parseInt(value, 10)
      if (!Number.isNaN(parsed)) return parsed
    }
  }
  return null
}

const handleDelete = async (product: any) => {
  const key = getProductKey(product)
  if (!key) return
  if (deletingKey.value === key) return
  if (!window.confirm('정말 삭제하시겠습니까?')) return
  deletingKey.value = key
  errorMessage.value = ''
  try {
    await deleteProduct(key)
    await refreshProducts()
  } catch {
    errorMessage.value = '삭제에 실패했어요. 잠시 후 다시 시도해주세요.'
  } finally {
    deletingKey.value = null
  }
}

onMounted(async () => {
  loadStatusMap()
  await refreshProducts()
  if (import.meta.env.DEV && USE_MOCK_API) {
    const { SELLER_PRODUCTS_EVENT } = await import('../../lib/mocks/sellerProducts')
    mockEventName.value = SELLER_PRODUCTS_EVENT
    window.addEventListener(SELLER_PRODUCTS_EVENT, onProductsChanged)
  }
})

onBeforeUnmount(() => {
  if (mockEventName.value) {
    window.removeEventListener(mockEventName.value, onProductsChanged)
  }
})
</script>

<template>
  <section class="admin-products">
    <PageHeader eyebrow="DESKIT" title="상품관리" />

    <header class="page-head">
      <div>
        <h2 class="section-title">전체 판매 목록({{ filteredProducts.length }})</h2>
        <p class="ds-section-sub">판매 중인 상품 상태를 확인할 수 있습니다.</p>
      </div>
    </header>

    <section class="controls ds-surface">
      <label class="control-field">
        <span class="control-label">상태</span>
        <select v-model="statusFilter">
          <option value="all">전체</option>
          <option value="selling">판매중</option>
          <option value="soldout">품절</option>
          <option value="hidden">숨김</option>
        </select>
      </label>
      <label class="control-field">
        <span class="control-label">정렬</span>
        <select v-model="sortOption">
          <option value="name">상품이름순</option>
          <option value="status">상태순</option>
        </select>
      </label>
      <label class="control-field search">
        <span class="control-label">검색</span>
        <input v-model="searchQuery" type="search" placeholder="상품명 또는 설명 검색" />
      </label>
    </section>

    <p v-if="isLoading" class="ds-section-sub">불러오는 중…</p>
    <section v-else-if="errorMessage" class="empty-state ds-surface">
      <p>{{ errorMessage }}</p>
    </section>
    <section v-else-if="keyedProducts.length === 0" class="empty-state ds-surface">
      <p>등록된 판매 상품이 없습니다.</p>
    </section>
    <section v-else class="product-list">
      <article v-for="item in keyedProducts" :key="item.key">
        <div class="product-card ds-surface">
          <div class="thumb ds-thumb-frame ds-thumb-16x10">
            <img
              v-if="item.product.imageUrl"
              class="ds-thumb-img"
              :src="item.product.imageUrl"
              :alt="item.product.name"
            />
            <div v-else class="thumb__placeholder"></div>
          </div>
          <div class="product-main">
            <div class="product-title">{{ item.product.name }}</div>
            <p class="product-desc">{{ item.product.short_desc }}</p>
            <p v-if="resolveSellerId(item.product)" class="seller-info">
              판매자: {{ resolveSellerId(item.product) }}
            </p>
            <div class="product-prices">
              <span class="price-original">{{ formatPrice(item.product.cost_price ?? 0) }}</span>
              <span class="price-sale">{{ formatPrice(item.product.price) }}</span>
              <span v-if="getDiscountPercent(item.product) > 0" class="price-discount">
                -{{ getDiscountPercent(item.product) }}%
              </span>
            </div>
          </div>
          <div class="product-side">
            <div class="stock">재고: {{ getStockCount(item.product) }}개</div>
            <span class="status-pill">{{ statusLabelMap[getStatus(item.key)] }}</span>
            <button
              type="button"
              class="btn danger"
              :disabled="deletingKey === item.key"
              @click="handleDelete(item.product)"
            >
              {{ deletingKey === item.key ? '삭제 중…' : '삭제' }}
            </button>
          </div>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
}

.control-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 140px;
}

.control-field.search {
  flex: 1 1 220px;
}

.control-label {
  font-weight: 800;
  color: var(--text-strong);
  font-size: 0.85rem;
}

select,
input[type='search'] {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 10px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.product-card {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr) 200px;
  gap: 16px;
  padding: 16px;
  align-items: center;
}

.thumb {
  width: 140px;
  height: 110px;
  border-radius: 12px;
  overflow: hidden;
}

.thumb__placeholder {
  width: 100%;
  height: 100%;
  background: #fff;
}

.product-main {
  min-width: 0;
}

.product-title {
  font-weight: 900;
  color: var(--text-strong);
  font-size: 1rem;
}

.product-desc {
  margin: 6px 0 10px;
  color: var(--text-muted);
  font-weight: 700;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.seller-info {
  margin: 0 0 10px;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.product-prices {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.price-original {
  text-decoration: line-through;
  color: var(--text-soft);
  font-weight: 700;
}

.price-sale {
  color: var(--text-strong);
  font-weight: 900;
}

.price-discount {
  color: #ef4444;
  font-weight: 800;
}

.product-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
}

.stock {
  font-weight: 800;
  color: var(--text-strong);
}

.status-pill {
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--surface-weak);
  color: var(--text-strong);
  font-weight: 800;
  font-size: 0.85rem;
}

.empty-state {
  padding: 18px;
  text-align: center;
  font-weight: 700;
  color: var(--text-muted);
}

.btn {
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 900;
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  cursor: pointer;
}

.btn.danger {
  background: #ef4444;
  color: #fff;
  border-color: transparent;
}

@media (max-width: 960px) {
  .product-card {
    grid-template-columns: 120px minmax(0, 1fr);
  }

  .product-side {
    align-items: flex-start;
  }
}

@media (max-width: 720px) {
  .page-head {
    flex-direction: column;
  }

  .product-card {
    grid-template-columns: 1fr;
  }

  .thumb {
    width: 100%;
    height: 180px;
  }
}
</style>
