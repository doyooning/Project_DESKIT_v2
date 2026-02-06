<script setup lang="ts">
const props = defineProps<{
  name: string
  shortDesc: string
  price: number
  stock: number
  disableName?: boolean
  disableShortDesc?: boolean
  disablePrice?: boolean
  disableStock?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:name', value: string): void
  (e: 'update:shortDesc', value: string): void
  (e: 'update:price', value: number): void
  (e: 'update:stock', value: number): void
}>()
</script>

<template>
  <label class="field">
    <span class="field__label">상품명</span>
    <input
      class="basic-input"
      :value="props.name"
      type="text"
      placeholder="예: 모던 데스크 매트"
      :disabled="props.disableName"
      @input="emit('update:name', ($event.target as HTMLInputElement).value)"
    />
  </label>
  <label class="field">
    <span class="field__label">한 줄 소개</span>
    <input
      class="basic-input"
      :value="props.shortDesc"
      type="text"
      placeholder="예: 감성적인 데스크테리어"
      :disabled="props.disableShortDesc"
      @input="emit('update:shortDesc', ($event.target as HTMLInputElement).value)"
    />
  </label>
  <div class="field-grid">
    <slot name="extra-fields" />
    <label class="field">
      <span class="field__label">판매가</span>
      <input
        class="basic-input"
        :value="props.price"
        type="number"
        min="0"
        :disabled="props.disablePrice"
        @input="emit('update:price', Number(($event.target as HTMLInputElement).value))"
      />
    </label>
    <label class="field">
      <span class="field__label">재고 수량</span>
      <input
        class="basic-input"
        :value="props.stock"
        type="number"
        min="0"
        :disabled="props.disableStock"
        @input="emit('update:stock', Number(($event.target as HTMLInputElement).value))"
      />
    </label>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field__label {
  font-weight: 800;
  color: var(--text-strong);
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.basic-input {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 10px 12px;
  font-weight: 700;
  color: var(--text-strong);
  background: var(--surface);
}
</style>
