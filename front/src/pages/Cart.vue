<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { getProductDetail } from '../api/products'
import {
  clearCart as clearCartStorage,
  loadCart,
  removeCartItem,
  setAllSelected,
  updateQuantity,
  updateSelection,
  updateCartItemsPricing,
  type StoredCartItem,
} from '../lib/cart/cart-storage'
import { createCheckoutFromCart, saveCheckout } from '../lib/checkout/checkout-storage'

const router = useRouter()
const cartItems = ref<StoredCartItem[]>(loadCart())
const priceNotice = ref('')
const priceSyncTimer = ref<number | null>(null)
const priceSyncInFlight = ref(false)
const priceChangePending = ref(false)
const priceChangeModalOpen = ref(false)
const priceChangeMessage = ref('')

const formatPrice = (value: number) => `${value.toLocaleString('ko-KR')}원`

const totalCount = computed(() => cartItems.value.length)
const selectedItems = computed(() => cartItems.value.filter((item) => item.isSelected))
const selectedCount = computed(() => selectedItems.value.length)
const selectedQuantity = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + item.quantity, 0),
)

const subtotal = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0),
)

// 무료배송 50,000원 이상, 아니면 3,000원. 선택된 상품이 없으면 배송비 0.
const shippingFee = computed(() => {
  if (selectedCount.value === 0) return 0
  return subtotal.value >= 50000 ? 0 : 3000
})

const total = computed(() => subtotal.value + shippingFee.value)
const isAllSelected = computed(
  () => cartItems.value.length > 0 && selectedCount.value === cartItems.value.length,
)

const refresh = () => {
  cartItems.value = loadCart()
}

const resolvePricing = (product: any) => {
  const price = Number(product?.price ?? 0) || 0
  const candidateOriginal = Number(product?.cost_price ?? product?.costPrice ?? price) || price
  const originalPrice = candidateOriginal > price ? candidateOriginal : price
  const discountRate = originalPrice > price ? Math.round((1 - price / originalPrice) * 100) : 0
  const stock = Math.max(1, Number(product?.stock_qty ?? product?.stock ?? 99) || 99)
  return { price, originalPrice, discountRate, stock }
}

const syncPrices = async () => {
  if (priceSyncInFlight.value) return false
  const current = loadCart()
  if (current.length === 0) return false
  priceSyncInFlight.value = true
  try {
    const results = await Promise.all(
      current.map(async (item) => {
        try {
          const detail = await getProductDetail(item.productId)
          return { item, detail }
        } catch {
          return { item, detail: null }
        }
      }),
    )
    const patches: Array<{
      productId: string
      price: number
      originalPrice: number
      discountRate: number
      stock: number
    }> = []
    let priceChanged = false
    let quantityAdjusted = false
    results.forEach(({ item, detail }) => {
      if (!detail) return
      const pricing = resolvePricing(detail)
      const needsUpdate =
        item.price !== pricing.price ||
        item.originalPrice !== pricing.originalPrice ||
        item.discountRate !== pricing.discountRate ||
        item.stock !== pricing.stock
      if (needsUpdate) {
        patches.push({ productId: item.productId, ...pricing })
      }
      if (item.price !== pricing.price) {
        priceChanged = true
      }
      if (item.quantity > pricing.stock) {
        quantityAdjusted = true
      }
    })
    if (patches.length > 0) {
      updateCartItemsPricing(patches)
      refresh()
      const noticeMessage = priceChanged
        ? '가격이 변경된 상품이 있어 금액을 최신으로 업데이트했습니다.'
        : quantityAdjusted
          ? '재고가 변경되어 수량을 조정했습니다.'
          : ''
      if (noticeMessage) {
        priceNotice.value = noticeMessage
        priceChangeMessage.value = priceChanged
          ? '상품 가격이 변경되어 장바구니 금액을 갱신했습니다. 확인 후 계속 진행해주세요.'
          : '재고가 변경되어 수량을 조정했습니다. 확인 후 계속 진행해주세요.'
        priceChangePending.value = true
        priceChangeModalOpen.value = true
        return true
      }
    }
    return false
  } finally {
    priceSyncInFlight.value = false
  }
}

const toggleItemSelection = (id: string) => {
  const target = cartItems.value.find((item) => item.id === id)
  if (!target) return
  updateSelection(target.productId, !target.isSelected)
  refresh()
}

const toggleSelectAll = () => {
  const next = !isAllSelected.value
  setAllSelected(next)
  refresh()
}

const changeQuantity = (id: string, delta: number) => {
  const target = cartItems.value.find((item) => item.id === id)
  if (!target) return
  updateQuantity(target.productId, target.quantity + delta)
  refresh()
}

const removeItem = (id: string) => {
  const target = cartItems.value.find((item) => item.id === id)
  if (!target) return
  const name = target.name || '이 상품'
  const confirmed = window.confirm(`"${name}"을(를) 장바구니에서 삭제할까요?`)
  if (!confirmed) return
  removeCartItem(target.productId || id)
  refresh()
}

const removeSelected = () => {
  if (selectedCount.value === 0) return
  const confirmed = window.confirm(`선택한 상품 ${selectedCount.value}개를 삭제할까요?`)
  if (!confirmed) return
  selectedItems.value.forEach((item) => removeCartItem(item.productId))
  refresh()
}

const clearCart = () => {
  if (cartItems.value.length === 0) return
  const confirmed = window.confirm('장바구니 상품을 전체 삭제할까요?')
  if (!confirmed) return
  clearCartStorage()
  refresh()
}

const handleCheckout = async () => {
  if (selectedCount.value === 0) return
  const hasUpdates = await syncPrices()
  if (hasUpdates) return
  if (priceChangePending.value) {
    priceChangeModalOpen.value = true
    return
  }
  const draft = createCheckoutFromCart(selectedItems.value)
  saveCheckout(draft)
  router.push({ name: 'checkout' }).catch(() => router.push('/checkout'))
}

const confirmPriceChange = () => {
  priceChangePending.value = false
  priceChangeModalOpen.value = false
  priceChangeMessage.value = ''
}

const storageRefreshHandler = () => refresh()

onMounted(() => {
  window.addEventListener('deskit-cart-updated', storageRefreshHandler)
  window.addEventListener('storage', storageRefreshHandler)
  window.addEventListener('focus', syncPrices)
  document.addEventListener('visibilitychange', syncPrices)
  refresh()
  syncPrices()
  priceSyncTimer.value = window.setInterval(syncPrices, 15000)
})

onBeforeUnmount(() => {
  window.removeEventListener('deskit-cart-updated', storageRefreshHandler)
  window.removeEventListener('storage', storageRefreshHandler)
  window.removeEventListener('focus', syncPrices)
  document.removeEventListener('visibilitychange', syncPrices)
  if (priceSyncTimer.value) {
    window.clearInterval(priceSyncTimer.value)
  }
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" :title="`장바구니 (${totalCount})`" />

    <div class="cart-steps">
      <span class="cart-step cart-step--active">01 장바구니</span>
      <span class="cart-step__divider">></span>
      <span class="cart-step">02 주문/결제</span>
      <span class="cart-step__divider">></span>
      <span class="cart-step">03 주문 완료</span>
    </div>

    <div v-if="cartItems.length === 0" class="cart-empty">
      <p>장바구니가 비어 있습니다.</p>
      <RouterLink to="/products" class="empty-link">상품 보러가기</RouterLink>
    </div>

    <section v-else class="cart-layout">
      <div v-if="priceNotice" class="price-notice">
        <span>{{ priceNotice }}</span>
        <button type="button" class="price-notice__close" @click="priceNotice = ''">닫기</button>
      </div>
      <div class="cart-left">
        <div class="cart-list">
          <article v-for="item in cartItems" :key="item.id" class="cart-row">
            <div class="cart-item">
              <label class="cart-item__select">
                <input
                    type="checkbox"
                    :checked="item.isSelected"
                    @change="toggleItemSelection(item.id)"
                    aria-label="상품 선택"
                />
                <span class="checkbox__fake" />
              </label>

              <div class="cart-item__main">
                <div class="cart-item__thumb ds-thumb-frame ds-thumb-square">
                  <img class="ds-thumb-img" :src="item.imageUrl" :alt="item.name" />
                </div>
                <div class="cart-item__info">
                  <h3 class="cart-title">{{ item.name }}</h3>
                  <div class="cart-pricing">
                    <span v-if="item.discountRate > 0" class="discount">-{{ item.discountRate }}%</span>
                    <span class="original">{{ formatPrice(item.originalPrice) }}</span>
                    <span class="price">{{ formatPrice(item.price) }}</span>
                  </div>
                </div>
              </div>

              <div class="cart-item__actions">
                <div class="stepper" aria-label="수량 조절">
                  <button type="button" @click="changeQuantity(item.id, -1)">-</button>
                  <span>{{ item.quantity }}</span>
                  <button type="button" @click="changeQuantity(item.id, 1)">+</button>
                </div>
                <button type="button" class="delete" @click="removeItem(item.id)">삭제</button>
              </div>
            </div>
          </article>
        </div>

        <div class="cart-actions">
          <label class="select-all">
            <input
                type="checkbox"
                :checked="isAllSelected"
                @change="toggleSelectAll"
                aria-label="전체 선택"
            />
            <span class="checkbox__fake" />
            <span>전체 선택 ({{ selectedCount }})</span>
          </label>
          <div class="action-buttons">
            <button type="button" @click="removeSelected">선택 삭제</button>
            <button type="button" @click="clearCart">전체 삭제</button>
          </div>
        </div>
      </div>

      <aside class="cart-summary">
        <h3 class="summary-title">주문 예상 금액</h3>
        <div class="summary-row">
          <span>총 상품 가격</span>
          <strong>{{ formatPrice(subtotal) }}</strong>
        </div>
        <div class="summary-row">
          <span>배송비</span>
          <strong>{{ selectedCount === 0 ? '-' : formatPrice(shippingFee) }}</strong>
        </div>
        <div class="summary-total">
          <span>총 결제 금액</span>
          <strong>{{ selectedCount === 0 ? '-' : formatPrice(total) }}</strong>
        </div>
        <button
            type="button"
            class="summary-cta"
            :disabled="selectedCount === 0 || priceChangePending"
            @click="handleCheckout"
        >
          {{ selectedCount === 0 ? '상품을 선택해주세요' : `총 ${selectedQuantity}개 상품 구매하기` }}
        </button>
      </aside>
    </section>

    <div v-if="priceChangeModalOpen" class="modal-overlay" role="dialog" aria-modal="true">
      <div class="modal-card">
        <h3>변경 안내</h3>
        <p>
          {{
            priceChangeMessage ||
              '상품 가격이 변경되어 장바구니 금액을 갱신했습니다. 확인 후 계속 진행해주세요.'
          }}
        </p>
        <div class="modal-actions">
          <button type="button" class="btn primary" @click="confirmPriceChange">확인</button>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.cart-steps {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  color: var(--text-muted);
  font-weight: 700;
}

.cart-step {
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--surface-weak);
}

.cart-step--active {
  background: var(--hover-bg);
  color: var(--primary-color);
}

.cart-step__divider {
  color: var(--text-soft);
}

.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.6fr);
  gap: 18px;
}

.price-notice {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--surface-weak);
  border-radius: 12px;
  border: 1px solid var(--border-color);
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

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  display: grid;
  place-items: center;
  z-index: 1300;
}

.modal-card {
  width: min(420px, 90vw);
  background: var(--surface);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid var(--border-color);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.2);
}

.modal-card h3 {
  margin: 0 0 10px;
}

.modal-card p {
  margin: 0;
  color: var(--text-muted);
}

.modal-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.cart-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cart-list {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
}

.cart-row {
  border-bottom: 1px solid var(--border-color);
}

.cart-row:last-child {
  border-bottom: none;
}

.cart-item {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
}

.cart-item__select {
  display: grid;
  place-items: center;
  align-self: stretch;
  width: 36px;
}

.cart-item__main {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 14px;
  align-items: center;
  min-width: 0;
}

.cart-item__thumb {
  width: 96px;
  height: 96px;
  border-radius: 12px;
}

.cart-item__info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cart-item__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  white-space: nowrap;
}

.cart-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cart-pricing {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.discount {
  color: var(--primary-color);
  font-weight: 800;
}

.original {
  color: var(--text-muted);
  text-decoration: line-through;
}

.price {
  font-weight: 800;
  color: var(--text-strong);
  font-size: 1.05rem;
}

.stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface);
}

.stepper button {
  border: none;
  background: transparent;
  padding: 8px 10px;
  cursor: pointer;
  font-weight: 800;
  color: var(--text-strong);
}

.stepper span {
  padding: 0 12px;
  font-weight: 700;
}

.delete {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-weight: 700;
}

.cart-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 12px 16px;
  background: var(--surface);
  gap: 12px;
}

.select-all {
  display: grid;
  grid-template-columns: 36px auto;
  align-items: center;
  column-gap: 8px;
  cursor: pointer;
  font-weight: 700;
}
.select-all > .checkbox__fake {
  justify-self: center;
}
.select-all > span:not(.checkbox__fake) {
  grid-column: 2;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-buttons button {
  border: 1px solid var(--border-color);
  background: var(--surface);
  padding: 8px 12px;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 700;
  color: var(--text-strong);
}

.cart-summary {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: sticky;
  top: 80px;
  align-self: flex-start;
}

.summary-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 800;
}

.summary-row,
.summary-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-strong);
}

.summary-total {
  border-top: 1px solid var(--border-color);
  padding-top: 10px;
  font-size: 1.05rem;
}

.summary-cta {
  border: none;
  background: var(--primary-color);
  color: #fff;
  font-weight: 800;
  border-radius: 12px;
  padding: 12px 16px;
  cursor: pointer;
}

.summary-cta:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cart-item__select,
.select-all {
  position: relative;
}

.cart-item__select input,
.select-all input {
  position: absolute;
  opacity: 0;
  width: 1px;
  height: 1px;
  margin: 0;
  pointer-events: none;
}

/* ✅ 체크박스 모양 자연스럽게 */
.checkbox__fake {
  width: 18px;
  height: 18px;
  border: 1.5px solid var(--border-color);
  border-radius: 6px;
  background: var(--surface);
  position: relative;
  display: inline-block;
  flex-shrink: 0;
  box-sizing: border-box;
}

.cart-item__select input:checked + .checkbox__fake,
.select-all input:checked + .checkbox__fake {
  border-color: var(--primary-color);
  background: var(--primary-color);
}

.cart-item__select input:checked + .checkbox__fake::after,
.select-all input:checked + .checkbox__fake::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 2.5px;
  width: 6px;
  height: 10px;
  border: 2px solid #fff;
  border-top: 0;
  border-left: 0;
  transform: rotate(45deg);
}

.cart-item__select input:focus-visible + .checkbox__fake,
.select-all input:focus-visible + .checkbox__fake {
  outline: 2px solid rgba(0, 0, 0, 0.12);
  outline-offset: 2px;
}

.cart-empty {
  border: 1px dashed var(--border-color);
  padding: 24px;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: var(--text-muted);
}

.empty-link {
  color: var(--primary-color);
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 1080px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }

  .cart-summary {
    position: static;
    order: -1;
  }
}

@media (max-width: 640px) {
  .cart-item {
    grid-template-columns: 1fr;
    align-items: start;
    gap: 12px;
  }

  .cart-item__main {
    grid-template-columns: 1fr;
  }

  .cart-item__thumb {
    width: 100%;
    height: 180px;
  }

  .cart-item__actions {
    justify-content: flex-start;
  }

  .cart-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .action-buttons {
    width: 100%;
    justify-content: flex-start;
  }

  .cart-summary {
    position: static;
  }
}
</style>
