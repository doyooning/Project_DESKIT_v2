<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import SortTabs, { type SortOption } from '../components/SortTabs.vue';
import TagChipsFilter from '../components/TagChipsFilter.vue';
import ProductListCard from '../components/ProductListCard.vue';
import PageContainer from '../components/PageContainer.vue';
import PageHeader from '../components/PageHeader.vue';
import { listProducts } from '../api/products';
import { type DbProduct } from '../lib/products-data';
import { mapProducts } from '../lib/products-mapper';

const sortBy = ref<SortOption>('ranking');
const tagKeys = ['space', 'tone', 'situation', 'mood'] as const;
type TagKey = (typeof tagKeys)[number];
type TagSelection = Record<TagKey, string[]>;

const emptySelection: TagSelection = {
  space: [],
  tone: [],
  situation: [],
  mood: [],
};

const selectedTags = ref<Partial<TagSelection>>({ ...emptySelection });

const normalizeSelection = (input: Partial<TagSelection>) =>
  tagKeys.reduce(
    (acc, key) => {
      const raw = input?.[key]
      return {
        ...acc,
        [key]: Array.isArray(raw) ? raw : [],
      }
    },
    { ...emptySelection }
  )

const tagsModel = computed({
  get: () => normalizeSelection(selectedTags.value),
  set: (value: Partial<TagSelection>) => {
    selectedTags.value = normalizeSelection(value)
  },
})

const products = ref<DbProduct[]>([]);

const baseProducts = computed(() => {
  const mapped = mapProducts(products.value)
  return mapped
});

const loadProducts = async () => {
  try {
    products.value = await listProducts();
  } catch (error) {
    console.error('Failed to load products.', error);
  }
};

onMounted(() => {
  loadProducts();
});

const availableTags = computed<TagSelection>(() => {
  const tagSets: Record<TagKey, Set<string>> = {
    space: new Set(),
    tone: new Set(),
    situation: new Set(),
    mood: new Set(),
  };
  baseProducts.value.forEach((product) => {
    tagKeys.forEach((key) => {
      const tags = product.tags?.[key] ?? [];
      tags.forEach((tag) => tagSets[key].add(tag));
    });
  });
  return tagKeys.reduce(
    (acc, key) => ({
      ...acc,
      [key]: Array.from(tagSets[key]).sort((a, b) =>
        a.localeCompare(b, 'ko-KR')
      ),
    }),
    { ...emptySelection }
  );
});

const filteredProducts = computed(() => {
  let result = baseProducts.value;
  const normalizedSelection = tagsModel.value
  result = result.filter((product) =>
    tagKeys.every((key) => {
      const selections = normalizedSelection[key];
      if (!selections.length) return true;
      const productTags = product.tags?.[key] ?? [];
      return selections.some((tag) => productTags.includes(tag));
    })
  );

  const sorted = [...result];
  switch (sortBy.value) {
    case 'ranking':
      sorted.sort((a, b) => b.popularity - a.popularity);
      break;
    case 'price-low':
      sorted.sort((a, b) => a.price - b.price);
      break;
    case 'price-high':
      sorted.sort((a, b) => b.price - a.price);
      break;
    case 'sales':
      sorted.sort((a, b) => b.salesVolume - a.salesVolume);
      break;
    case 'latest':
      sorted.sort((a, b) => b.order - a.order);
      break;
  }
  return sorted;
});
</script>

<template>
  <PageContainer>
    <PageHeader
      eyebrow="DESKIT PRODUCT"
      title="상품"
      subtitle="당신의 책상을 완성할 아이템을 찾아보세요"
    >
      <template #headerRight>
        <span class="count">({{ filteredProducts.length }}개)</span>
      </template>
    </PageHeader>

    <section class="filters">
      <TagChipsFilter
        v-model="tagsModel"
        :available-tags="availableTags"
      />
    </section>

    <section class="sort-row">
      <SortTabs v-model="sortBy" />
    </section>

    <section>
      <div v-if="filteredProducts.length === 0" class="empty">
        <p>조건에 맞는 상품이 없습니다.</p>
        <p class="empty-sub">필터를 변경하거나 다른 카테고리를 선택해보세요.</p>
      </div>
      <div v-else class="grid">
        <ProductListCard
          v-for="product in filteredProducts"
          :key="product.id"
          :id="product.id"
          :name="product.name"
          :image-url="product.imageUrl"
          :price="product.price"
          :original-price="product.originalPrice"
          :description="product.description"
        />
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.count {
  color: var(--text-soft);
  font-size: 1rem;
  font-weight: 700;
  margin-left: 6px;
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: var(--surface);
  border: 1px solid var(--border-color);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.filter-label {
  color: var(--text-muted);
  font-weight: 800;
  letter-spacing: 0.01em;
}

.sort-row {
  display: flex;
  justify-content: flex-start;
  margin: 10px 0 16px;
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
}

.empty {
  padding: 24px;
  border: 1px dashed var(--border-color);
  border-radius: 14px;
  text-align: center;
  color: var(--text-muted);
  background: #fff;
}

.empty-sub {
  margin: 6px 0 0;
  color: var(--text-soft);
}

@media (min-width: 540px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 900px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1200px) {
  .grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
