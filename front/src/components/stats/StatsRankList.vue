<script setup lang="ts">
const props = defineProps<{
  items: Array<{ rank: number; title: string; value: number }>
  valueFormatter?: (value: number) => string
}>()

const formatValue = (value: number) => {
  if (props.valueFormatter) return props.valueFormatter(value)
  return value.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="rank-list">
    <div v-for="item in items" :key="item.rank" class="rank-row">
      <div class="rank-badge">{{ item.rank }}</div>
      <div class="rank-body">
        <p class="rank-title">{{ item.title }}</p>
      </div>
      <div class="rank-value">{{ formatValue(item.value) }}</div>
    </div>
    <p v-if="!items.length" class="empty">데이터가 없습니다.</p>
  </div>
</template>

<style scoped>
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--surface-weak);
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(var(--primary-rgb), 0.12);
  color: var(--primary-color);
  font-weight: 900;
}

.rank-title {
  margin: 0;
  font-weight: 800;
  color: var(--text-strong);
}

.rank-value {
  font-weight: 900;
  color: var(--text-strong);
  white-space: nowrap;
}

.empty {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}
</style>
