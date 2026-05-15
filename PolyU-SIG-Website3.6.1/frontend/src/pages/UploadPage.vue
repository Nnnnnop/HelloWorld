<template>
  <el-card class="page-card">
    <template #header>
      <span>Upload File</span>
    </template>

    <el-form label-width="120px" class="meta-form">
      <el-form-item label="Folder">
        <el-select v-model="form.folderId" placeholder="Select a folder">
          <el-option label="Root (default)" :value="null" />
          <el-option
            v-for="folder in flatFolders"
            :key="folder.id"
            :label="folder.label"
            :value="folder.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Title">
        <el-input v-model="form.title" placeholder="Resource title" />
      </el-form-item>
      <el-form-item label="Description">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="Resource description" />
      </el-form-item>
      <el-form-item label="Category">
        <el-select v-model="form.category" placeholder="Select a category">
          <el-option label="reference-code" value="reference-code" />
          <el-option label="research-report" value="research-report" />
          <el-option label="self-study" value="self-study" />
        </el-select>
      </el-form-item>
      <el-form-item label="Tags">
        <el-input v-model="form.tags" placeholder="Comma separated, e.g. java,spring,security" />
      </el-form-item>
      <el-form-item label="Visibility">
        <el-radio-group v-model="form.visibility">
          <el-radio-button label="HIDDEN">HIDDEN</el-radio-button>
          <el-radio-button label="PUBLIC">Public</el-radio-button>
          <el-radio-button label="L1">Member: Level 1</el-radio-button>
          <el-radio-button label="L2">Member: Level 2</el-radio-button>
        </el-radio-group>
        <div class="visibility-tip">
          All levels require login. HIDDEN = uploader only, Public = any logged-in PolyU user, Member: Level 1/2 = approved members/admins only.
        </div>
      </el-form-item>
    </el-form>

    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :limit="1"
      :on-change="onSelect"
      :show-file-list="true"
      :on-exceed="onExceed"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">Drag a file here, or click to select</div>
      <template #tip>
        <div class="el-upload__tip">Upload one file each time, max size 5GB</div>
      </template>
    </el-upload>

    <div class="actions">
      <el-button type="primary" :loading="loading" @click="submitUpload">Upload</el-button>
      <el-button @click="clearFile">Clear</el-button>
    </div>
    <el-progress v-if="loading || uploadProgress > 0" :percentage="uploadProgress" :text-inside="true" status="active" style="margin-top: 16px;" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { uploadFile } from '../api/fileApi'
import { listFolders } from '../api/folderApi'
import { useAuthStore } from '../stores/auth'

const uploadRef = ref()
const selectedFile = ref(null)
const loading = ref(false)
const uploadProgress = ref(0)
const router = useRouter()
const authStore = useAuthStore()
const folders = ref([])

const form = reactive({
  title: '',
  description: '',
  category: 'reference-code',
  tags: '',
  visibility: 'HIDDEN',
  folderId: null
})

const flattenFolderTree = (nodes, prefix = '') => {
  const out = []
  for (const n of nodes || []) {
    const label = prefix ? `${prefix} / ${n.name}` : n.name
    out.push({ id: n.id, label })
    out.push(...flattenFolderTree(n.children, label))
  }
  return out
}

const flatFolders = computed(() => flattenFolderTree(folders.value).filter(f => f.label !== 'Root'))

const loadFolders = async () => {
  try {
    folders.value = await listFolders()
  } catch (error) {
    ElMessage.error(error.message || 'Failed to load folders')
  }
}

function onSelect(uploadFileItem) {
  const file = uploadFileItem.raw || null
  selectedFile.value = file
  if (file) {
    const idx = file.name.lastIndexOf('.')
    form.title = idx > 0 ? file.name.slice(0, idx) : file.name
  }
}

function onExceed() {
  ElMessage.warning('Only one file can be selected.')
}

function clearFile() {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  uploadProgress.value = 0
}

async function submitUpload() {
  await authStore.fetchMe()
  if (!authStore.isLoggedIn) {
    ElMessage.warning('Login session expired. Please log in again.')
    router.push('/login')
    return
  }
  if (!authStore.isAdmin) {
    ElMessage.error('Only administrators can upload files.')
    router.push('/files')
    return
  }

  if (!selectedFile.value) {
    ElMessage.warning('Please select a file first.')
    return
  }
  if (!form.title.trim()) {
    ElMessage.warning('Please enter a title.')
    return
  }
  if (!form.category || !form.category.trim()) {
    form.category = 'reference-code'
  }

  loading.value = true
  try {
    await uploadFile(selectedFile.value, form, (percent) => {
      uploadProgress.value = percent
    })
    ElMessage.success('Upload successful')
    clearFile()
    form.title = ''
    form.description = ''
    form.tags = ''
    form.visibility = 'HIDDEN'
    form.folderId = null
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
    uploadProgress.value = 0
  }
}

onMounted(async () => {
  await loadFolders()
})
</script>

<style scoped>
.page-card {
  width: 100%;
}

.meta-form {
  margin-bottom: 16px;
}

.actions {
  margin-top: 20px;
}

.visibility-tip {
  margin-top: 8px;
  margin-right: 20px;
  color: #909399;
  font-size: 12px;
}
</style>
