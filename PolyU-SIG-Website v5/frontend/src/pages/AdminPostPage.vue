<template>
  <el-card class="card">
    <template #header>
      <div class="head">
        <span>{{ pageTitle }}</span>
        <div class="head-actions">
          <el-select v-if="!lockType" v-model="filters.type" style="width: 180px" @change="loadPosts">
            <el-option label="All Types" value="" />
            <el-option label="News" value="NEWS" />
            <el-option label="Event" value="EVENT" />
            <el-option label="Announcement" value="ANNOUNCEMENT" />
          </el-select>
          <el-tag v-else type="info">{{ filters.type }}</el-tag>
          <el-button @click="openCreate">Create Post</el-button>
          <el-button :loading="loading" @click="loadPosts">Refresh</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" border stripe empty-text="No content yet.">
      <el-table-column label="ID" width="100">
        <template #default="{ row }">
          {{ row.displayId || row.id }}
        </template>
      </el-table-column>
      <el-table-column prop="title" label="Title" min-width="220" />
      <el-table-column prop="type" label="Type" width="150" />
      <el-table-column label="Status" width="160">
        <template #default="{ row }">
          <el-tag :type="row.published ? 'success' : 'info'" size="small">{{ row.published ? 'Published' : 'Draft' }}</el-tag>
          <el-tag v-if="row.pinned" type="danger" size="small" class="ml8">Pinned</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="author" label="Author" width="140" />
      <el-table-column label="Published At" min-width="180">
        <template #default="{ row }">
          {{ row.newsDate || row.createdAt }}
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="210" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">Edit</el-button>
          <el-button type="danger" link @click="remove(row)">Delete</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="editingId ? 'Edit Post' : 'Create Post'" width="800px">
    <el-form label-position="top">
      <el-form-item label="Title">
        <el-input v-model="form.title" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item v-if="!lockType" label="Type">
        <el-radio-group v-model="form.type">
          <el-radio value="NEWS">News</el-radio>
          <el-radio value="EVENT">Event</el-radio>
          <el-radio value="ANNOUNCEMENT">Announcement</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="isNewsForm" label="News Date">
        <el-date-picker
          v-model="form.newsDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="Select news date"
          clearable
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item v-if="isNewsForm" label="Photos (max 10)">
        <el-upload
          drag
          multiple
          :show-file-list="false"
          :before-upload="beforeNewsImageUpload"
          accept="image/*"
          style="width: 100%"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">Drop images here or <em>click to upload</em></div>
          <template #tip>
            <div class="el-upload__tip">JPG/PNG/GIF/WEBP/BMP/SVG supported, up to 10 photos.</div>
          </template>
        </el-upload>
        <div v-if="form.newsImages.length" class="news-image-list">
          <div v-for="(item, index) in form.newsImages" :key="`${item.id}-${index}`" class="news-image-row">
            <img :src="item.previewUrl" :alt="item.name || `news-image-${index + 1}`" class="news-image-thumb">
            <div class="news-image-meta">
              <div class="news-image-title">{{ item.name || `Image ${index + 1}` }}</div>
              <div class="news-image-id">File ID: {{ item.id }}</div>
            </div>
            <div class="news-image-actions">
              <el-button :disabled="index === 0" @click="moveNewsImage(index, -1)">Up</el-button>
              <el-button :disabled="index === form.newsImages.length - 1" @click="moveNewsImage(index, 1)">Down</el-button>
              <el-button type="danger" @click="removeNewsImage(index)">Remove</el-button>
            </div>
          </div>
        </div>
      </el-form-item>
      <el-form-item v-if="isEventForm" :label="summaryLabel">
        <el-input
          v-model="form.summary"
          type="textarea"
          :rows="isEventForm ? 2 : 3"
          maxlength="500"
          :placeholder="summaryPlaceholder"
          show-word-limit
        />
      </el-form-item>
      <template v-if="isEventForm">
        <el-divider content-position="left">Event Information</el-divider>
        <el-form-item label="Event Date Range">
          <el-date-picker
            v-model="form.eventRange"
            type="datetimerange"
            start-placeholder="Start date and time"
            end-placeholder="End date and time"
            value-format="YYYY-MM-DDTHH:mm:ss"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="Organizer">
          <el-input
            v-model="form.organizer"
            maxlength="255"
            placeholder="e.g. Industrial Centre, RiFood"
          />
        </el-form-item>
        <el-form-item label="Time">
          <el-input
            v-model="form.eventTimeLabel"
            maxlength="255"
            placeholder="e.g. 15:00 - 12:00"
          />
        </el-form-item>
        <el-form-item label="Venue">
          <el-input
            v-model="form.venue"
            maxlength="255"
            placeholder="e.g. ZN602, PolyU"
          />
        </el-form-item>
        <el-form-item label="Category">
          <el-select
            v-model="form.eventCategory"
            clearable
            placeholder="Select category"
            style="width: 100%"
          >
            <el-option label="Competition" value="Competition" />
            <el-option label="Workshop" value="Workshop" />
            <el-option label="Seminar" value="Seminar" />
            <el-option label="Webinar" value="Webinar" />
            <el-option label="Tour" value="Tour" />
            <el-option label="Others" value="Others" />
          </el-select>
        </el-form-item>
      </template>
      <el-form-item :label="contentLabel">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="isEventForm ? 10 : 12"
          maxlength="10000"
          :placeholder="contentPlaceholder"
          show-word-limit
        />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="form.published">Publish now</el-checkbox>
        <el-checkbox v-model="form.pinned" class="ml12">Pin to top</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">Cancel</el-button>
      <el-button type="primary" :loading="saving" @click="submit">Save</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { createPost, deletePost, listPosts, updatePost } from '../api/postApi'
import { uploadFile, filePreviewUrl } from '../api/fileApi'
import { csrf } from '../api/authApi'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  presetType: {
    type: String,
    default: ''
  },
  lockType: {
    type: Boolean,
    default: false
  },
  pageTitle: {
    type: String,
    default: 'News, Events & Announcements Management'
  }
})

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const filters = reactive({
  type: ''
})

const form = reactive({
  title: '',
  content: '',
  summary: '',
  newsDate: '',
  newsImages: [],
  eventRange: [],
  organizer: '',
  eventTimeLabel: '',
  venue: '',
  eventCategory: '',
  type: 'NEWS',
  published: true,
  pinned: false
})

const isEventForm = computed(() => form.type === 'EVENT')
const isNewsForm = computed(() => form.type === 'NEWS')

const summaryLabel = computed(() => {
  if (form.type === 'EVENT') return 'Event Summary'
  if (form.type === 'ANNOUNCEMENT') return 'Summary'
  return 'Summary'
})

const summaryPlaceholder = computed(() => {
  if (form.type === 'EVENT') return 'Write a short event highlight.'
  if (form.type === 'ANNOUNCEMENT') return ''
  return ''
})

const contentLabel = computed(() => {
  if (form.type === 'ANNOUNCEMENT') return 'Announcement Content'
  if (form.type === 'EVENT') return 'Event Details'
  return 'News Content'
})

const contentPlaceholder = computed(() => {
  if (form.type === 'ANNOUNCEMENT') return 'Enter announcement details.'
  if (form.type === 'EVENT') return 'Enter full event details.'
  return 'Enter full news content.'
})

watch(
  () => [route.path, route.query.type, props.presetType],
  () => {
  filters.type = resolveInitialType()
    form.type = filters.type || 'NEWS'
  loadPosts()
  },
  { immediate: true }
)

watch(
  () => form.type,
  (type) => {
    if (type !== 'EVENT') {
      form.eventRange = []
      form.organizer = ''
      form.eventTimeLabel = ''
      form.venue = ''
      form.eventCategory = ''
    form.summary = ''
    }
    if (type !== 'NEWS') {
      form.newsDate = ''
      form.newsImages = []
    }
  }
)

async function loadPosts() {
  loading.value = true
  try {
    rows.value = await listPosts({
      type: filters.type || undefined,
      published: null,
      limit: 100
    })
  } catch (error) {
    ElMessage.error(error?.message || 'Load failed')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.title = ''
  form.content = ''
  form.summary = ''
  form.newsDate = ''
  form.newsImages = []
  form.eventRange = []
  form.organizer = ''
  form.eventTimeLabel = ''
  form.venue = ''
  form.eventCategory = ''
  form.type = (filters.type || 'NEWS')
  form.published = true
  form.pinned = false
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.title = row.title
  form.content = row.content || ''
  form.summary = row.summary || ''
  form.newsDate = row.newsDate || ''
  form.newsImages = mapNewsImages(row.newsImageIds)
  form.eventRange = row.eventStartAt && row.eventEndAt ? [row.eventStartAt, row.eventEndAt] : []
  form.organizer = row.organizer || ''
  form.eventTimeLabel = row.eventTimeLabel || ''
  form.venue = row.venue || ''
  form.eventCategory = row.eventCategory || ''
  form.type = row.type
  if (props.lockType && filters.type) {
    form.type = filters.type
  }
  form.published = row.published
  form.pinned = row.pinned
  dialogVisible.value = true
}

async function submit() {
  await authStore.fetchMe()
  if (!authStore.isAdmin) {
    ElMessage.warning('Your session has expired. Please log in as admin again.')
    router.push({
      name: 'login',
      query: {
        next: route.fullPath || '/admin/posts/news'
      }
    })
    return
  }
  if (!form.title.trim()) {
    ElMessage.warning('Please enter a title.')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('Please enter content.')
    return
  }
  if (form.type === 'NEWS' && !form.newsDate) {
    ElMessage.warning('Please select a news date.')
    return
  }
  if (form.type === 'EVENT' && form.eventRange.length === 1) {
    ElMessage.warning('Please select both event start and end datetime.')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      summary: form.type === 'EVENT' ? form.summary.trim() : '',
      newsDate: form.type === 'NEWS' ? form.newsDate : null,
      newsImageIds: form.type === 'NEWS' ? form.newsImages.map((item) => item.id) : [],
      eventStartAt: form.type === 'EVENT' ? (form.eventRange[0] || null) : null,
      eventEndAt: form.type === 'EVENT' ? (form.eventRange[1] || null) : null,
      organizer: form.type === 'EVENT' ? form.organizer.trim() : '',
      eventTimeLabel: form.type === 'EVENT' ? form.eventTimeLabel.trim() : '',
      venue: form.type === 'EVENT' ? form.venue.trim() : '',
      eventCategory: form.type === 'EVENT' ? form.eventCategory || '' : '',
      type: props.lockType && filters.type ? filters.type : form.type,
      published: form.published,
      pinned: form.pinned
    }
    if (editingId.value) {
      await updatePost(editingId.value, payload)
      ElMessage.success('Updated successfully')
    } else {
      await createPost(payload)
      ElMessage.success('Published successfully')
    }
    dialogVisible.value = false
    await loadPosts()
  } catch (error) {
    if (String(error?.message || '').toLowerCase().includes('login required')) {
      ElMessage.error('Login session expired. Please log in again.')
      router.push({
        name: 'login',
        query: {
          next: route.fullPath || '/admin/posts/news'
        }
      })
      return
    }
    ElMessage.error(error?.message || 'Save failed')
  } finally {
    saving.value = false
  }
}

function mapNewsImages(ids) {
  if (!Array.isArray(ids)) {
    return []
  }
  return ids
    .map((id) => Number(id))
    .filter((id) => Number.isInteger(id) && id > 0)
    .slice(0, 10)
    .map((id, index) => ({
      id,
      name: `Image ${index + 1}`,
      previewUrl: filePreviewUrl(id)
    }))
}

async function beforeNewsImageUpload(rawFile) {
  if (!isNewsForm.value) {
    ElMessage.warning('News photos can only be uploaded when type is News.')
    return false
  }
  if (form.newsImages.length >= 10) {
    ElMessage.warning('At most 10 photos are allowed for one news post.')
    return false
  }
  if (!String(rawFile?.type || '').startsWith('image/')) {
    ElMessage.warning('Please upload image files only.')
    return false
  }
  try {
    await csrf().catch(() => null)
    const result = await uploadFile(rawFile, {
      title: `${form.title.trim() || 'News image'} - ${form.newsImages.length + 1}`,
      description: `Image for news post: ${form.title.trim() || 'Untitled'}`,
      category: 'News',
      tags: 'news-image',
      visibility: 'PUBLIC'
    })
    const imageId = Number(result?.id)
    if (!Number.isInteger(imageId) || imageId <= 0) {
      throw new Error('Upload succeeded but no valid file ID was returned.')
    }
    if (form.newsImages.some((item) => item.id === imageId)) {
      return false
    }
    form.newsImages.push({
      id: imageId,
      name: rawFile.name,
      previewUrl: filePreviewUrl(imageId)
    })
    ElMessage.success('Image uploaded.')
  } catch (error) {
    ElMessage.error(error?.message || 'Image upload failed')
  }
  return false
}

function moveNewsImage(index, direction) {
  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= form.newsImages.length) {
    return
  }
  const temp = form.newsImages[index]
  form.newsImages[index] = form.newsImages[targetIndex]
  form.newsImages[targetIndex] = temp
}

function removeNewsImage(index) {
  form.newsImages.splice(index, 1)
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`Are you sure you want to delete "${row.title}"?`, 'Confirmation', { type: 'warning' })
    await deletePost(row.id)
    ElMessage.success('Deleted successfully')
    await loadPosts()
  } catch (error) {
    if (error?.message !== 'cancel') {
      ElMessage.error(error?.message || 'Delete failed')
    }
  }
}

function resolveInitialType() {
  const routeType = String(route.query.type || '').toUpperCase()
  const presetType = String(props.presetType || '').toUpperCase()
  const pathTypeMap = {
    '/admin/posts/news': 'NEWS',
    '/admin/posts/announcements': 'ANNOUNCEMENT',
    '/admin/posts/events': 'EVENT'
  }
  const routePathType = pathTypeMap[route.path] || ''
  const valid = ['NEWS', 'EVENT', 'ANNOUNCEMENT']
  if (valid.includes(presetType)) {
    return presetType
  }
  if (valid.includes(routePathType)) {
    return routePathType
  }
  if (valid.includes(routeType)) {
    return routeType
  }
  return ''
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
  gap: 12px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ml8 {
  margin-left: 8px;
}

.ml12 {
  margin-left: 12px;
}

.news-image-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.news-image-row {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.news-image-thumb {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #dcdfe6;
}

.news-image-meta {
  flex: 1;
  min-width: 0;
}

.news-image-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.news-image-id {
  color: #909399;
  font-size: 12px;
}

.news-image-actions {
  display: flex;
  gap: 8px;
}

</style>
