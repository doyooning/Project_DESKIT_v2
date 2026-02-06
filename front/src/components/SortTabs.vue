<script setup lang="ts">
export type SortOption = 'ranking' | 'price-low' | 'price-high' | 'sales' | 'latest'

const props = defineProps<{
  modelValue: SortOption
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SortOption): void
}>()

const options: { value: SortOption; label: string }[] = [
  { value: 'ranking', label: '랭킹순' },
  { value: 'price-low', label: '낮은 가격' },
  { value: 'price-high', label: '높은 가격' },
  { value: 'sales', label: '판매순' },
  { value: 'latest', label: '최신순' },
]

const change = (value: SortOption) => emit('update:modelValue', value)
</script>

<template>
  <div class="tabs">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :class="['tab', { 'tab--active': option.value === props.modelValue }]"
      @click="change(option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
.tabs {
  display: inline-flex;
  gap: 8px;
  padding: 6px;
  border-radius: 14px;
  background: var(--surface-weak);
  border: 1px solid var(--border-color);
}

.tab {
  border: 1px solid transparent;
  background: transparent;
  padding: 10px 12px;
  border-radius: 10px;
  font-weight: 700;
  color: var(--text-muted);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.tab:hover {
  color: var(--text-strong);
  background: var(--hover-bg);
}

.tab--active {
  background: var(--surface);
  color: var(--primary-color);
  box-shadow: 0 10px 24px rgba(var(--primary-rgb), 0.12);
  border: 1px solid var(--primary-color);
}
</style>
