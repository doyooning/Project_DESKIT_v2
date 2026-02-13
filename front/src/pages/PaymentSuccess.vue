<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { confirmTossPayment } from '../api/payments'
import { abandonCreatedOrder } from '../api/orders'
import { clearCheckout } from '../lib/checkout/checkout-storage'
import { removeCartItemsByProductIds } from '../lib/cart/cart-storage'
import { appendOrder, saveLastOrder, type OrderReceipt } from '../lib/order/order-storage'
import {
  clearPendingTossPayment,
  loadPendingTossPayment,
} from '../lib/checkout/toss-payment-storage'

const route = useRoute()
const router = useRouter()
const isProcessing = ref(true)
const errorMessage = ref('')

const paymentLabelOf = (method: string | null) => {
  if (!method) return '토스페이'
  const normalized = method.toUpperCase()
  if (normalized === 'CARD' || method.includes('카드')) return '카드'
  if (normalized === 'EASY_PAY' || method.includes('간편')) return '간편결제'
  if (normalized === 'TRANSFER' || method.includes('계좌')) return '계좌이체'
  if (normalized === 'VIRTUAL_ACCOUNT' || method.includes('가상')) return '가상계좌'
  if (normalized === 'MOBILE_PHONE' || method.includes('휴대폰')) return '휴대폰'
  if (normalized === 'CULTURE_GIFT_CERTIFICATE' || method.includes('문화상품권')) return '문화상품권'
  if (normalized === 'BOOK_GIFT_CERTIFICATE' || method.includes('도서')) return '도서문화상품권'
  if (normalized === 'GAME_GIFT_CERTIFICATE' || method.includes('게임')) return '게임문화상품권'
  if (normalized === 'PAYCO' || method.toUpperCase().includes('PAYCO')) return 'PAYCO'
  if (normalized === 'TOSSPAY' || method.includes('토스페이')) return '토스페이'
  return '토스페이'
}

const resolvePaymentMethodLabel = (response: any) => {
  if (!response) return null
  const method = typeof response.method === 'string' ? response.method : ''
  if (!method) return null

  const normalized = method.toUpperCase()
  if (normalized === 'EASY_PAY' || method.includes('간편')) {
    const provider =
      response.easyPay?.provider ||
      response.easyPay?.providerCode ||
      response.easyPay?.providerName
    return provider ? `간편결제(${provider})` : '간편결제'
  }
  return paymentLabelOf(method)
}

const finalize = async () => {
  const pending = loadPendingTossPayment()
  const paymentKey = String(route.query.paymentKey ?? '')
  const orderId = String(route.query.orderId ?? pending?.tossOrderId ?? '')
  const amountRaw = Number(route.query.amount ?? NaN)
  const amount =
    Number.isFinite(amountRaw) && amountRaw > 0
      ? amountRaw
      : pending?.orderAmount ?? NaN

  if (!paymentKey || !orderId || !Number.isFinite(amount)) {
    errorMessage.value = '결제 확인 정보를 확인할 수 없습니다.'
    isProcessing.value = false
    return
  }

  try {
    const confirmResponse = await confirmTossPayment({ paymentKey, orderId, amount })
    const confirmedLabel = resolvePaymentMethodLabel(confirmResponse)
    if (pending) {
      const receipt: OrderReceipt = {
        orderId: pending.orderNumber || String(pending.orderId),
        createdAt: pending.createdAt,
        items: pending.items.map((item) => ({
          productId: item.productId,
          name: item.name,
          quantity: item.quantity,
          price: item.price,
          originalPrice: item.originalPrice,
          discountRate: item.discountRate,
        })),
        shipping: { ...pending.shipping },
        status: 'PAID',
        paymentMethodLabel:
          confirmedLabel || pending.paymentMethodLabel || paymentLabelOf(pending.paymentMethod),
        totals: { ...pending.totals },
      }
      saveLastOrder(receipt)
      appendOrder(receipt)
      removeCartItemsByProductIds(pending.items.map((item) => item.productId))
      clearCheckout()
    }
    clearPendingTossPayment()
    router.replace({
      name: 'order-complete-detail',
      params: { orderId: pending?.orderId ?? orderId },
    })
  } catch (error: any) {
    if (pending?.orderId) {
      try {
        await abandonCreatedOrder(pending.orderId)
      } catch (cleanupError) {
        console.error(cleanupError)
      }
    }
    clearPendingTossPayment()
    errorMessage.value =
      error?.response?.data?.message || '결제 확인 처리에 실패했습니다.'
  } finally {
    isProcessing.value = false
  }
}

onMounted(() => {
  finalize()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="결제 확인" />

    <section class="status-card">
      <h2 v-if="isProcessing" class="status-title">결제 확인 처리 중입니다.</h2>
      <h2 v-else-if="errorMessage" class="status-title">결제 확인에 실패했습니다.</h2>
      <h2 v-else class="status-title">결제가 완료되었습니다.</h2>
      <p v-if="errorMessage" class="status-desc">{{ errorMessage }}</p>
      <p v-else class="status-desc">주문이 완료되면 주문 상세 페이지로 이동합니다.</p>
      <div v-if="errorMessage" class="actions">
        <button type="button" class="btn ghost" @click="router.push('/checkout')">
          결제 화면으로 돌아가기
        </button>
        <button type="button" class="btn primary" @click="router.push('/cart')">
          장바구니로 이동
        </button>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.status-card {
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 18px;
  background: var(--surface);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.status-title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 900;
}

.status-desc {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn {
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface);
  font-weight: 900;
  cursor: pointer;
}

.btn.primary {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #fff;
}
</style>
