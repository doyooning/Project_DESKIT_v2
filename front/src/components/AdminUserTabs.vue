<script setup lang="ts">
type AdminUserTab = 'members' | 'companies'

const props = defineProps<{
  modelValue: AdminUserTab
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: AdminUserTab): void
}>()

const tabs: Array<{ key: AdminUserTab; label: string }> = [
  { key: 'members', label: '회원 조회' },
  { key: 'companies', label: '사업자 조회' },
]

const setTab = (tab: AdminUserTab) => {
  if (props.modelValue === tab) return
  emit('update:modelValue', tab)
}
</script>

<template>
  <header class="user-tabs">
    <div class="user-tabs__spacer" aria-hidden="true"></div>

    <div class="user-tabs__list" role="tablist" aria-label="회원관리 소분류">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="user-tab"
        :class="{ 'user-tab--active': modelValue === tab.key }"
        @click="setTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div class="user-tabs__spacer" aria-hidden="true"></div>
  </header>
</template>

<style scoped>
.user-tabs {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.user-tabs__spacer {
  min-height: 1px;
}

.user-tabs__list {
  display: inline-flex;
  gap: 10px;
  justify-content: center;
}

.user-tab {
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.user-tab:hover {
  border-color: var(--primary-color);
  box-shadow: 0 8px 18px rgba(var(--primary-rgb), 0.12);
  transform: translateY(-1px);
}

.user-tab--active {
  background: var(--surface-weak);
  border-color: var(--primary-color);
}
</style>
