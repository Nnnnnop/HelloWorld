<template>
  <div class="page">

    <!-- Main content: split layout with resizable divider -->
    <div class="content resizable-layout">
      <!-- LEFT PANEL: Folders -->
      <div v-if="!isNarrow || showFolderPanel" class="left-panel" :style="{ width: leftWidth + 'px' }">
        <el-card class="panel-card">
          <template #header>Folders</template>

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
              </span>
            </template>
          </el-tree>
        </el-card>
      </div>

      <!-- RESIZER -->
      <div v-if="!isNarrow || showFolderPanel" class="drag-divider" @mousedown="startResize"></div>

      <!-- RIGHT PANEL: Files -->
      <div class="right-panel">
        <el-card class="panel-card">
          <template #header>
            <div class="header-row">
              <div class="files-title">
                <span>Files</span>
              </div>

              <div class="header-actions">
                <div class="batch-actions" v-if="selectedFileCount > 0">
                  <el-button size="small" @click="openBatchMove" type="primary" plain>
                    Move selected
                  </el-button>
                  <el-button size="small" @click="downloadSelectedAsZip" type="success" plain>
                    Download selected as zip
                  </el-button>
                  <el-button
                    v-if="auth.isAdmin"
                    size="small"
                    @click="openBatchEdit"
                    type="warning"
                    plain
                  >
                    Edit selected
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
                <el-button v-if="auth.isAdmin" type="primary" @click="showCreateFolderDialog = true" plain>
                  New Folder
                </el-button>
                <el-button v-if="auth.isAdmin" type="primary" @click="goToUploadPage">
                  Upload File
                </el-button>
                <el-button v-if="auth.isAdmin" type="primary" @click="goToUploadFolderPage" plain>
                  Upload Folder
                </el-button>
              </div>
            </div>
            <div v-if="selectedFolderId !== 'all' && displayPathParts.length" ref="pathRowRef" class="path-row">
              <div class="path-breadcrumb">
                <template v-for="(part, index) in displayPathParts" :key="part.type === 'ellipsis' ? part.key : part.id">
                  <el-button
                    v-if="part.type === 'part'"
                    link
                    type="primary"
                    size="small"
                    @click="goToFolder(part.id)"
                  >
                    {{ part.name }}
                  </el-button>
                  <span v-else class="path-ellipsis">...</span>
                  <span v-if="index < displayPathParts.length - 1" class="path-sep">&gt;</span>
                </template>
              </div>
            </div>
          </template>

          <el-table
            ref="filesTable"
            :data="paginatedFiles"
            border
            :row-key="getRowKey"
            @selection-change="onSelectionChange"
            empty-text="no files in this folder"
          >
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column prop="title" label="Name" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="name-cell">
                  <i v-if="row.isFolder" class="el-icon-folder" style="color: #409eff; margin-right: 8px;"></i>
                  <span v-if="row.isFolder" class="folder-name" @click="navigateToFolder(row)" style="cursor: pointer; color: #409eff;">
                    {{ row.title }}
                  </span>
                  <span v-else>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Type" width="80">
              <template #default="{ row }">
                {{ row.isFolder ? 'folder' : getFileType(row.fileName || row.title) }}
              </template>
            </el-table-column>
            <el-table-column prop="category" label="Category" width="150">
              <template #default="{ row }">
                {{ row.isFolder ? '-' : row.category || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="File Size" width="100" align="right">
              <template #default="{ row }">
                {{ row.isFolder ? (row.totalSize != null ? formatFileSize(row.totalSize) : '-') : formatFileSize(row.fileSize || 0) }}
              </template>
            </el-table-column>
            <el-table-column label="Uploaded At" width="180">
              <template #default="{ row }">
                {{ row.isFolder ? (row.latestUploadTime ? formatTime(row.latestUploadTime) : '-') : formatTime(row.uploadTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="visibility" label="Access" width="130" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.visibility" size="small">
                  {{ visibilityLabel(row.visibility) }}
                </el-tag>
                <span v-else>-</span>
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
                      <el-dropdown-item command="info">Info</el-dropdown-item>
                      <el-dropdown-item command="view" v-if="!row.isFolder">View</el-dropdown-item>
                      <el-dropdown-item command="directory" v-if="row.isFolder || selectedFolderId === 'all'">Open</el-dropdown-item>
                      <el-dropdown-item command="download">Download</el-dropdown-item>
                      <el-dropdown-item command="move" v-if="auth.isAdmin">Move</el-dropdown-item>
                      <el-dropdown-item command="edit" v-if="auth.isAdmin">Edit</el-dropdown-item>
                      <el-dropdown-item command="delete" v-if="auth.isAdmin">Delete</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <div class="table-toolbar">
          <el-pagination
            background
            layout="sizes, prev, pager, next, jumper, ->, total"
            :page-size="effectivePageSize"
            :current-page="currentPage"
            :total="totalFilesCount"
            :page-sizes="pageSizes"
            @current-change="(page) => { currentPage = page }"
            @size-change="handlePageSizeChange"
          />
          <el-button
            class="page-all-btn"
            type="primary"
            plain
            :disabled="selectedFolderId === 'all'"
            @click="showAllFiles"
          >
            All
          </el-button>
        </div>
      </div>
    </div>
  </div>

    <!-- FILE EDIT DIALOG -->
    <el-dialog v-model="showCreateFolderDialog" title="New Folder" width="420px">
      <el-form label-width="110px">
        <el-form-item label="Folder name">
          <el-input v-model="newFolderName" placeholder="Enter folder name" />
        </el-form-item>
        <el-form-item label="Parent folder">
          <span>{{ selectedFolderId === 'all' ? 'Root' : getFolderPath(selectedFolderId) }}</span>
        </el-form-item>
        <el-form-item v-if="createFolderError" class="error-text">
          <span style="color: #f56c6c">{{ createFolderError }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeCreateFolderDialog">Cancel</el-button>
        <el-button type="primary" @click="createFolder">Create</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="Edit file info" width="500px">
      <el-form label-width="110px">
        <el-form-item label="File name">
          <el-input v-model="editing.baseName">
            <template #append>{{ editing.ext }}</template>
          </el-input>
        </el-form-item>
        <el-form-item label="Folder" class="form-item-tree">
          <el-tree
            class="dialog-folder-tree"
            :data="dialogFolderTree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            :current-node-key="getDialogTreeCurrentNodeKey(editing.folderId)"
            highlight-current
            :expand-on-click-node="false"
            @current-change="onEditFileFolderSelect"
          >
            <template #default="{ data }">
              <span class="tree-row">
                <span class="tree-name" @click.stop="onEditFileFolderSelect(data)">
                  {{ data.name }}
                </span>
              </span>
            </template>
          </el-tree>
        </el-form-item>
        <el-form-item label="Category">
          <el-select v-model="editing.category" style="width: 100%">
            <el-option label="reference-code" value="reference-code" />
            <el-option label="research-report" value="research-report" />
            <el-option label="self-study" value="self-study" />
          </el-select>
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="editing.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="Tags">
          <el-input v-model="editing.tags" placeholder="Comma separated tags" />
        </el-form-item>
        <el-form-item label="Access">
          <el-select v-model="editing.visibility" style="width: 100%">
            <el-option label="HIDDEN" value="HIDDEN" />
            <el-option label="Public" value="PUBLIC" />
            <el-option label="Member: Level 1" value="L1" />
            <el-option label="Member: Level 2" value="L2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveEdit">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchEditVisible" title="Edit selected items" width="500px">
      <el-form label-width="140px">
        <el-form-item label="Selected items">
          <div>{{ selectedFileCount }} item(s) selected</div>
        </el-form-item>
        <el-form-item label="Category">
          <el-select v-model="batchEditCategory" style="width: 100%" :disabled="!batchEditAppliesToFiles">
            <el-option label="reference-code" value="reference-code" />
            <el-option label="research-report" value="research-report" />
            <el-option label="self-study" value="self-study" />
            <el-option label="(clear)" value="" />
          </el-select>
          <div class="el-form-item__help" v-if="!batchEditAppliesToFiles">
            Category changes apply only to selected files.
          </div>
        </el-form-item>
        <el-form-item label="Access">
          <el-select v-model="batchEditVisibility" style="width: 100%">
            <el-option label="HIDDEN" value="HIDDEN" />
            <el-option label="Public" value="PUBLIC" />
            <el-option label="Member: Level 1" value="L1" />
            <el-option label="Member: Level 2" value="L2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchEditVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveBatchEdit">Save</el-button>
      </template>
    </el-dialog>

    <!-- FILE INFO DIALOG -->
    <el-dialog
      v-model="infoVisible"
      title="File info"
      width="500px"
    >
      <el-form label-width="110px">

        <el-form-item label="Name">
          <div>{{ infoFile.title || '-' }}</div>
        </el-form-item>

        <el-form-item label="Folder">
          <div>{{ getFilePath(infoFile.folderId) || '-' }}</div>
        </el-form-item>

        <el-form-item label="Category">
          <div>{{ infoFile.category || '-' }}</div>
        </el-form-item>

        <el-form-item label="Description">
          <div>{{ infoFile.description || '-' }}</div>
        </el-form-item>

        <el-form-item label="Access">
          <div>{{ visibilityLabel(infoFile.visibility) }}</div>
        </el-form-item>

        <el-form-item label="Size">
          <div>{{ formatFileSize(infoFile.fileSize || 0) }}</div>
        </el-form-item>

        <el-form-item label="Uploaded At">
          <div>{{ formatTime(infoFile.uploadTime) || '-' }}</div>
        </el-form-item>

        <el-form-item label="Tags">
          <div>
            {{
              Array.isArray(infoFile.tags) && infoFile.tags.length
                ? infoFile.tags.join(', ')
                : '-'
            }}
          </div>
        </el-form-item>

      </el-form>

      <template #footer>
        <el-button
          type="primary"
          @click="infoVisible = false"
        >
          Close
        </el-button>
      </template>
    </el-dialog>

    <!-- FOLDER INFO DIALOG -->
    <el-dialog
      v-model="folderInfoVisible"
      title="Folder info"
      width="460px"
    >
      <el-form label-width="110px">

        <el-form-item label="Name">
          <div>{{ folderInfo.name || '-' }}</div>
        </el-form-item>

        <el-form-item label="Total files">
          <div>{{ folderInfo.totalFiles ?? 0 }}</div>
        </el-form-item>

        <el-form-item label="Total size">
          <div>{{ formatFileSize(folderInfo.totalSize || 0) }}</div>
        </el-form-item>

        <el-form-item label="Access">
          <div>{{ visibilityLabel(folderInfo.visibility) }}</div>
        </el-form-item>

      </el-form>

      <template #footer>
        <el-button
          type="primary"
          @click="folderInfoVisible = false"
        >
          Close
        </el-button>
      </template>
    </el-dialog>

    <!-- FOLDER EDIT DIALOG -->
      <el-dialog v-model="folderEditVisible" title="Edit folder" width="460px">
        <el-form label-width="110px">
          <el-form-item label="Folder name">
            <el-input v-model="folderEditing.name" />
          </el-form-item>
          <el-form-item label="Parent folder">
            <span>{{ folderEditing.parentId ? getFolderPath(folderEditing.parentId) : 'Root' }}</span>
          </el-form-item>
          <el-form-item label="Access">
            <el-select v-model="folderEditing.visibility" style="width: 100%">
              <el-option label="HIDDEN" value="HIDDEN" />
              <el-option label="Public" value="PUBLIC" />
              <el-option label="Member: Level 1" value="L1" />
              <el-option label="Member: Level 2" value="L2" />
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
    <el-dialog
      v-model="batchMoveVisible"
      :title="moveDialogTitle"
      width="900px"
      top="5vh"
      class="move-dialog"
    >
      <div class="move-layout">
        <!-- LEFT -->
        <div class="move-left" :style="{ width: moveLeftWidth + 'px' }">
          <el-card class="panel-card move-panel-card">
            <template #header>
              <div class="move-header">
                <span>Folders</span>

                <el-input
                  v-model="moveFolderSearch"
                  clearable
                  size="small"
                  placeholder="Search folders"
                  style="margin-top: 10px"
                />
              </div>
            </template>

            <el-tree
              class="dialog-folder-tree move-tree"
              :data="filteredMoveFolders"
              node-key="id"
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
                    {{ data.name }}
                  </span>
                </span>
              </template>
            </el-tree>
          </el-card>
        </div>

        <!-- RESIZER -->
        <div
          class="move-divider"
          @mousedown="startMoveResize"
        ></div>

        <!-- RIGHT -->
        <div class="move-right">
          <el-card class="panel-card move-panel-card">
            <template #header>
              <span>Destination</span>
            </template>

            <div class="move-destination">
              <div class="move-path-label">Selected folder</div>

              <div class="move-path-value">
                {{
                  batchMoveFolderId
                    ? getFolderPath(
                        batchMoveFolderId === 'ROOT'
                          ? null
                          : batchMoveFolderId
                      )
                    : 'No folder selected'
                }}
              </div>

              <div class="move-summary">
                <div>
                  <strong>{{ moveRows.length }}</strong>
                  item(s) selected
                </div>

                <div class="move-summary-list">
                  <div
                    v-for="row in moveRows"
                    :key="getRowKey(row)"
                    class="move-summary-item"
                  >
                    <i
                      v-if="row.isFolder"
                      class="el-icon-folder"
                    ></i>

                    <span>{{ row.title }}</span>
                  </div>
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
          @click="moveSelectedFiles"
        >
          Move
        </el-button>
      </template>
    </el-dialog>

</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { deleteFile, downloadFile, listFiles, updateFile } from '../api/fileApi'
import { listFolders, createFolder as createFolderApi, updateFolder, deleteFolder as deleteFolderApi } from '../api/folderApi'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

// ============================================================================
// STATE & CONFIGURATION
// ============================================================================

// Panel sizing
const leftWidth = ref(320)
const MIN_LEFT = 240
const MAX_LEFT = 700

// Folder state
const folders = ref([])
const files = ref([])
const selectedFolderId = ref('1')
const newFolderName = ref('')
const folderSearch = ref('')
const fileSearch = ref('')

// Selection state
const selectedFiles = ref([])
const filesTable = ref(null)
const tableResizeCleanups = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const pageSizes = [10, 20, 30, 40, 50]
const showAllMode = ref(true)

// Dialog visibility states
const showCreateFolderDialog = ref(false)
const createFolderError = ref('')
const editVisible = ref(false)
const infoVisible = ref(false)
const folderInfoVisible = ref(false)
const folderEditVisible = ref(false)
const batchEditVisible = ref(false)
const batchMoveVisible = ref(false)

// Edit/Form states
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

const folderEditing = ref({
  id: null,
  name: '',
  parentId: null,
  visibility: 'HIDDEN'
})

const infoFile = ref({
  id: null,
  title: '',
  fileName: '',
  name: '',
  folderId: null,
  fileSize: 0,
  uploadTime: null,
  visibility: 'HIDDEN',
  category: '',
  description: '',
  tags: []
})
const folderInfo = ref({
  id: null,
  name: '',
  totalFiles: 0,
  totalSize: 0,
  visibility: 'HIDDEN'
})

// Batch operations
const batchEditCategory = ref('')
const batchEditVisibility = ref('HIDDEN')
const batchAppliesToFiles = computed(() => selectedFiles.value.some((row) => !row.isFolder))

// Batch move
const batchMoveFolderId = ref(null)
const batchMoveLoading = ref(false)
const moveRows = ref([])
const singleMoveMode = ref(false)
const deleteLoading = ref(false)
const folderDeleteLoading = ref(false)

// Responsive states
const isNarrow = ref(window.innerWidth <= 1000)
const showFolderPanel = ref(!isNarrow.value)
const moveFolderSearch = ref('')
const moveLeftWidth = ref(320)
const MIN_MOVE_LEFT = 240
const MAX_MOVE_LEFT = 500


// ============================================================================
// UTILITY FUNCTIONS & HELPERS
// ============================================================================

// Window resize handling
const updateWindowWidth = () => {
  const narrow = window.innerWidth <= 1000
  isNarrow.value = narrow
  showFolderPanel.value = !narrow
}

// Folder path utilities
const getRouteFolderPath = () => {
  const raw = route.query.folder
  if (!raw) return []
  return String(raw).split('/').filter(Boolean)
}

// Format utilities
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

const visibilityLabel = (visibility) => {
  if (visibility === 'HIDDEN') return 'HIDDEN'
  if (visibility === 'PUBLIC') return 'Public'
  if (visibility === 'L1') return 'Member: Level 1'
  if (visibility === 'L2') return 'Member: Level 2'
  return String(visibility || '-').toUpperCase()
}

// File/folder type utilities
const splitName = (full) => {
  const name = String(full || '')
  const idx = name.lastIndexOf('.')
  if (idx <= 0 || idx === name.length - 1) {
    return { base: name, ext: '' }
  }
  return { base: name.slice(0, idx), ext: name.slice(idx) }
}

const extOf = (name) => {
  const n = String(name || "")
  const parts = n.split(".")
  if (parts.length < 2) return ""
  const ext = parts[parts.length - 1]
  if (!ext) return ""
  return ext.toLowerCase()
}

const getFileType = (fileName) => {
  const ext = extOf(fileName)
  return ext ? `.${ext}` : '-'
}

const getRowKey = (row) => {
  return row.isFolder ? `folder-${row.id}` : row.id
}

// Folder detection
const isRootFolder = (folder) => {
  return folder && (String(folder.id) === '1' || String(folder.name || '').toLowerCase() === 'root')
}

const isRootFolderId = (folderId) => {
  return folderId == null || String(folderId).toLowerCase() === 'root' || String(folderId) === '1'
}

// Path utilities
const normalizePath = (p) =>
  String(p || "")
    .replaceAll("\\", "/")
    .replace(/^\/+/, "")

const splitPath = (p) => normalizePath(p).split("/").filter(Boolean)

const formatFilePathString = (parts) => {
  if (!parts || !parts.length) return 'Root'
  const normalized = parts.map((part) => String(part || '').trim()).filter(Boolean)
  if (!normalized.length) return 'Root'
  if (normalized[0].toLowerCase() === 'root') {
    return normalized.join(' / ')
  }
  return `Root / ${normalized.join(' / ')}`
}

// Sorting utilities
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

const sortByName = (arr) => {
  return [...arr].sort((a, b) => {
    const nameA = String(a.name || '').toLowerCase()
    const nameB = String(b.name || '').toLowerCase()
    
    if (nameA === 'root') return -1
    if (nameB === 'root') return 1
    
    return customFolderSort(a.name, b.name)
  })
}

// ============================================================================
// DATA LOADING & INITIALIZATION
// ============================================================================

const loadData = async () => {
  try {
    const [folderRes, fileRes] = await Promise.all([listFolders(), listFiles()])
    folders.value = folderRes
    files.value = fileRes
  } catch (error) {
    ElMessage.error(error.message || 'Failed to load data')
  }
}

// ============================================================================
// COMPUTED PROPERTIES (Filters, pagination, paths)
// ============================================================================

const folderByPath = computed(() => {
  const map = new Map()

  const walk = (nodes, parentPath = []) => {
    for (const node of nodes || []) {
      const currentPath = [...parentPath, node.name]

      map.set(
        currentPath.join('/').toLowerCase(),
        node
      )

      walk(node.children, currentPath)
    }
  }

  walk(folders.value)

  return map
})

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

watch([currentPathParts, selectedFolderId], () => {
  nextTick(updateBreadcrumbCollapsed)
})

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
  const filtered = filterFolderTree(folders.value, folderSearch.value)
  const sortTree = (nodes) => {
    const sorted = sortByName(nodes)
    return sorted.map(node => ({
      ...node,
      children: node.children ? sortTree(node.children) : []
    }))
  }
  return sortTree(filtered)
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

const dialogFolderTree = computed(() => {
  const hasRootNode = folders.value.some((node) => isRootFolder(node))
  if (hasRootNode) {
    return folders.value
  }
  return [
    {
      id: 'ROOT',
      name: 'Root',
      children: folders.value
    }
  ]
})

const getDialogTreeCurrentNodeKey = (folderId) => {
  const key = String(folderId || 'ROOT')
  if (key === 'ROOT') {
    const rootNode = dialogFolderTree.value.find((node) => isRootFolder(node))
    return rootNode ? String(rootNode.id) : 'ROOT'
  }
  return key
}

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

const getDescendantFolderIds = (folderId) => {
  if (folderId === 'all') return []
  const descendants = new Set()
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (String(node.id) === String(folderId)) {
        collectDescendants(node)
      } else {
        walk(node.children)
      }
    }
  }
  const collectDescendants = (node) => {
    descendants.add(String(node.id))
    for (const child of node.children || []) {
      collectDescendants(child)
    }
  }
  walk(folders.value)
  return Array.from(descendants)
}

const calculateFolderStats = (folderId) => {
  let totalFiles = 0
  let totalSize = 0
  let latestUploadTime = null
  let hasHidden = false
  let hasMemberOnly = false

  const walk = (nodes) => {
    for (const node of nodes || []) {
      files.value.forEach(file => {
        const fileFolderId = file.folderId == null ? null : String(file.folderId)
        const matchesRoot = isRootFolderId(node.id) && (fileFolderId === null || fileFolderId === String(node.id))
        const matchesFolder = !isRootFolderId(node.id) && fileFolderId === String(node.id)

        if (matchesRoot || matchesFolder) {
          totalFiles++
          totalSize += file.fileSize || 0
          if (file.uploadTime) {
            const time = new Date(file.uploadTime).getTime()
            if (!Number.isNaN(time) && (latestUploadTime == null || time > new Date(latestUploadTime).getTime())) {
              latestUploadTime = file.uploadTime
            }
          }
          if (file.visibility === 'HIDDEN') {
            hasHidden = true
          } else if (file.visibility === 'L2') {
            hasMemberOnly = true
          }
        }
      })
      walk(node.children)
    }
  }
  
  const findNode = (nodes, id) => {
    for (const node of nodes || []) {
      if (String(node.id) === String(id)) return node
      const found = findNode(node.children, id)
      if (found) return found
    }
    return null
  }
  
  const startNode = findNode(folders.value, folderId)
  if (startNode) {
    walk([startNode])
  }

  let visibility = null
  if (hasHidden) {
    visibility = 'HIDDEN'
  } else if (hasMemberOnly) {
    visibility = 'L2'
  } else if (totalFiles > 0) {
    visibility = 'L1'
  }

  return { totalFiles, totalSize, latestUploadTime, visibility }
}

const filteredFiles = computed(() => {
  const keyword = fileSearch.value.trim().toLowerCase()
  const descendantIds = keyword ? getDescendantFolderIds(selectedFolderId.value) : []
  const currentFolderIsRoot = isRootFolderId(selectedFolderId.value)
  const parentFolderId = currentFolderIsRoot ? null : selectedFolderId.value

  const filteredFiles = files.value.filter((file) => {
    const fileFolderId = file.folderId == null ? null : String(file.folderId)
    const selectedFolderIdStr = String(selectedFolderId.value)

    const inFolder =
      selectedFolderId.value === 'all' ||
      (currentFolderIsRoot && (fileFolderId === null || fileFolderId === '1' || descendantIds.includes(fileFolderId))) ||
      (!currentFolderIsRoot && (!keyword && fileFolderId === selectedFolderIdStr)) ||
      (!currentFolderIsRoot && keyword && (fileFolderId === selectedFolderIdStr || descendantIds.includes(fileFolderId)))

    if (!inFolder) return false
    if (!keyword) return true
    return (file.title || '').toLowerCase().includes(keyword)
  })

  const filteredFolders = []
  if (selectedFolderId.value !== 'all') {
    const walkFolders = (nodes, parentId) => {
      const selectedFolderIdStr = String(selectedFolderId.value)
      const isRootFolderSelected = isRootFolderId(selectedFolderId.value)

      for (const node of nodes || []) {
        const nodeParentId = node.parentId == null ? null : String(node.parentId)
        const matchesParent = isRootFolderSelected
          ? (nodeParentId === null || nodeParentId === selectedFolderIdStr) && String(node.id) !== selectedFolderIdStr
          : nodeParentId === String(parentId)

        if (matchesParent) {
          const stats = calculateFolderStats(node.id)
          filteredFolders.push({
            id: node.id,
            name: node.name,
            title: node.name,
            isFolder: true,
            folderId: parentId,
            totalSize: stats.totalSize,
            latestUploadTime: stats.latestUploadTime,
            visibility: node.visibility || stats.visibility || 'HIDDEN'
          })
        }
        walkFolders(node.children, parentId)
      }
    }
    walkFolders(folders.value, parentFolderId)
  }

  const combined = [...filteredFolders, ...filteredFiles]
  return combined.sort((a, b) => {
    if (a.isFolder && !b.isFolder) return -1
    if (!a.isFolder && b.isFolder) return 1
    return customFolderSort(a.title, b.title)
  })
})

const selectedFileCount = computed(() => selectedFiles.value.length)

const effectivePageSize = computed(() => {
  return showAllMode.value ? Math.max(totalFilesCount.value, 1) : pageSize.value
})

const paginatedFiles = computed(() => {
  const start = (currentPage.value - 1) * effectivePageSize.value
  return filteredFiles.value.slice(start, start + effectivePageSize.value)
})

const totalFilesCount = computed(() => filteredFiles.value.length)

const filteredMoveFolders = computed(() => {
  return filterFolderTree(
    dialogFolderTree.value,
    moveFolderSearch.value
  )
})

const moveDialogTitle = computed(() => singleMoveMode.value ? 'Move item' : 'Move selected files')

const batchEditAppliesToFiles = computed(() => selectedFiles.value.some((row) => !row.isFolder))

const fileInfoTitle = computed(() => {
  if (!infoFile.value) return '-'
  return infoFile.value.title || infoFile.value.name || infoFile.value.fileName || '-'
})

const fileInfoTags = computed(() => {
  if (!infoFile.value?.tags) return []
  if (Array.isArray(infoFile.value.tags)) return infoFile.value.tags
  return String(infoFile.value.tags)
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
})

// ============================================================================
// INFO ACTIONS - Display file/folder metadata
// ============================================================================

const openInfo = (row) => {

  if (!row) return

  if (row.isFolder) {
    openFolderInfo(row)
    return
  }

  const completeFile = files.value.find(
    f => String(f.id) === String(row.id)
  )

  const fileData = completeFile || row

  infoFile.value = {
    id: fileData.id,

    title:
      fileData.title ??
      fileData.fileName ??
      fileData.name ??
      'Unknown',

    fileName:
      fileData.fileName ??
      fileData.name ??
      '',

    folderId:
      fileData.folderId,

    fileSize:
      fileData.fileSize ??
      fileData.size ??
      fileData.resourceSize ??
      0,

    uploadTime:
      fileData.uploadTime ??
      fileData.createdAt ??
      null,

    visibility:
      fileData.visibility ??
      fileData.accessLevel ??
      'HIDDEN',

    category:
      fileData.category ??
      fileData.fileCategory ??
      fileData.type ??
      '-',

    description:
      fileData.description ??
      '-',

    tags:
      Array.isArray(fileData.tags)
        ? fileData.tags
        : []
  }

  infoVisible.value = true
}

// ============================================================================
// PREVIEW ACTION - Open file preview in detail page
// ============================================================================

const openFilePreview = (row) => {
  if (!row || row.isFolder) return

  // Check permission for L2 visibility files
  if (row.visibility === 'L2' && !auth.isMember && !auth.isAdmin) {
    ElMessage.warning('This resource is member-only (approved members/admins).')
    return
  }

  // Navigate to file detail page
  router.push({
    name: 'file-detail',
    params: { id: row.id }
  })
}

const openFolderInfo = (folder) => {
  const folderId = Number(folder.id)
  const stats = calculateFolderStats(folderId)

  // Use folder.name directly if path lookup fails
  const folderPath = getFolderPath(folderId)
  const displayName = (folderPath && folderPath !== 'Root') ? folderPath : (folder.name || 'Root')

  folderInfo.value = {
    id: folderId,
    name: displayName,
    totalFiles: stats.totalFiles != null && !isNaN(stats.totalFiles) ? stats.totalFiles : 0,
    totalSize: stats.totalSize != null && !isNaN(stats.totalSize) ? stats.totalSize : 0,
    visibility: folder.visibility ?? stats.visibility ?? 'HIDDEN'
  }
  folderInfoVisible.value = true
}

// ============================================================================
// DOWNLOAD ACTIONS - Download files and folders
// ============================================================================

const getDownloadIdsForRows = (rows) => {
  const ids = new Set()

  for (const row of rows || []) {
    if (!row) continue

    if (row.isFolder) {
      const folderIds = new Set(
        getDescendantFolderIds(row.id).map(String)
      )

      for (const file of files.value || []) {
        if (file.isFolder) continue

        const fileFolderId =
          file.folderId == null
            ? '1'
            : String(file.folderId)

        if (folderIds.has(fileFolderId)) {
          ids.add(String(file.id))
        }
      }
    } else {
      ids.add(String(row.id))
    }
  }

  return [...ids]
}

const downloadRowsAsZip = async (rows, zipName = 'selected-files.zip') => {
  const fileIds = getDownloadIdsForRows(rows)
  if (!fileIds.length) {
    ElMessage.warning('No files to download')
    return
  }

  ElMessage.info('Server is preparing your download...')
  try {
    const response = await fetch(`/api/files/download-zip?ids=${fileIds.join(',')}`, {
      credentials: 'include'
    })
    if (!response.ok) throw new Error('Download failed')
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = zipName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    ElMessage.success('Download started')
  } catch (error) {
    ElMessage.error(error.message || 'Download failed')
  }
}

const onDownload = async (row) => {
  if (!row) return
  if (row.isFolder) {
    ElMessage.info('Server is preparing folder download...')
    try {
      const response = await fetch(`/api/files/download-folder/${row.id}`, {
        credentials: 'include'
      })
      if (!response.ok) throw new Error('Download failed')
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${row.title || 'folder'}.zip`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      ElMessage.success('Download started')
    } catch (error) {
      ElMessage.error(error.message || 'Download failed')
    }
    return
  }
  downloadFile(row.id, row.title)
}

const downloadSelectedAsZip = async () => {
  if (!selectedFiles.value?.length) {
    ElMessage.warning('Please select at least one item')
    return
  }

  if (
    selectedFiles.value.length === 1 &&
    selectedFiles.value[0]?.isFolder
  ) {
    await onDownload(selectedFiles.value[0])
    return
  }

  await downloadRowsAsZip(selectedFiles.value, 'selected-files.zip')
}

// ============================================================================
// MOVE ACTIONS - Move files and folders
// ============================================================================

const openBatchMove = () => {
  singleMoveMode.value = false
  moveRows.value = [...selectedFiles.value]
  batchMoveFolderId.value = selectedFolderId.value !== 'all' ? selectedFolderId.value : getDialogTreeCurrentNodeKey(null)
  batchMoveVisible.value = true
}

const openSingleMove = (row) => {
  singleMoveMode.value = true
  moveRows.value = [row]
  const currentParent = row.parentId ?? row.folderId ?? null
  batchMoveFolderId.value = currentParent != null ? currentParent : getDialogTreeCurrentNodeKey(null)
  batchMoveVisible.value = true
}

const onBatchMoveFolderSelect = (node) => {
  if (!node) return
  batchMoveFolderId.value = node.id
}

const moveSelectedFiles = async () => {
  if (batchMoveFolderId.value == null) {
    ElMessage.error('Please choose a destination folder')
    return
  }
  const targetFolderId = String(batchMoveFolderId.value) === 'ROOT' ? null : batchMoveFolderId.value
  const itemsToMove = moveRows.value.length ? moveRows.value : selectedFiles.value
  batchMoveLoading.value = true
  try {
    await Promise.all(
      itemsToMove.map((row) => {
        if (row.isFolder) {
          return updateFolder(row.id, row.title, targetFolderId)
        }
        return updateFile(row.id, {
          title: row.title,
          folderId: targetFolderId,
          visibility: row.visibility
        })
      })
    )
    batchMoveVisible.value = false
    if (!singleMoveMode.value) {
      selectedFiles.value = []
      filesTable.value?.clearSelection?.()
    }
    moveRows.value = []
    singleMoveMode.value = false
    await loadData()
    ElMessage.success('Files moved')
  } catch (error) {
    ElMessage.error(error.message || 'Move failed')
  } finally {
    batchMoveLoading.value = false
  }
}

// ============================================================================
// EDIT ACTIONS - Edit file and folder metadata
// ============================================================================

const openEdit = (row) => {
  // Find the complete file data from files array to ensure all metadata
  const completeFile = files.value.find(f => String(f.id) === String(row.id))
  const fileData = completeFile || row
  const { base, ext } = splitName(fileData.title)
  editing.value = {
    id: fileData.id,
    baseName: base,
    ext,
    folderId: fileData.folderId,
    visibility: fileData.visibility || 'HIDDEN',
    category: fileData.category || '',
    description: fileData.description || '',
    tags: Array.isArray(fileData.tags) ? fileData.tags.join(',') : fileData.tags || ''
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

const openFolderEdit = (folder) => {
  if (isRootFolder(folder)) return
  // Ensure we have complete folder data
  const completeFolderData = folders.value.find(f => String(f.id) === String(folder.id))
  const folderData = completeFolderData || folder
  folderEditing.value = {
    id: folderData.id,
    name: folderData.name,
    parentId: folderData.parentId ?? null,
    visibility: folderData.visibility || 'HIDDEN'
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
    await updateFolder(
      folderEditing.value.id,
      name,
      folderEditing.value.parentId,
      folderEditing.value.visibility
    )
    folderEditVisible.value = false
    await loadData()
    ElMessage.success('Folder updated')
  } catch (error) {
    ElMessage.error(error.message || 'Update folder failed')
  }
}

const openBatchEdit = () => {
  batchEditVisibility.value = selectedFiles.value[0]?.visibility || 'HIDDEN'
  const selectedFilesOnly = selectedFiles.value.filter((row) => !row.isFolder)
  if (selectedFilesOnly.length) {
    const firstCategory = selectedFilesOnly[0]?.category || ''
    batchEditCategory.value = selectedFilesOnly.every((row) => row.category === firstCategory)
      ? firstCategory
      : ''
  } else {
    batchEditCategory.value = ''
  }
  batchEditVisible.value = true
}

const saveBatchEdit = async () => {
  if (!selectedFiles.value.length) {
    ElMessage.error('No items selected')
    return
  }

  const visibility = batchEditVisibility.value
  const category = batchEditCategory.value || null

  try {
    await Promise.all(
      selectedFiles.value.map((row) => {
        if (row.isFolder) {
          return updateFolder(row.id, row.title, row.parentId ?? row.folderId ?? null, visibility)
        }
        return updateFile(row.id, {
          title: row.title,
          folderId: row.folderId,
          visibility,
          category
        })
      })
    )
    batchEditVisible.value = false
    selectedFiles.value = []
    filesTable.value?.clearSelection?.()
    await loadData()
    ElMessage.success('Selected items updated')
  } catch (error) {
    ElMessage.error(error.message || 'Update failed')
  }
}

const onEditFileFolderSelect = (node) => {
  if (!node) return
  editing.value.folderId = isRootFolder(node) || String(node.id) === 'ROOT' ? null : node.id
}

const onEditFolderParentSelect = (node) => {
  if (!node) return
  folderEditing.value.parentId = isRootFolder(node) || String(node.id) === 'ROOT' ? null : node.id
}

// ============================================================================
// DELETE ACTIONS - Delete files and folders
// ============================================================================

const removeFile = async (id) => {
  try {
    await deleteFile(id)
    await loadData()
    ElMessage.success('Deleted successfully')
  } catch (error) {
    ElMessage.error(error.message || 'Delete failed')
  }
}

const removeFolder = async (id) => {
  try {
    await deleteFolderApi(id)
    await loadData()
    ElMessage.success('Folder deleted')
  } catch (error) {
    ElMessage.error(error.message || 'Delete folder failed')
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

const deleteSelectedFiles = async () => {
  if (!selectedFiles.value.length) return
  deleteLoading.value = true
  try {
    await ElMessageBox.confirm('Delete selected items?', 'Confirm', { type: 'warning' })
    await Promise.all(selectedFiles.value.map((row) => {
      return row.isFolder ? deleteFolderApi(row.id) : deleteFile(row.id)
    }))
    selectedFiles.value = []
    filesTable.value?.clearSelection?.()
    await loadData()
    ElMessage.success('Selected items deleted')
  } catch (error) {
    if (error?.message !== 'cancel') {
      ElMessage.error(error.message || 'Delete failed')
    }
  } finally {
    deleteLoading.value = false
  }
}

// ============================================================================
// VIEW/PREVIEW ACTIONS - View and navigate to files/folders
// ============================================================================

const showDirectory = (row) => {
  if (!row) return
  if (row.isFolder) {
    const folderId = String(row.id)
    selectedFolderId.value = folderId
    updateRouteFolder(folderId)
    return
  }
  if (!row?.folderId) return
  selectedFolderId.value = row.folderId
  updateRouteFolder(row.folderId)
}

const navigateToFolder = (folderRow) => {
  if (folderRow.isFolder) {
    const folderId = String(folderRow.id)
    selectedFolderId.value = folderId
    updateRouteFolder(folderId)
  }
}

// ============================================================================
// CREATE ACTIONS - Create folders
// ============================================================================

const createFolder = async () => {
  const folderName = String(newFolderName.value || '').trim()
  if (!folderName) {
    createFolderError.value = 'Folder name cannot be empty'
    return
  }

  const parentId = selectedFolderId.value !== 'all' ? selectedFolderId.value : null
  try {
    await createFolderApi(folderName, parentId)
    newFolderName.value = ''
    createFolderError.value = ''
    showCreateFolderDialog.value = false
    await loadData()
    ElMessage.success('Folder created')
  } catch (error) {
    createFolderError.value = error.message || 'Failed to create folder'
    ElMessage.error(createFolderError.value)
  }
}

const closeCreateFolderDialog = () => {
  showCreateFolderDialog.value = false
  createFolderError.value = ''
  newFolderName.value = ''
}

// ============================================================================
// NAVIGATION & ROUTE ACTIONS
// ============================================================================

const syncFolderFromRoute = () => {
  const pathParts = getRouteFolderPath()

  if (!pathParts.length) {
    selectedFolderId.value = '1'
    return
  }

  const pathKey = pathParts.join('/').toLowerCase()
  const folder = folderByPath.value.get(pathKey)

  if (folder) {
    selectedFolderId.value = String(folder.id)
  } else {
    selectedFolderId.value = '1'
  }
}

const buildFolderPath = (folderId) => {
  const map = folderMetaById.value
  const parts = []

  let current = map.get(String(folderId))

  while (current) {
    parts.unshift(current.name)

    current =
      current.parentId != null
        ? map.get(String(current.parentId))
        : null
  }

  return parts
}

const updateRouteFolder = (folderId) => {
  if (!folderId || folderId === 'all') {
    router.push({ path: '/home/internal_portal/files', query: {} })
    return
  }

  const parts = buildFolderPath(folderId)
  const folderPath = parts.join('/')

  const query = { ...route.query }
  query.folder = folderPath

  router.push({ path: '/home/internal_portal/files', query })
}

const getFolderPath = (folderId) => {
  if (folderId == null) return 'Root'
  const map = folderMetaById.value
  const parts = []
  let current = map.get(String(folderId))
  
  while (current) {
    parts.unshift(current.name)
    if (current.parentId == null) {
      break
    }
    current = map.get(String(current.parentId))
  }
  
  return formatFilePathString(parts)
}

const getFilePath = (folderId) => {
  if (folderId == null) return 'Root'
  const map = folderMetaById.value
  const parts = []
  let current = map.get(String(folderId))

  while (current) {
    parts.unshift(current.name)
    if (current.parentId == null) {
      break
    }
    current = map.get(String(current.parentId))
  }

  return formatFilePathString(parts)
}

const selectAll = () => {
  selectedFolderId.value = 'all'
  showAllMode.value = false
  updateRouteFolder('all')
}

const onTreeSelect = (node) => {
  if (!node?.id) return
  selectedFolderId.value = node.id
  updateRouteFolder(node.id)
}

const goToFolder = (id) => {
  selectedFolderId.value = id
  updateRouteFolder(id)
}

const goToUploadPage = () => {
  router.push('/home/internal_portal/files/uploads')
}

const goToUploadFolderPage = () => {
  router.push('/home/internal_portal/files/uploads-folder')
}

// ============================================================================
// UI HANDLERS & EVENTS - Table, selection, pagination, resize
// ============================================================================

const onSelectionChange = (rows) => {
  selectedFiles.value = rows || []
}

const resetPage = () => {
  currentPage.value = 1
}

const handlePageSizeChange = (size) => {
  pageSize.value = size
  showAllMode.value = false
}

const showAllFiles = () => {
  showAllMode.value = true
  currentPage.value = 1
}

const handleRowAction = (row, command) => {
  if (command === 'info') {
    if (row.isFolder) {
      openFolderInfo(row)
    } else {
      openInfo(row)
    }
  } else if (command === 'view' && !row.isFolder) {
    openFilePreview(row)
  } else if (command === 'download') {
    onDownload(row)
  } else if (command === 'directory') {
    showDirectory(row)
  } else if (command === 'move') {
    openSingleMove(row)
  } else if (command === 'edit') {
    if (row.isFolder) {
      openFolderEdit({
        id: row.id,
        name: row.title,
        parentId: row.parentId ?? row.folderId ?? null,
        visibility: row.visibility
      })
    } else {
      openEdit(row)
    }
  } else if (command === 'delete') {
    if (row.isFolder) {
      removeFolder(row.id)
    } else {
      removeFile(row.id)
    }
  }
}

// Resize handlers
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

const initializeTableColumnResizing = (tableRef) => {
  const tableWrapper = tableRef?.value?.$el
  if (!tableWrapper) return

  const headerCells = Array.from(tableWrapper.querySelectorAll('.el-table__header-wrapper th'))
  const cols = Array.from(tableWrapper.querySelectorAll('colgroup col'))

  headerCells.forEach((th, index) => {
    if (index === 0) return
    if (th.querySelector('.column-resizer')) return

    th.style.position = 'relative'
    const resizer = document.createElement('span')
    resizer.className = 'column-resizer'
    resizer.title = 'Drag to resize'
    th.appendChild(resizer)

    // Calculate minimum width based on header text content
    const headerText = th.textContent?.trim() || ''
    const tempSpan = document.createElement('span')
    tempSpan.style.fontSize = '14px'
    tempSpan.style.fontWeight = '500'
    tempSpan.style.fontFamily = 'inherit'
    tempSpan.style.visibility = 'hidden'
    tempSpan.style.position = 'absolute'
    tempSpan.style.whiteSpace = 'nowrap'
    tempSpan.textContent = headerText
    document.body.appendChild(tempSpan)
    const headerWidth = tempSpan.getBoundingClientRect().width
    document.body.removeChild(tempSpan)
    const minWidth = Math.max(60, Math.ceil(headerWidth + 20))

    const onMouseDown = (event) => {
      event.preventDefault()
      event.stopPropagation()
      const startX = event.clientX
      const startWidth = th.getBoundingClientRect().width
      const targetCol = cols[index]

      const onMouseMove = (moveEvent) => {
        const nextWidth = Math.max(minWidth, startWidth + (moveEvent.clientX - startX))
        if (targetCol) {
          targetCol.style.width = `${nextWidth}px`
        }
        th.style.width = `${nextWidth}px`
      }

      const onMouseUp = () => {
        document.removeEventListener('mousemove', onMouseMove)
        document.removeEventListener('mouseup', onMouseUp)
        document.body.style.cursor = ''
      }

      document.addEventListener('mousemove', onMouseMove)
      document.addEventListener('mouseup', onMouseUp)
      document.body.style.cursor = 'col-resize'
    }

    resizer.addEventListener('mousedown', onMouseDown)
    tableResizeCleanups.value.push(() => resizer.removeEventListener('mousedown', onMouseDown))
  })
}

// ============================================================================
// WATCH & LIFECYCLE HOOKS
// ============================================================================

watch([fileSearch, selectedFolderId, pageSize], resetPage)

watch([fileSearch, selectedFolderId], () => {
  if (showAllMode.value) {
    currentPage.value = 1
  }
})

watch(selectedFolderId, (folderId) => {
  showAllMode.value = folderId !== 'all'
  if (showAllMode.value) {
    currentPage.value = 1
  }
})

onMounted(async () => {
  document.body.classList.add('filelist-fullwidth')
  window.addEventListener('resize', updateWindowWidth)
  window.addEventListener('resize', updateBreadcrumbCollapsed)
  updateWindowWidth()
  await loadData()
  syncFolderFromRoute()
  watch(
    () => route.query.folder,
    () => {
      syncFolderFromRoute()
    }
  )
  nextTick(() => {
    initializeTableColumnResizing(filesTable)
    updateBreadcrumbCollapsed()
  })
})

onBeforeUnmount(() => {
  document.body.classList.remove('filelist-fullwidth')
  window.removeEventListener('resize', updateWindowWidth)
  window.removeEventListener('resize', updateBreadcrumbCollapsed)
  tableResizeCleanups.value.forEach((cleanup) => cleanup())
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
/* Layout */
.page {
  height: calc(100vh - 140px);
  width: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* Toolbar */
.table-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 16px 0 0 0;
  gap: 8px;
}

/* Pagination */
.page-all-btn {
  min-width: 80px;
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
  display: flex;
  flex-direction: column;
}

.right-panel > .panel-card {
  flex: 1;
  min-height: 0;
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
  display: flex;
  flex-direction: column;
}

.left-panel .panel-card {
  height: 100%;
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

.dialog-folder-tree {
  max-height: 320px;
  overflow: auto;
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  width: 100%;
}

.dialog-folder-tree :deep(.el-tree-node__content) {
  padding: 4px 8px;
}

.form-item-tree :deep(.el-form-item__label) {
  white-space: nowrap;
}

.form-item-tree :deep(.el-form-item__content) {
  display: block;
  width: 100%;
}

.form-item-tree .dialog-folder-tree {
  margin-top: 8px;
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
  flex-wrap: wrap;
}

.path-row {
  margin-top: 8px;
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

:deep(.el-table .cell) {
  overflow: hidden;
  max-width: 100%;
}

/* Allow name column to wrap text */
:deep(.el-table__header-wrapper th:nth-child(2) .cell),
:deep(.el-table__body .el-table__row td:nth-child(2) .cell) {
  white-space: normal;
  word-wrap: break-word;
  text-overflow: clip;
}

/* Allow type column to wrap if needed */
:deep(.el-table__header-wrapper th:nth-child(3) .cell),
:deep(.el-table__body .el-table__row td:nth-child(3) .cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Minimize other columns */
:deep(.el-table__header-wrapper th:nth-child(4) .cell),
:deep(.el-table__body .el-table__row td:nth-child(4) .cell),
:deep(.el-table__header-wrapper th:nth-child(5) .cell),
:deep(.el-table__body .el-table__row td:nth-child(5) .cell),
:deep(.el-table__header-wrapper th:nth-child(6) .cell),
:deep(.el-table__body .el-table__row td:nth-child(6) .cell),
:deep(.el-table__header-wrapper th:nth-child(7) .cell),
:deep(.el-table__body .el-table__row td:nth-child(7) .cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.picked-text {
  color: #606266;
  font-size: 13px;
}

  .column-resizer {
    position: absolute;
    top: 0;
    right: 0;
    width: 12px;
    height: 100%;
    cursor: col-resize;
    z-index: 1;
    user-select: none;
    background: transparent;
    transition: background 0.15s ease, border-color 0.15s ease;
    border-left: 1px solid transparent;
  }

  .column-resizer:hover {
    background: rgba(64, 158, 255, 0.15);
    border-left-color: rgba(64, 158, 255, 0.45);
  }

  .column-resizer::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 3px;
    transform: translateY(-50%);
    width: 2px;
    height: 18px;
    background: rgba(64, 158, 255, 0.7);
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  .column-resizer:hover::after {
    opacity: 1;
  }

.inner :deep(.el-card__body) {
  height: 400px;
  overflow: auto;
}

.folder-path-text {
  color: #409eff;
  font-weight: 500;
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
