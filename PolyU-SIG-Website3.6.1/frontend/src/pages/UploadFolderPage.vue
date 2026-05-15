<template>
  <div class="page">
    <el-card class="page-card" ref="pageCard">
    <template #header>
      <div class="header-row">
        <div class="header-left-group">
          <span>Upload Folder</span>
          <el-button type="primary" size="small" @click="openDirectoryPicker">Select Folder</el-button>
          <span class="picked-text">{{ pickedDirectoryText }}</span>
        </div>
        <el-space>
          <el-button v-if="isNarrow" size="small" plain @click="showFolderPanel = !showFolderPanel">
            {{ showFolderPanel ? 'Hide folders' : 'Show folders' }}
          </el-button>
          <el-button type="primary" :disabled="!canUpload" :loading="loading" @click="uploadAll">Upload</el-button>
          <el-button @click="clearAll">Clear</el-button>
        </el-space>
      </div>
    </template>

    <input
      ref="dirInput"
      type="file"
      webkitdirectory
      multiple
      style="display: none"
      @change="onPickDirectory"
    />

    <el-alert
      v-if="!auth.isAdmin"
      type="error"
      show-icon
      title="Admin only"
      description="Only administrators can upload folders."
    />

    <el-alert
      v-if="uploadError"
      type="error"
      show-icon
      closable
      title="Upload Error"
      @close="uploadError = null"
      style="margin-bottom: 16px; margin-top: 12px;"
    >
      <div style="white-space: pre-line;">{{ uploadError }}</div>
    </el-alert>

    <el-row v-if="folderTree.length" :gutter="16" class="content">
      <el-col v-if="!isNarrow || showFolderPanel" :span="5">
        <el-card class="inner">
          <template #header>
            <div class="folder-header">
              <span>Folders (preview)</span>
              <el-button size="small" type="primary" @click="selectAllPreview">All Files</el-button>
            </div>
          </template>
          <div class="folder-container">
            <el-tree
              :data="folderTree"
              node-key="tempId"
              :default-expand-all="expandFolderTreeByDefault"
              :expand-on-click-node="false"
              :props="{ label: 'name', children: 'children' }"
              :current-node-key="selectedPreviewFolderId === 'ROOT' ? null : selectedPreviewFolderId"
              highlight-current
              @node-click="onPreviewFolderSelect"
            >
              <template #default="{ data }">
                <span class="tree-row">
                  <span class="tree-name" @click.stop="onPreviewFolderSelect(data)">
                    {{ data.name || 'Root' }}
                  </span>
                  <el-space class="tree-actions" size="small">
                    <el-select
                      v-model="data.level"
                      size="small"
                      style="width: 125px"
                      @change="onFolderLevelChange(data)"
                    >
                      <el-option label="HIDDEN" value="HIDDEN" />
                      <el-option label="Public" value="PUBLIC" />
                      <el-option label="Member: Level 1" value="L1" />
                      <el-option label="Member: Level 2" value="L2" />
                    </el-select>
                  </el-space>
                </span>
              </template>
            </el-tree>
          </div>
        </el-card>
      </el-col>

      <el-col :span="19">
        <el-card class="inner">
          <template #header>
            <div class="header-row">
              <div>
                <div class="files-title">
                  <div>Files to be uploaded ({{ fileRows.length }})</div>
                </div>
                <div class="upload-summary" v-if="uploadProgress > 0">
                  <template v-if="uploadPhase === 'sending'">
                    Transfer to server: {{ uploadProgress }}% — {{ uploadedFileCount }} / {{ totalFileCount }} files received (not yet finalized on disk)
                  </template>
                  <template v-else-if="uploadPhase === 'processing'">
                    Server finalizing: {{ uploadProgress }}% — {{ serverFinalizedFileCount }} / {{ totalFileCount }} files moved, saved, and indexed
                  </template>
                </div>
              </div>
              <div class="header-actions">
                <el-space size="small">
                  <el-button v-if="selectedRows.length" size="small" type="warning" @click="openBatchMove">
                    Move selected
                  </el-button>
                  <el-button v-if="selectedRows.length" size="small" type="danger" @click="deleteSelectedRows">
                    Delete selected
                  </el-button>
                  <span v-if="selectedRows.length" class="selected-count">{{ selectedRows.length }} selected</span>
                </el-space>
                <el-space size="small" style="align-items: center;">
                  <el-input v-model="search" clearable placeholder="Search filename or .ext" style="width: 240px" />
                </el-space>
              </div>
            </div>
            <div v-if="selectedPreviewFolderId !== 'ROOT' && displayPathParts.length" ref="pathRow" class="path-row">
              <div class="path-breadcrumb">
                <template v-for="(part, index) in displayPathParts" :key="part.type === 'ellipsis' ? part.key : part.tempId">
                  <el-button
                    v-if="part.type === 'part'"
                    link
                    type="primary"
                    size="small"
                    @click="goToFolder(part.tempId)"
                  >
                    {{ part.name }}
                  </el-button>
                  <span v-else class="path-ellipsis">...</span>
                  <span v-if="index < displayPathParts.length - 1" class="path-sep">&gt;</span>
                </template>
              </div>
            </div>
          </template>

          <div class="body-content">
            <el-progress v-if="uploadProgress > 0" :percentage="uploadProgress" status="active" style="margin-bottom: 12px;" />
            <div class="table-container">
              <el-table ref="fileTable" :data="pagedRows" height="100%" row-key="id" :row-class-name="tableRowClassName" @selection-change="onSelectionChange" style="width: 100%">
                <el-table-column type="selection" width="55" align="center" />
                <el-table-column label="File name" min-width="220">
                  <template #default="{ row }">
                    <span v-if="row.type === 'folder'" class="folder-link" @click.stop="navigateIntoFolder(row)">
                      {{ row.displayName }}
                    </span>
                    <span v-else>{{ row.displayName }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="folderPath" label="Folder" min-width="220">
                  <template #default="{ row }">
                    {{ row.folderPath }}
                  </template>
                </el-table-column>
                <el-table-column label="Status" width="90">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      :type="row.status === 'Failed' ? 'danger' : row.status === 'Uploaded' ? 'success' : row.status === 'Sent' ? 'warning' : 'info'"
                    >
                      {{ row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="Access" width="140">
                  <template #default="{ row }">
                    <el-select
                      v-model="row.level"
                      size="small"
                      style="width: 125px"
                      @change="onFolderRowAccessChange(row)"
                    >
                      <el-option label="HIDDEN" value="HIDDEN" />
                      <el-option label="Public" value="PUBLIC" />
                      <el-option label="Member: Level 1" value="L1" />
                      <el-option label="Member: Level 2" value="L2" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="Actions" width="100" fixed="right">
                  <template #default="{ row }">
                        <el-dropdown trigger="click" @command="(command) => handleRowAction(row, command)">
                      <el-button size="small" type="primary" plain>
                        <i class="el-icon-more"></i>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="move">Move</el-dropdown-item>
                          <el-dropdown-item command="edit">Edit</el-dropdown-item>
                          <el-dropdown-item command="remove">Remove</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div v-if="showFileTablePager" class="file-table-pager">
              <el-pagination
                v-model:current-page="fileTablePage"
                v-model:page-size="fileTablePageSize"
                :page-sizes="[50, 100, 150, 200]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="filteredRows.length"
              />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="!folderTree.length" class="empty-state">
      No folder selected yet. Please select a folder to upload.
    </div>

    <el-dialog v-model="fileEditVisible" title="Edit file" width="500px">
      <el-form label-width="120px">
        <el-form-item label="File Name">
          <el-input v-model="editingFile.displayName" />
        </el-form-item>
        <el-form-item label="Folder">
          <el-input v-model="editingFile.folderPath" disabled />
        </el-form-item>
        <el-form-item label="Category">
          <el-select v-model="editingFile.category" style="width: 100%">
            <el-option label="reference-code" value="reference-code" />
            <el-option label="research-report" value="research-report" />
            <el-option label="self-study" value="self-study" />
          </el-select>
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="editingFile.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="Tags">
          <el-input v-model="editingFile.tags" placeholder="Comma separated tags" />
        </el-form-item>
        <el-form-item label="Access">
          <el-select v-model="editingFile.level" style="width: 100%">
            <el-option label="HIDDEN" value="HIDDEN" />
            <el-option label="Public" value="PUBLIC" />
            <el-option label="Member: Level 1" value="L1" />
            <el-option label="Member: Level 2" value="L2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fileEditVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveFileEdit">Save</el-button>
      </template>
    </el-dialog>

      <el-dialog
      v-model="batchMoveVisible"
      :title="moveDialogTitle"
      width="780px"
      class="move-dialog"
    >
      <div class="move-layout">

        <!-- LEFT -->
        <div
          class="move-left"
          :style="{ width: moveLeftWidth + 'px' }"
        >
          <el-card class="move-panel-card" shadow="never">

            <template #header>
              <div class="move-header">
                <span>Select destination</span>

                <el-input
                  v-model="moveFolderSearch"
                  size="small"
                  clearable
                  placeholder="Search folder"
                />
              </div>
            </template>

            <div class="move-tree">
              <el-tree
                class="dialog-folder-tree"
                :data="filteredMoveFolders"
                node-key="tempId"
                :props="{ label: 'name', children: 'children' }"
                :current-node-key="getDialogTreeCurrentNodeKey(batchMoveFolderId)"
                highlight-current
                :expand-on-click-node="false"
                @current-change="onBatchMoveFolderSelect"
              >
                <template #default="{ data }">
                  <span class="tree-row">
                    <span
                      class="tree-name"
                      @click.stop="onBatchMoveFolderSelect(data)"
                    >
                      {{ data.name || 'Root' }}
                    </span>
                  </span>
                </template>
              </el-tree>
            </div>

          </el-card>
        </div>

        <!-- RESIZER -->
        <div
          class="move-divider"
          @mousedown="startMoveResize"
        />

        <!-- RIGHT -->
        <div class="move-right">

          <el-card class="move-panel-card" shadow="never">

            <template #header>
              <div class="move-header">
                <span>Move summary</span>
              </div>
            </template>

            <div class="move-summary">

              <div class="move-destination">
                <div class="move-path-label">
                  Destination
                </div>

                <div class="move-path-value">
                  {{ buildFolderPath(batchMoveFolderId) || 'Root' }}
                </div>
              </div>

              <div class="move-summary-list">

                <div
                  v-for="row in moveRows"
                  :key="row.id"
                  class="move-summary-item"
                >
                  <el-tag
                    size="small"
                    :type="row.type === 'folder' ? 'warning' : 'info'"
                  >
                    {{ row.type === 'folder' ? 'Folder' : 'File' }}
                  </el-tag>

                  <span>
                    {{ row.displayName }}
                  </span>
                </div>

              </div>

            </div>

          </el-card>

        </div>

      </div>

      <template #footer>
        <el-button @click="batchMoveVisible = false">
          Cancel
        </el-button>

        <el-button
          type="primary"
          :disabled="!batchMoveFolderId"
          :loading="batchMoveLoading"
          @click="moveSelectedRows"
        >
          Move
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="folderEditVisible" title="Edit folder" width="520px">
      <el-form label-width="120px">
        <el-form-item label="Folder name">
          <el-input v-model="folderEditing.name" />
        </el-form-item>
        <el-form-item label="Parent folder">
          <el-select v-model="folderEditing.parentTempId" style="width: 100%">
            <el-option label="Root" value="ROOT" />
            <el-option
              v-for="option in folderParentOptions"
              :key="option.tempId"
              :label="option.label"
              :value="option.tempId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Access level">
          <el-select v-model="folderEditing.level" style="width: 100%">
            <el-option label="HIDDEN" value="HIDDEN" />
            <el-option label="Public" value="PUBLIC" />
            <el-option label="Member: Level 1" value="L1" />
            <el-option label="Member: Level 2" value="L2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderEditVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveFolderEdit">Save</el-button>
      </template>
    </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { initializeUploadSession, uploadSessionFile, completeUploadSession } from '../api/fileApi'
import { createFolder } from '../api/folderApi'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const dirInput = ref(null)
const pageCard = ref(null)
const pickedFiles = ref([])
const folderById = ref(new Map())
const folderTree = ref([])
const fileRows = ref([])
const search = ref('')
const selectedPreviewFolderId = ref('ROOT')
const selectedFolderNode = computed(() => {
  if (selectedPreviewFolderId.value === 'ROOT') return folderById.value.get('ROOT')
  return folderById.value.get(selectedPreviewFolderId.value) || folderById.value.get('ROOT')
})
const currentFolderChildFolders = computed(() => selectedFolderNode.value?.children || [])
const currentFolderFiles = computed(() => {
  if (selectedPreviewFolderId.value === 'ROOT') return fileRows.value
  return fileRows.value.filter((row) => row.folderTempId === selectedPreviewFolderId.value)
})
const currentPathParts = computed(() => {
  if (selectedPreviewFolderId.value === 'ROOT') return []
  const parts = []
  let current = folderById.value.get(selectedPreviewFolderId.value)
  while (current && current.tempId !== 'ROOT') {
    parts.unshift({ tempId: current.tempId, name: current.name })
    current = current.parentTempId !== 'ROOT' ? folderById.value.get(current.parentTempId) : null
  }
  return parts
})
const pathRowRef = ref(null)
const breadcrumbCollapsed = ref(false)
const displayPathParts = computed(() => {
  const parts = currentPathParts.value
  if (!parts.length) return []
  if (!breadcrumbCollapsed.value || parts.length <= 3) {
    return parts.map((part) => ({ ...part, type: 'part' }))
  }
  return [
    { ...parts[0], type: 'part' },
    { type: 'ellipsis', key: 'ellipsis' },
    { ...parts[parts.length - 1], type: 'part' }
  ]
})
const updateBreadcrumbCollapsed = () => {
  const row = pathRowRef.value
  if (!row || !currentPathParts.value.length) {
    breadcrumbCollapsed.value = false
    return
  }
  const style = window.getComputedStyle(row)
  const lineHeight = parseFloat(style.lineHeight) || 24
  const maxHeight = lineHeight * 2.2
  breadcrumbCollapsed.value = row.scrollHeight > maxHeight
}

watch([currentPathParts, selectedPreviewFolderId], () => {
  nextTick(updateBreadcrumbCollapsed)
})
const isNarrow = ref(window.innerWidth <= 1000)
const showFolderPanel = ref(!isNarrow.value)
const panelHeight = ref('600px')
const loading = ref(false)
const uploadProgress = ref(0)
const uploadedFileCount = ref(0)
const totalFileCount = ref(0)
const totalFileSize = ref(0)
const uploadError = ref(null)
const uploadSessionId = ref(null)
/** 'idle' | 'sending' (HTTP to app) | 'processing' (server queue: move, DB, search index) */
const uploadPhase = ref('idle')
/** During processing phase, from GET session status (files fully finalized on server). */
const serverFinalizedFileCount = ref(0)

/** Progress bar 0–this value while browser uploads; remainder reflects server-side finalization. */
const CLIENT_TRANSFER_PROGRESS_CAP = 88

/** Parallel browser uploads; bounded so we do not overwhelm the browser or server. */
const maxBrowserUploadConcurrency = (() => {
  const cores = typeof navigator !== 'undefined' && navigator.hardwareConcurrency ? navigator.hardwareConcurrency : 4
  return Math.min(10, Math.max(5, Math.floor(cores * 0.75)))
})()

/** Paginate the file table so 500+ rows are not all in the DOM at once. */
const fileTablePage = ref(1)
const fileTablePageSize = ref(100)
const fileTable = ref(null)
const selectedRows = ref([])
const moveRows = ref([])
const singleMoveMode = ref(false)
const batchMoveVisible = ref(false)
const batchMoveFolderId = ref('ROOT')
const batchMoveLoading = ref(false)
const moveFolderSearch = ref('')
const moveLeftWidth = ref(320)
const MIN_MOVE_LEFT = 240
const MAX_MOVE_LEFT = 500

const moveDialogTitle = computed(() => singleMoveMode.value ? 'Move item' : 'Move selected items')

const dialogFolderTree = computed(() => {
  const hasRootNode = folderTree.value.some((node) => node.tempId === 'ROOT')
  if (hasRootNode) {
    return folderTree.value
  }
  return [
    {
      tempId: 'ROOT',
      name: 'Root',
      children: folderTree.value
    }
  ]
})

const getDialogTreeCurrentNodeKey = (folderId) => {
  const key = String(folderId || 'ROOT')
  if (key === 'ROOT') {
    const rootNode = dialogFolderTree.value.find((node) => node.tempId === 'ROOT')
    return rootNode ? String(rootNode.tempId) : 'ROOT'
  }
  return key
}

const onBatchMoveFolderSelect = (node) => {
  if (!node) return
  batchMoveFolderId.value = String(node.tempId || 'ROOT')
}

const getRouteFolderId = () => {
  const folderQuery = route.query.folder
  return folderQuery == null || String(folderQuery) === '' ? 'ROOT' : String(folderQuery)
}

const syncFolderFromRoute = () => {
  const folderId = getRouteFolderId()
  if (String(selectedPreviewFolderId.value) !== folderId) {
    selectedPreviewFolderId.value = folderId
  }
}

const updateRouteFolder = (folderId) => {
  const targetFolder = folderId === 'ROOT' ? undefined : String(folderId)
  const currentFolder = getRouteFolderId()
  if (currentFolder === (targetFolder || 'ROOT')) return

  const query = { ...route.query }
  if (targetFolder === undefined) {
    delete query.folder
  } else {
    query.folder = targetFolder
  }

  router.push({ path: route.path, query })
}

watch(() => [route.path, route.query.folder], syncFolderFromRoute)
watch(selectedPreviewFolderId, () => {
  selectedRows.value = []
  fileTable.value?.clearSelection?.()
})

const canUpload = computed(() => auth.isAdmin && fileRows.value.length > 0)

const pickedDirectoryText = computed(() => {
  if (!pickedFiles.value.length) {
    return 'No folder selected'
  }
  const firstPath = pickedFiles.value[0]?.webkitRelativePath || ''
  const folderName = firstPath.split('/')[0] || 'Folder'
  return `${folderName} (${pickedFiles.value.length} files)`
})

const openDirectoryPicker = () => {
  const input = dirInput.value
  if (input) {
    input.value = ''
  }
  input?.click()
}

const normalizePath = (p) =>
  String(p || '')
    .replaceAll('\\', '/')
    .replace(/^\/+/, '')

const splitPath = (p) => normalizePath(p).split('/').filter(Boolean)

const buildFolderTree = (entries) => {
  const map = new Map()
  const root = {
    tempId: 'ROOT',
    parentTempId: '',
    name: '',
    level: 'HIDDEN',
    children: []
  }
  map.set('ROOT', root)

  const getOrCreateChild = (parent, name) => {
    const key = `${parent.tempId}/${name}`
    if (map.has(key)) return map.get(key)
    const node = { tempId: key, parentTempId: parent.tempId, name, level: parent.level, children: [] }
    map.set(key, node)
    parent.children.push(node)
    return node
  }

  for (const entry of entries) {
    const rel = normalizePath(entry.clientPath)
    const parts = splitPath(rel)
    const folderParts = parts.slice(0, Math.max(0, parts.length - 1))
    let cur = root
    for (const seg of folderParts) {
      cur = getOrCreateChild(cur, seg)
    }
  }

  sortFolderTree(root)
  folderById.value = map
  folderTree.value = root.children
}

const buildFileRows = (entries) => {
  const rows = []
  for (const entry of entries) {
    const clientPath = normalizePath(entry.clientPath)
    const parts = splitPath(clientPath)
    const fileName = parts[parts.length - 1] || entry.fileObj?.name || 'file'
    const folderParts = parts.slice(0, Math.max(0, parts.length - 1))
    const folderTempId = folderParts.length ? `ROOT/${folderParts.join('/')}` : 'ROOT'
    const folderPath = folderParts.join(' / ')
    rows.push({
      id: clientPath,
      clientPath,
      folderTempId,
      displayName: fileName,
      category: 'reference-code',
      description: '',
      tags: '',
      level: 'HIDDEN',
      folderPath,
      fileObj: entry.fileObj,
      status: 'Ready',
      progress: 0
    })
  }
  fileRows.value = rows
}

const buildUploadEntries = (files) => {
  return Array.from(files).map((fileObj) => {
    const rel = normalizePath(fileObj.webkitRelativePath || fileObj.name)
    return { fileObj, clientPath: rel }
  })
}

const applyInheritanceFrom = (folderNode) => {
  const walk = (node) => {
    for (const child of node.children || []) {
      child.level = node.level
      walk(child)
    }
  }
  walk(folderNode)

  const descendantPrefix = `${folderNode.tempId}/`
  for (const row of fileRows.value) {
    if (row.folderTempId === folderNode.tempId || row.folderTempId.startsWith(descendantPrefix)) {
      row.level = folderNode.level
    }
  }
}

const buildFolderPath = (folderTempId) => {
  if (!folderTempId || folderTempId === 'ROOT') return ''
  const parts = []
  let current = folderById.value.get(folderTempId)
  while (current && current.tempId !== 'ROOT') {
    parts.unshift(current.name)
    current = folderById.value.get(current.parentTempId)
  }
  return parts.join(' / ')
}

const selectAllPreview = () => {
  selectedPreviewFolderId.value = 'ROOT'
  updateRouteFolder('ROOT')
}

const onPreviewFolderSelect = (data) => {
  if (!data?.tempId) return
  selectedPreviewFolderId.value = data.tempId
  updateRouteFolder(data.tempId)
}

const goToFolder = (tempId) => {
  selectedPreviewFolderId.value = tempId
  updateRouteFolder(tempId)
}

const openFolderEdit = (data) => {
  if (!data) return
  folderEditing.value = {
    tempId: data.tempId,
    name: data.name,
    parentTempId: data.parentTempId || 'ROOT',
    level: data.level
  }
  folderEditVisible.value = true
}

const removePreviewFolder = (node) => {
  if (!node || node.tempId === 'ROOT') return
  const removedPrefix = `${node.tempId}/`
  for (const key of Array.from(folderById.value.keys())) {
    if (key === node.tempId || key.startsWith(removedPrefix)) {
      folderById.value.delete(key)
    }
  }
  const parent = folderById.value.get(node.parentTempId)
  if (parent) {
    parent.children = parent.children.filter((child) => child.tempId !== node.tempId)
  }
  const root = folderById.value.get('ROOT')
  folderTree.value = root?.children || []
  fileRows.value = fileRows.value.filter(
    (row) => row.folderTempId !== node.tempId && !row.folderTempId.startsWith(removedPrefix)
  )
  if (selectedPreviewFolderId.value === node.tempId || selectedPreviewFolderId.value.startsWith(removedPrefix)) {
    selectedPreviewFolderId.value = 'ROOT'
  }
}

const folderEditVisible = ref(false)
const folderEditing = ref({
  tempId: null,
  name: '',
  parentTempId: 'ROOT',
  level: 'HIDDEN'
})

const fileEditVisible = ref(false)
const editingFile = ref({
  id: null,
  displayName: '',
  folderTempId: '',
  folderPath: '',
  category: '',
  description: '',
  tags: '',
  level: ''
})

const updateWindowWidth = () => {
  const narrow = window.innerWidth <= 1000
  isNarrow.value = narrow
  showFolderPanel.value = !narrow
  const cardTop = pageCard.value?.getBoundingClientRect().top || 0
  const newHeight = Math.max(320, window.innerHeight - cardTop - 36)
  panelHeight.value = `${newHeight}px`
}

const openFileEdit = (row) => {
  editingFile.value = {
    id: row.id,
    displayName: row.displayName,
    folderTempId: row.folderTempId,
    folderPath: buildFolderPath(row.folderTempId) || 'Root',
    category: row.category || '',
    description: row.description || '',
    tags: row.tags || '',
    level: row.level
  }
  fileEditVisible.value = true
}

const saveFileEdit = () => {
  const index = fileRows.value.findIndex((row) => row.id === editingFile.value.id)
  if (index >= 0) {
    fileRows.value[index] = {
      ...fileRows.value[index],
      displayName: editingFile.value.displayName,
      folderTempId: editingFile.value.folderTempId,
      category: editingFile.value.category,
      description: editingFile.value.description,
      tags: editingFile.value.tags,
      level: editingFile.value.level,
      folderPath: buildFolderPath(editingFile.value.folderTempId)
    }
  }
  fileEditVisible.value = false
}

const flattenFolderOptions = (nodes, prefix = '') => {
  const out = []
  for (const n of nodes || []) {
    const label = prefix ? `${prefix} / ${n.name}` : n.name
    out.push({ tempId: n.tempId, label })
    out.push(...flattenFolderOptions(n.children, label))
  }
  return out
}

const folderParentOptions = computed(() => {
  const currentId = folderEditing.value.tempId
  const excluded = new Set()
  if (currentId) {
    const collect = (node) => {
      excluded.add(node.tempId)
      for (const child of node.children || []) {
        collect(child)
      }
    }
    const current = folderById.value.get(currentId)
    if (current) collect(current)
  }

  return [
    { tempId: 'ROOT', label: 'Root' },
    ...flattenFolderOptions(folderTree.value).filter((option) => option.label !== 'Root' && !excluded.has(option.tempId))
  ]
})

const saveFolderEdit = () => {
  const node = folderById.value.get(folderEditing.value.tempId)
  if (!node) {
    folderEditVisible.value = false
    return
  }
  const newName = String(folderEditing.value.name || '').trim()
  if (!newName) {
    ElMessage.error('Folder name cannot be empty')
    return
  }
  const newParentTempId = folderEditing.value.parentTempId || 'ROOT'
  const oldTempId = node.tempId
  const oldParentTempId = node.parentTempId
  const newTempId = newParentTempId === 'ROOT' ? `ROOT/${newName}` : `${newParentTempId}/${newName}`

  const rewriteSubtree = (n, oldPrefix, newPrefix) => {
    for (const child of n.children || []) {
      const oldChildTempId = child.tempId
      const newChildTempId = oldChildTempId.replace(`${oldPrefix}/`, `${newPrefix}/`)
      child.tempId = newChildTempId
      child.parentTempId = child.parentTempId === oldPrefix ? newPrefix : child.parentTempId.replace(`${oldPrefix}/`, `${newPrefix}/`)
      folderById.value.delete(oldChildTempId)
      folderById.value.set(newChildTempId, child)
      rewriteSubtree(child, oldChildTempId, newChildTempId)
    }
  }

  if (newTempId !== oldTempId) {
    const oldParent = folderById.value.get(oldParentTempId)
    if (oldParent) {
      oldParent.children = oldParent.children.filter((child) => child.tempId !== oldTempId)
    }
    const newParent = folderById.value.get(newParentTempId)
    if (newParent) {
      newParent.children.push(node)
    }
    folderById.value.delete(oldTempId)
    node.tempId = newTempId
    node.parentTempId = newParentTempId
    node.name = newName
    folderById.value.set(newTempId, node)
    rewriteSubtree(node, oldTempId, newTempId)

    const oldPrefix = `${oldTempId}/`
    for (const row of fileRows.value) {
      if (row.folderTempId === oldTempId) {
        row.folderTempId = newTempId
      } else if (row.folderTempId.startsWith(oldPrefix)) {
        row.folderTempId = `${newTempId}${row.folderTempId.substring(oldTempId.length)}`
      }
      row.folderPath = buildFolderPath(row.folderTempId)
    }
    if (selectedPreviewFolderId.value === oldTempId || selectedPreviewFolderId.value.startsWith(oldPrefix)) {
      selectedPreviewFolderId.value = selectedPreviewFolderId.value.replace(oldTempId, newTempId)
    }
  } else {
    node.name = newName
  }

  node.level = folderEditing.value.level
  applyInheritanceFrom(node)
    sortFolderTree(folderById.value.get('ROOT'))
}

const removeFileRow = (row) => {
  fileRows.value = fileRows.value.filter((item) => item.id !== row.id)
}

const handleRowAction = (row, command) => {
  if (command === 'move') {
    openSingleMove(row)
  } else if (command === 'edit') {
    if (row.type === 'folder') {
      openFolderEdit(row.folderNode)
    } else {
      openFileEdit(row)
    }
  } else if (command === 'remove') {
    if (row.type === 'folder') {
      removePreviewFolder(row.folderNode)
    } else {
      removeFileRow(row)
    }
  }
}

const onSelectionChange = (rows) => {
  selectedRows.value = rows || []
}

const openBatchMove = () => {
  singleMoveMode.value = false
  moveRows.value = [...selectedRows.value]
  batchMoveFolderId.value = selectedPreviewFolderId.value || getDialogTreeCurrentNodeKey(null)
  batchMoveVisible.value = true
}

const openSingleMove = (row) => {
  singleMoveMode.value = true
  moveRows.value = [row]
  if (row.type === 'folder' && row.folderNode) {
    batchMoveFolderId.value = row.folderNode.parentTempId || 'ROOT'
  } else {
    batchMoveFolderId.value = row.folderTempId || 'ROOT'
  }
  batchMoveVisible.value = true
}

const deleteSelectedRows = () => {
  const rows = selectedRows.value || []
  if (!rows.length) return

  const selectedFolderNodes = rows
    .filter((row) => row.type === 'folder' && row.folderNode)
    .map((row) => row.folderNode)
  const selectedFileIds = new Set(rows.filter((row) => row.type !== 'folder').map((row) => row.id))

  for (const folderNode of selectedFolderNodes) {
    removePreviewFolder(folderNode)
  }

  fileRows.value = fileRows.value.filter((item) => !selectedFileIds.has(item.id))
  selectedRows.value = []
  fileTable.value?.clearSelection?.()
}

const moveFolderNode = (node, newParentTempId) => {
  if (!node || node.tempId === 'ROOT') return
  if (newParentTempId === node.tempId || String(newParentTempId).startsWith(`${node.tempId}/`)) {
    throw new Error('Cannot move a folder into itself or its descendant')
  }

  const oldTempId = node.tempId
  const oldParentTempId = node.parentTempId
  const newTempId = newParentTempId === 'ROOT' ? `ROOT/${node.name}` : `${newParentTempId}/${node.name}`
  if (newTempId === oldTempId) return

  const oldParent = folderById.value.get(oldParentTempId)
  if (oldParent) {
    oldParent.children = oldParent.children.filter((child) => child.tempId !== oldTempId)
  }

  const newParent = folderById.value.get(newParentTempId)
  if (!newParent) throw new Error('Destination folder not found')

  newParent.children.push(node)
  folderById.value.delete(oldTempId)
  node.tempId = newTempId
  node.parentTempId = newParentTempId
  folderById.value.set(newTempId, node)

  const rewriteSubtree = (n, oldPrefix, newPrefix) => {
    for (const child of n.children || []) {
      const oldChildTempId = child.tempId
      const newChildTempId = oldChildTempId.replace(`${oldPrefix}/`, `${newPrefix}/`)
      child.tempId = newChildTempId
      child.parentTempId = child.parentTempId === oldPrefix ? newPrefix : child.parentTempId.replace(`${oldPrefix}/`, `${newPrefix}/`)
      folderById.value.delete(oldChildTempId)
      folderById.value.set(newChildTempId, child)
      rewriteSubtree(child, oldChildTempId, newChildTempId)
    }
  }

  rewriteSubtree(node, oldTempId, newTempId)

  const oldPrefix = `${oldTempId}/`
  for (const row of fileRows.value) {
    if (row.folderTempId === oldTempId) {
      row.folderTempId = newTempId
    } else if (row.folderTempId.startsWith(oldPrefix)) {
      row.folderTempId = `${newTempId}${row.folderTempId.substring(oldTempId.length)}`
    }
    row.folderPath = buildFolderPath(row.folderTempId)
  }
}

const moveSelectedRows = () => {
  if (!moveRows.value.length) return
  if (!batchMoveFolderId.value) {
    ElMessage.error('Please choose a destination folder')
    return
  }

  const selectedFolderNodes = moveRows.value
    .filter((row) => row.type === 'folder' && row.folderNode)
    .map((row) => row.folderNode)
  const topLevelFolders = selectedFolderNodes.filter((node) => {
    return !selectedFolderNodes.some((other) => other !== node && node.tempId.startsWith(`${other.tempId}/`))
  })

  try {
    for (const node of topLevelFolders) {
      moveFolderNode(node, batchMoveFolderId.value)
    }

    for (const row of moveRows.value.filter((row) => row.type !== 'folder')) {
      row.folderTempId = batchMoveFolderId.value
      row.folderPath = buildFolderPath(batchMoveFolderId.value)
    }

    sortFolderTree(folderById.value.get('ROOT'))
    folderTree.value = folderById.value.get('ROOT')?.children || []
    if (!singleMoveMode.value) {
      selectedRows.value = []
      fileTable.value?.clearSelection?.()
    }
    moveRows.value = []
    singleMoveMode.value = false
    batchMoveVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || 'Move failed')
  }
}

const onFolderLevelChange = (node) => {
  applyInheritanceFrom(node)
}

const onPickDirectory = (e) => {
  const list = Array.from(e.target?.files || [])
  pickedFiles.value = list
  if (!list.length) {
    clearAll()
    return
  }
  const entries = buildUploadEntries(list)
  buildFolderTree(entries)
  buildFileRows(entries)
  for (const node of folderTree.value) applyInheritanceFrom(node)
}

const customSort = (a, b) => {
  const strA = String(a || '')
  const strB = String(b || '')

  for (let i = 0; i < Math.min(strA.length, strB.length); i++) {
    const charA = strA.charCodeAt(i)
    const charB = strB.charCodeAt(i)
    const catA = getCharCategory(charA)
    const catB = getCharCategory(charB)

    if (catA !== catB) return catA - catB
    if (charA !== charB) return charA - charB
  }
  return strA.length - strB.length
}

const getCharCategory = (code) => {
  if (code >= 48 && code <= 57) return 0
  if (code >= 97 && code <= 122) return 1
  if (code >= 65 && code <= 90) return 2
  if (code >= 0x4e00 && code <= 0x9fff) return 3
  return 4
}

const tokenizeString = (input) => {
  const raw = String(input || '')
  const tokens = []
  const matches = raw.match(/(\d+|\D+)/g) || []
  for (const token of matches) {
    if (/^\d+$/.test(token)) {
      tokens.push({ type: 'number', raw: token, value: parseInt(token, 10) })
    } else {
      tokens.push({ type: 'text', raw: token })
    }
  }
  return tokens
}

const customFolderSort = (a, b) => {
  const strA = String(a || '')
  const strB = String(b || '')
  const tokensA = tokenizeString(strA)
  const tokensB = tokenizeString(strB)
  const length = Math.min(tokensA.length, tokensB.length)

  for (let i = 0; i < length; i++) {
    const tokenA = tokensA[i]
    const tokenB = tokensB[i]

    if (tokenA.type === 'number' && tokenB.type === 'number') {
      if (tokenA.value !== tokenB.value) return tokenA.value - tokenB.value
      if (tokenA.raw.length !== tokenB.raw.length) return tokenA.raw.length - tokenB.raw.length
      continue
    }

    if (tokenA.type !== tokenB.type) {
      return tokenA.type === 'number' ? -1 : 1
    }

    const cmp = customSort(tokenA.raw, tokenB.raw)
    if (cmp !== 0) return cmp
  }

  return tokensA.length - tokensB.length
}

const sortFolderTree = (node) => {
  node.children.sort((a, b) => customFolderSort(a.name, b.name))
  for (const child of node.children) {
    sortFolderTree(child)
  }
}

const filteredRows = computed(() => {
  const kw = search.value.trim().toLowerCase()
  const folderRows = selectedPreviewFolderId.value === 'ROOT'
    ? []
    : currentFolderChildFolders.value
        .map((folder) => ({
          id: folder.tempId,
          type: 'folder',
          displayName: folder.name || 'Root',
          folderPath: 'Folder',
          level: folder.level,
          folderNode: folder,
          status: 'Ready'
        }))
        .filter((row) => {
          if (!kw) return true
          return String(row.displayName || '').toLowerCase().includes(kw) || String(row.folderPath || '').toLowerCase().includes(kw)
        })

  const fileRowsForFolder = currentFolderFiles.value.filter((row) => {
    if (!kw) return true
    const name = String(row.displayName || '').toLowerCase()
    const ext = name.slice(name.lastIndexOf('.') + 1).toLowerCase()
    return name.includes(kw) || (kw.startsWith('.') ? ext.includes(kw.slice(1)) : ext.includes(kw) || name.includes(kw))
  })

  const combined = [...folderRows, ...fileRowsForFolder]
  return combined.sort((a, b) => {
    if (a.type === 'folder' && b.type !== 'folder') return -1
    if (a.type !== 'folder' && b.type === 'folder') return 1
    return customFolderSort(a.displayName || '', b.displayName || '')
  })
})

const expandFolderTreeByDefault = computed(
  () => folderTree.value.length < 120 && fileRows.value.length < 400
)

const showFileTablePager = computed(() => filteredRows.value.length > fileTablePageSize.value)

const pagedRows = computed(() => {
  const rows = filteredRows.value
  const size = fileTablePageSize.value
  if (rows.length <= size) return rows
  const start = (fileTablePage.value - 1) * size
  return rows.slice(start, start + size)
})

watch([search, selectedPreviewFolderId], () => {
  fileTablePage.value = 1
})

const navigateIntoFolder = (row) => {
  if (!row || row.type !== 'folder') return
  selectedPreviewFolderId.value = row.folderNode?.tempId || row.id
  updateRouteFolder(selectedPreviewFolderId.value)
}

const onFolderRowAccessChange = (row) => {
  if (!row || row.type !== 'folder' || !row.folderNode) return
  row.folderNode.level = row.level
  applyInheritanceFrom(row.folderNode)
}

const tableRowClassName = ({ row }) => {
  return row.type === 'folder' ? 'folder-row' : ''
}

watch(
  () => filteredRows.value.length,
  (len) => {
    const size = fileTablePageSize.value
    const maxPage = Math.max(1, Math.ceil(len / size) || 1)
    if (fileTablePage.value > maxPage) fileTablePage.value = maxPage
  }
)

watch(fileTablePageSize, () => {
  fileTablePage.value = 1
})

const clearAll = () => {
  pickedFiles.value = []
  folderById.value = new Map()
  folderTree.value = []
  fileRows.value = []
  search.value = ''
  uploadProgress.value = 0
  uploadedFileCount.value = 0
  totalFileCount.value = 0
  totalFileSize.value = 0
  uploadError.value = null
  selectedPreviewFolderId.value = 'ROOT'
  fileTablePage.value = 1
  uploadPhase.value = 'idle'
  serverFinalizedFileCount.value = 0
  if (dirInput.value) dirInput.value.value = ''
}

const updateUploadProgress = () => {
  if (uploadPhase.value !== 'sending') return
  if (!fileRows.value.length || totalFileSize.value <= 0) {
    uploadProgress.value = 0
    return
  }

  const uploadedBytes = fileRows.value.reduce((sum, row) => {
    const size = row.fileObj?.size || 0
    return sum + Math.round((row.progress || 0) * size / 100)
  }, 0)
  uploadProgress.value = Math.min(
    CLIENT_TRANSFER_PROGRESS_CAP,
    Math.round((uploadedBytes / totalFileSize.value) * CLIENT_TRANSFER_PROGRESS_CAP)
  )
  uploadedFileCount.value = fileRows.value.filter((row) => row.status === 'Sent' || row.status === 'Failed').length
}

let uploadProgressRaf = null
const scheduleUploadProgressUpdate = () => {
  if (uploadProgressRaf != null) return
  uploadProgressRaf = requestAnimationFrame(() => {
    uploadProgressRaf = null
    updateUploadProgress()
  })
}

const createFolderStructure = async () => {
  const tempIdToFolderId = new Map()
  tempIdToFolderId.set('ROOT', 1) // Root folder ID is always 1

  // Collect all folders in level order (by depth)
  const folders = []
  const collectFolders = (nodes, depth = 0) => {
    for (const node of nodes || []) {
      folders.push({ node, depth })
      collectFolders(node.children, depth + 1)
    }
  }
  collectFolders(folderTree.value)

  // Sort by depth to create parent folders first; parallelize all folders at the same depth.
  folders.sort((a, b) => a.depth - b.depth)
  const maxDepth = folders.reduce((m, f) => Math.max(m, f.depth), 0)

  for (let d = 0; d <= maxDepth; d++) {
    const atDepth = folders.filter((f) => f.depth === d)
    await Promise.all(
      atDepth.map(async ({ node }) => {
        try {
          const parentId = tempIdToFolderId.get(node.parentTempId) || 1
          const response = await createFolder(node.name, parentId, node.level)
          tempIdToFolderId.set(node.tempId, response.id)
        } catch (error) {
          throw new Error(`Failed to create folder "${node.name}": ${error.message || 'Unknown error'}`)
        }
      })
    )
  }

  return tempIdToFolderId
}

const filterFolderTree = (nodes, keyword) => {
  const kw = String(keyword || '').trim().toLowerCase()

  if (!kw) return nodes

  const walk = (list) => {
    const result = []

    for (const node of list || []) {
      const children = walk(node.children || [])

      const matched =
        String(node.name || '')
          .toLowerCase()
          .includes(kw)

      if (matched || children.length) {
        result.push({
          ...node,
          children
        })
      }
    }

    return result
  }

  return walk(nodes)
}

const uploadAll = async () => {
  if (!canUpload.value) return
  if (!auth.isAdmin) {
    uploadError.value = 'Only administrators can upload folders.'
    return
  }

  loading.value = true
  uploadProgress.value = 0
  uploadedFileCount.value = 0
  totalFileCount.value = fileRows.value.length
  totalFileSize.value = fileRows.value.reduce((sum, row) => sum + (row.fileObj?.size || 0), 0)
  uploadError.value = null
  uploadPhase.value = 'sending'
  serverFinalizedFileCount.value = 0

  let tempIdToFolderId = null

  try {
    // Create folder structure on the server
    tempIdToFolderId = await createFolderStructure()
  } catch (error) {
    uploadError.value = error.message || 'Failed to create folder structure.'
    loading.value = false
    uploadPhase.value = 'idle'
    return
  }

  const sessionId = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `session-${Date.now()}-${Math.random().toString(36).slice(2)}`

  uploadSessionId.value = sessionId

  try {
    await initializeUploadSession(sessionId, totalFileCount.value, totalFileSize.value)
  } catch (error) {
    uploadError.value = error.message || 'Failed to initialize upload session.'
    loading.value = false
    uploadPhase.value = 'idle'
    return
  }

  const queue = [...fileRows.value]
  const worker = async () => {
    while (queue.length > 0) {
      const row = queue.shift()
      if (!row) break

      row.status = 'Uploading'
      row.progress = 0
      scheduleUploadProgressUpdate()

      // Get the actual folder ID from the mapping
      const actualFolderId = tempIdToFolderId.get(row.folderTempId) || 1

      const payload = {
        clientPath: row.clientPath,
        displayName: row.displayName,
        folderId: actualFolderId,
        category: row.category || null,
        description: row.description || null,
        tags: row.tags || null,
        visibility: row.level || 'HIDDEN'
      }

      try {
        await uploadSessionFile(
          sessionId,
          row.fileObj,
          payload,
          (percent) => {
            row.progress = percent
            scheduleUploadProgressUpdate()
          },
          { progressThrottleMs: 100 }
        )
        row.status = 'Sent'
        row.progress = 100
      } catch (error) {
        row.status = 'Failed'
        row.progress = 0
        row.error = error.message || 'Upload failed'
        console.error(`Error uploading ${row.displayName}:`, error)
      } finally {
        updateUploadProgress()
      }
    }
  }

  const workerCount = Math.min(maxBrowserUploadConcurrency, queue.length)
  await Promise.all(Array.from({ length: workerCount }, () => worker()))

  uploadPhase.value = 'processing'
  uploadProgress.value = CLIENT_TRANSFER_PROGRESS_CAP
  serverFinalizedFileCount.value = 0

  try {
    await completeUploadSession(sessionId)
  } catch (error) {
    console.error('Failed to start upload session processing', error)
    uploadError.value = uploadError.value
      ? `${uploadError.value}\nFailed to start processing: ${error.message || 'Unknown error'}`
      : `Failed to start processing: ${error.message || 'Unknown error'}`
    loading.value = false
    uploadPhase.value = 'idle'
    return
  }

  /* move dialog */
  const filteredMoveFolders = computed(() => {
    return filterFolderTree(
      dialogFolderTree.value,
      moveFolderSearch.value
    )
  })

  const startMoveResize = (e) => {
  e.preventDefault()

  const startX = e.clientX
  const startWidth = moveLeftWidth.value

  const onMove = (event) => {
    const next =
      startWidth + (event.clientX - startX)

    moveLeftWidth.value = Math.min(
      MAX_MOVE_LEFT,
      Math.max(MIN_MOVE_LEFT, next)
    )
  }

  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

  // Scale poll budget with batch size (server may still be processing many files after uploads finish).
  const maxPollAttempts = Math.min(900, Math.max(120, Math.ceil(totalFileCount.value / 2) + 90))
  let pollAttempts = 0
  let sessionCompleted = false
  let sessionFailed = false
  let sessionError = ''

  while (pollAttempts < maxPollAttempts && !sessionCompleted && !sessionFailed) {
    await new Promise((resolve) => setTimeout(resolve, 1000)) // Wait 1 second between polls
    pollAttempts++

    try {
      const status = await fetch(`/api/files/upload/session/${encodeURIComponent(sessionId)}/status`, {
        method: 'GET',
        credentials: 'include',
        headers: {
          'Accept': 'application/json'
        }
      })

      if (!status.ok) {
        console.error(`Status check failed with status ${status.status}`)
        continue
      }

      const sessionStatus = await status.json()
      console.debug(`Session ${sessionId} status: ${sessionStatus.status}`, sessionStatus)

      if (uploadPhase.value === 'processing') {
        const sp = Number(sessionStatus.progressPercentage)
        const pct = Number.isFinite(sp) ? Math.max(0, Math.min(100, sp)) : 0
        uploadProgress.value = Math.min(
          100,
          CLIENT_TRANSFER_PROGRESS_CAP + Math.round((pct / 100) * (100 - CLIENT_TRANSFER_PROGRESS_CAP))
        )
        const uf = sessionStatus.uploadedFiles
        if (uf != null && uf !== '') {
          const n = Number(uf)
          if (Number.isFinite(n)) {
            serverFinalizedFileCount.value = Math.min(totalFileCount.value, n)
          }
        }
      }

      if (sessionStatus.status === 'COMPLETED') {
        sessionCompleted = true
      } else if (sessionStatus.status === 'FAILED' || sessionStatus.status === 'CANCELLED') {
        sessionFailed = true
        sessionError = sessionStatus.errorMessage || 'Upload session failed'
      }
    } catch (error) {
      console.error('Error polling session status', error)
      // Continue polling; network errors are transient
    }
  }

  if (!sessionCompleted && !sessionFailed) {
    uploadError.value = 'Upload processing did not complete within the expected time. Please check session status.'
    ElMessage.warning('Upload processing timeout. Files may still be being processed on the server.')
    uploadPhase.value = 'idle'
  } else if (sessionFailed) {
    uploadError.value = `Upload session failed: ${sessionError}`
    ElMessage.error(`Upload processing failed: ${sessionError}`)
    uploadPhase.value = 'idle'
  }

  const failedCount = fileRows.value.filter((row) => row.status === 'Failed').length
  const successCount = fileRows.value.filter((row) => row.status === 'Uploaded' || row.status === 'Sent').length

  if (sessionCompleted && failedCount === 0) {
    for (const row of fileRows.value) {
      if (row.status === 'Sent') row.status = 'Uploaded'
    }
    uploadProgress.value = 100
    uploadedFileCount.value = totalFileCount.value
    serverFinalizedFileCount.value = totalFileCount.value
    ElMessage.success('All files uploaded successfully. Redirecting...')
    await new Promise((resolve) => setTimeout(resolve, 1200))
    clearAll()
    router.push('/home/internal_portal/files')
  } else if (sessionCompleted) {
    const failedFiles = fileRows.value.filter((row) => row.status === 'Failed')
    const failedDetails = failedFiles.map((row) => `• ${row.displayName}: ${row.error || 'Unknown error'}`).join('\n')
    uploadError.value = `${successCount}/${totalFileCount.value} files uploaded successfully. ${failedCount} file(s) failed:\n${failedDetails}`
    ElMessage.error('Some files failed to upload. Review the file statuses and try again.')
    uploadPhase.value = 'idle'
  }

  loading.value = false
}

onMounted(() => {
  document.body.classList.add('upload-folder-fullwidth')
  window.addEventListener('resize', updateWindowWidth)
  window.addEventListener('resize', updateBreadcrumbCollapsed)
  updateWindowWidth()
  syncFolderFromRoute()
  nextTick(updateBreadcrumbCollapsed)
})

onBeforeUnmount(() => {
  if (uploadProgressRaf != null) {
    cancelAnimationFrame(uploadProgressRaf)
    uploadProgressRaf = null
  }
  document.body.classList.remove('upload-folder-fullwidth')
  window.removeEventListener('resize', updateWindowWidth)
  window.removeEventListener('resize', updateBreadcrumbCollapsed)
})
</script>

<style>
body.upload-folder-fullwidth .main {
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

.page-card {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
}

.page-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
  padding: 20px;
}

.upload-summary {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
}

.header-left-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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
  display: flex;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  min-height: 0;
  min-width: 0;
}

.inner :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  padding: 20px;
}

.content :deep(.el-col) {
  height: 100%;
  display: flex;
  min-height: 0;
}

.content :deep(.el-col > .inner) {
  flex: 1;
}

.folder-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: auto;
}

.table-container {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  overflow-x: auto;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto !important;
}

.body-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.table-container {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
}

.folder-link {
  color: #409eff;
  cursor: pointer;
  font-weight: 600;
}

.folder-link:hover {
  text-decoration: underline;
}

.folder-row {
  background: #f4f9ff;
}

.header-row,
.folder-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selected-count {
  color: #606266;
  font-size: 13px;
}

.tree-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.tree-name {
  flex: 1 1 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-actions {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.tree-actions .el-button,
.tree-actions .el-select {
  margin-top: 0 !important;
}

.tree-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-actions {
  flex: 0 0 auto;
}

.folder-container :deep(.el-tree-node__content) {
  height: 32px;
}

.tree-row {
  gap: 4px;
}

.tree-actions {
  gap: 4px;
}

.empty-state {
  color: #909399;
  margin-top: 24px;
}

.files-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.table-container :deep(.el-table th),
.table-container :deep(.el-table td) {
  padding: 6px 0;
}

.path-breadcrumb {
  display: flex;
  align-items: center;
  gap: 2px;
  overflow: hidden;
  flex-wrap: wrap;
}

.path-row {
  margin-top: 8px;
}

.path-sep {
  margin: 0 4px;
  color: #606266;
}

/* MOVE DIALOG */

.move-layout {
  display: flex;
  height: 65vh;
  overflow: hidden;
}

.move-left {
  min-width: 240px;
  max-width: 500px;
  height: 100%;
  overflow: hidden;
  flex-shrink: 0;
}

.move-right {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
}

.move-divider {
  width: 8px;
  cursor: col-resize;
  position: relative;
  transition: background 0.15s;
}

.move-divider:hover {
  background: #dcdfe6;
}

.move-divider::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 3px;
  width: 2px;
  background: #c0c4cc;
}

.move-panel-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.move-panel-card :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
}

.move-tree {
  height: 100%;
}

.move-header {
  display: flex;
  flex-direction: column;
}

.move-destination {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.move-path-label {
  font-size: 13px;
  color: #909399;
}

.move-path-value {
  font-size: 15px;
  font-weight: 600;
  color: #409eff;
  word-break: break-word;
}

.move-summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.move-summary-list {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  max-height: 320px;
  overflow: auto;
}

.move-summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
}

.move-summary-item:last-child {
  border-bottom: none;
}

@media (max-width: 900px) {
  .move-layout {
    flex-direction: column;
    height: auto;
    max-height: 70vh;
  }

  .move-left {
    width: 100% !important;
    max-width: none;
    min-width: 0;
    height: 300px;
  }

  .move-divider {
    display: none;
  }

  .move-right {
    height: auto;
  }
}
</style>
