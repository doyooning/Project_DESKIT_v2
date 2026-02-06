<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps<{
  id: string
  name: string
  imageUrl: string
  price: number
  originalPrice?: number
  description?: string
}>()

const formatPrice = (value: number) => value.toLocaleString('ko-KR') + '원'

const discountRate = computed(() => {
  if (!props.originalPrice || props.originalPrice <= props.price) return 0
  return Math.round((1 - props.price / props.originalPrice) * 100)
})
</script>

<template>
  <RouterLink :to="`/products/${props.id}`" class="card">
    <div class="thumb ds-thumb-frame ds-thumb-square">
      <img class="ds-thumb-img" :src="props.imageUrl" :alt="props.name" />
      <span v-if="discountRate > 0" class="badge">-{{ discountRate }}%</span>
    </div>
    <div class="body">
      <h3>{{ props.name }}</h3>
      <p v-if="props.description" class="desc">{{ props.description }}</p>
      <div class="price-row">
        <p class="price">{{ formatPrice(props.price) }}</p>
        <p v-if="discountRate > 0 && props.originalPrice" class="original">{{ formatPrice(props.originalPrice) }}</p>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  height: 100%;
}

.card:hover {
  transform: translateY(-6px);
  box-shadow: 0 18px 38px rgba(var(--primary-rgb), 0.16);
  border-color: var(--accent-color);
}

.thumb {
  aspect-ratio: 1 / 1;
  position: relative;
  width: 100%;
  overflow: hidden;
}

.body {
  padding: 14px 15px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

h3 {
  margin: 0;
  font-size: 1.05rem;
  color: var(--text-strong);
}

.desc {
  margin: 2px 0 0;
  color: var(--text-muted);
  font-size: 0.95rem;
  line-height: 1.4;
}

.price {
  margin: 6px 0 2px;
  color: var(--primary-color);
  font-weight: 800;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: auto;
}

.original {
  margin: 0;
  color: var(--text-soft);
  font-size: 0.95rem;
  text-decoration: line-through;
}

.badge {
  position: absolute;
  top: 10px;
  left: 10px;
  background: var(--discount-color);
  color: #fff;
  padding: 6px 10px;
  border-radius: 12px;
  font-weight: 700;
  font-size: 0.85rem;
  box-shadow: 0 10px 18px var(--discount-color-soft);
}

</style>
