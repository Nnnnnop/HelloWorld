<template>
  <div class="page">
    <el-card class="page-card">
    <template #header>
      <div class="header-row">
        <span>Upload Folder</span>
        <el-space>
          <el-button v-if="isNarrow" size="small" plain @click="showFolderPanel = !showFolderPanel">
            {{ showFolderPanel ? 'Hide folders' : 'Show folders' }}
          </el-button>
          <el-button type="primary" :disabled="!canUpload" :loading="loading" @click="uploadAll">Upload</el-button>
          <el-button @click="clearAll">Clear</el-button>
        </el-space>
      </div>
    </template>

    <div class="pick-row">
      <input
        ref="dirInput"
        type="file"
        webkitdirectory
        multiple
        style="display: none"
        @change="onPickDirectory"
      />
      <el-button type="primary" @click="openDirectoryPicker">
        Select Folder
      </el-button>
      <span class="picked-text">{{ pickedDirectoryText }}</span>
    </div>

    <el-alert
      v-if="!auth.isAdmin"
      type="error"
      show-icon
      title="Admin only"
      description="Only administrators can upload folders."
    />

    <el-row v-if="folderTree.length" :gutter="16" class="content">
      <el-col v-if="!isNarrow || showFolderPanel" :span="8">
        <el-card class="inner">
          <template #header>
            <div class="folder-header">
              <span>Folders (preview)</span>
              <el-button size="small" type="primary" @click="selectAllPreview">All Files</el-button>
            </div>
          </template>
          <el-tree
            :data="folderTree"
            node-key="tempId"
            default-expand-all
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
                  <el-button link type="primary" size="small" @click.stop="openFolderEdit(data)">Edit</el-button>
                  <el-button link type="danger" size="small" @click.stop="removePreviewFolder(data)">Remove</el-button>
                  <el-select
                    v-model="data.level"
                    size="small"
                    style="width: 90px"
                    @change="onFolderLevelChange(data)"
                  >
                    <el-option label="HIDDEN" value="HIDDEN" />
                    <el-option label="Public" value="L1" />
                    <el-option label="Member: Level1" value="L2" />
                    <el-option label="Member: Level2" value="L3" />
                  </el-select>
                </el-space>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card class="inner">
          <template #header>
            <div class="header-row">
              <span>Files to be uploaded ({{ fileRows.length }})</span>
              <el-input v-model="search" clearable placeholder="Search filename or .ext" style="width: 240px" />
            </div>
          </template>

          <el-table :data="filteredFileRows" height="400" row-key="id">
            <el-table-column prop="displayName" label="File name" min-width="220" />
            <el-table-column prop="folderPath" label="Folder" min-width="220" />
            <el-table-column label="Access" width="110">
              <template #default="{ row }">
                <el-select v-model="row.level" size="small" style="width: 95px">
                  <el-option label="HIDDEN" value="HIDDEN" />
                  <el-option label="Public" value="L1" />
                  <el-option label="Member: Level1" value="L2" />
                  <el-option label="Member: Level2" value="L3" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="160">
              <template #default="{ row }">
                <el-button size="small" type="warning" plain @click="openFileEdit(row)">Edit</el-button>
                <el-button size="small" type="danger" plain @click="removeFileRow(row)">Remove</el-button>
              </template>
            </el-table-column>
          </el-table>
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
          <el-select v-model="editingFile.folderTempId" style="width: 100%">
            <el-option label="Root" value="ROOT" />
            <el-option
              v-for="option in folderParentOptions"
              :key="option.tempId"
              :label="option.label"
              :value="option.tempId"
            />
          </el-select>
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
            <el-option label="Public" value="L1" />
            <el-option label="Member: Level1" value="L2" />
            <el-option label="Member: Level2" value="L3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fileEditVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveFileEdit">Save</el-button>
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
            <el-option label="Public" value="L1" />
            <el-option label="Member: Level1" value="L2" />
            <el-option label="Member: Level2" value="L3" />
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
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { uploadBulk } from '../api/fileApi'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const dirInput = ref(null)
const pickedFiles = ref([])
const folderById = ref(new Map())
const folderTree = ref([])
const fileRows = ref([])
const search = ref('')
const selectedPreviewFolderId = ref('ROOT')
const isNarrow = ref(window.innerWidth <= 1000)
const showFolderPanel = ref(!isNarrow.value)
const loading = ref(false)

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
  dirInput.value?.click()
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
      fileObj: entry.fileObj
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
}

const onPreviewFolderSelect = (data) => {
  if (!data?.tempId) return
  selectedPreviewFolderId.value = data.tempId
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
  category: '',
  description: '',
  tags: '',
  level: ''
})

const updateWindowWidth = () => {
  const narrow = window.innerWidth <= 1000
  isNarrow.value = narrow
  showFolderPanel.value = !narrow
}

const openFileEdit = (row) => {
  editingFile.value = {
    id: row.id,
    displayName: row.displayName,
    folderTempId: row.folderTempId,
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
  folderTree.value = folderById.value.get('ROOT')?.children || []
  folderEditVisible.value = false
}

const removeFileRow = (row) => {
  fileRows.value = fileRows.value.filter((item) => item.id !== row.id)
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

const filteredFileRows = computed(() => {
  const kw = search.value.trim().toLowerCase()
  return fileRows.value.filter((row) => {
    if (selectedPreviewFolderId.value !== 'ROOT' && row.folderTempId !== selectedPreviewFolderId.value) {
      return false
    }
    if (!kw) return true
    const name = String(row.displayName || '').toLowerCase()
    const ext = name.slice(name.lastIndexOf('.') + 1).toLowerCase()
    return name.includes(kw) || (kw.startsWith('.') ? ext.includes(kw.slice(1)) : ext.includes(kw) || name.includes(kw))
  })
})

const clearAll = () => {
  pickedFiles.value = []
  folderById.value = new Map()
  folderTree.value = []
  fileRows.value = []
  search.value = ''
  selectedPreviewFolderId.value = 'ROOT'
  if (dirInput.value) dirInput.value.value = ''
}

const uploadAll = async () => {
  if (!canUpload.value) return
  loading.value = true
  try {
    const folders = []
    for (const [key, node] of folderById.value.entries()) {
      if (key === 'ROOT') continue
      folders.push({
        tempId: node.tempId,
        parentTempId: node.parentTempId || 'ROOT',
        name: node.name
      })
    }

    const files = fileRows.value.map((row) => ({
      folderTempId: row.folderTempId || 'ROOT',
      clientPath: row.clientPath,
      displayName: row.displayName,
      minAccessLevel: row.level,
      category: row.category || null,
      description: row.description || null,
      tags: row.tags || null
    }))

    const manifest = { folders, files }
    const form = new FormData()
    form.append('manifest', JSON.stringify(manifest))
    for (const row of fileRows.value) {
      form.append('files', row.fileObj, row.clientPath)
    }

    await uploadBulk(form)
    ElMessage.success('Upload successful')
    clearAll()
    router.push('/home/internal_portal/files')
  } catch (error) {
    ElMessage.error(error.message || 'Upload failed')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  document.body.classList.add('upload-folder-fullwidth')
  window.addEventListener('resize', updateWindowWidth)
  updateWindowWidth()
})

onBeforeUnmount(() => {
  document.body.classList.remove('upload-folder-fullwidth')
  window.removeEventListener('resize', updateWindowWidth)
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
  width: 100%;
  padding: 0;
}

.page-card :deep(.el-card__body) {
  padding: 20px;
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

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}

.tree-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-actions {
  flex: 0 0 auto;
}

.empty-state {
  color: #909399;
  margin-top: 24px;
}
</style>
