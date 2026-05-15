<template>
  <div class="admin-join">
    <el-page-header @back="goBack" title="Join Us (Admin)" />

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="Interest groups" name="groups">
        <el-card shadow="never">
          <template #header>
            <div class="row">
              <span>Manage recruiting groups</span>
              <div class="row-actions">
                <el-button type="primary" size="small" @click="openCreate">New group</el-button>
                <el-button size="small" :loading="loadingGroups" @click="loadGroups">Refresh</el-button>
              </div>
            </div>
          </template>

          <el-table :data="groups" border stripe empty-text="No groups.">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="name" label="Name" min-width="160" />
            <el-table-column prop="active" label="Active" width="88" />
            <el-table-column prop="recruiting" label="Recruiting" width="100" />
            <el-table-column prop="sortOrder" label="Sort" width="72" />
            <el-table-column label="Actions" width="200">
              <template #default="{ row }">
                <el-button size="small" link @click="openEdit(row)">Edit</el-button>
                <el-button size="small" link type="danger" @click="deactivate(row)">Deactivate</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="Applications" name="apps">
        <el-card shadow="never">
          <template #header>
            <div class="row">
              <el-select v-model="statusFilter" style="width: 200px" @change="loadApps">
                <el-option label="Pending" value="PENDING" />
                <el-option label="Approved" value="APPROVED" />
                <el-option label="Rejected" value="REJECTED" />
                <el-option label="Withdrawn" value="WITHDRAWN" />
                <el-option label="All" value="ALL" />
              </el-select>
              <el-button size="small" :loading="loadingApps" @click="loadApps">Refresh</el-button>
            </div>
          </template>

          <el-table :data="applications" border stripe empty-text="No applications.">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="applicantUsername" label="User" width="140" />
            <el-table-column prop="interestGroup.name" label="Group" min-width="160" />
            <el-table-column prop="status" label="Status" width="110" />
            <el-table-column prop="message" label="Message" min-width="160" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="Submitted" width="170" />
            <el-table-column label="Review" width="200">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button size="small" type="success" @click="review(row, true)">Approve</el-button>
                  <el-button size="small" type="danger" @click="review(row, false)">Reject</el-button>
                </template>
                <span v-else class="muted">{{ row.reviewedBy || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="groupDlg" :title="editId ? 'Edit group' : 'New group'" width="520px" @closed="resetGroupForm">
      <el-form label-position="top">
        <el-form-item label="Name" required>
          <el-input v-model="groupForm.name" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="groupForm.description" type="textarea" :rows="4" maxlength="4000" />
        </el-form-item>
        <el-form-item label="Sort order">
          <el-input-number v-model="groupForm.sortOrder" :min="0" :max="999999" />
        </el-form-item>
        <el-form-item label="Active">
          <el-switch v-model="groupForm.active" />
        </el-form-item>
        <el-form-item label="Recruiting">
          <el-switch v-model="groupForm.recruiting" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDlg = false">Cancel</el-button>
        <el-button type="primary" :loading="groupSaving" @click="saveGroup">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { csrf } from '../api/authApi'
import { useAuthStore } from '../stores/auth'
import {
  adminCreateGroup,
  adminDeactivateGroup,
  adminListAllGroups,
  adminListJoinApplications,
  adminReviewJoinApplication,
  adminUpdateGroup
} from '../api/joinApi'

const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref('groups')
const loadingGroups = ref(false)
const loadingApps = ref(false)
const groups = ref([])
const applications = ref([])
const statusFilter = ref('PENDING')

const groupDlg = ref(false)
const editId = ref(null)
const groupSaving = ref(false)
const groupForm = reactive({
  name: '',
  description: '',
  sortOrder: 0,
  active: true,
  recruiting: true
})

onMounted(() => {
  loadGroups()
})

watch(
  activeTab,
  (name) => {
    if (name === 'apps') {
      loadApps()
    }
  },
  { immediate: true }
)

function goBack() {
  router.push('/admin/approval')
}

async function loadGroups() {
  loadingGroups.value = true
  try {
    if (!authStore.loaded) {
      await authStore.fetchMe()
    }
    await csrf().catch(() => {})
    groups.value = await adminListAllGroups()
  } catch (e) {
    ElMessage.error(e?.message || 'Failed to load groups')
  } finally {
    loadingGroups.value = false
  }
}

async function loadApps() {
  loadingApps.value = true
  try {
    if (!authStore.loaded) {
      await authStore.fetchMe()
    }
    await csrf().catch(() => {})
    applications.value = await adminListJoinApplications(statusFilter.value)
  } catch (e) {
    ElMessage.error(e?.message || 'Failed to load applications')
  } finally {
    loadingApps.value = false
  }
}

function openCreate() {
  editId.value = null
  groupForm.name = ''
  groupForm.description = ''
  groupForm.sortOrder = 0
  groupForm.active = true
  groupForm.recruiting = true
  groupDlg.value = true
}

function openEdit(row) {
  editId.value = row.id
  groupForm.name = row.name
  groupForm.description = row.description || ''
  groupForm.sortOrder = row.sortOrder
  groupForm.active = row.active
  groupForm.recruiting = row.recruiting
  groupDlg.value = true
}

function resetGroupForm() {
  editId.value = null
}

async function saveGroup() {
  if (!groupForm.name.trim()) {
    ElMessage.warning('Name is required')
    return
  }
  const payload = {
    name: groupForm.name.trim(),
    description: groupForm.description.trim(),
    sortOrder: groupForm.sortOrder,
    active: groupForm.active,
    recruiting: groupForm.recruiting
  }
  groupSaving.value = true
  try {
    if (editId.value) {
      await adminUpdateGroup(editId.value, payload)
      ElMessage.success('Group updated')
    } else {
      await adminCreateGroup(payload)
      ElMessage.success('Group created')
    }
    groupDlg.value = false
    await loadGroups()
  } catch (e) {
    ElMessage.error(e?.message || 'Save failed')
  } finally {
    groupSaving.value = false
  }
}

async function deactivate(row) {
  try {
    await ElMessageBox.confirm(`Deactivate "${row.name}"? It will stop accepting applications.`, 'Confirm', {
      type: 'warning'
    })
    await adminDeactivateGroup(row.id)
    ElMessage.success('Deactivated')
    await loadGroups()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || 'Failed')
    }
  }
}

async function review(row, approved) {
  try {
    await adminReviewJoinApplication(row.id, approved)
    ElMessage.success(approved ? 'Approved' : 'Rejected')
    await loadApps()
  } catch (e) {
    ElMessage.error(e?.message || 'Review failed')
  }
}
</script>

<style scoped>
.admin-join {
  max-width: 1100px;
  margin: 0 auto;
  padding: 16px 24px 48px;
}

.tabs {
  margin-top: 16px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.row-actions {
  display: flex;
  gap: 8px;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 0.85rem;
}
</style>
