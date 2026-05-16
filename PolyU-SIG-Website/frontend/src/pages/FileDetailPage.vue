<template>
  <el-card class="page-card" v-loading="loading">
    <template #header>
      <div class="header-row">
        <span>File Details</span>
        <div class="actions">
          <el-button @click="goBack">Back</el-button>
          <el-button v-if="canOpenPreviewTab" type="default" :disabled="!file" @click="openPreviewTab">Open Preview in New Tab</el-button>
          <el-button type="primary" :disabled="!file" @click="onDownload">Download</el-button>
        </div>
      </div>
    </template>

    <el-empty v-if="!file && !loading" description="File not found or no permission to view." />

    <template v-else-if="file">
      <div v-if="isPdfLike" class="viewer-wrap">
        <iframe :src="previewSrc" class="pdf-viewer" title="Document Preview" />
      </div>
      <div v-else-if="isImage" class="viewer-wrap image-wrap">
        <img :src="previewSrc" class="image-viewer" alt="Image Preview" />
      </div>

      <div v-else-if="isZip" class="viewer-wrap archive-panel" v-loading="archiveLoading">
        <div class="archive-head">ZIP contents</div>
        <el-alert
          v-if="archiveTruncated"
          type="warning"
          :closable="false"
          show-icon
          class="archive-trunc"
          title="Listing truncated for safety (very large archives). Download the file to see everything."
        />
        <div class="archive-toolbar">
          <el-input
            v-model="archiveSearchKeyword"
            clearable
            placeholder="Filter files by name or path…"
            class="archive-search-input"
            @clear="applyArchiveTreeFilter"
          />
        </div>
        <el-text v-if="archiveSearchNoMatches" type="info" size="small" class="archive-search-hint">
          No entries match “{{ archiveSearchKeyword.trim() }}”.
        </el-text>
        <el-tree
          ref="archiveTreeRef"
          :data="archiveTree"
          :props="{ label: 'label', children: 'children', isLeaf: 'isLeaf' }"
          node-key="path"
          default-expand-all
          :filter-node-method="filterArchiveTreeNode"
          class="archive-tree"
          @node-click="onArchiveTreeNodeClick"
        >
          <template #default="{ data }">
            <span class="archive-tree-node">
              <span class="archive-tree-label">{{ data.label }}</span>
              <span v-if="!data.directory" class="archive-tree-meta">{{ formatArchiveBytes(data.size) }}</span>
            </span>
          </template>
        </el-tree>
      </div>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="Title">{{ file.title }}</el-descriptions-item>
        <el-descriptions-item label="Uploader">{{ file.uploader }}</el-descriptions-item>
        <el-descriptions-item label="Category">{{ file.category }}</el-descriptions-item>
        <el-descriptions-item label="Visibility">{{ visibilityLabel(file.visibility) }}</el-descriptions-item>
        <el-descriptions-item label="File Name">{{ file.fileName }}</el-descriptions-item>
        <el-descriptions-item label="File Type">{{ displayFileType }}</el-descriptions-item>
        <el-descriptions-item label="Uploaded At">{{ formatDate(file.uploadTime) }}</el-descriptions-item>
        <el-descriptions-item label="Description">
          <span v-if="!file.description">-</span>
          <span v-else>{{ file.description }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Tags">
          <span v-if="!file.tags?.length">-</span>
          <el-tag v-for="tag in file.tags" :key="tag" class="tag">{{ tag }}</el-tag>
        </el-descriptions-item>
        <!-- Matched Snippet: always visible when a file is loaded. Text Preview: hidden for types shown inline as PDF (or PDF-converted) in the viewer above. -->
        <el-descriptions-item label="Matched Snippet">
          <span v-if="!file.highlight">-</span>
          <span v-else v-html="file.highlight"></span>
        </el-descriptions-item>
        <el-descriptions-item v-if="showExtractedTextPreview" label="Text Preview">
          <span v-if="!file.previewContent">No text preview available for this file.</span>
          <pre v-else class="preview" v-html="highlightPreview(file.previewContent)"></pre>
        </el-descriptions-item>
      </el-descriptions>
    </template>

    <el-dialog
      v-model="archivePreviewVisible"
      :title="archivePreviewTitle"
      width="min(920px, 92vw)"
      destroy-on-close
      class="archive-preview-dialog"
      @closed="resetArchivePreview"
    >
      <div v-loading="archivePreviewLoading" class="archive-preview-body">
        <iframe
          v-if="archivePreviewKind === 'pdf'"
          :src="archivePreviewSrc"
          class="archive-preview-frame"
          title="ZIP entry preview"
        />
        <div v-else-if="archivePreviewKind === 'image'" class="archive-preview-image-wrap">
          <img :src="archivePreviewSrc" class="archive-preview-image" :alt="archivePreviewTitle" />
        </div>
        <pre v-else-if="archivePreviewKind === 'text'" class="archive-preview-text">{{ archivePreviewText }}</pre>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="This file type cannot be shown inline. Download the ZIP to open it locally."
        />
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { downloadFile, filePreviewUrl, archiveEntryUrl, getArchiveList, getFileDetail } from '../api/fileApi'
import { extensionOf, fileNameConvertibleToPdfPreview } from '../utils/previewableAsPdf'
import { visibilityLabel } from '../utils/resourceVisibility'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const file = ref(null)

const archiveLoading = ref(false)
const archiveEntries = ref([])
const archiveTruncated = ref(false)
const archiveSearchKeyword = ref('')
const archiveTreeRef = ref(null)

const ARCHIVE_TEXT_PREVIEW_MAX_CHARS = 400_000

const archivePreviewVisible = ref(false)
const archivePreviewLoading = ref(false)
const archivePreviewTitle = ref('')
const archivePreviewKind = ref('unsupported')
const archivePreviewSrc = ref('')
const archivePreviewText = ref('')

/** Types that use the top iframe PDF (or PDF-converted) preview — hide redundant extracted text block. */
function hidesExtractedTextPreviewForFile(f) {
  if (!f) return false
  if (fileNameConvertibleToPdfPreview(f.fileName)) return true
  const fileType = String(f.fileType || '').toLowerCase()
  if (fileType.includes('pdf')) return true
  if (fileType.includes('msword') || fileType.includes('wordprocessingml')) return true
  if (fileType.includes('vnd.ms-excel') || fileType.includes('spreadsheetml')) return true
  if (fileType.includes('vnd.ms-powerpoint') || fileType.includes('presentationml')) return true
  return false
}

const showExtractedTextPreview = computed(() => {
  if (!file.value) return false
  return !hidesExtractedTextPreviewForFile(file.value)
})

const archiveTree = computed(() => buildArchiveTree(archiveEntries.value))

/** Paths to show when filtering (entry + ancestor folders). Null = show whole tree. */
const archiveSearchVisiblePaths = computed(() => {
  const q = archiveSearchKeyword.value.trim().toLowerCase()
  if (!q) return null
  const paths = new Set()
  for (const e of archiveEntries.value) {
    const p = String(e.path || '').trim()
    if (!p || !p.toLowerCase().includes(q)) continue
    paths.add(p)
    const parts = p.split('/').filter(Boolean)
    for (let i = 1; i < parts.length; i++) {
      paths.add(parts.slice(0, i).join('/'))
    }
  }
  return paths
})

const archiveSearchNoMatches = computed(() => {
  const q = archiveSearchKeyword.value.trim()
  if (!q) return false
  const set = archiveSearchVisiblePaths.value
  return set !== null && set.size === 0
})

function filterArchiveTreeNode(value, data) {
  const q = String(value || '').trim().toLowerCase()
  if (!q) return true
  const set = archiveSearchVisiblePaths.value
  if (!set) return true
  return set.has(data.path)
}

function applyArchiveTreeFilter() {
  nextTick(() => {
    archiveTreeRef.value?.filter(archiveSearchKeyword.value)
  })
}

watch(archiveSearchKeyword, applyArchiveTreeFilter)

watch(
  () => archiveTree.value,
  () => applyArchiveTreeFilter(),
  { flush: 'post' }
)

onMounted(loadDetail)
watch(() => [route.params.id, route.query.keyword], loadDetail)

watch(
  () => file.value,
  async (f) => {
    archiveEntries.value = []
    archiveTruncated.value = false
    archiveSearchKeyword.value = ''
    if (!f || !isZipFromFile(f)) {
      return
    }
    archiveLoading.value = true
    try {
      const data = await getArchiveList(f.id)
      archiveEntries.value = Array.isArray(data.entries) ? data.entries : []
      archiveTruncated.value = !!data.truncated
    } catch (e) {
      ElMessage.error(e?.message || 'Could not read ZIP listing')
    } finally {
      archiveLoading.value = false
    }
  },
  { immediate: true }
)

async function loadDetail() {
  loading.value = true
  try {
    file.value = await getFileDetail(route.params.id, route.query.keyword || '')
  } catch (error) {
    ElMessage.error(error.message)
    file.value = null
  } finally {
    loading.value = false
  }
}

function onDownload() {
  if (!file.value) return
  downloadFile(file.value.id, file.value.fileName)
}

function openPreviewTab() {
  if (!file.value) return
  window.open(previewSrc.value, '_blank')
}

function goBack() {
  router.back()
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
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

function highlightPreview(text) {
  const escaped = escapeHtml(text)
  const keyword = String(route.query.keyword || '').trim()
  if (!keyword) return escaped
  const escapedKeyword = escapeRegex(escapeHtml(keyword))
  return escaped.replace(new RegExp(escapedKeyword, 'gi'), '<em>$&</em>')
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function isZipFromFile(f) {
  if (!f) return false
  const fileName = String(f.fileName || '')
  const fileType = String(f.fileType || '').toLowerCase()
  return extensionOf(fileName) === 'zip' || fileType.includes('zip')
}

function formatArchiveBytes(n) {
  const num = Number(n)
  if (!Number.isFinite(num) || num < 0) return '—'
  if (num < 1024) return `${num} B`
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(1)} KB`
  return `${(num / (1024 * 1024)).toFixed(1)} MB`
}

function sortArchiveChildren(nodes) {
  if (!nodes?.length) return
  nodes.sort((a, b) => {
    if (a.directory !== b.directory) return a.directory ? -1 : 1
    return String(a.label).localeCompare(String(b.label), undefined, { sensitivity: 'base' })
  })
  for (const n of nodes) sortArchiveChildren(n.children)
}

function buildArchiveTree(entries) {
  const roots = []
  const sorted = [...entries].sort((a, b) =>
    String(a.path || '').localeCompare(String(b.path || ''), undefined, { sensitivity: 'base' })
  )
  for (const entry of sorted) {
    const rawPath = String(entry.path || '').trim()
    if (!rawPath) continue
    const parts = rawPath.split('/').filter(Boolean)
    if (!parts.length) continue
    let level = roots
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      const pathSoFar = parts.slice(0, i + 1).join('/')
      const isFinal = i === parts.length - 1
      let node = level.find((n) => n.segment === part)
      if (!node) {
        node = {
          segment: part,
          label: part,
          path: pathSoFar,
          children: [],
          directory: true,
          size: 0,
          isLeaf: false
        }
        level.push(node)
      }
      if (isFinal) {
        node.directory = !!entry.directory
        node.size = entry.uncompressedSize ?? 0
        node.isLeaf = !entry.directory
        if (entry.directory) node.size = 0
      }
      level = node.children
    }
  }
  sortArchiveChildren(roots)
  return roots
}

function archiveInnerExt(pathStr) {
  const base = pathStr.includes('/') ? pathStr.slice(pathStr.lastIndexOf('/') + 1) : pathStr
  const dot = base.lastIndexOf('.')
  return dot >= 0 ? base.slice(dot + 1).toLowerCase() : ''
}

function innerArchivePreviewKind(pathStr) {
  const ext = archiveInnerExt(pathStr)
  if (ext === 'pdf') return 'pdf'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'].includes(ext)) return 'image'
  if (
    ['txt', 'csv', 'log', 'json', 'md', 'markdown', 'xml', 'html', 'htm', 'css', 'js', 'ts', 'yml', 'yaml'].includes(
      ext
    )
  ) {
    return 'text'
  }
  return 'unsupported'
}

function resetArchivePreview() {
  archivePreviewLoading.value = false
  archivePreviewTitle.value = ''
  archivePreviewKind.value = 'unsupported'
  archivePreviewSrc.value = ''
  archivePreviewText.value = ''
}

async function onArchiveTreeNodeClick(data) {
  if (!file.value || !data || data.directory) return
  const kind = innerArchivePreviewKind(data.path)
  archivePreviewVisible.value = true
  archivePreviewTitle.value = data.label || data.path
  archivePreviewKind.value = kind
  archivePreviewSrc.value = archiveEntryUrl(file.value.id, data.path)
  archivePreviewText.value = ''

  if (kind === 'text') {
    archivePreviewLoading.value = true
    try {
      const res = await fetch(archivePreviewSrc.value, { credentials: 'include' })
      if (!res.ok) {
        const body = await res.json().catch(() => null)
        throw new Error(body?.message || `Preview failed (${res.status})`)
      }
      let text = await res.text()
      if (text.length > ARCHIVE_TEXT_PREVIEW_MAX_CHARS) {
        text =
          text.slice(0, ARCHIVE_TEXT_PREVIEW_MAX_CHARS) +
          '\n\n…preview truncated (file too long for this view)'
      }
      archivePreviewText.value = text
    } catch (e) {
      archivePreviewKind.value = 'unsupported'
      ElMessage.error(e?.message || 'Could not load text preview')
    } finally {
      archivePreviewLoading.value = false
    }
  }
}

const previewMode = computed(() => {
  if (!file.value) return 'none'
  const fileName = file.value.fileName
  const fileType = String(file.value.fileType || '').toLowerCase()
  const ext = extensionOf(fileName)

  if (ext === 'zip' || fileType.includes('zip')) return 'zip'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'].includes(ext) || fileType.startsWith('image/')) return 'image'
  if (ext === 'pdf' || fileType.includes('pdf')) return 'pdf'
  if (ext === 'docx' || fileType.includes('wordprocessingml') || fileType.includes('msword')) return 'docx'
  if (ext === 'xlsx' || fileType.includes('spreadsheetml')) return 'xlsx'
  if (ext === 'pptx' || fileType.includes('presentationml')) return 'pptx'
  if (fileNameConvertibleToPdfPreview(fileName)) return 'embeddedPdf'
  return 'none'
})

const isPdfLike = computed(() => ['pdf', 'docx', 'xlsx', 'pptx', 'embeddedPdf'].includes(previewMode.value))
const isImage = computed(() => previewMode.value === 'image')
const isZip = computed(() => previewMode.value === 'zip')
const canOpenPreviewTab = computed(() => isPdfLike.value || isImage.value)

const previewSrc = computed(() => {
  if (!file.value) return ''
  return filePreviewUrl(file.value.id)
})

const displayFileType = computed(() => {
  if (!file.value) return '-'
  const fileName = String(file.value.fileName || '').trim()
  const mimeType = String(file.value.fileType || '').trim().toLowerCase()

  const dotIndex = fileName.lastIndexOf('.')
  if (dotIndex >= 0 && dotIndex < fileName.length - 1) {
    return `.${fileName.slice(dotIndex + 1).toLowerCase()}`
  }

  const mimeToExt = {
    'application/pdf': '.pdf',
    'application/msword': '.doc',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': '.docx',
    'application/vnd.ms-excel': '.xls',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': '.xlsx',
    'application/vnd.ms-powerpoint': '.ppt',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation': '.pptx',
    'application/zip': '.zip',
    'text/plain': '.txt',
    'text/markdown': '.md',
    'application/json': '.json',
    'image/png': '.png',
    'image/jpeg': '.jpg',
    'image/gif': '.gif',
    'image/webp': '.webp',
    'image/bmp': '.bmp',
    'image/svg+xml': '.svg'
  }

  return mimeToExt[mimeType] || '-'
})
</script>

<style scoped>
.page-card {
  width: 100%;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions {
  display: flex;
  gap: 8px;
}

.viewer-wrap {
  margin-bottom: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}

.pdf-viewer {
  width: 100%;
  height: 72vh;
  border: none;
  background: #fff;
}

.image-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  background: #fff;
  padding: 12px;
}

.image-viewer {
  max-width: 100%;
  max-height: 72vh;
}

.tag {
  margin-right: 6px;
}

.preview {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.archive-panel {
  padding: 12px;
  background: var(--el-fill-color-blank);
}

.archive-head {
  font-weight: 600;
  margin-bottom: 10px;
}

.archive-trunc {
  margin-bottom: 10px;
}

.archive-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.archive-search-input {
  flex: 1;
  max-width: 420px;
}

.archive-search-hint {
  display: block;
  margin: -4px 0 10px;
}

.archive-tree {
  width: 100%;
  max-height: 420px;
  overflow: auto;
  padding: 4px 0;
}

.archive-tree-node {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  padding-right: 8px;
}

.archive-tree-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.archive-tree-meta {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.archive-preview-body {
  min-height: 200px;
}

.archive-preview-frame {
  width: 100%;
  height: min(72vh, 640px);
  border: none;
  border-radius: 4px;
  background: #fff;
}

.archive-preview-image-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 8px;
  background: var(--el-fill-color-blank);
  border-radius: 4px;
}

.archive-preview-image {
  max-width: 100%;
  max-height: min(72vh, 640px);
}

.archive-preview-text {
  margin: 0;
  max-height: min(72vh, 640px);
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
}
</style>
