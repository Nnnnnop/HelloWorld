<template>
  <el-card class="card">
    <template #header>User Login</template>
    <el-form label-width="110px" @submit.prevent>
      <el-form-item label="Username">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="Password">
        <el-input v-model="form.password" show-password type="password" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleLogin">Login</el-button>
        <el-button @click="handleOauthLogin">OAuth2 Login</el-button>
      </el-form-item>
      <el-form-item>
        <el-button link @click="router.push('/register')">No account yet? Register</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const form = reactive({
  username: '',
  password: ''
})

onMounted(() => {
  if (route.query.reason === 'timeout') {
    ElMessage.warning('Your session has timed out. Please log in again.')
  }
})

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('Please enter username and password.')
    return
  }
  loading.value = true
  try {
    await authStore.localLogin(form)
    ElMessage.success('Login successful')

    const nextPath = typeof route.query.next === 'string' ? route.query.next : ''
    const mode = typeof route.query.mode === 'string' ? route.query.mode : ''
    const pendingInternal = sessionStorage.getItem('pendingInternalPortal')
    
    if (nextPath.startsWith('/')) {
      if (mode === 'replace') {
        await router.replace(nextPath)
      } else {
        await router.push(nextPath)
      }
      return
    }

    if (pendingInternal) {
      sessionStorage.removeItem('pendingInternalPortal')
      router.push('/home/internal_portal/home')
      return
    }

    if (!authStore.isApproved && !authStore.isAdmin) {
      router.push('/pending')
    } else {
      router.push('/home')
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function handleOauthLogin() {
  try {
    await authStore.oauthLogin()
  } catch (error) {
    ElMessage.error(error.message)
  }
}
</script>

<style scoped>
.card {
  max-width: 560px;
  margin: 30px auto;
}
</style>
