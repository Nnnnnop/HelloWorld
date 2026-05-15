<template>
  <el-card class="card">
    <template #header>User Registration</template>
    <el-form label-width="110px" @submit.prevent>
      <el-form-item label="Username">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="Email">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item label="Password">
        <el-input v-model="form.password" show-password type="password" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleRegister">Register</el-button>
        <el-button link @click="router.push('/login')">Back to Login</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const form = reactive({
  username: '',
  email: '',
  password: ''
})

async function handleRegister() {
  if (!form.username || !form.email || !form.password) {
    ElMessage.warning('Please complete all registration fields.')
    return
  }
  loading.value = true
  try {
    await authStore.localRegister(form)
    ElMessage.success('Registration successful. Please wait for admin approval.')
    router.push('/pending')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.card {
  max-width: 560px;
  margin: 30px auto;
}
</style>
