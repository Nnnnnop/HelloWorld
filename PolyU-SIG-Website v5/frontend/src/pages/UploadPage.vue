<template>
  <el-card class="page-card">
    <template #header>
      <span>Upload File</span>
    </template>

    <el-form label-width="120px" class="meta-form">
      <el-form-item label="Folder">
        <el-input
          v-model="selectedFolderPath"
          placeholder="Select a folder"
          readonly
          @click="openFolderDialog"
        />
        <el-button @click="openFolderDialog" style="margin-top: 8px; width: 100%">Select Folder</el-button>
      </el-form-item>
      <el-form-item label="Title">
        <el-input v-model="form.title" placeholder="Resource title" />
      </el-form-item>
      <el-form-item label="Description">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="Resource description" />
      </el-form-item>
      <el-form-item label="Category">
        <el-select v-model="form.category" placeholder="Select a category">
          <el-option label="Reference Code" value="reference-code" />
          <el-option label="Research Report" value="research-report" />
          <el-option label="Self Study" value="self-study" />
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

    <el-dialog v-model="folderDialogVisible" title="Select Folder" width="600px">
      <div class="folder-selection-dialog">
        <el-input
          v-model="folderSearch"
          size="small"
          clearable
          placeholder="Search folder"
          style="margin-bottom: 12px;"
        />
        <div class="folder-tree-container">
          <el-tree
            :data="filteredFolders"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            :current-node-key="selectedFolderDialogKey"
            highlight-current
            :expand-on-click-node="false"
            @node-click="onSelectFolderInDialog"
          >
            <template #default="{ data }">
              <span class="tree-row">
                <span class="tree-name">{{ data.name || 'Root' }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>
      <template #footer>
        <el-button @click="folderDialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="confirmFolderSelect">Confirm</el-button>
      </template>
    </el-dialog>
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

const folderDialogVisible = ref(false)
const folderSearch = ref('')
const selectedFolderDialogKey = ref('ROOT')

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

const treeFolders = computed(() => {
  if (!folders.value || !folders.value.length) {
    return [
      {
        id: 'ROOT',
        name: 'Root',
        children: []
      }
    ]
  }

  const hasRootNode = folders.value.some((node) => {
    return String(node.id) === '1' || String(node.name || '').toLowerCase() === 'root'
  })

  if (hasRootNode) {
    return folders.value
  }

  // Wrap all folders under a ROOT node
  return [
    {
      id: 'ROOT',
      name: 'Root',
      children: folders.value
    }
  ]
})

const selectedFolderTreeKey = computed(() => String(form.folderId || 'ROOT'))

const selectedFolderPath = computed(() => {
  if (!form.folderId) return 'Root (default)'
  const flat = flatFolders.value
  const found = flat.find((f) => String(f.id) === String(form.folderId))
  return found ? found.label : 'Root (default)'
})

const filterFolderTree = (nodes, keyword) => {
  const kw = String(keyword || '').trim().toLowerCase()
  if (!kw) return nodes

  const walk = (list) => {
    const result = []
    for (const node of list || []) {
      const children = walk(node.children || [])
      const matched = String(node.name || '').toLowerCase().includes(kw)
      if (matched || children.length) {
        result.push({ ...node, children })
      }
    }
    return result
  }

  return walk(nodes)
}

const filteredFolders = computed(() => {
  return filterFolderTree(treeFolders.value, folderSearch.value)
})

const openFolderDialog = () => {
  selectedFolderDialogKey.value = String(form.folderId || 'ROOT')
  folderDialogVisible.value = true
}

const onSelectFolderInDialog = (node) => {
  if (!node) return
  selectedFolderDialogKey.value = String(node.id || 'ROOT')
}

const confirmFolderSelect = () => {
  form.folderId = selectedFolderDialogKey.value === 'ROOT' ? null : Number(selectedFolderDialogKey.value)
  folderDialogVisible.value = false
}

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

.folder-selection-dialog {
  padding: 12px 0;
}

.folder-tree-container {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  max-height: 400px;
  overflow: auto;
  padding: 8px;
}

.tree-row {
  display: flex;
  align-items: center;
}

.tree-name {
  cursor: pointer;
}
</style>
