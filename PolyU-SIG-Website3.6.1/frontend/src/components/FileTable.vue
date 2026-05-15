<template>
  <div>
    <div v-if="mode === 'list'">
      <el-empty v-if="!files.length" :description="emptyDescription" />

      <div v-else class="result-list">
        <article v-for="row in files" :key="row.id" class="result-card">
          <div class="result-main">
            <h3 class="result-title">
              <button type="button" class="title-link" @click="$emit('view', row)">{{ row.title }}</button>
            </h3>
            <div class="meta-row">
              <span class="meta-item">Category: {{ row.category || '-' }}</span>
              <span class="meta-item">Uploader: {{ row.uploader || '-' }}</span>
              <span class="meta-item">Visibility: {{ visibilityLabel(row.visibility) }}</span>
              <span class="meta-item">Type: {{ formatFileType(row) }}</span>
              <span class="meta-item">Size: {{ formatSize(row.fileSize) }}</span>
              <span class="meta-item">Time: {{ formatDate(row.uploadTime) }}</span>
            </div>
            <div class="snippet-row">
              <span class="snippet-label">Matched Snippet:</span>
              <span v-if="!row.highlight">-</span>
              <span v-else class="highlight-text" v-html="row.highlight"></span>
            </div>
          </div>
          <div class="result-actions">
            <el-button type="text" size="small" @click="handleAction(row, 'view')">Info</el-button>
            <el-button type="text" size="small" @click="handleAction(row, 'download')">Download</el-button>
            <el-button type="text" size="small" v-if="editable" @click="handleAction(row, 'edit')">Edit</el-button>
            <el-button type="text" size="small" v-if="editable" @click="handleAction(row, 'delete')">Delete</el-button>
          </div>
        </article>
      </div>
    </div>

    <el-table v-else :data="files" border stripe :empty-text="emptyDescription" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="Title" min-width="220" />
      <el-table-column prop="category" label="Category" width="140" />
      <el-table-column prop="uploader" label="Uploader" width="140" />
      <el-table-column label="Visibility" width="180">
        <template #default="{ row }">
          {{ visibilityLabel(row.visibility) }}
        </template>
      </el-table-column>
      <el-table-column label="File Type" min-width="200">
        <template #default="{ row }">
          {{ formatFileType(row) }}
        </template>
      </el-table-column>
      <el-table-column label="Matched Snippet" min-width="240">
        <template #default="{ row }">
          <span v-if="!row.highlight">-</span>
          <span v-else class="highlight-text" v-html="row.highlight"></span>
        </template>
      </el-table-column>
      <el-table-column label="Size" width="140">
        <template #default="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="Uploaded At" min-width="200">
        <template #default="{ row }">
          {{ formatDate(row.uploadTime) }}
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="180" fixed="right">
        <template #default="{ row }">
          <div class="result-actions">
            <el-button type="text" size="small" @click="handleAction(row, 'view')">Info</el-button>
            <el-button type="text" size="small" @click="handleAction(row, 'download')">Download</el-button>
            <el-button type="text" size="small" v-if="editable" @click="handleAction(row, 'edit')">Edit</el-button>
            <el-button type="text" size="small" v-if="editable" @click="handleAction(row, 'delete')">Delete</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
defineProps({
  files: {
    type: Array,
    default: () => []
  },
  editable: {
    type: Boolean,
    default: false
  },
  mode: {
    type: String,
    default: 'table'
  },
  emptyDescription: {
    type: String,
    default: 'No files found.'
  }
})

const emit = defineEmits(['view', 'download', 'delete', 'edit'])

function handleAction(row, command) {
  if (command === 'view') {
    emit('view', row)
  } else if (command === 'download') {
    emit('download', row)
  } else if (command === 'edit') {
    emit('edit', row)
  } else if (command === 'delete') {
    emit('delete', row)
  }
}

function formatSize(size) {
  if (!size && size !== 0) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  return `${(size / (1024 * 1024)).toFixed(2)} MB`
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

function formatFileType(row) {
  const fileName = String(row?.fileName || '').trim()
  const mimeType = String(row?.fileType || '').trim().toLowerCase()

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
}

function visibilityLabel(visibility) {
  if (visibility === 'PUBLIC') return 'Public'
  if (visibility === 'L1') return 'Member: Level 1'
  if (visibility === 'L2') return 'Member: Level 2'
  if (visibility === 'HIDDEN') return 'HIDDEN'
  return String(visibility || '-').toUpperCase()
}
</script>

<style scoped>
.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.result-main {
  flex: 1;
  min-width: 0;
}

.result-title {
  margin: 0 0 8px 0;
  font-size: 17px;
}

.title-link {
  border: none;
  background: transparent;
  color: #303133;
  cursor: pointer;
  padding: 0;
  font-size: inherit;
  font-weight: 600;
  text-align: left;
}

.title-link:hover {
  color: #409eff;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #606266;
  font-size: 13px;
}

.snippet-row {
  margin-top: 10px;
  color: #606266;
}

.snippet-label {
  color: #909399;
}

.highlight-text :deep(em) {
  color: #b42318;
  background: #fef3f2;
  font-style: normal;
  padding: 0 2px;
  border-radius: 2px;
}

.result-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
}

@media (max-width: 768px) {
  .result-card {
    flex-direction: column;
  }

  .result-actions {
    flex-direction: row;
    justify-content: flex-start;
  }
}
</style>
