<template>
  <el-card class="card">
    <template #header>
      <div class="head">
        <span>Member Approval</span>
        <el-button :loading="loading" @click="loadPending">Refresh</el-button>
      </div>
    </template>

    <el-table :data="rows" border stripe empty-text="No users pending approval.">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="username" label="Username" />
      <el-table-column prop="email" label="Email" />
      <el-table-column prop="provider" label="Provider" width="130" />
      <el-table-column prop="createdAt" label="Registered At" min-width="180" />
      <el-table-column label="Actions" width="220">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="approve(row, true)">Approve</el-button>
          <el-button size="small" type="danger" @click="approve(row, false)">Reject</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { approveUser, listPendingUsers } from '../api/authApi'

const loading = ref(false)
const rows = ref([])

onMounted(loadPending)

async function loadPending() {
  loading.value = true
  try {
    rows.value = await listPendingUsers()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function approve(row, approved) {
  try {
    await approveUser({ userId: row.id, approved })
    ElMessage.success(approved ? 'Approved' : 'Rejected')
    await loadPending()
  } catch (error) {
    ElMessage.error(error.message)
  }
}
</script>

<style scoped>
.card {
  width: 100%;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
