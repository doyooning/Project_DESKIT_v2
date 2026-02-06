<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type TagOption = {
  id: number
  name: string
  categoryId: number | null
  categoryName: string | null
}

type TagGroup = {
  key: string
  title: string
  items: TagOption[]
}

const props = defineProps<{
  tags: string[]
}>()

const emit = defineEmits<{
  (e: 'update:tags', value: string[]): void
}>()

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const buildAuthHeaders = (): Record<string, string> => {
  const access = localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
  if (!access) return {}
  return { Authorization: `Bearer ${access}` }
}

const tagOptions = ref<TagOption[]>([])
const loadError = ref('')
const isLoading = ref(false)

const resolveCategoryTitle = (option: TagOption) => {
  if (option.categoryName) return option.categoryName
  if (option.categoryId != null) return `카테고리 #${option.categoryId}`
  return '기타'
}

const buildGroupKey = (option: TagOption) =>
  `${option.categoryId ?? 'none'}:${option.categoryName ?? ''}`

const groupedTags = computed<TagGroup[]>(() => {
  const groups: TagGroup[] = []
  const groupIndex = new Map<string, TagGroup>()
  tagOptions.value.forEach((option) => {
    const key = buildGroupKey(option)
    const group = groupIndex.get(key)
    if (group) {
      group.items.push(option)
      return
    }
    const nextGroup: TagGroup = {
      key,
      title: resolveCategoryTitle(option),
      items: [option],
    }
    groupIndex.set(key, nextGroup)
    groups.push(nextGroup)
  })
  return groups
})

const isSelected = (tagName: string) => props.tags.includes(tagName)

const toggleTag = (tagName: string) => {
  if (isSelected(tagName)) {
    emit('update:tags', props.tags.filter((item) => item !== tagName))
    return
  }
  if (props.tags.length >= 10) return
  emit('update:tags', [...props.tags, tagName])
}

const fetchTags = async () => {
  loadError.value = ''
  isLoading.value = true
  try {
    const response = await fetch(`${apiBase}/seller/tags`, {
      method: 'GET',
      headers: {
        ...buildAuthHeaders(),
      },
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error('tag list failed')
    }
    const data = await response.json()
    if (!Array.isArray(data)) {
      tagOptions.value = []
      return
    }
    tagOptions.value = data
      .map((raw) => {
        if (!raw || typeof raw !== 'object') return null
        const record = raw as Record<string, unknown>
        const id = typeof record.tag_id === 'number' ? record.tag_id : null
        const name = typeof record.tag_name === 'string' ? record.tag_name : null
        if (id == null || !name) return null
        return {
          id,
          name,
          categoryId: typeof record.tag_category_id === 'number' ? record.tag_category_id : null,
          categoryName:
            typeof record.tag_category_name === 'string' ? record.tag_category_name : null,
        }
      })
      .filter((option): option is TagOption => Boolean(option))
  } catch {
    loadError.value = '태그 목록을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  fetchTags()
})
</script>

<template>
  <div class="tag-picker">
    <p v-if="loadError" class="tag-error">{{ loadError }}</p>
    <p v-else-if="isLoading" class="tag-empty">태그 목록을 불러오는 중입니다.</p>
    <p v-else-if="groupedTags.length === 0" class="tag-empty">등록된 태그가 없습니다.</p>
    <div v-else class="tag-groups">
      <div v-for="group in groupedTags" :key="group.key" class="tag-group">
        <div class="tag-group__title">{{ group.title }}</div>
        <div class="tag-group__divider"></div>
        <div class="tag-group__items">
          <button
            v-for="tag in group.items"
            :key="tag.id"
            type="button"
            class="tag-chip"
            :class="{ active: isSelected(tag.name) }"
            @click="toggleTag(tag.name)"
          >
            {{ tag.name }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tag-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tag-error,
.tag-empty {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85rem;
}

.tag-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tag-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tag-group__title {
  font-weight: 800;
  color: var(--text-strong);
}

.tag-group__divider {
  height: 1px;
  background: var(--border-color);
}

.tag-group__items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--surface-weak);
  border: 1px solid var(--border-color);
  font-weight: 700;
  font-size: 0.85rem;
  color: var(--text-strong);
  cursor: pointer;
}

.tag-chip.active {
  background: var(--text-strong);
  color: var(--surface);
  border-color: transparent;
}
</style>
