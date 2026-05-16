<template>
  <el-card class="card">
    <template #header>
      <div class="head">
        <span>Audit Logs</span>
        <el-button :loading="loading" @click="loadLogs">Refresh</el-button>
      </div>
    </template>
    <el-table :data="rows" border stripe empty-text="No logs yet.">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="action" label="Action" width="180" />
      <el-table-column prop="actor" label="Actor" width="140" />
      <el-table-column prop="message" label="Description" />
      <el-table-column prop="createdAt" label="Time" min-width="180" :formatter="formatAuditTime" />
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAuditLogs } from '../api/adminApi'

const loading = ref(false)
const rows = ref([])

const formatAuditTime = (row, column, cellValue) => {
  if (!cellValue) return '-'
  const value = String(cellValue).trim()
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/) 
  if (!match) return value
  const [, year, month, day, hour, minute, second] = match
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

onMounted(loadLogs)

async function loadLogs() {
  loading.value = true
  try {
    rows.value = await listAuditLogs()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
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
