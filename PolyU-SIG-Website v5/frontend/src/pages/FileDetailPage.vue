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
        <el-descriptions-item label="Matched Snippet">
          <span v-if="!file.highlight">-</span>
          <span v-else v-html="file.highlight"></span>
        </el-descriptions-item>
        <el-descriptions-item label="Text Preview">
          <span v-if="!file.previewContent">No text preview available for this file.</span>
          <pre v-else class="preview" v-html="highlightPreview(file.previewContent)"></pre>
        </el-descriptions-item>
      </el-descriptions>
    </template>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { downloadFile, filePreviewUrl, getFileDetail } from '../api/fileApi'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const file = ref(null)

onMounted(loadDetail)
watch(() => [route.params.id, route.query.keyword], loadDetail)

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

const previewMode = computed(() => {
  if (!file.value) return 'none'
  const fileName = String(file.value.fileName || '').toLowerCase()
  const fileType = String(file.value.fileType || '').toLowerCase()
  const ext = fileName.includes('.') ? fileName.split('.').pop() : ''

  if (ext === 'pdf' || fileType.includes('pdf')) return 'pdf'
  if (ext === 'docx' || fileType.includes('wordprocessingml') || fileType.includes('msword')) return 'docx'
  if (ext === 'xlsx' || fileType.includes('spreadsheetml')) return 'xlsx'
  if (ext === 'pptx' || fileType.includes('presentationml')) return 'pptx'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'].includes(ext) || fileType.startsWith('image/')) return 'image'
  return 'none'
})

const isPdfLike = computed(() => ['pdf', 'docx', 'xlsx', 'pptx'].includes(previewMode.value))
const isImage = computed(() => previewMode.value === 'image')
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

function visibilityLabel(visibility) {
  if (visibility === 'PUBLIC') return 'Public'
  if (visibility === 'L1') return 'Member: Level 1'
  if (visibility === 'L2') return 'Member: Level 2'
  if (visibility === 'HIDDEN') return 'HIDDEN'
  return visibility || '-'
}
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
</style>
