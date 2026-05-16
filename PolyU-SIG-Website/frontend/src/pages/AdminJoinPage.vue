<template>
  <div class="admin-join">
    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="Interest groups" name="groups">
        <el-card shadow="never">
          <template #header>
            <div class="row">
              <div class="grow">
                <span>Interest groups</span>
                <el-input
                  v-model="groupListFilter"
                  class="inline-filter"
                  clearable
                  placeholder="Filter by group name..."
                  style="width: 260px; margin-left: 12px"
                  @keyup.enter="loadGroups"
                />
              </div>
              <div class="row-actions">
                <el-button size="small" :loading="loadingGroups" @click="loadGroups">Search / Refresh</el-button>
                <el-button type="primary" size="small" @click="openCreate">New group</el-button>
              </div>
            </div>
          </template>

          <p class="muted tab-tip" style="margin-top: 0">
            Click a row to view members of that SIG group below.
          </p>

          <el-table
            :data="groups"
            row-key="id"
            highlight-current-row
            empty-text="No groups."
            border
            stripe
            @current-change="onGroupsRowChange"
          >
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="name" label="Name" min-width="160" />
            <el-table-column prop="active" label="Active" width="88" />
            <el-table-column prop="recruiting" label="Recruiting" width="100" />
            <el-table-column label="Actions" width="200">
              <template #default="{ row }">
                <el-button size="small" link @click.stop="openEdit(row)">Edit</el-button>
                <el-button size="small" link type="danger" @click.stop="deactivate(row)">Deactivate</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="members-panel">
            <div class="sub">
              {{
                selectedGroupId
                  ? `Members — ${selectedGroupName} (#${selectedGroupId})`
                  : 'Members — select a group above'
              }}
            </div>
            <el-table
              :data="groupMembers"
              border
              stripe
              empty-text="No members in this group, or no group selected."
              v-loading="loadingMembers"
            >
              <el-table-column prop="username" label="Username (student id)" width="180" />
              <el-table-column prop="email" label="Email" min-width="190" />
              <el-table-column prop="role" label="Site role" width="100" />
              <el-table-column label="Tier (members)" width="150">
                <template #default="{ row }">
                  <template v-if="row.role === 'MEMBER'">
                    <el-select
                      :model-value="row.memberSiteTier || 'L1'"
                      size="small"
                      style="width: 120px"
                      :disabled="tierSavingUserId === row.userId"
                      @change="(v) => updateMemberTier(row, v)"
                    >
                      <el-option label="Level 1" value="L1" />
                      <el-option label="Level 2" value="L2" />
                    </el-select>
                  </template>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column prop="siteAccessLevel" label="Site access" width="150" />
              <el-table-column prop="status" label="Account status" width="110" />
              <el-table-column prop="joinedAt" label="Joined group at" width="180">
                <template #default="{ row }">
                  {{ fmtTime(row.joinedAt) }}
                </template>
              </el-table-column>
              <el-table-column label="" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" link type="danger" @click="removeMemberFromGroup(row)">
                    Remove
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
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

          <el-alert type="info" show-icon :closable="false" class="tab-tip">
            Approving adds the applicant to that SIG group. Student accounts become
            <strong>Member</strong>
            at the tier you choose. For users who are already members, the tier you pick updates their site level
            everywhere (Interest groups member list and Find Members use the same value). Level&nbsp;2 can access
            Level&nbsp;2 resources. Administrators stay Admin; tier choice does not apply to them.
          </el-alert>

          <el-table :data="applications" border stripe empty-text="No applications." class="table-below-tip">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="applicantUsername" label="User" width="140" />
            <el-table-column prop="interestGroup.name" label="Group" min-width="160" />
            <el-table-column prop="status" label="Status" width="110" />
            <el-table-column prop="message" label="Message" min-width="140" show-overflow-tooltip />
            <el-table-column label="Tier if approve" min-width="200">
              <template #default="{ row }">
                <el-radio-group
                  v-if="row.status === 'PENDING'"
                  v-model="approveTier[row.id]"
                  size="small"
                  class="tier-radios"
                >
                  <el-radio-button label="L1">Level 1</el-radio-button>
                  <el-radio-button label="L2">Level 2</el-radio-button>
                </el-radio-group>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
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

      <el-tab-pane label="Find Members" name="find-member">
        <el-card shadow="never">
          <template #header>
            <div class="row">
              <span>Find members</span>
            </div>
          </template>
          <div class="stack">
            <div class="row wrap align-center">
              <el-input
                v-model="lookupUsername"
                clearable
                placeholder="NetID (optional if filtering by level)"
                style="width: 280px"
                @keyup.enter="runLookup"
              />
              <el-select
                v-model="lookupTierFilter"
                clearable
                placeholder="Site level"
                style="width: 150px"
              >
                <el-option label="Level 1" value="L1" />
                <el-option label="Level 2" value="L2" />
              </el-select>
              <el-button type="primary" size="small" :loading="loadingLookup" @click="runLookup">Search</el-button>
            </div>
            <el-text v-if="lookupTierOnlyHint" type="info" size="small" class="lookup-tier-hint">
              Listing site members at the selected level (up to 200 NetIDs). Combine with NetID to narrow results.
            </el-text>
            <el-empty v-if="lookupDone && !lookupMatches.length" description="No users found." />
            <div v-for="m in lookupMatches" :key="m.userId" class="lookup-card">
              <div class="lookup-head">
                <strong>{{ m.username }}</strong>
                <el-tag>{{ m.siteAccessLevel }}</el-tag>
                <span class="muted">role {{ m.role }}</span>
                <span class="muted">status {{ m.status }}</span>
              </div>
              <div v-if="m.role === 'MEMBER'" class="lookup-tier-row row wrap align-center">
                <span class="muted">Edit site tier</span>
                <el-select
                  :model-value="m.memberSiteTier || 'L1'"
                  size="small"
                  style="width: 126px"
                  :disabled="tierSavingUserId === m.userId"
                  @change="(v) => updateLookupMemberTier(m, v)"
                >
                  <el-option label="Level 1" value="L1" />
                  <el-option label="Level 2" value="L2" />
                </el-select>
              </div>
              <p class="muted small">Joined site: {{ fmtTime(m.createdAt) }}</p>
              <div class="sub">SIG group memberships</div>
              <el-table :data="m.memberships" border stripe empty-text="Not in any SIG group yet.">
                <el-table-column prop="groupId" label="Group ID" width="90" />
                <el-table-column prop="groupName" label="Group" min-width="160" />
                <el-table-column prop="joinedAt" label="Joined" width="180">
                  <template #default="{ row }">
                    {{ fmtTime(row.joinedAt) }}
                  </template>
                </el-table-column>
                <el-table-column label="" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      size="small"
                      link
                      type="danger"
                      @click="removeLookupMembership(m, row)"
                    >
                      Remove
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { csrf } from '../api/authApi'
import { useAuthStore } from '../stores/auth'
import {
  adminCreateGroup,
  adminDeactivateGroup,
  adminListAllGroups,
  adminListGroupMembers,
  adminListJoinApplications,
  adminLookupMember,
  adminRemoveGroupMembership,
  adminReviewJoinApplication,
  adminSetMemberSiteTier,
  adminUpdateGroup
} from '../api/joinApi'

const authStore = useAuthStore()
const activeTab = ref('groups')
const loadingGroups = ref(false)
const loadingApps = ref(false)
const groups = ref([])
const groupListFilter = ref('')
const applications = ref([])
const statusFilter = ref('PENDING')
const approveTier = reactive({})
/** Last server-known tier per app id; used so loadApps refreshes radios after PATCH elsewhere unless admin changed selection. */
const approveTierBaseline = reactive({})

const selectedGroupId = ref(null)
const selectedGroupName = ref('')
const groupMembers = ref([])
const loadingMembers = ref(false)
const tierSavingUserId = ref(null)

const lookupUsername = ref('')
const lookupTierFilter = ref('')
const lookupMatches = ref([])
const loadingLookup = ref(false)
const lookupDone = ref(false)

const lookupTierOnlyHint = computed(() => {
  const t = lookupTierFilter.value
  const hasTier = t === 'L1' || t === 'L2'
  return hasTier && !lookupUsername.value.trim()
})

const groupDlg = ref(false)
const editId = ref(null)
const groupSaving = ref(false)
const groupForm = reactive({
  name: '',
  description: '',
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

async function loadGroups() {
  loadingGroups.value = true
  try {
    if (!authStore.loaded) {
      await authStore.fetchMe()
    }
    await csrf().catch(() => {})
    groups.value = await adminListAllGroups(groupListFilter.value)
    if (selectedGroupId.value != null && !groups.value.some((g) => g.id === selectedGroupId.value)) {
      selectedGroupId.value = null
      selectedGroupName.value = ''
      groupMembers.value = []
    }
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
    syncApproveTierDefaults()
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
  groupForm.active = true
  groupForm.recruiting = true
  groupDlg.value = true
}

function openEdit(row) {
  editId.value = row.id
  groupForm.name = row.name
  groupForm.description = row.description || ''
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
    active: groupForm.active,
    recruiting: groupForm.recruiting
  }
  groupSaving.value = true
  try {
    await csrf().catch(() => {})
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
    await csrf().catch(() => {})
    await adminDeactivateGroup(row.id)
    ElMessage.success('Deactivated')
    await loadGroups()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || 'Failed')
    }
  }
}

async function loadGroupMembersForRow(rawGroupId) {
  const groupId =
    typeof rawGroupId === 'number' && !Number.isNaN(rawGroupId) ? rawGroupId : Number(rawGroupId)
  if (!Number.isFinite(groupId)) {
    groupMembers.value = []
    return
  }
  loadingMembers.value = true
  try {
    if (!authStore.loaded) {
      await authStore.fetchMe()
    }
    await csrf().catch(() => {})
    groupMembers.value = await adminListGroupMembers(groupId)
  } catch (e) {
    ElMessage.error(e?.message || 'Failed to load members')
    groupMembers.value = []
  } finally {
    loadingMembers.value = false
  }
}

function onGroupsRowChange(row) {
  if (!row || row.id == null) {
    selectedGroupId.value = null
    selectedGroupName.value = ''
    groupMembers.value = []
    return
  }
  selectedGroupId.value = row.id
  selectedGroupName.value = row.name
  loadGroupMembersForRow(row.id)
}

async function updateMemberTier(row, tier) {
  const desired = tier == null ? 'L1' : tier
  const current = row.memberSiteTier || 'L1'
  if (row.role !== 'MEMBER' || desired === current) return
  tierSavingUserId.value = row.userId
  try {
    await csrf().catch(() => {})
    await adminSetMemberSiteTier(row.userId, tier)
    ElMessage.success('Tier updated')
    await loadGroupMembersForRow(selectedGroupId.value)
  } catch (e) {
    ElMessage.error(e?.message || 'Update failed')
  } finally {
    tierSavingUserId.value = null
  }
}

async function removeMemberFromGroup(row) {
  if (!selectedGroupId.value || row.membershipId == null) {
    ElMessage.warning('Select a group and try again.')
    return
  }
  try {
    await ElMessageBox.confirm(`Remove "${row.username}" from this SIG group only?`, 'Confirm remove', {
      type: 'warning'
    })
    await csrf().catch(() => {})
    await adminRemoveGroupMembership(selectedGroupId.value, row.membershipId)
    ElMessage.success('Removed from group')
    await loadGroupMembersForRow(selectedGroupId.value)
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || 'Remove failed')
    }
  }
}

async function updateLookupMemberTier(match, tier) {
  const desired = tier == null ? 'L1' : tier
  const current = match.memberSiteTier || 'L1'
  if (match.role !== 'MEMBER' || desired === current) return
  tierSavingUserId.value = match.userId
  try {
    await csrf().catch(() => {})
    await adminSetMemberSiteTier(match.userId, tier)
    ElMessage.success('Tier updated')
    await runLookup()
  } catch (e) {
    ElMessage.error(e?.message || 'Update failed')
  } finally {
    tierSavingUserId.value = null
  }
}

async function removeLookupMembership(match, gRow) {
  if (gRow.membershipId == null || gRow.groupId == null) {
    ElMessage.warning('Missing membership reference; try Search again after refresh.')
    return
  }
  try {
    await ElMessageBox.confirm(
      `Remove "${match.username}" from group "${gRow.groupName}" only?`,
      'Confirm remove',
      { type: 'warning' }
    )
    await csrf().catch(() => {})
    await adminRemoveGroupMembership(gRow.groupId, gRow.membershipId)
    ElMessage.success('Removed from group')
    await runLookup()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || 'Remove failed')
    }
  }
}

function syncApproveTierDefaults() {
  for (const a of applications.value) {
    if (a.status !== 'PENDING') continue
    const serverTier = a.applicantMemberSiteTier || 'L1'
    const cur = approveTier[a.id]
    const baseline = approveTierBaseline[a.id]
    if (cur == null || baseline == null || cur === baseline) {
      approveTier[a.id] = serverTier
      approveTierBaseline[a.id] = serverTier
    }
  }
}

function fmtTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

async function runLookup() {
  const q = lookupUsername.value.trim()
  const tier = lookupTierFilter.value === 'L1' || lookupTierFilter.value === 'L2' ? lookupTierFilter.value : undefined
  if (!q && !tier) {
    ElMessage.warning('Enter NetID and/or choose Level 1 or Level 2.')
    return
  }
  loadingLookup.value = true
  lookupDone.value = false
  try {
    if (!authStore.loaded) {
      await authStore.fetchMe()
    }
    await csrf().catch(() => {})
    const res = await adminLookupMember({ username: q || undefined, memberSiteTier: tier })
    lookupMatches.value = res.matches || []
    lookupDone.value = true
    if (!lookupMatches.value.length) {
      ElMessage.info('No matching users.')
    }
  } catch (e) {
    ElMessage.error(e?.message || 'Search failed')
    lookupMatches.value = []
    lookupDone.value = true
  } finally {
    loadingLookup.value = false
  }
}

async function review(row, approved) {
  try {
    await csrf().catch(() => {})
    const tier = approved ? approveTier[row.id] || 'L1' : undefined
    await adminReviewJoinApplication(row.id, approved, tier)
    ElMessage.success(approved ? 'Approved' : 'Rejected')
    await loadApps()
    if (approved && row.interestGroup?.id != null && selectedGroupId.value === row.interestGroup.id) {
      await loadGroupMembersForRow(selectedGroupId.value)
    }
    if (approved && lookupDone.value) {
      const q = lookupUsername.value.trim().toLowerCase()
      const u = String(row.applicantUsername || '').toLowerCase()
      if (q && u === q) await runLookup()
    }
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
  margin-top: 0;
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
.row.align-center {
  align-items: center;
}

.lookup-tier-hint {
  display: block;
  margin-top: 4px;
}

.wrap {
  flex-wrap: wrap;
}

.grow {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
  min-width: 200px;
}

.tab-tip {
  margin-bottom: 12px;
}

.members-panel {
  margin-top: 20px;
}

.tier-radios {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.table-below-tip {
  margin-top: 0;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.lookup-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px 14px;
}

.lookup-tier-row {
  margin: 8px 0 4px;
  gap: 10px;
}

.lookup-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.small {
  font-size: 0.85rem;
  margin: 0 0 8px;
}

.sub {
  font-weight: 600;
  margin: 10px 0 6px;
  font-size: 0.9rem;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 0.85rem;
}
</style>
