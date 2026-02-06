<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { getProductDetail } from '../api/products'
import { flattenTags, type DbProduct } from '../lib/products-data'
import PageContainer from '../components/PageContainer.vue'
import { upsertCartItem } from '../lib/cart/cart-storage'
import { createCheckoutFromBuyNow, saveCheckout } from '../lib/checkout/checkout-storage'
import {
  createImageErrorHandler,
  PLACEHOLDER_IMAGE,
  resolveImageList,
  resolvePrimaryImage,
} from '../lib/images/productImages'

const route = useRoute()
const router = useRouter()
const productId = computed(() => (Array.isArray(route.params.id) ? route.params.id[0] : route.params.id))

const rawProduct = ref<DbProduct | null>(null)
const isLoading = ref(false)
const priceNotice = ref('')
const pricePollTimer = ref<number | null>(null)
const { handleImageError } = createImageErrorHandler()
const placeholderImage = PLACEHOLDER_IMAGE

const loadProduct = async (options: { silent?: boolean; notifyOnChange?: boolean } = {}) => {
  const id = productId.value ? String(productId.value) : ''
  if (!id) {
    rawProduct.value = null
    return
  }
  if (!options.silent) {
    isLoading.value = true
  }
  const previous = rawProduct.value as any
  try {
    const next = await getProductDetail(id)
    if (options.notifyOnChange && previous && next) {
      const prevPrice = Number(previous.price ?? 0) || 0
      const prevCost = Number(previous.cost_price ?? previous.costPrice ?? prevPrice) || prevPrice
      const nextPrice = Number((next as any).price ?? 0) || 0
      const nextCost = Number((next as any).cost_price ?? (next as any).costPrice ?? nextPrice) || nextPrice
      if (prevPrice !== nextPrice || prevCost !== nextCost) {
        priceNotice.value = '가격이 변경되어 최신 정보로 업데이트했습니다.'
      }
    }
    rawProduct.value = next
  } catch (error) {
    console.error('Failed to load product.', error)
    rawProduct.value = null
  } finally {
    if (!options.silent) {
      isLoading.value = false
    }
  }
}

watch(productId, () => {
  priceNotice.value = ''
  loadProduct({ notifyOnChange: false })
}, { immediate: true })

const product = computed(() => {
  const raw = rawProduct.value as any
  if (!raw) return undefined
  return {
    ...raw,
    id: String(raw.product_id),
    imageUrl: resolvePrimaryImage(raw),
    description: (raw as any).short_desc ?? (raw as any).description,
    seller: (raw as any).seller_id ? `판매자 #${(raw as any).seller_id}` : undefined,
    tags: (raw as any).tags ?? { space: [], tone: [], situation: [], mood: [] },
    // NOTE: In production, HTML should be sanitized server-side or before rendering.
    detailHtml: (raw as any).detailHtml ?? '<h2>상품 상세</h2><p>판매자 에디터 HTML 영역입니다.</p>',
  }
})

const flatTags = computed(() => {
  if (!product.value?.tags) return []
  return flattenTags(product.value.tags)
})

const imageList = computed(() => {
  const raw = rawProduct.value as any
  if (!raw) return []
  const gallery = resolveImageList(raw)
  if (gallery.length === 0) {
    const primary = resolvePrimaryImage(raw)
    return [primary, primary, primary]
  }
  if (gallery.length === 1) {
    const primary = gallery[0]
    return [primary, primary, primary]
  }
  return gallery
})

const selectedImageIndex = ref(0)
watch(
    () => imageList.value,
    () => {
      selectedImageIndex.value = 0
    },
)

const selectedImage = computed(() => imageList.value[selectedImageIndex.value] ?? placeholderImage)

const originalPrice = computed(() => {
  if (!product.value) return undefined
  const candidate = (product.value as any).cost_price ?? product.value.price
  return candidate > product.value.price ? candidate : undefined
})

const discountRate = computed(() => {
  if (!product.value || !originalPrice.value) return 0
  if (originalPrice.value <= product.value.price) return 0
  return Math.round((1 - product.value.price / originalPrice.value) * 100)
})

const quantity = ref(1)

const isCartModalOpen = ref(false)
const modalCardRef = ref<HTMLDivElement | null>(null)
const lastFocusedEl = ref<HTMLElement | null>(null)

const decrease = () => {
  if (quantity.value > 1) quantity.value -= 1
}
const increase = () => {
  quantity.value += 1
}

const openModal = () => {
  lastFocusedEl.value = (document.activeElement as HTMLElement) || null
  isCartModalOpen.value = true
}

const closeModal = () => {
  isCartModalOpen.value = false
}

const goToCart = async () => {
  closeModal()
  try {
    await router.push({ name: 'cart' })
  } catch {
    await router.push('/cart')
  }
}

const handleAddToCart = () => {
  if (!product.value) return

  const salePrice = product.value.price
  const orig = originalPrice.value ?? salePrice
  const discount = orig > salePrice ? Math.round(((orig - salePrice) / orig) * 100) : 0

  const imageUrl = product.value.imageUrl || (product.value as any).image_url || placeholderImage

  upsertCartItem({
    productId: String((product.value as any).product_id ?? product.value.id),
    name: product.value.name,
    imageUrl,
    price: salePrice,
    originalPrice: orig,
    discountRate: discount,
    quantity: quantity.value,
    stock: (product.value as any).stock ?? 99,
    isSelected: true,
  })

  openModal()
}

const handleBuyNow = () => {
  if (!product.value) return
  const draft = createCheckoutFromBuyNow(product.value, quantity.value)
  saveCheckout(draft)
  router.push({ name: 'checkout' }).catch(() => router.push('/checkout'))
}

const handleEsc = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && isCartModalOpen.value) {
    closeModal()
  }
}

// (Optional but nice) focus trap: keep tab focus inside modal
const handleTrapFocus = (event: KeyboardEvent) => {
  if (!isCartModalOpen.value) return
  if (event.key !== 'Tab') return
  const root = modalCardRef.value
  if (!root) return

  const focusables = Array.from(
      root.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      ),
  ).filter((el) => !el.hasAttribute('disabled') && !el.getAttribute('aria-disabled'))

  if (focusables.length === 0) return

  const first = focusables[0] ?? null
  const last = focusables[focusables.length - 1] ?? null
  if (!first || !last) return
  const active = document.activeElement as HTMLElement | null

  if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  } else if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus()
  }
}

watch(isCartModalOpen, (open) => {
  // lock scroll
  if (open) {
    document.body.style.overflow = 'hidden'
    // focus modal
    requestAnimationFrame(() => {
      const root = modalCardRef.value
      const primaryBtn = root?.querySelector<HTMLElement>('.cart-modal__actions .btn.primary')
      ;(primaryBtn ?? root)?.focus?.()
    })
  } else {
    document.body.style.overflow = ''
    // restore focus
    requestAnimationFrame(() => {
      lastFocusedEl.value?.focus?.()
      lastFocusedEl.value = null
    })
  }
})

// close modal on route change
watch(
    () => route.fullPath,
    () => {
      if (isCartModalOpen.value) closeModal()
    },
)

onMounted(() => {
  window.addEventListener('keydown', handleEsc)
  window.addEventListener('keydown', handleTrapFocus)
  pricePollTimer.value = window.setInterval(() => {
    loadProduct({ silent: true, notifyOnChange: true })
  }, 15000)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleEsc)
  window.removeEventListener('keydown', handleTrapFocus)
  document.body.style.overflow = ''
  if (pricePollTimer.value) {
    window.clearInterval(pricePollTimer.value)
  }
})
</script>

<template>
  <PageContainer>
    <RouterLink to="/products" class="back">← 상품 목록으로</RouterLink>

    <div v-if="isLoading" class="empty">상품 정보를 불러오는 중...</div>
    <div v-else-if="product" class="card">
      <div v-if="priceNotice" class="price-notice">
        <span>{{ priceNotice }}</span>
        <button type="button" class="price-notice__close" @click="priceNotice = ''">닫기</button>
      </div>
      <div class="media">
        <div class="thumbs" v-if="imageList.length">
          <button
            v-for="(img, idx) in imageList"
            :key="`${img ?? ''}-${idx}`"
            type="button"
            class="thumb-btn ds-thumb-frame ds-thumb-square"
            :class="{ active: idx === selectedImageIndex }"
            @click="selectedImageIndex = idx"
          >
            <img class="ds-thumb-img" :src="img || placeholderImage" :alt="`${product.name} 썸네일 ${idx + 1}`" @error="handleImageError" />
          </button>
        </div>
        <div class="main-image ds-thumb-frame">
          <img class="ds-thumb-img" :src="selectedImage" :alt="product.name" @error="handleImageError" />
        </div>
      </div>

      <div class="info">
        <div class="content">
          <p class="eyebrow">DESKIT PRODUCT</p>
          <p v-if="product.seller" class="seller">{{ product.seller }}</p>
          <h1>{{ product.name }}</h1>
          <p class="desc" v-if="product.description">{{ product.description }}</p>
          <div v-if="flatTags.length" class="tags flat">
            <span v-for="tag in flatTags" :key="tag" class="tag">#{{ tag }}</span>
          </div>
        </div>

        <div class="spacer" />

        <div class="purchase">
          <div class="price-box">
            <span v-if="discountRate > 0" class="badge">-{{ discountRate }}%</span>
            <div class="prices">
              <p v-if="originalPrice" class="original">{{ originalPrice.toLocaleString('ko-KR') }}원</p>
              <p class="final">{{ product.price.toLocaleString('ko-KR') }}원</p>
            </div>
          </div>

          <div class="qty">
            <span class="label">수량</span>
            <div class="stepper">
              <button type="button" @click="decrease" :disabled="quantity === 1">-</button>
              <span class="count">{{ quantity }}</span>
              <button type="button" @click="increase">+</button>
            </div>
          </div>

          <div class="actions">
            <button type="button" class="btn secondary" @click="handleAddToCart">장바구니 담기</button>
            <button type="button" class="btn primary" @click="handleBuyNow">구매하기</button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty">
      <p>상품을 찾을 수 없습니다.</p>
      <RouterLink to="/products" class="back-link">상품 목록으로 돌아가기</RouterLink>
    </div>

    <section v-if="product?.detailHtml" class="detail-html">
      <h2 class="detail-html__title">상세 설명</h2>
      <!-- NOTE: Ensure HTML is sanitized before rendering in production -->
      <div class="detail-html__content" v-html="product.detailHtml" />
    </section>

    <!-- ✅ Add-to-cart modal -->
    <div
        v-if="isCartModalOpen"
        class="cart-modal__overlay"
        role="dialog"
        aria-modal="true"
        aria-label="장바구니 담기 모달"
        @click.self="closeModal"
    >
      <div ref="modalCardRef" class="cart-modal" tabindex="-1">
        <h3 class="cart-modal__title">장바구니 담기 완료!</h3>
        <p class="cart-modal__desc">장바구니에 상품이 추가되었습니다.</p>

        <div class="cart-modal__actions">
          <button type="button" class="btn secondary" @click="closeModal">계속 쇼핑하기</button>
          <button type="button" class="btn primary" @click="goToCart">장바구니 보러가기</button>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.back {
  color: var(--text-muted);
  font-weight: 700;
}

.card {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(260px, 1fr);
  gap: 20px;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.price-notice {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: var(--surface-weak);
  border-bottom: 1px solid var(--border-color);
  font-weight: 600;
  color: var(--text-strong);
}

.price-notice__close {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-weight: 700;
  cursor: pointer;
}

.media {
  display: flex;
  flex-direction: row;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 20px 16px 20px;
  box-sizing: border-box;
}

.main-image {
  flex: 1;
  min-height: 260px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.main-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumbs {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.thumb-btn {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid transparent;
  padding: 0;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(var(--primary-rgb), 0.1);
  transition: border-color 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
}

.thumb-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(var(--primary-rgb), 0.14);
}

.thumb-btn.active {
  border-color: var(--primary-color);
  box-shadow: 0 10px 22px rgba(var(--primary-rgb), 0.18);
}

.thumb-btn img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
  padding: 12px 20px 16px;
  box-sizing: border-box;
}

.content {
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.eyebrow {
  margin: 0;
  color: var(--text-soft);
  font-weight: 800;
  letter-spacing: 0.04em;
}

.seller {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 0.95rem;
}

h1 {
  margin: 0;
  font-size: 1.6rem;
  font-weight: 800;
}

.desc {
  margin: 4px 0 0;
  color: var(--text-muted);
  line-height: 1.5;
  max-width: 780px;
}

.tags.flat {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  row-gap: 8px;
}

.tag {
  padding: 6px 8px;
  background: var(--surface-weak);
  border-radius: 8px;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.9rem;
}

.spacer {
  flex: 1;
  min-height: 8px;
  max-height: 24px;
}

.purchase {
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #fff;
  width: min(520px, 100%);
  align-self: flex-start;
  box-sizing: border-box;
  box-shadow: var(--shadow-card);
}

.price-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.prices {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.badge {
  align-self: flex-start;
  background: var(--discount-color);
  color: #fff;
  padding: 6px 10px;
  border-radius: 12px;
  font-weight: 800;
  font-size: 0.9rem;
  box-shadow: 0 10px 18px var(--discount-color-soft);
}

.original {
  margin: 0;
  color: var(--text-soft);
  text-decoration: line-through;
}

.final {
  margin: 0;
  color: var(--text-strong);
  font-weight: 800;
  font-size: 1.4rem;
}

.qty {
  display: flex;
  align-items: center;
  gap: 10px;
}

.qty .label {
  color: var(--text-muted);
  font-weight: 700;
}

.stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow: hidden;
}

.stepper button {
  width: 36px;
  height: 36px;
  background: #fff;
  border: none;
  color: var(--text-strong);
  font-size: 1rem;
  cursor: pointer;
}

.stepper button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.stepper .count {
  min-width: 40px;
  text-align: center;
  font-weight: 800;
  color: var(--text-strong);
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.btn {
  padding: 12px 10px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.btn.secondary:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 16px rgba(var(--primary-rgb), 0.12);
}

.btn.primary {
  background: var(--primary-color);
  color: #fff;
  border-color: var(--primary-color);
}

.btn.primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(var(--primary-rgb), 0.2);
}

.empty {
  padding: 24px;
  border-radius: 14px;
  background: #fff;
  border: 1px dashed var(--border-color);
  text-align: center;
  color: var(--text-muted);
}

.back-link {
  display: inline-block;
  margin-top: 8px;
  font-weight: 700;
  color: var(--primary-color);
}

.detail-html {
  padding: 28px 0 38px;
  background: var(--surface);
}

.detail-html__title {
  margin: 0 0 12px;
  font-size: 1.2rem;
  font-weight: 800;
  color: var(--text-strong);
}

.detail-html__content {
  padding: 14px 0 0;
  line-height: 1.6;
  color: var(--text-muted);
}

.detail-html__content h1,
.detail-html__content h2,
.detail-html__content h3,
.detail-html__content h4 {
  margin: 16px 0 10px;
  color: var(--text-strong);
}

.detail-html__content p {
  margin: 8px 0;
}

.detail-html__content img {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 10px 0;
}

/* ✅ modal */
.cart-modal__overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.48);
  display: grid;
  place-items: center;
  padding: 18px;
  z-index: 9999;
}

.cart-modal {
  width: min(520px, 100%);
  background: var(--surface);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.24);
  padding: 18px 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  outline: none;
}

.cart-modal__title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 900;
  color: var(--text-strong);
}

.cart-modal__desc {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.5;
}

.cart-modal__actions {
  margin-top: 8px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

@media (max-width: 720px) {
  .detail-html {
    padding: 22px 0 30px;
  }
}

@media (max-width: 720px) {
  .card {
    grid-template-columns: 1fr;
  }

  .media {
    flex-direction: column;
    padding: 10px 14px 12px;
  }

  .thumbs {
    flex-direction: row;
    order: 2;
  }

  .thumb-btn {
    width: 60px;
    height: 60px;
  }

  .purchase {
    border-left: none;
    border-top: 1px solid var(--border-color);
  }

  .cart-modal__actions {
    grid-template-columns: 1fr;
  }
}
</style>
