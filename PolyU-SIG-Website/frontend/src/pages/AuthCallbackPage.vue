<template>
  <el-result icon="success" title="Processing OAuth2 Login" sub-title="Syncing account status, please wait..." />
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

onMounted(async () => {
  await authStore.fetchMe()
  if (!authStore.isLoggedIn) {
    router.replace('/login')
    return
  }
  if (!authStore.isApproved && !authStore.isAdmin) {
    router.replace('/pending')
  } else if (authStore.isAdmin) {
    router.replace('/home/internal_portal/home')
  } else {
    router.replace('/search')
  }
})
</script>
