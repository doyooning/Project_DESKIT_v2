<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import { createImageErrorHandler, PLACEHOLDER_IMAGE, resolveProductImageUrlFromRaw } from '../../lib/images/productImages'

type ProductStatus = 'DRAFT' | 'READY' | 'ON_SALE' | 'LIMITED_SALE' | 'SOLD_OUT' | 'PAUSED' | 'HIDDEN'

type StatusFilter = 'all' | ProductStatus

type SortOption = 'name' | 'status'

type SellerProduct = {
  product_id: number
  product_name: string
  price: number
  status: ProductStatus
  stock_qty: number
  created_at: string
  imageUrl?: string
}

const router = useRouter()
const statusFilter = ref<StatusFilter>('all')
const sortOption = ref<SortOption>('name')
const searchQuery = ref('')
const baseProducts = ref<SellerProduct[]>([])
const updatingStatus = ref<Record<number, boolean>>({})
const { handleImageError } = createImageErrorHandler()

const statusLabelMap: Record<ProductStatus, string> = {
  DRAFT: '작성중',
  READY: '준비',
  ON_SALE: '판매중',
  LIMITED_SALE: '한정판매',
  SOLD_OUT: '품절',
  PAUSED: '일시중지',
  HIDDEN: '숨김',
}

const getSelectableStatuses = (status: ProductStatus) => {
  const effectiveStatus = status === 'LIMITED_SALE' ? 'ON_SALE' : status
  switch (effectiveStatus) {
    case 'DRAFT':
      return ['READY'] as ProductStatus[]
    case 'READY':
      return ['ON_SALE', 'HIDDEN'] as ProductStatus[]
    case 'ON_SALE':
      return ['PAUSED', 'HIDDEN'] as ProductStatus[]
    case 'PAUSED':
      return ['ON_SALE', 'HIDDEN'] as ProductStatus[]
    case 'SOLD_OUT':
      return ['ON_SALE', 'HIDDEN'] as ProductStatus[]
    case 'HIDDEN':
      return ['READY'] as ProductStatus[]
    default:
      return [] as ProductStatus[]
  }
}

const filteredProducts = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  const filtered = baseProducts.value.filter((product) => {
    const name = (product.product_name || '').toLowerCase()
    const match = !q || name.includes(q)
    if (!match) return false
    const status = product.status
    if (statusFilter.value !== 'all' && statusFilter.value !== status) return false
    return true
  })

  if (sortOption.value === 'name') {
    return filtered.slice().sort((a, b) => a.product_name.localeCompare(b.product_name))
  }
  if (sortOption.value === 'status') {
    // Product status sort priority (display order only, not business logic)
    const order: Record<ProductStatus, number> = {
      DRAFT: 0,
      READY: 1,
      ON_SALE: 2,
      LIMITED_SALE: 3,
      SOLD_OUT: 4,
      PAUSED: 5,
      HIDDEN: 6,
    }
    return filtered.slice().sort((a, b) => order[a.status] - order[b.status])
  }
  return filtered
})

const formatPrice = (value: number) => `${value.toLocaleString('ko-KR')}원`

const handleCreate = () => {
  router.push('/seller/products/create').catch(() => {})
}

const handleEdit = (product: SellerProduct) => {
  router.push(`/seller/products/${product.product_id}/edit`).catch(() => {})
}

const fetchProducts = async () => {
  const base = import.meta.env.VITE_API_BASE_URL ?? ''
  try {
    const response = await fetch(`${base}/seller/products`, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      baseProducts.value = []
      return
    }
    const data = await response.json()
    const normalizeProducts = (items: unknown[]) => {
      return items.map((item) => {
        if (!item || typeof item !== 'object') return item
        const record = item as Record<string, unknown>
        const normalized = record.product_name || !record.name
          ? record
          : { ...record, product_name: record.name }
        return {
          ...normalized,
          imageUrl: resolveProductImageUrlFromRaw(normalized),
        }
      }) as SellerProduct[]
    }

    if (Array.isArray(data)) {
      baseProducts.value = normalizeProducts(data)
      return
    }
    if (Array.isArray(data?.products)) {
      baseProducts.value = normalizeProducts(data.products)
      return
    }
    baseProducts.value = []
  } catch {
    baseProducts.value = []
  }
}

const updateStatus = async (product: SellerProduct, nextStatus: ProductStatus) => {
  if (product.status === nextStatus) return
  if (updatingStatus.value[product.product_id]) return
  updatingStatus.value = { ...updatingStatus.value, [product.product_id]: true }
  const base = import.meta.env.VITE_API_BASE_URL ?? ''
  const apiBase = base.endsWith('/api') ? base.slice(0, -4) : base
  try {
    const response = await fetch(`${apiBase}/api/seller/products/${product.product_id}/status`, {
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ status: nextStatus }),
    })
    if (!response.ok) {
      window.alert('상태 변경에 실패했습니다.')
      await fetchProducts()
      return
    }
    await fetchProducts()
  } catch {
    window.alert('상태 변경에 실패했습니다.')
    await fetchProducts()
  } finally {
    updatingStatus.value = { ...updatingStatus.value, [product.product_id]: false }
  }
}

const handleStatusChange = (product: SellerProduct, event: Event) => {
  const target = event.target as HTMLSelectElement
  updateStatus(product, target.value as ProductStatus)
}

onMounted(() => {
  fetchProducts()
})
</script>

<template>
  <div>
    <PageHeader eyebrow="DESKIT" title="상품관리" />
    <header class="page-head">
      <div>
        <h2 class="section-title">나의 판매 목록({{ filteredProducts.length }})</h2>
        <p class="ds-section-sub">판매 중인 상품 상태를 관리할 수 있습니다.</p>
      </div>
      <div class="head-actions">
        <button type="button" class="btn primary" @click="handleCreate">상품등록</button>
      </div>
    </header>

    <section class="controls ds-surface">
      <label class="control-field">
        <span class="control-label">상태</span>
        <select v-model="statusFilter">
          <option value="all">전체</option>
          <option value="DRAFT">작성중</option>
          <option value="READY">준비</option>
          <option value="ON_SALE">판매중</option>
          <option value="LIMITED_SALE">한정판매</option>
          <option value="SOLD_OUT">품절</option>
          <option value="PAUSED">일시중지</option>
          <option value="HIDDEN">숨김</option>
        </select>
      </label>
      <label class="control-field">
        <span class="control-label">정렬</span>
        <select v-model="sortOption">
          <option value="name">상품이름</option>
          <option value="status">상태</option>
        </select>
      </label>
      <label class="control-field search">
        <span class="control-label">검색</span>
        <input v-model="searchQuery" type="search" placeholder="상품명 또는 설명 검색" />
      </label>
    </section>

    <section v-if="filteredProducts.length === 0" class="empty-state ds-surface">
      <p>등록된 판매 상품이 없습니다.</p>
    </section>
    <section v-else class="product-list">
      <article v-for="product in filteredProducts" :key="product.product_id" class="product-card ds-surface">
        <div class="thumb">
          <img :src="product.imageUrl || PLACEHOLDER_IMAGE" :alt="product.product_name" @error="handleImageError" />
        </div>
        <div class="product-main">
          <div class="product-title">
            {{ product.product_name }}
            <span v-if="product.status === 'LIMITED_SALE'" class="status-badge">한정판매</span>
          </div>
          <!-- description not provided by seller list API -->
          <div class="product-prices">
            <span class="price-sale">{{ formatPrice(product.price) }}</span>
          </div>
        </div>
        <div class="product-side">
          <div class="stock">재고: {{ product.stock_qty }}개</div>
          <select
            :value="product.status"
            class="status-select"
            :disabled="updatingStatus[product.product_id]"
            @change="handleStatusChange(product, $event)"
          >
            <option :value="product.status" disabled>
              {{ statusLabelMap[product.status] }}
            </option>
            <option
              v-for="nextStatus in getSelectableStatuses(product.status)"
              :key="nextStatus"
              :value="nextStatus"
            >
              {{ statusLabelMap[nextStatus] }}
            </option>
          </select>
          <div class="edit-group">
            <button type="button" class="btn btn-compact" @click="handleEdit(product)">수정</button>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.breadcrumb {
  font-size: 0.85rem;
  color: var(--text-muted);
  font-weight: 700;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.head-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
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
  background: var(--surface-weak);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb__placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #1f2937, #0f172a);
}

.product-main {
  min-width: 0;
}

.product-title {
  font-weight: 900;
  color: var(--text-strong);
  font-size: 1rem;
}

.status-badge {
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #111827;
  color: #f9fafb;
  font-weight: 800;
  font-size: 0.72rem;
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

.status-text {
  min-width: 120px;
  font-weight: 800;
  color: var(--text-strong);
}

.edit-group {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.inline-hint {
  font-size: 0.8rem;
  color: var(--text-muted);
  font-weight: 700;
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

.btn.primary {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.btn-compact {
  padding: 6px 12px;
  line-height: 1.1;
  font-size: 0.85rem;
  min-height: 28px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

  .head-actions {
    align-items: flex-start;
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
