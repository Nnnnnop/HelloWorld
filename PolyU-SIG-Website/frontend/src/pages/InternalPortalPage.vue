<template>
  <div></div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

onMounted(async () => {
  if (!authStore.loaded) {
    await authStore.fetchMe()
  }
  if (!authStore.isLoggedIn) {
    sessionStorage.setItem('pendingInternalPortal', 'true')
    router.replace('/login')
  } else {
    // Set internal portal mode somehow
    // Since we can't access the ref from here, perhaps use a global event
    window.dispatchEvent(new CustomEvent('enterInternalPortal'))
    router.replace('/home/internal_portal/home')
  }
})
</script>