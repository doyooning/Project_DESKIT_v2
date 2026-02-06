<script setup lang="ts">
import {computed, ref} from 'vue'

type TagCategoryKey = 'space' | 'tone' | 'situation' | 'mood'
type ProductCategoryKey = 'all' | 'furniture' | 'computer' | 'accessory'

type TagCategoryMap = Record<TagCategoryKey, string[]>

const props = defineProps<{
  availableTags: TagCategoryMap
  modelValue: TagCategoryMap
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: TagCategoryMap): void
  (e: 'update:productCategory', value: ProductCategoryKey): void
}>()

const categories: { key: TagCategoryKey; label: string }[] = [
  {key: 'space', label: '공간'},
  {key: 'tone', label: '톤'},
  {key: 'situation', label: '상황'},
  {key: 'mood', label: '무드'},
]

const tabs = [
  {key: 'all', label: '전체'},
  ...categories,
] as const

const activeCategory = ref<'all' | TagCategoryKey>('all')
const activeProductCategory = ref<ProductCategoryKey>('all')

const toggle = (category: TagCategoryKey, tag: string) => {
  const current = props.modelValue[category]
  const nextCategoryTags = current.includes(tag) ? current.filter((t) => t !== tag) : [...current, tag]
  emit('update:modelValue', {...props.modelValue, [category]: nextCategoryTags})
}

const clearAll = () => {
  emit('update:modelValue', {
    space: [],
    tone: [],
    situation: [],
    mood: [],
  })
}

const isSelected = (category: TagCategoryKey, tag: string) => props.modelValue[category].includes(tag)

const currentTags = computed(() => {
  if (activeCategory.value === 'all') return []
  return props.availableTags[activeCategory.value].map((tag) => ({
    category: activeCategory.value as TagCategoryKey,
    tag,
  }))
})
</script>

<template>
  <div class="filter">
    <div class="top-row">
      <div class="tabs" role="tablist">
        <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="tab"
            :class="{ 'tab--active': activeCategory === tab.key }"
            role="tab"
            :aria-selected="activeCategory === tab.key"
            @click="
            () => {
              activeCategory = tab.key as any
              if (activeCategory !== 'all') {
                activeProductCategory = 'all'
                emit('update:productCategory', 'all')
              }
            }
          "
        >
          {{ tab.label }}
        </button>
      </div>
      <button class="clear" type="button" @click="clearAll">전체 해제</button>
    </div>

    <div v-if="activeCategory !== 'all'" class="chips-row">
      <button
          v-for="item in currentTags"
          :key="`${item.category}-${item.tag}`"
          type="button"
          :class="['chip', { 'chip--active': isSelected(item.category, item.tag) }]"
          @click="toggle(item.category, item.tag)"
      >
        {{ item.tag }}
      </button>
      <p v-if="currentTags.length === 0" class="empty">태그 없음</p>
    </div>
  </div>
</template>

<style scoped>
.filter {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 6px 0;
}

.top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.clear {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-muted);
  padding: 8px 12px;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 700;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.clear:hover {
  color: var(--text-strong);
  border-color: var(--primary-color);
  background: var(--hover-bg);
}

.tabs {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
  white-space: nowrap;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tab {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-strong);
  padding: 10px 16px;
  min-height: 40px;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 700;
  white-space: nowrap;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.tab--active {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: var(--hover-bg);
}

.product-tabs {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
  white-space: nowrap;
}

.tab--product {
  min-height: 34px;
  padding: 8px 14px;
}

.chips-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-height: 34px;
  padding-top: 6px;
}

.chips-row--empty {
  min-height: 32px;
}

.chip {
  border: 1px solid var(--border-color);
  background: var(--surface);
  color: var(--text-muted);
  padding: 8px 12px;
  min-height: 34px;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 700;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.chip:hover {
  color: var(--text-strong);
  border-color: var(--accent-color);
  background: var(--hover-bg);
}

.chip--active {
  background: var(--hover-bg);
  color: var(--primary-color);
  border-color: var(--primary-color);
}

.empty {
  margin: 0;
  color: var(--text-muted);
  font-weight: 600;
}

.hint {
  margin: 0;
  color: var(--text-soft);
  font-weight: 700;
}

@media (max-width: 600px) {
  .top-row {
    gap: 10px;
  }
}
</style>
