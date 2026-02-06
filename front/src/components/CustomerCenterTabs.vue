<script setup lang="ts">
type CustomerCenterTab = 'sellerApproval' | 'inquiries'

const props = defineProps<{
  modelValue: CustomerCenterTab
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: CustomerCenterTab): void
}>()

const tabs: Array<{ key: CustomerCenterTab; label: string }> = [
  { key: 'sellerApproval', label: '판매자 등록 승인' },
  { key: 'inquiries', label: '문의사항 확인' },
]

const setTab = (tab: CustomerCenterTab) => {
  if (props.modelValue === tab) return
  emit('update:modelValue', tab)
}
</script>

<template>
  <header class="live-header">
    <div class="live-header__spacer" aria-hidden="true"></div>

    <div class="live-tabs" role="tablist" aria-label="고객센터 탭">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="live-tab"
        :class="{ 'live-tab--active': modelValue === tab.key }"
        @click="setTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div class="live-header__right"></div>
  </header>
</template>

<style scoped>
.live-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.live-header__spacer {
  min-height: 1px;
}

.live-header__right {
  display: flex;
  justify-content: flex-end;
}

.live-tabs {
  display: inline-flex;
  gap: 10px;
  justify-content: center;
}

.live-tab {
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-strong);
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.live-tab:hover {
  border-color: var(--primary-color);
  box-shadow: 0 8px 18px rgba(var(--primary-rgb), 0.12);
  transform: translateY(-1px);
}

.live-tab--active {
  background: var(--surface-weak);
  border-color: var(--primary-color);
}
</style>
