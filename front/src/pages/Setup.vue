<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listSetups } from '../api/setups';
import { type SetupWithProducts } from '../lib/setups-data';
import SetupCard from '../components/SetupCard.vue';
import PageContainer from '../components/PageContainer.vue';
import PageHeader from '../components/PageHeader.vue';

const setups = ref<SetupWithProducts[]>([]);

const loadSetups = async () => {
  try {
    setups.value = await listSetups();
  } catch (error) {
    console.error('Failed to load setups.', error);
  }
};

onMounted(() => {
  loadSetups();
});
</script>

<template>
  <PageContainer>
    <PageHeader
      title="셋업"
      eyebrow="DESKIT SETUP"
      subtitle="실제 사용된 아이템으로 완성된 셋업을 확인해보세요"
    />

    <section class="grid">
      <SetupCard
        v-for="setup in setups"
        :key="setup.setup_id"
        :id="String(setup.setup_id)"
        :title="setup.title"
        :description="setup.short_desc"
        :image-url="setup.imageUrl"
      />
    </section>
  </PageContainer>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}
</style>
