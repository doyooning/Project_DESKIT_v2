<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    eyebrow?: string
    showHeader?: boolean
  }>(),
  {
    showHeader: true,
  },
)
</script>

<template>
  <header v-if="props.showHeader !== false" class="header-row">
    <div class="header-left">
      <p v-if="props.eyebrow" class="eyebrow">{{ props.eyebrow }}</p>
      <h1 v-if="props.title" class="title">{{ props.title }}</h1>
      <p v-if="props.subtitle" class="subtitle">{{ props.subtitle }}</p>
    </div>
    <div class="header-right">
      <slot name="headerRight" />
    </div>
  </header>
</template>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.eyebrow {
  margin: 0;
  color: var(--text-soft);
  font-weight: 800;
  letter-spacing: 0.04em;
  font-size: 0.9rem;
}

.title {
  margin: 0;
  font-size: 1.9rem;
  font-weight: 800;
  letter-spacing: -0.3px;
  color: var(--text-strong);
}

.subtitle {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.4;
}

.header-right {
  margin-left: auto;
}

@media (max-width: 640px) {
  .title {
    font-size: 1.6rem;
  }
}
</style>
