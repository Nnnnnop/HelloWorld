<template>
  <div></div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  // Exit internal portal mode
  window.dispatchEvent(new CustomEvent('exitInternalPortal'))
  router.replace('/')
})
</script>