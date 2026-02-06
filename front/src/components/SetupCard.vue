<script setup lang="ts">
const props = defineProps<{
  id: string
  title: string
  description: string
  imageUrl: string
}>()
</script>

<template>
  <RouterLink :to="`/setups/${props.id}`" class="card-link">
    <article class="card">
      <div class="media">
        <img :src="props.imageUrl" :alt="props.title" />
        <div class="overlay">
          <div class="overlay-content">
            <h3>{{ props.title }}</h3>
            <p class="desc">{{ props.description }}</p>
          </div>
        </div>
      </div>
    </article>
  </RouterLink>
</template>

<style scoped>
.card-link {
  display: block;
  text-decoration: none;
  color: inherit;
}

.card {
  display: block;
  background: var(--surface);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.card:hover {
  transform: translateY(-3px);
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.1);
}

.media {
  position: relative;
  aspect-ratio: 4 / 3;
  width: 100%;
  overflow: hidden;
  background: var(--surface-weak);
}

.media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.35s ease;
}

.card:hover .media img {
  transform: scale(1.03);
}

.overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16px 16px 14px;
  height: 50%;
  background: linear-gradient(
    to top,
    rgba(15, 23, 42, 0.72) 0%,
    rgba(15, 23, 42, 0.48) 45%,
    rgba(15, 23, 42, 0) 100%
  );
  display: flex;
  align-items: flex-end;
}

.overlay-content {
  width: 100%;
  padding: 14px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  color: #ffffff;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.desc {
  margin: 4px 0 0;
  color: rgba(255, 255, 255, 0.88);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
