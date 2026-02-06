<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps<{
  id: string | number
  name: string
  imageUrl: string
  price: number
  originalPrice?: number
  tags?: string[]
}>()

const formatPrice = (value: number) => {
  return value.toLocaleString('ko-KR') + '원'
}

const discountRate = computed(() => {
  if (!props.originalPrice || props.originalPrice <= props.price) return 0
  return Math.round((1 - props.price / props.originalPrice) * 100)
})
</script>

<template>
  <RouterLink :to="`/products/${props.id}`" class="card-link">
    <article class="card">
      <div class="thumb ds-thumb-frame ds-thumb-16x10">
        <img class="ds-thumb-img" :src="props.imageUrl" :alt="props.name" />
        <span v-if="discountRate > 0" class="badge">-{{ discountRate }}%</span>
      </div>
      <div class="body">
        <h3>{{ props.name }}</h3>
        <div class="price-row">
          <p class="price">{{ formatPrice(props.price) }}</p>
          <p v-if="discountRate > 0 && props.originalPrice" class="original">
            {{ formatPrice(props.originalPrice) }}
          </p>
        </div>
      </div>
    </article>
  </RouterLink>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--surface);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.card-link {
  display: block;
  text-decoration: none;
  color: inherit;
}

.card-link:hover .card {
  transform: translateY(-3px);
  box-shadow: 0 16px 32px rgba(var(--primary-rgb), 0.14);
}

.thumb {
  position: relative;
  width: 100%;
  overflow: hidden;
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
  box-shadow: 0 2px 6px rgba(249, 115, 22, 0.25);
}

.body {
  padding: 12px 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  height: 100%;
  min-height: 120px;
}

h3 {
  margin: 0;
  font-size: 1.05rem;
  color: var(--text-strong);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: auto;
}

.price {
  margin: 0;
  color: var(--primary-color);
  font-weight: 800;
  font-size: 1.05rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.original {
  margin: 0;
  color: var(--text-soft);
  font-size: 0.9rem;
  text-decoration: line-through;
}
</style>
