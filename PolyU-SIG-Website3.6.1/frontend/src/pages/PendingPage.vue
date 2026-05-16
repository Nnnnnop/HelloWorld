<template>
  <el-result icon="warning" title="Approval Pending" sub-title="Your account is registered. Please wait for admin approval before accessing restricted resources.">
    <template #extra>
      <el-button type="primary" @click="refresh">Refresh Status</el-button>
      <el-button @click="logoutNow">Logout</el-button>
    </template>
  </el-result>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function refresh() {
  await authStore.fetchMe()
  if (authStore.isApproved || authStore.isAdmin) {
    ElMessage.success('Your account has been approved.')
    router.push('/home')
  } else {
    ElMessage.info('Still waiting for approval.')
  }
}

async function logoutNow() {
  await authStore.doLogout()
  router.push('/login')
}
</script>
