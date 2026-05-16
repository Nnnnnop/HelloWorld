<template>
  <PostListPanel
    title="SIG Announcements"
    :rows="rows"
    :loading="loading"
    :admin-editable="authStore.isAdmin"
    @manage="router.push('/admin/posts/announcements')"
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

onMounted(loadAnnouncements)

async function loadAnnouncements() {
  loading.value = true
  try {
    rows.value = await listPosts({ type: 'ANNOUNCEMENT', limit: 50 })
  } catch (error) {
    ElMessage.error(error?.message || 'Failed to load announcements')
  } finally {
    loading.value = false
  }
}
</script>
