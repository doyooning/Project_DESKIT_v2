<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { getSetupDetail } from '../api/setups'
import { listProducts } from '../api/products'
import { type DbProduct } from '../lib/products-data'
import { mapProducts, type UiProduct } from '../lib/products-mapper'
import ProductCard from '../components/ProductCard.vue'
import PageContainer from '../components/PageContainer.vue'

const route = useRoute()
const setupId = computed(() => Number(Array.isArray(route.params.id) ? route.params.id[0] : route.params.id))

const rawSetup = ref<any | null>(null)
const isLoading = ref(false)
const products = ref<DbProduct[]>([])

const loadSetup = async () => {
  const id = setupId.value
  if (!Number.isFinite(id)) {
    rawSetup.value = null
    return
  }
  isLoading.value = true
  try {
    rawSetup.value = await getSetupDetail(id)
  } catch (error) {
    console.error('Failed to load setup.', error)
    rawSetup.value = null
  } finally {
    isLoading.value = false
  }
}

const loadProducts = async () => {
  try {
    products.value = await listProducts()
  } catch (error) {
    console.error('Failed to load products.', error)
  }
}

watch(setupId, () => {
  loadSetup()
}, { immediate: true })

onMounted(() => {
  loadProducts()
})

const setup = computed(() => rawSetup.value)

const uiProducts = computed<UiProduct[]>(() => mapProducts(products.value))
const productById = computed(() => new Map(uiProducts.value.map((p: UiProduct) => [Number(p.id), p])))

const setupProducts = computed<UiProduct[]>(() => {
  if (!setup.value) return []
  const ids = setup.value.product_ids as Array<number | string>
  return ids
    .map((id) => productById.value.get(Number(id)))
    .filter((p): p is UiProduct => Boolean(p))
})
</script>

<template>
  <PageContainer>
    <RouterLink to="/setup" class="back">← 셋업 목록</RouterLink>

    <div v-if="isLoading" class="empty">셋업 정보를 불러오는 중...</div>
    <div v-else-if="setup" class="layout">
      <div class="hero-media">
        <img :src="setup.imageUrl" :alt="setup.title" />
      </div>

      <section class="info">
        <p class="eyebrow">DESKIT SETUP</p>
        <h1>{{ setup.title }}</h1>
        <p class="desc">{{ setup.short_desc }}</p>
        <div v-if="setup.tags && setup.tags.length" class="tags">
          <span v-for="tag in setup.tags" :key="tag" class="tag">#{{ tag }}</span>
        </div>
      </section>

      <section class="tip" v-if="setup.tip">
        <h2 class="section-title">Tip</h2>
        <div class="tip-card">
          <p class="tip-text">{{ setup.tip }}</p>
        </div>
      </section>
      <section class="tip" v-else>
        <h2 class="section-title">Tip</h2>
        <div class="tip-card">
          <p class="tip-text">Tip coming soon</p>
        </div>
      </section>

      <section class="products-block">
        <div class="products-head">
          <h2>셋업에 사용된 상품</h2>
          <span class="count">({{ setupProducts.length }}개)</span>
        </div>
        <div v-if="setupProducts.length" class="product-grid">
          <ProductCard
            v-for="product in setupProducts"
            :key="product.id"
            :id="product.id"
            :name="product.name"
            :image-url="product.imageUrl"
            :price="product.price"
            :original-price="product.originalPrice"
          />
        </div>
        <div v-else class="products-empty">연결된 상품이 아직 없어요.</div>
      </section>

      <div class="bottom-cta">
        <RouterLink to="/products" class="btn-link">전체 상품 보기</RouterLink>
      </div>
    </div>

    <div v-else class="empty">
      <p>셋업을 찾을 수 없습니다.</p>
      <div class="links">
        <RouterLink to="/" class="link">홈으로</RouterLink>
        <RouterLink to="/setup" class="link">셋업 목록으로</RouterLink>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.setup-detail {
  padding: 16px 0 36px;
}

.layout {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.back {
  color: var(--text-muted);
  font-weight: 700;
  display: inline-block;
  margin-bottom: 12px;
}

.hero-media {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: var(--surface-weak);
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.hero-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.eyebrow {
  margin: 0;
  color: var(--text-soft);
  font-weight: 800;
  letter-spacing: 0.04em;
}

h1 {
  margin: 0;
  font-size: 1.6rem;
  font-weight: 800;
}

.desc {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.5;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  row-gap: 8px;
}

.tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.88rem;
}

.tip {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--text-strong);
}

.tip-card {
  padding: 18px 20px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-weak);
  box-shadow: var(--shadow-soft);
}

.tip-text {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.products-block {
  margin-top: 6px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}

.products-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 12px;
}

.products-head h2 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--text-strong);
}

.count {
  color: var(--text-muted);
  font-weight: 700;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.products-empty {
  padding: 16px;
  text-align: center;
  color: var(--text-muted);
}

.bottom-cta {
  display: flex;
  justify-content: center;
  margin-top: 4px;
}

.btn-link {
  display: inline-block;
  padding: 12px 18px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-strong);
  font-weight: 800;
  text-decoration: none;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.btn-link:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 16px rgba(15, 23, 42, 0.08);
}

.empty {
  padding: 24px;
  border: 1px dashed var(--border-color);
  border-radius: 14px;
  background: #fff;
  text-align: center;
  color: var(--text-muted);
}

.links {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 10px;
}

.link {
  font-weight: 700;
  color: var(--primary-color);
}
</style>
