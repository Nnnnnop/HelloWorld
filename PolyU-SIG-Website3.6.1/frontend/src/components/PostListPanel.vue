<template>
  <el-card class="card">
    <template #header>
      <div class="head">
        <span>{{ title }}</span>
        <el-button v-if="adminEditable" type="primary" plain @click="$emit('manage')">Manage Posts</el-button>
      </div>
    </template>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="!rows.length" :description="`No ${title} available.`" />
    <div v-else class="post-list">
      <article v-for="item in rows" :key="item.id" class="post-row">
        <div class="post-main">
          <h3 class="post-title">{{ item.title }}</h3>
          <p class="post-summary" :class="{ clamp: previewLines > 0 }">{{ previewText(item) }}</p>
          <div v-if="showMeta" class="meta">
            <span v-if="showAuthor">Author: {{ item.author }}</span>
            <span v-if="showPublishedAt">Published: {{ formatDate(item.newsDate || item.createdAt) }}</span>
            <el-tag v-if="item.pinned" type="danger" size="small">Pinned</el-tag>
          </div>
        </div>
        <el-button type="primary" plain @click="open(item)">View Details</el-button>
      </article>
    </div>
  </el-card>

  <el-dialog v-if="detailMode !== 'route'" v-model="detailVisible" :title="detail?.title || 'Details'" width="760px">
    <div v-if="detail" class="detail-wrap">
      <div class="detail-meta">
        <span>Author: {{ detail.author }}</span>
        <span>Published: {{ formatDate(detail.newsDate || detail.createdAt) }}</span>
      </div>
      <div v-if="detail.type === 'NEWS' && detail.newsImageIds?.length" class="detail-images">
        <img
          v-for="(imageId, idx) in detail.newsImageIds"
          :key="`${imageId}-${idx}`"
          :src="newsImageUrl(imageId)"
          :alt="`news-image-${idx + 1}`"
          class="detail-image"
        >
      </div>
      <div class="detail-content">{{ detail.content }}</div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { filePreviewUrl } from '../api/fileApi'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  rows: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  adminEditable: {
    type: Boolean,
    default: false
  },
  detailMode: {
    type: String,
    default: 'dialog'
  },
  detailRouteBase: {
    type: String,
    default: ''
  },
  showMeta: {
    type: Boolean,
    default: true
  },
  showAuthor: {
    type: Boolean,
    default: true
  },
  showPublishedAt: {
    type: Boolean,
    default: true
  },
  previewLines: {
    type: Number,
    default: 0
  },
  preferContentPreview: {
    type: Boolean,
    default: false
  }
})

defineEmits(['manage'])

const detailVisible = ref(false)
const detail = ref(null)
const router = useRouter()

function open(item) {
  if (props.detailMode === 'route' && props.detailRouteBase) {
    router.push(`${props.detailRouteBase}/${item.id}`)
    return
  }
  detail.value = item
  detailVisible.value = true
}

function summarize(content, maxLength = 120) {
  if (!content) return ''
  const plain = String(content).replace(/\s+/g, ' ').trim()
  if (plain.length <= maxLength) return plain
  return `${plain.slice(0, maxLength)}...`
}

function previewText(item) {
  if (props.preferContentPreview) {
    return summarize(item?.content, 260)
  }
  if (item?.summary) {
    return String(item.summary).trim()
  }
  return summarize(item?.content, 260)
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function newsImageUrl(imageId) {
  const id = Number(imageId)
  if (!Number.isInteger(id) || id <= 0) return ''
  return filePreviewUrl(id)
}
</script>

<style scoped>
.card {
  width: 100%;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.post-row {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  overflow: hidden;
  min-width: 0;
}

.post-main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.post-title {
  margin: 0;
  font-size: 17px;
}

.post-summary {
  margin: 10px 0;
  color: #606266;
}

.post-summary.clamp {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.meta {
  display: flex;
  gap: 12px;
  align-items: center;
  color: #909399;
  font-size: 12px;
}

.detail-meta {
  color: #909399;
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-content {
  white-space: pre-wrap;
  line-height: 1.7;
}

.detail-images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.detail-image {
  width: 100%;
  height: 130px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

@media (max-width: 768px) {
  .post-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
