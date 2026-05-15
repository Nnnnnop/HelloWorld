<template>
  <PostListPanel
    title="SIG News"
    :rows="rows"
    :loading="loading"
    :admin-editable="authStore.isAdmin"
    detail-mode="route"
    detail-route-base="/news"
    :show-meta="true"
    :show-author="false"
    :show-published-at="true"
    :preview-lines="2"
    :prefer-content-preview="true"
    @manage="router.push('/admin/posts/news')"
  />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PostListPanel from '../components/PostListPanel.vue'
import { listPosts } from '../api/postApi'
import { useAuthStore } from '../stores/auth'

const rows = ref([])
const loading = ref(false)
const router = useRouter()
const authStore = useAuthStore()

onMounted(loadNews)

async function loadNews() {
  loading.value = true
  try {
    rows.value = await listPosts({ type: 'NEWS', limit: 50 })
  } catch (error) {
    ElMessage.error(error?.message || 'Failed to load news')
  } finally {
    loading.value = false
  }
}
</script>
