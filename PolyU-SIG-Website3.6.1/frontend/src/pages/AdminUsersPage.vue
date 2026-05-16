<template>
  <el-card class="card">
    <template #header>
      <div class="head">
        <div>
          <div class="title">Site roles</div>
          <div class="subtitle">
            Search by username, select a user, then assign STUDENT / MEMBER / ADMIN. Users who are promoted to administrator
            should sign out and sign in again to refresh admin menus and API access.
          </div>
        </div>
      </div>
    </template>

    <div class="toolbar">
      <el-input
        v-model="searchQuery"
        class="search-input"
        placeholder="Username (at least 2 characters)"
        clearable
        @keyup.enter="runSearch"
      />
      <el-button type="primary" :loading="searchLoading" @click="runSearch">Search</el-button>
    </div>

    <el-table
      :data="results"
      border
      stripe
      empty-text="No users found. Enter at least 2 characters and search."
      highlight-current-row
      class="results-table"
      @current-change="onRowSelect"
    >
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="username" label="Username" min-width="140" />
      <el-table-column prop="email" label="Email" min-width="200" show-overflow-tooltip />
      <el-table-column prop="role" label="Role" width="100" />
      <el-table-column prop="status" label="Status" width="110" />
    </el-table>

    <el-divider />

    <div v-if="selected" class="assign-panel">
      <div class="assign-title">Selected: {{ selected.username }} (ID {{ selected.id }})</div>
      <div class="assign-row">
        <span class="label">New site role</span>
        <el-select v-model="newRole" placeholder="Role" style="width: 200px">
          <el-option label="Student" value="STUDENT" />
          <el-option label="MEMBER" value="MEMBER" />
          <el-option label="ADMIN" value="ADMIN" />
        </el-select>
        <el-button type="primary" :disabled="!newRole || newRole === selected.role" :loading="saving" @click="onApply">
          Apply
        </el-button>
      </div>
      <el-alert type="info" :closable="false" show-icon class="hint" title="Granting ADMIN approves pending accounts. You cannot remove your own admin role or demote the last administrator." />
    </div>
    <el-empty v-else description="Select a row in the table above to change that user's role." />
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchUsersForRoles, updateRole } from '../api/authApi'

const searchQuery = ref('')
const searchLoading = ref(false)
const results = ref([])
const selected = ref(null)
const newRole = ref('')
const saving = ref(false)

watch(selected, (row) => {
  newRole.value = row?.role || ''
})

async function runSearch() {
  const q = searchQuery.value.trim()
  if (q.length < 2) {
    ElMessage.warning('Enter at least 2 characters to search.')
    results.value = []
    selected.value = null
    return
  }
  searchLoading.value = true
  try {
    const list = await searchUsersForRoles(q)
    results.value = Array.isArray(list) ? list : []
    selected.value = null
  } catch (e) {
    ElMessage.error(e?.message || 'Search failed')
    results.value = []
    selected.value = null
  } finally {
    searchLoading.value = false
  }
}

function onRowSelect(row) {
  selected.value = row || null
}

async function onApply() {
  if (!selected.value || !newRole.value) return
  if (newRole.value === selected.value.role) {
    ElMessage.info('Role is unchanged.')
    return
  }
  try {
    await ElMessageBox.confirm(
      `Change ${selected.value.username} from ${selected.value.role} to ${newRole.value}?`,
      'Confirm role change',
      { type: 'warning', confirmButtonText: 'Yes', cancelButtonText: 'Cancel' }
    )
  } catch {
    return
  }
  saving.value = true
  try {
    const userId = selected.value.id
    await updateRole({ userId, role: newRole.value })
    ElMessage.success('Role updated. If this user is signed in, they should sign out and sign in again.')
    await runSearch()
    const updated = results.value.find((r) => r.id === userId)
    selected.value = updated || null
    if (updated) {
      newRole.value = updated.role
    } else {
      newRole.value = ''
    }
  } catch (e) {
    ElMessage.error(e?.message || 'Could not update role')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.card {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.title {
  font-weight: 600;
  font-size: 16px;
}

.subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  max-width: 720px;
  line-height: 1.45;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.search-input {
  flex: 1;
  min-width: 200px;
  max-width: 400px;
}

.results-table {
  width: 100%;
}

.assign-panel {
  margin-top: 8px;
}

.assign-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.assign-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.label {
  color: var(--el-text-color-regular);
}

.hint {
  max-width: 720px;
}
</style>
