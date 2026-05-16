<template>
  <div class="join-page">
    <el-page-header @back="goBack" title="Join Us" />

    <el-alert
      class="intro"
      type="info"
      :closable="false"
      title="Interest groups"
      description="Browse SIG teams that are recruiting and submit a short message. Admins review applications here; when approved you are added to the group and guest accounts may be promoted to member."
    />

    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="card-head">
              <span>Open groups</span>
              <el-button :loading="loadingGroups" size="small" @click="loadGroups">Refresh</el-button>
            </div>
          </template>

          <el-empty v-if="!loadingGroups && groups.length === 0" description="No groups are recruiting right now." />

          <el-skeleton v-else-if="loadingGroups" animated :rows="4" />

          <div v-else class="group-list">
            <el-card
              v-for="g in groups"
              :key="g.id"
              shadow="hover"
              class="group-card"
            >
              <div class="group-title">{{ g.name }}</div>
              <p class="group-desc">{{ g.description || 'No description.' }}</p>
              <el-button type="primary" size="small" :disabled="!canApply" @click="openApply(g)">
                Apply
              </el-button>
            </el-card>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="card-head">
              <span>My memberships</span>
              <el-button :loading="loadingMem" size="small" @click="loadMemberships">Refresh</el-button>
            </div>
          </template>
          <el-empty v-if="!loadingMem && memberships.length === 0" description="You are not in any group yet." />
          <el-skeleton v-else-if="loadingMem" animated :rows="3" />
          <ul v-else class="membership-list">
            <li v-for="m in memberships" :key="m.id">
              <strong>{{ m.group.name }}</strong>
              <span class="muted">since {{ formatDate(m.joinedAt) }}</span>
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="block-card apps-card">
      <template #header>
        <div class="card-head">
          <span>My applications</span>
          <el-button :loading="loadingApps" size="small" @click="loadApplications">Refresh</el-button>
        </div>
      </template>

      <el-table :data="applications" border stripe empty-text="No applications yet.">
        <el-table-column prop="interestGroup.name" label="Group" min-width="160" />
        <el-table-column prop="status" label="Status" width="120" />
        <el-table-column prop="message" label="Message" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="Submitted" width="180" />
        <el-table-column label="Actions" width="140">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              size="small"
              type="warning"
              link
              @click="withdraw(row)"
            >
              Withdraw
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="applyVisible" title="Apply to group" width="520px" @closed="resetApply">
      <p v-if="applyTarget" class="apply-group-name">{{ applyTarget.name }}</p>
      <el-input
        v-model="applyMessage"
        type="textarea"
        :rows="4"
        maxlength="2000"
        show-word-limit
        placeholder="Tell us why you want to join (optional)"
      />
      <template #footer>
        <el-button @click="applyVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="applySubmitting" @click="confirmApply">Submit</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { csrf } from '../api/authApi'
import {
  listMyGroupMemberships,
  listMyJoinApplications,
  listRecruitingGroups,
  submitJoinApplication,
  withdrawJoinApplication
} from '../api/joinApi'

const router = useRouter()
const authStore = useAuthStore()

const loadingGroups = ref(false)
const loadingApps = ref(false)
const loadingMem = ref(false)
const groups = ref([])
const applications = ref([])
const memberships = ref([])

const applyVisible = ref(false)
const applyTarget = ref(null)
const applyMessage = ref('')
const applySubmitting = ref(false)

const canApply = computed(() => authStore.isLoggedIn && authStore.user?.status === 'APPROVED')

onMounted(async () => {
  await Promise.all([loadGroups(), loadApplications(), loadMemberships()])
})

function goBack() {
  router.push('/home/internal_portal/home')
}

async function loadGroups() {
  loadingGroups.value = true
  try {
    groups.value = await listRecruitingGroups()
  } catch (e) {
    ElMessage.error(e?.message || 'Failed to load groups')
  } finally {
    loadingGroups.value = false
  }
}

async function loadApplications() {
  loadingApps.value = true
  try {
    applications.value = await listMyJoinApplications()
  } catch (e) {
    ElMessage.error(e?.message || 'Failed to load applications')
  } finally {
    loadingApps.value = false
  }
}

async function loadMemberships() {
  loadingMem.value = true
  try {
    memberships.value = await listMyGroupMemberships()
  } catch (e) {
    ElMessage.error(e?.message || 'Failed to load memberships')
  } finally {
    loadingMem.value = false
  }
}

function openApply(g) {
  if (!canApply.value) {
    ElMessage.warning('Your account must be approved before you can apply.')
    return
  }
  applyTarget.value = g
  applyMessage.value = ''
  applyVisible.value = true
}

function resetApply() {
  applyTarget.value = null
  applyMessage.value = ''
}

async function confirmApply() {
  if (!applyTarget.value) return
  applySubmitting.value = true
  try {
    await csrf().catch(() => {})
    await submitJoinApplication({
      groupId: applyTarget.value.id,
      message: applyMessage.value.trim() || undefined
    })
    ElMessage.success('Application submitted')
    applyVisible.value = false
    await loadApplications()
  } catch (e) {
    const msg = e?.message || 'Submit failed'
    ElMessage.error(msg === 'Failed to fetch' ? 'Network error: check that the API is reachable (same host/port as this site).' : msg)
  } finally {
    applySubmitting.value = false
  }
}

async function withdraw(row) {
  try {
    await ElMessageBox.confirm('Withdraw this application?', 'Confirm', { type: 'warning' })
    await csrf().catch(() => {})
    await withdrawJoinApplication(row.id)
    ElMessage.success('Withdrawn')
    await loadApplications()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || 'Withdraw failed')
    }
  }
}

function formatDate(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.join-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 16px 24px 48px;
}

.intro {
  margin: 16px 0 20px;
}

.block-card {
  margin-bottom: 16px;
}

.apps-card {
  margin-top: 8px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.group-card {
  border-radius: 10px;
}

.group-title {
  font-weight: 600;
  font-size: 1.05rem;
  margin-bottom: 8px;
}

.group-desc {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  font-size: 0.92rem;
}

.membership-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.membership-list li {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 0.85rem;
}

.apply-group-name {
  font-weight: 600;
  margin-bottom: 12px;
}
</style>
