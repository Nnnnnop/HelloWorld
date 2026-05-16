<template>
  <div class="news-detail-page" v-loading="loading">
    <el-card v-if="post" shadow="never" class="detail-card">
      <h1 class="news-title">{{ post.title }}</h1>
      <div class="news-date">{{ formatNewsDate(post.newsDate || post.createdAt) }}</div>

      <el-carousel
        v-if="newsImages.length"
        :interval="3500"
        arrow="always"
        indicator-position="outside"
        height="420px"
        autoplay
        class="news-carousel"
      >
        <el-carousel-item v-for="(item, index) in newsImages" :key="`${item.id}-${index}`">
          <img :src="item.url" :alt="`news-photo-${index + 1}`" class="carousel-image">
        </el-carousel-item>
      </el-carousel>
      <el-empty v-else description="No photos uploaded for this news." />

      <div class="news-content">{{ post.content }}</div>
    </el-card>

    <el-empty v-else-if="!loading" description="News not found." />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { getPost } from '../api/postApi'
import { filePreviewUrl } from '../api/fileApi'

const route = useRoute()
const loading = ref(false)
const post = ref(null)

const newsImages = computed(() => {
  const ids = Array.isArray(post.value?.newsImageIds) ? post.value.newsImageIds : []
  return ids
    .map((id) => Number(id))
    .filter((id) => Number.isInteger(id) && id > 0)
    .map((id) => ({
      id,
      url: filePreviewUrl(id)
    }))
})

onMounted(loadDetail)
watch(() => route.params.id, loadDetail)

async function loadDetail() {
  loading.value = true
  try {
    const data = await getPost(route.params.id)
    if (String(data?.type || '').toUpperCase() !== 'NEWS') {
      throw new Error('This page only displays news details.')
    }
    post.value = data
  } catch (error) {
    post.value = null
    ElMessage.error(error?.message || 'Failed to load news details')
  } finally {
    loading.value = false
  }
}

function formatNewsDate(value) {
  if (!value) return '-'
  const text = String(value)
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    return text
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return text
  return date.toLocaleDateString()
}
</script>

<style scoped>
.news-detail-page {
  margin: 0 auto;
  max-width: 100%;
  width: 100%;
  overflow-x: hidden;
}

.detail-card {
  border-radius: 10px;
  width: 100%;
  box-sizing: border-box;
}

.news-title {
  margin: 0;
  font-size: 38px;
  color: #8d153a;
  font-weight: 500;
}

.news-date {
  margin-top: 10px;
  margin-bottom: 16px;
  color: #606266;
  font-size: 16px;
}

.news-carousel {
  margin-bottom: 20px;
}

.carousel-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #f5f7fa;
}

.news-content {
  margin-top: 10px;
  white-space: pre-wrap;
  line-height: 1.9;
  color: #303133;
  font-size: 16px;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.news-title {
  overflow-wrap: anywhere;
  word-break: break-word;
}

@media (max-width: 900px) {
  .news-title {
    font-size: 28px;
  }

  .news-detail-page {
    padding: 0 6px;
  }
}
</style>
