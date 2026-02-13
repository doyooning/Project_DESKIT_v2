<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { abandonCreatedOrder } from '../api/orders'
import {
  clearPendingTossPayment,
  loadPendingTossPayment,
} from '../lib/checkout/toss-payment-storage'

const route = useRoute()
const router = useRouter()

const code = computed(() => String(route.query.code ?? ''))
const message = computed(() => String(route.query.message ?? '결제 요청이 취소되었거나 실패했습니다.'))

onMounted(async () => {
  const pending = loadPendingTossPayment()
  if (pending?.orderId) {
    try {
      await abandonCreatedOrder(pending.orderId)
    } catch (error) {
      console.error(error)
    }
  }
  clearPendingTossPayment()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="결제 실패" />

    <section class="status-card">
      <h2 class="status-title">결제에 실패했습니다.</h2>
      <p class="status-desc">에러 코드: {{ code || '-' }}</p>
      <p class="status-desc">사유: {{ message }}</p>
      <div class="actions">
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
