<template>
  <div class="search-page">
    <el-card class="query-card">
      <div class="query-row">
        <el-input
          v-model="form.keyword"
          class="query-input"
          placeholder="Search title, description, tags, and content"
          clearable
          @keyup.enter="onSearch"
        />
        <el-button type="primary" :loading="loading" @click="onSearch">Search</el-button>
        <el-button @click="onReset">Clear</el-button>
      </div>
      <div class="quick-row">
        <span class="quick-label">Common categories:</span>
        <el-tag
          v-for="quickCategory in quickCategories"
          :key="quickCategory"
          class="quick-tag"
          :type="form.category === quickCategory ? 'primary' : 'info'"
          @click="applyQuickCategory(quickCategory)"
        >
          {{ quickCategory }}
        </el-tag>
      </div>
      <el-alert
        v-if="suggestion && form.keyword.trim().toLowerCase() !== suggestion.toLowerCase()"
        class="suggestion-alert"
        type="info"
        :closable="false"
      >
        <template #default>
          Did you mean:
          <el-button text class="suggestion-btn" @click="applySuggestion">{{ suggestion }}</el-button>
        </template>
      </el-alert>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="7" :lg="6">
        <el-card class="filter-card" shadow="never">
          <template #header>
            <span>Filters</span>
          </template>

          <el-form label-position="top">
            <el-form-item label="File Type">
              <el-input v-model="form.fileType" placeholder="e.g. pdf, application/" clearable @keyup.enter="onSearch" />
            </el-form-item>
            <el-form-item label="Category">
              <el-input v-model="form.category" placeholder="e.g. report / code / notice" clearable @keyup.enter="onSearch" />
            </el-form-item>
            <el-form-item label="Uploader">
              <el-input v-model="form.uploader" placeholder="Enter username" clearable @keyup.enter="onSearch" />
            </el-form-item>
            <el-form-item label="Upload date from">
              <el-date-picker
                v-model="form.uploadDateFrom"
                type="date"
                placeholder="Start date"
                clearable
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                class="filter-date"
              />
            </el-form-item>
            <el-form-item label="Upload date to">
              <el-date-picker
                v-model="form.uploadDateTo"
                type="date"
                placeholder="End date"
                clearable
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                class="filter-date"
              />
            </el-form-item>
            <div class="filter-actions">
              <el-button type="primary" plain :loading="loading" @click="onSearch">Apply Filters</el-button>
              <el-button @click="clearFilters">Reset Filters</el-button>
            </div>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="17" :lg="18">
        <el-card class="result-card" shadow="never">
          <template #header>
            <div class="result-header">
              <div class="result-title-wrap">
                <div class="result-title">Search Results</div>
                <div class="result-subtitle">
                  <template v-if="searched">
                    {{ files.length }} result(s)
                    <span v-if="activeKeyword">, keyword: {{ activeKeyword }}</span>
                  </template>
                  <template v-else>Enter a keyword or filters to start searching</template>
                </div>
              </div>
              <div class="result-actions">
                <el-radio-group v-model="viewMode" size="small" @change="onViewModeChange">
                  <el-radio-button label="list">List View</el-radio-button>
                  <el-radio-button label="table">Table View</el-radio-button>
                </el-radio-group>
                <el-button link @click="onReset">Clear All</el-button>
              </div>
            </div>
          </template>

          <el-alert
            v-if="errorMessage"
            type="error"
            :closable="false"
            class="state-block"
            :title="errorMessage"
          />

          <div v-else-if="loading" class="state-block">
            <el-skeleton v-for="i in 3" :key="i" animated class="result-skeleton">
              <template #template>
                <el-skeleton-item variant="h3" style="width: 45%" />
                <el-skeleton-item variant="text" style="width: 80%; margin-top: 10px" />
                <el-skeleton-item variant="text" style="width: 60%; margin-top: 8px" />
              </template>
            </el-skeleton>
          </div>

          <FileTable
            v-else
            :files="files"
            :mode="viewMode"
            :empty-description="emptyDescription"
            :favourites-enabled="true"
            :favourite-ids="favouriteIds"
            @view="onView"
            @download="onDownload"
            @toggle-favourite="onToggleFavourite"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import FileTable from '../components/FileTable.vue'
import { downloadFile, searchFiles } from '../api/fileApi'
import { addFavourite, listFavouriteIds, removeFavourite } from '../api/favouriteApi'
import { useAuthStore } from '../stores/auth'
import { previewBlockedReason } from '../utils/resourceVisibility'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  keyword: '',
  fileType: '',
  category: '',
  uploader: '',
  uploadDateFrom: '',
  uploadDateTo: ''
})
const quickCategories = ['notice', 'report', 'code', 'self-study']
const viewMode = ref('list')
const files = ref([])
const loading = ref(false)
const searched = ref(false)
const errorMessage = ref('')
const suggestion = ref('')
const syncingRoute = ref(false)
const favouriteIds = ref([])

const isInternalPortal = computed(() => route.path.startsWith('/home/internal_portal'))
const searchRouteName = computed(() => (isInternalPortal.value ? 'internal-search' : 'search'))
const fileDetailRouteName = computed(() =>
  isInternalPortal.value ? 'internal-file-detail' : 'file-detail'
)

watch(
  () => route.query,
  async () => {
    if (syncingRoute.value) return
    await applyRouteState()
  },
  { immediate: true }
)

const activeKeyword = computed(() => form.keyword.trim())
const emptyDescription = computed(() => {
  if (!searched.value) {
    return 'You can enter keywords or set filters before searching.'
  }
  return 'No matching results found. Try another keyword or reduce filters.'
})

async function applyRouteState() {
  form.keyword = queryValue('keyword')
  form.fileType = queryValue('fileType')
  form.category = queryValue('category')
  form.uploader = queryValue('uploader')
  form.uploadDateFrom = queryValue('uploadDateFrom')
  form.uploadDateTo = queryValue('uploadDateTo')
  viewMode.value = queryValue('view') === 'table' ? 'table' : 'list'

  if (hasAnySearchInput()) {
    await runSearch(false)
    return
  }
  files.value = []
  searched.value = false
  errorMessage.value = ''
  suggestion.value = ''
}

function queryValue(key) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function hasAnySearchInput() {
  return Boolean(
    form.keyword.trim() ||
    form.fileType.trim() ||
    form.category.trim() ||
    form.uploader.trim() ||
    form.uploadDateFrom ||
    form.uploadDateTo
  )
}

function buildRouteQuery() {
  const next = {}
  if (form.keyword.trim()) next.keyword = form.keyword.trim()
  if (form.fileType.trim()) next.fileType = form.fileType.trim()
  if (form.category.trim()) next.category = form.category.trim()
  if (form.uploader.trim()) next.uploader = form.uploader.trim()
  if (form.uploadDateFrom) next.uploadDateFrom = form.uploadDateFrom
  if (form.uploadDateTo) next.uploadDateTo = form.uploadDateTo
  if (viewMode.value === 'table') next.view = 'table'
  return next
}

async function updateRouteQuery() {
  syncingRoute.value = true
  try {
    await router.push({ name: searchRouteName.value, query: buildRouteQuery() })
  } finally {
    syncingRoute.value = false
  }
}

async function runSearch(syncRoute = true) {
  loading.value = true
  errorMessage.value = ''
  searched.value = true
  try {
    if (syncRoute) {
      await updateRouteQuery()
    }
    const payload = await searchFiles(form)
    files.value = payload?.items || []
    suggestion.value = payload?.suggestion || ''
    await refreshFavouriteIds()
  } catch (error) {
    files.value = []
    favouriteIds.value = []
    errorMessage.value = error?.message || 'Search failed, please try again later.'
    suggestion.value = ''
  } finally {
    loading.value = false
  }
}

async function onSearch() {
  const keyword = form.keyword.trim()
  if (keyword && Array.from(keyword).length < 2) {
    errorMessage.value = 'Keyword must contain at least 2 characters.'
    files.value = []
    searched.value = false
    suggestion.value = ''
    return
  }
  if (form.uploadDateFrom && form.uploadDateTo && form.uploadDateFrom > form.uploadDateTo) {
    errorMessage.value = '"Upload date from" must be on or before "Upload date to".'
    files.value = []
    searched.value = false
    suggestion.value = ''
    return
  }
  await runSearch(true)
}

async function onViewModeChange() {
  await updateRouteQuery()
}

async function onReset() {
  form.keyword = ''
  form.fileType = ''
  form.category = ''
  form.uploader = ''
  form.uploadDateFrom = ''
  form.uploadDateTo = ''
  viewMode.value = 'list'
  files.value = []
  searched.value = false
  errorMessage.value = ''
  suggestion.value = ''
  syncingRoute.value = true
  try {
    await router.push({ name: searchRouteName.value, query: {} })
  } finally {
    syncingRoute.value = false
  }
}

function clearFilters() {
  form.fileType = ''
  form.category = ''
  form.uploader = ''
  form.uploadDateFrom = ''
  form.uploadDateTo = ''
}

async function applyQuickCategory(category) {
  form.category = category
  await onSearch()
}

async function applySuggestion() {
  form.keyword = suggestion.value
  await onSearch()
}

function onDownload(row) {
  downloadFile(row.id, row.fileName)
}

async function onView(row) {
  const blocked = previewBlockedReason(authStore, row.visibility)
  if (blocked) {
    ElMessage.warning(blocked)
    return
  }
  router.push({
    name: fileDetailRouteName.value,
    params: { id: row.id },
    query: form.keyword ? { keyword: form.keyword.trim() } : {}
  })
}

async function refreshFavouriteIds() {
  try {
    const ids = await listFavouriteIds()
    favouriteIds.value = Array.isArray(ids) ? ids : []
  } catch {
    favouriteIds.value = []
  }
}

async function onToggleFavourite(row) {
  const id = row.id
  const isFav = favouriteIds.value.includes(id)
  try {
    if (isFav) {
      await removeFavourite(id)
      favouriteIds.value = favouriteIds.value.filter((x) => x !== id)
      ElMessage.success('Removed from favourites')
    } else {
      await addFavourite(id)
      favouriteIds.value = [...favouriteIds.value, id]
      ElMessage.success('Added to favourites')
    }
  } catch (error) {
    ElMessage.error(error?.message || 'Could not update favourites.')
  }
}
</script>

<style scoped>
.search-page {
  width: 100%;
}

.query-card {
  margin-bottom: 16px;
}

.query-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.query-input {
  flex: 1;
}

.quick-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-label {
  color: #606266;
}

.quick-tag {
  cursor: pointer;
}

.suggestion-alert {
  margin-top: 10px;
}

.suggestion-btn {
  margin-left: 4px;
  font-weight: 600;
}

.filter-card,
.result-card {
  min-height: 100%;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.filter-date {
  width: 100%;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
}

.result-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.state-block {
  margin-bottom: 12px;
}

.result-skeleton {
  padding: 12px 0;
}

@media (max-width: 768px) {
  .query-row {
    flex-direction: column;
    align-items: stretch;
  }

  .result-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
