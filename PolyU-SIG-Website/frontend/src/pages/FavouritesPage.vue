<template>
  <div class="favourites-page">
    <el-card class="header-card" shadow="never">
      <div class="header-row">
        <div>
          <div class="page-title">Favourites</div>
          <div class="page-sub">Sources you saved from search. Remove items you no longer need.</div>
        </div>
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button label="list">List View</el-radio-button>
          <el-radio-button label="table">Table View</el-radio-button>
        </el-radio-group>
      </div>
    </el-card>

    <el-card class="result-card" shadow="never">
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import FileTable from '../components/FileTable.vue'
import { downloadFile } from '../api/fileApi'
import { listFavourites, removeFavourite } from '../api/favouriteApi'
import { useAuthStore } from '../stores/auth'
import { previewBlockedReason } from '../utils/resourceVisibility'

const router = useRouter()
const authStore = useAuthStore()

const viewMode = ref('list')
const files = ref([])
const loading = ref(true)
const errorMessage = ref('')

const favouriteIds = computed(() => files.value.map((f) => f.id))

const emptyDescription =
  'No favourites yet. Use Search and add sources to your favourites.'

onMounted(() => {
  loadFavourites()
})

async function loadFavourites() {
  loading.value = true
  errorMessage.value = ''
  try {
    files.value = await listFavourites()
  } catch (error) {
    files.value = []
    errorMessage.value = error?.message || 'Failed to load favourites.'
  } finally {
    loading.value = false
  }
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
    name: 'internal-file-detail',
    params: { id: row.id }
  })
}

async function onToggleFavourite(row) {
  try {
    await removeFavourite(row.id)
    files.value = files.value.filter((f) => f.id !== row.id)
    ElMessage.success('Removed from favourites')
  } catch (error) {
    ElMessage.error(error?.message || 'Could not update favourites.')
  }
}
</script>

<style scoped>
.favourites-page {
  width: 100%;
}

.header-card {
  margin-bottom: 16px;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.page-sub {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.result-card {
  min-height: 280px;
}

.state-block {
  margin-bottom: 12px;
}

.result-skeleton {
  padding: 12px 0;
}
</style>
