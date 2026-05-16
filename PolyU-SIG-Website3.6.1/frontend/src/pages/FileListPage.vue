<template>
  <div class="page">

    <!-- Main content: split layout with resizable divider -->
    <div class="content resizable-layout">
      <!-- LEFT PANEL: Folders -->
      <div v-if="!isNarrow || showFolderPanel" class="left-panel" :style="{ width: leftWidth + 'px' }">
        <el-card class="panel-card">
          <template #header>Folders</template>

          <el-input
            v-if="auth.isAdmin"
            v-model="newFolderName"
            placeholder="New folder name (root)"
          >
            <template #append>
              <el-button @click="createFolder">Create</el-button>
            </template>
          </el-input>

          <el-input
            v-model="folderSearch"
            clearable
            placeholder="Search folders"
            style="margin-top: 10px;"
          />

          <el-button
            class="all-files-btn"
            :type="selectedFolderId === 'all' ? 'primary' : 'default'"
            @click="selectAll"
          >
            All Files
          </el-button>

          <el-tree
            class="folder-tree"
            :data="filteredFolders"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            :current-node-key="selectedFolderId === 'all' ? null : selectedFolderId"
            highlight-current
            :expand-on-click-node="false"
            @current-change="onTreeSelect"
          >
            <template #default="{ data }">
              <span class="tree-row">
                <span class="tree-name" @click.stop="onTreeSelect(data)">
                  {{ data.name }}
                </span>
                <span class="tree-actions">
                  <el-button
                    link
                    type="info"
                    size="small"
                    @click.stop="openFolderInfo(data)"
                  >
                    Info
                  </el-button>
                  <el-button
                    v-if="auth.isAdmin && !isRootFolder(data)"
                    link
                    type="primary"
                    size="small"
                    @click.stop="openFolderEdit(data)"
                  >
                    Edit
                  </el-button>
                </span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </div>

      <!-- RESIZER -->
      <div v-if="!isNarrow || showFolderPanel" class="drag-divider" @mousedown="startResize" />

      <!-- RIGHT PANEL: Files -->
      <div class="right-panel">
        <el-card class="panel-card">
          <template #header>
            <div class="header-row">
              <div class="files-title">
                <span>Files</span>
                <div
                  v-if="selectedFolderId !== 'all' && currentPathParts.length"
                  class="path-breadcrumb"
                >
                  <template v-for="(part, index) in currentPathParts" :key="part.id">
                    <el-button
                      link
                      type="primary"
                      size="small"
                      @click="goToFolder(part.id)"
                    >
                      {{ part.name }}
                    </el-button>
                    <span v-if="index < currentPathParts.length - 1" class="path-sep">
                      &gt;
                    </span>
                  </template>
                </div>
              </div>

              <div class="header-actions">
                <div class="batch-actions" v-if="selectedFileCount > 0">
                  <el-button size="small" @click="openBatchMove" type="primary" plain>
                    Move selected
                  </el-button>
                  <el-button size="small" @click="downloadSelectedAsZip" type="success" plain>
                    Download selected as zip
                  </el-button>
                  <el-button size="small" :loading="deleteLoading" @click="deleteSelectedFiles" type="danger" plain>
                    Delete selected
                  </el-button>
                  <span class="selected-count">({{ selectedFileCount }} selected)</span>
                </div>

                <el-input
                  v-model="fileSearch"
                  clearable
                  placeholder="Search by file name"
                  style="width: 240px"
                />

                <el-button v-if="isNarrow" size="small" plain @click="showFolderPanel = !showFolderPanel">
                  {{ showFolderPanel ? 'Hide folders' : 'Show folders' }}
                </el-button>
                <el-button v-if="auth.isAdmin" type="primary" @click="goToUploadPage">
                  Upload File
                </el-button>
                <el-button v-if="auth.isAdmin" type="primary" @click="goToUploadFolderPage" plain>
                  Upload Folder
                </el-button>
              </div>
            </div>
          </template>

          <el-table
            ref="filesTable"
            :data="filteredFiles"
            border
            row-key="id"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column prop="title" label="Name" min-width="240" show-overflow-tooltip />
            <el-table-column prop="uploader" label="Uploaded By" min-width="140" />
            <el-table-column label="File Size" min-width="120" align="right">
              <template #default="{ row }">
                {{ formatFileSize(row.fileSize || 0) }}
              </template>
            </el-table-column>
            <el-table-column label="Uploaded At" min-width="180">
              <template #default="{ row }">
                {{ formatTime(row.uploadTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="visibility" label="Access" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small">
                  {{ visibilityLabel(row.visibility) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Action" min-width="440" fixed="right">
              <template #default="{ row }">
                <div class="action-buttons">
                  <el-button
                    size="small"
                    type="info"
                    plain
                    @click="openInfo(row)"
                  >
                    Info
                  </el-button>

                  <el-button
                    size="small"
                    type="success"
                    plain
                    @click="openFilePreview(row)"
                  >
                    View
                  </el-button>

                  <el-button
                    size="small"
                    type="primary"
                    plain
                    @click="onDownload(row)"
                  >
                    Download
                  </el-button>

                  <el-button
                    v-if="selectedFolderId === 'all'"
                    size="small"
                    type="info"
                    plain
                    @click="showDirectory(row)"
                  >
                    Directory
                  </el-button>

                  <el-button
                    v-if="auth.isAdmin"
                    size="small"
                    type="warning"
                    plain
                    @click="openEdit(row)"
                  >
                    Edit
                  </el-button>

                  <el-popconfirm
                    v-if="auth.isAdmin"
                    title="Delete this file?"
                    @confirm="removeFile(row.id)"
                  >
                    <template #reference>
                      <el-button
                        size="small"
                        type="danger"
                        plain
                      >
                        Delete
                      </el-button>
                    </template>
                  </el-popconfirm>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <!-- FILE EDIT DIALOG -->
    <el-dialog v-model="editVisible" title="Edit file info" width="500px">
      <el-form label-width="110px">
        <el-form-item label="File name">
          <el-input v-model="editing.baseName">
            <template #append>{{ editing.ext }}</template>
          </el-input>
        </el-form-item>
        <el-form-item label="Folder">
          <el-select v-model="editing.folderId" style="width: 100%">
            <el-option v-for="f in flatFolders" :key="f.id" :label="f.label" :value="f.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Category">
          <el-select v-model="editing.category" style="width: 100%">
            <el-option label="reference-code" value="reference-code" />
            <el-option label="research-report" value="research-report" />
            <el-option label="self-study" value="self-study" />
          </el-select>
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="editing.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="Tags">
          <el-input v-model="editing.tags" placeholder="Comma separated tags" />
        </el-form-item>
        <el-form-item label="Access">
          <el-select v-model="editing.visibility" style="width: 100%">
            <el-option label="HIDDEN" value="HIDDEN" />
            <el-option label="Public" value="L1" />
            <el-option label="Member: Level1" value="L2" />
            <el-option label="Member: Level2" value="L3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveEdit">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="infoVisible" title="File Info" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Title">{{ infoFile?.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Description">
          <span v-if="!infoFile?.description">-</span>
          <span v-else>{{ infoFile.description }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="File Name">{{ infoFile?.fileName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Category">{{ infoFile?.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Uploader">{{ infoFile?.uploader || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Uploaded At">{{ formatTime(infoFile?.uploadTime) }}</el-descriptions-item>
        <el-descriptions-item label="Access Level">{{ visibilityLabel(infoFile?.visibility) }}</el-descriptions-item>
        <el-descriptions-item label="File Path">{{ getFilePath(infoFile?.folderId) }}</el-descriptions-item>
        <el-descriptions-item label="Tags">
          <span v-if="!fileInfoTags.length">-</span>
          <el-tag v-for="tag in fileInfoTags" :key="tag" class="tag">{{ tag }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="infoVisible = false">Close</el-button>
      </template>
    </el-dialog>


    <!-- FOLDER INFO DIALOG -->
    <el-dialog v-model="folderInfoVisible" title="Folder Info" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Folder Name">{{ folderInfo?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Total Files">{{ folderInfo?.totalFiles || 0 }}</el-descriptions-item>
        <el-descriptions-item label="Total Size">
          <span>{{ formatFileSize(folderInfo?.totalSize || 0) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Path">{{ getFolderPath(folderInfo?.id) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="folderInfoVisible = false">Close</el-button>
      </template>
    </el-dialog>

    <!-- FOLDER EDIT DIALOG -->
    <el-dialog v-model="folderEditVisible" title="Edit folder" width="460px">
      <el-form label-width="120px">
        <el-form-item label="Folder name">
          <el-input v-model="folderEditing.name" />
        </el-form-item>
        <el-form-item label="Parent folder">
          <el-select v-model="folderEditing.parentId" style="width: 100%" placeholder="Root">
            <el-option
              v-for="option in folderParentOptions"
              :key="option.id"
              :label="option.label"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-space>
          <el-popconfirm title="Delete this folder and all subfolders/files?" @confirm="deleteFolderFromEdit">
            <template #reference>
              <el-button type="danger" plain :loading="folderDeleteLoading">Delete</el-button>
            </template>
          </el-popconfirm>
          <el-button @click="folderEditVisible = false">Cancel</el-button>
          <el-button type="primary" @click="saveFolderEdit">Save</el-button>
        </el-space>
      </template>
    </el-dialog>

    <!-- BATCH MOVE DIALOG -->
    <el-dialog v-model="batchMoveVisible" title="Move selected files" width="500px">
      <el-form label-width="120px">
        <el-form-item label="Destination folder">
          <el-select v-model="batchMoveFolderId" style="width: 100%" placeholder="Select folder">
            <el-option
              v-for="folder in flatFolders"
              :key="folder.id"
              :label="folder.label"
              :value="folder.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchMoveVisible = false">Cancel</el-button>
        <el-button type="primary" :disabled="!batchMoveFolderId" :loading="batchMoveLoading" @click="moveSelectedFiles">Move</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { deleteFile, downloadFile, listFiles, updateFile, uploadBulk } from '../api/fileApi'
import { listFolders, createFolder as createFolderApi, updateFolder, deleteFolder as deleteFolderApi } from '../api/folderApi'
import { previewBlockedReason, visibilityLabel } from '../utils/resourceVisibility'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

// Panel sizing
const leftWidth = ref(320)
const MIN_LEFT = 240
const MAX_LEFT = 700

// Folder state
const folders = ref([])
const files = ref([])
const selectedFolderId = ref('all')
const newFolderName = ref('')
const folderSearch = ref('')
const fileSearch = ref('')

// Selection state
const selectedFiles = ref([])
const filesTable = ref(null)

// File edit dialog
const editVisible = ref(false)
const editing = ref({
  id: null,
  baseName: '',
  ext: '',
  folderId: null,
  visibility: 'HIDDEN',
  category: '',
  description: '',
  tags: ''
})

const infoVisible = ref(false)
const infoFile = ref(null)

// Folder info dialog
const folderInfoVisible = ref(false)
const folderInfo = ref(null)

// Folder edit dialog
const folderEditVisible = ref(false)
const folderEditing = ref({
  id: null,
  name: '',
  parentId: null
})

// Batch move dialog
const batchMoveVisible = ref(false)
const batchMoveFolderId = ref(null)
const batchMoveLoading = ref(false)
const deleteLoading = ref(false)
const folderDeleteLoading = ref(false)
const isNarrow = ref(window.innerWidth <= 1000)
const showFolderPanel = ref(!isNarrow.value)

const updateWindowWidth = () => {
  const narrow = window.innerWidth <= 1000
  isNarrow.value = narrow
  showFolderPanel.value = !narrow
}

// Computed properties
const selectedFileCount = computed(() => selectedFiles.value.length)

const filterFolderTree = (nodes, keyword) => {
  if (!keyword) return nodes
  const lower = keyword.trim().toLowerCase()
  return nodes
    .map((node) => {
      const children = filterFolderTree(node.children || [], keyword)
      const match = String(node.name || '').toLowerCase().includes(lower)
      if (match || children.length) {
        return { ...node, children }
      }
      return null
    })
    .filter(Boolean)
}

const filteredFolders = computed(() => {
  return filterFolderTree(folders.value, folderSearch.value)
})

const filteredFiles = computed(() => {
  const keyword = fileSearch.value.trim().toLowerCase()
  return files.value.filter((file) => {
    const inFolder =
      selectedFolderId.value === 'all' || String(file.folderId) === String(selectedFolderId.value)
    if (!inFolder) return false
    if (!keyword) return true
    return (file.title || '').toLowerCase().includes(keyword)
  })
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

const flatFolders = computed(() => flattenFolderTree(folders.value))

const folderMetaById = computed(() => {
  const map = new Map()
  const walk = (nodes, parentId = null) => {
    for (const n of nodes || []) {
      map.set(String(n.id), { id: n.id, name: n.name, parentId })
      walk(n.children, n.id)
    }
  }
  walk(folders.value)
  return map
})

const currentPathParts = computed(() => {
  if (selectedFolderId.value === 'all') return []
  const map = folderMetaById.value
  const parts = []
  let current = map.get(String(selectedFolderId.value))
  while (current) {
    parts.unshift({ id: current.id, name: current.name })
    current = current.parentId != null ? map.get(String(current.parentId)) : null
  }
  return parts
})

const folderParentOptions = computed(() => {
  const currentId = String(folderEditing.value.id)
  const descendants = new Set()

  const itemById = new Map()
  const walk = (nodes) => {
    for (const node of nodes || []) {
      itemById.set(String(node.id), node)
      walk(node.children)
    }
  }
  walk(folders.value)

  const collectDescendants = (id) => {
    const node = itemById.get(String(id))
    if (!node || descendants.has(String(id))) return
    descendants.add(String(id))
    for (const child of node.children || []) {
      collectDescendants(child.id)
    }
  }
  if (folderEditing.value.id != null) {
    collectDescendants(folderEditing.value.id)
  }

  return [
    { id: null, label: 'Root' },
    ...flattenFolderTree(folders.value).filter((option) => option.label !== 'Root' && (!folderEditing.value.id || (String(option.id) !== currentId && !descendants.has(String(option.id)))))
  ]
})

// Helper functions
const formatTime = (iso) => {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return String(iso)
  return date.toLocaleString('en-US', {
    timeZone: 'Asia/Hong_Kong',
    hour12: true,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  const size = (bytes / Math.pow(1024, index)).toFixed(2)
  return `${size} ${units[index]}`
}

const isRootFolder = (folder) => {
  return folder && (String(folder.id) === '1' || String(folder.name || '').toLowerCase() === 'root')
}

const getFilePath = (folderId) => {
  if (folderId == null) return 'Root'
  const map = folderMetaById.value
  const parts = []
  let current = map.get(String(folderId))

  while (current) {
    if (current.parentId == null) {
      parts.unshift(current.name)
      break
    }
    parts.unshift(current.name)
    current = map.get(String(current.parentId))
  }

  if (!parts.length) return 'Root'
  if (parts.length === 1 && String(parts[0]).toLowerCase() === 'root') return 'Root'
  return `Root / ${parts.join(' / ')}`
}

const fileInfoTags = computed(() => {
  if (!infoFile.value?.tags) return []
  if (Array.isArray(infoFile.value.tags)) return infoFile.value.tags
  return String(infoFile.value.tags)
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
})

const openInfo = (row) => {
  infoFile.value = row
  infoVisible.value = true
}

const calculateFolderStats = (folderId) => {
  let totalFiles = 0
  let totalSize = 0
  
  const walk = (parentId) => {
    // Count files in this folder
    files.value.forEach(file => {
      if (String(file.folderId) === String(parentId)) {
        totalFiles++
        totalSize += file.fileSize || 0
      }
    })
    
    // Recursively count files in subfolders
    folders.value.forEach(folder => {
      if (String(folder.parentId) === String(parentId)) {
        walk(folder.id)
      }
    })
  }
  
  walk(folderId)
  return { totalFiles, totalSize }
}

const openFolderInfo = (folder) => {
  const stats = calculateFolderStats(folder.id)
  folderInfo.value = {
    id: folder.id,
    name: folder.name,
    totalFiles: stats.totalFiles,
    totalSize: stats.totalSize
  }
  folderInfoVisible.value = true
}

const getFolderPath = (folderId) => {
  if (folderId == null) return 'Root'
  const map = folderMetaById.value
  const parts = []
  let current = map.get(String(folderId))
  
  while (current) {
    if (current.parentId == null) {
      parts.unshift(current.name)
      break
    }
    parts.unshift(current.name)
    current = map.get(String(current.parentId))
  }
  
  if (!parts.length) return 'Root'
  if (parts.length === 1 && String(parts[0]).toLowerCase() === 'root') return 'Root'
  return `Root / ${parts.join(' / ')}`
}

const splitName = (full) => {
  const name = String(full || '')
  const idx = name.lastIndexOf('.')
  if (idx <= 0 || idx === name.length - 1) {
    return { base: name, ext: '' }
  }
  return { base: name.slice(0, idx), ext: name.slice(idx) }
}

const extOf = (name) => {
  const n = String(name || "");
  const i = n.lastIndexOf(".");
  if (i <= 0 || i === n.length - 1) return "";
  return n.slice(i + 1).toLowerCase();
}

// Load data
const loadData = async () => {
  try {
    const [folderRes, fileRes] = await Promise.all([listFolders(), listFiles()])
    folders.value = folderRes
    files.value = fileRes
  } catch (error) {
    ElMessage.error(error.message || 'Failed to load data')
  }
}

// Resize handler
const startResize = (e) => {
  e.preventDefault()
  const startX = e.clientX
  const startWidth = leftWidth.value

  const onMove = (event) => {
    const next = startWidth + (event.clientX - startX)
    leftWidth.value = Math.min(MAX_LEFT, Math.max(MIN_LEFT, next))
  }

  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

// Folder operations
const createFolder = async () => {
  if (!newFolderName.value.trim()) return
  try {
    await createFolderApi(newFolderName.value.trim(), null)
    newFolderName.value = ''
    await loadData()
    ElMessage.success('Folder created')
  } catch (error) {
    ElMessage.error(error.message || 'Failed to create folder')
  }
}

const openFolderEdit = (folder) => {
  if (isRootFolder(folder)) return
  folderEditing.value = {
    id: folder.id,
    name: folder.name,
    parentId: folder.parentId ?? null
  }
  folderEditVisible.value = true
}

const saveFolderEdit = async () => {
  try {
    const name = String(folderEditing.value.name || '').trim()
    if (!name) {
      ElMessage.error('Folder name cannot be empty')
      return
    }
    await updateFolder(folderEditing.value.id, name, folderEditing.value.parentId)
    folderEditVisible.value = false
    await loadData()
    ElMessage.success('Folder updated')
  } catch (error) {
    ElMessage.error(error.message || 'Update folder failed')
  }
}

const deleteFolderFromEdit = async () => {
  folderDeleteLoading.value = true
  try {
    await deleteFolderApi(folderEditing.value.id)
    if (String(selectedFolderId.value) === String(folderEditing.value.id)) {
      selectedFolderId.value = 'all'
    }
    folderEditVisible.value = false
    await loadData()
    ElMessage.success('Folder deleted')
  } catch (error) {
    ElMessage.error(error.message || 'Delete folder failed')
  } finally {
    folderDeleteLoading.value = false
  }
}

// File operations
const goToUploadPage = () => {
  router.push('/home/internal_portal/files/uploads')
}

const goToUploadFolderPage = () => {
  router.push('/home/internal_portal/files/uploads-folder')
}

const openEdit = (row) => {
  const { base, ext } = splitName(row.title)
  editing.value = {
    id: row.id,
    baseName: base,
    ext,
    folderId: row.folderId,
    visibility: row.visibility,
    category: row.category || '',
    description: row.description || '',
    tags: Array.isArray(row.tags) ? row.tags.join(',') : row.tags || ''
  }
  editVisible.value = true
}

const saveEdit = async () => {
  try {
    const newTitle = `${(editing.value.baseName || '').trim() || 'file'}${editing.value.ext || ''}`
    await updateFile(editing.value.id, {
      title: newTitle,
      folderId: editing.value.folderId,
      visibility: editing.value.visibility,
      category: editing.value.category || null,
      description: editing.value.description || null,
      tags: editing.value.tags || null
    })
    editVisible.value = false
    await loadData()
    ElMessage.success('File updated')
  } catch (error) {
    ElMessage.error(error.message || 'Update failed')
  }
}

const removeFile = async (id) => {
  try {
    await deleteFile(id)
    await loadData()
    ElMessage.success('Deleted successfully')
  } catch (error) {
    ElMessage.error(error.message || 'Delete failed')
  }
}

const onDownload = (row) => {
  downloadFile(row.id, row.title)
}

/** Same navigation as Search page: file detail with embedded/pdf preview. */
function openFilePreview(row) {
  const blocked = previewBlockedReason(auth, row.visibility)
  if (blocked) {
    ElMessage.warning(blocked)
    return
  }
  const name = route.path.startsWith('/home/internal_portal') ? 'internal-file-detail' : 'file-detail'
  router.push({
    name,
    params: { id: row.id },
    query: {}
  })
}

const showDirectory = (row) => {
  if (!row?.folderId) return
  selectedFolderId.value = row.folderId
}

// Batch operations
const onSelectionChange = (rows) => {
  selectedFiles.value = rows || []
}

const openBatchMove = () => {
  batchMoveFolderId.value = selectedFolderId.value !== 'all' ? selectedFolderId.value : null
  batchMoveVisible.value = true
}

const moveSelectedFiles = async () => {
  if (!batchMoveFolderId.value) {
    ElMessage.error('Please choose a destination folder')
    return
  }
  batchMoveLoading.value = true
  try {
    await Promise.all(
      selectedFiles.value.map((row) =>
        updateFile(row.id, {
          title: row.title,
          folderId: batchMoveFolderId.value,
          visibility: row.visibility
        })
      )
    )
    batchMoveVisible.value = false
    selectedFiles.value = []
    filesTable.value?.clearSelection?.()
    await loadData()
    ElMessage.success('Files moved')
  } catch (error) {
    ElMessage.error(error.message || 'Move failed')
  } finally {
    batchMoveLoading.value = false
  }
}

const deleteSelectedFiles = async () => {
  if (!selectedFiles.value.length) return
  deleteLoading.value = true
  try {
    await ElMessageBox.confirm('Delete selected files?', 'Confirm', { type: 'warning' })
    await Promise.all(selectedFiles.value.map((row) => deleteFile(row.id)))
    selectedFiles.value = []
    filesTable.value?.clearSelection?.()
    await loadData()
    ElMessage.success('Selected files deleted')
  } catch (error) {
    if (error?.message !== 'cancel') {
      ElMessage.error(error.message || 'Delete failed')
    }
  } finally {
    deleteLoading.value = false
  }
}

const downloadSelectedAsZip = async () => {
  if (!selectedFiles.value.length) return
  try {
    const ids = selectedFiles.value.map(row => row.id)
    const response = await fetch(`/api/files/download-zip?ids=${ids.join(',')}`, {
      credentials: 'include'
    })
    if (!response.ok) throw new Error('Download failed')
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'selected-files.zip'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    ElMessage.success('Download started')
  } catch (error) {
    ElMessage.error(error.message || 'Download failed')
  }
}

// Navigation
const selectAll = () => {
  selectedFolderId.value = 'all'
}

const onTreeSelect = (node) => {
  if (!node?.id) return
  selectedFolderId.value = node.id
}

const goToFolder = (id) => {
  selectedFolderId.value = id
}

const normalizePath = (p) =>
  String(p || "")
    .replaceAll("\\", "/")
    .replace(/^\/+/, "");

const splitPath = (p) => normalizePath(p).split("/").filter(Boolean);

onMounted(() => {
  document.body.classList.add('filelist-fullwidth')
  window.addEventListener('resize', updateWindowWidth)
  updateWindowWidth()
  loadData()
})

onBeforeUnmount(() => {
  document.body.classList.remove('filelist-fullwidth')
  window.removeEventListener('resize', updateWindowWidth)
})


</script>

<style>
body.filelist-fullwidth .main {
  max-width: 100% !important;
  padding: 0 !important;
  margin-top: 12px !important;
}
</style>

<style scoped>
.page {
  height: calc(100vh - 140px);
  width: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.content {
  margin: 0;
  height: 100%;
  width: 100%;
  flex: 1;
  overflow: hidden;
}

.resizable-layout {
  display: flex;
  width: 100%;
  gap: 0;
  overflow: hidden;
}

.left-panel {
  min-width: 240px;
  max-width: 700px;
  height: 100%;
  overflow: hidden;
  flex-shrink: 0;
}

.right-panel {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  height: 100%;
}

.drag-divider {
  width: 8px;
  cursor: col-resize;
  position: relative;
  transition: background 0.15s;
}

.drag-divider:hover {
  background: #dcdfe6;
}

.drag-divider::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 3px;
  width: 2px;
  background: #c0c4cc;
}

.panel-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-card :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
}

.all-files-btn {
  width: 100%;
  margin-top: 10px;
}

.folder-tree {
  margin-top: 10px;
}

.tree-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  overflow: hidden;
}

.tree-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.tree-actions {
  flex-shrink: 0;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.files-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.path-breadcrumb {
  display: flex;
  align-items: center;
  gap: 2px;
  overflow: hidden;
}

.path-sep {
  margin: 0 4px;
  color: #606266;
}

.header-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.selected-count {
  color: #606266;
  font-size: 12px;
}

.action-buttons {
  display: flex;
  gap: 10px;
  align-items: center;
}

.file-picker-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-name {
  color: #606266;
  font-size: 13px;
}

.pick-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.picked-text {
  color: #606266;
  font-size: 13px;
}

.content {
  margin-top: 10px;
}

.inner :deep(.el-card__body) {
  height: 400px;
  overflow: auto;
}
</style>
