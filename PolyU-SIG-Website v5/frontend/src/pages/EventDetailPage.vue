<template>
  <div class="event-detail-page" v-loading="loading">
    <section class="hero">
      <div class="hero-inner">
        <div class="breadcrumb">Home / News & Events / Event</div>
        <h1>{{ post?.title || 'Event' }}</h1>
        <div class="type">{{ typeLabel }}</div>
      </div>
    </section>

    <section v-if="post" class="detail-wrap">
      <div class="action-row">
        <div class="action-group">
          <span class="action-label">Share</span>
          <el-button text @click="copyLink">Copy Link</el-button>
          <el-button text @click="shareByMail">Mail</el-button>
        </div>
        <div class="action-group">
          <span class="action-label">Add to Calendar</span>
          <a :href="googleCalendarUrl" target="_blank" rel="noopener noreferrer">Google</a>
          <a :href="outlookCalendarUrl" target="_blank" rel="noopener noreferrer">Outlook</a>
        </div>
      </div>

      <div class="meta-grid">
        <div class="meta-item">
          <div class="meta-label">Date</div>
          <div class="meta-value">{{ formatEventDateRange(post.eventStartAt, post.eventEndAt) }}</div>
        </div>
        <div class="meta-item">
          <div class="meta-label">Organiser</div>
          <div class="meta-value">{{ post.organizer || '-' }}</div>
        </div>
        <div class="meta-item">
          <div class="meta-label">Time</div>
          <div class="meta-value">{{ post.eventTimeLabel || '-' }}</div>
        </div>
        <div class="meta-item">
          <div class="meta-label">Venue</div>
          <div class="meta-value">{{ post.venue || '-' }}</div>
        </div>
        <div class="meta-item">
          <div class="meta-label">Category</div>
          <div class="meta-value">{{ post.eventCategory || typeLabel }}</div>
        </div>
        <div class="meta-item">
          <div class="meta-label">Posted By</div>
          <div class="meta-value">{{ post.author || '-' }}</div>
        </div>
      </div>

      <el-card shadow="never" class="summary-card">
        <template #header>Summary</template>
        <p v-if="post.summary" class="summary">{{ post.summary }}</p>
        <p v-else class="summary muted">No summary provided.</p>
      </el-card>

      <el-card shadow="never" class="content-card">
        <template #header>Details</template>
        <div class="content">{{ post.content }}</div>
      </el-card>
    </section>

    <el-empty v-else-if="!loading" description="Event not found." />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { getPost } from '../api/postApi'

const route = useRoute()
const loading = ref(false)
const post = ref(null)

const googleCalendarUrl = computed(() => {
  if (!post.value) return '#'
  const start = calendarDate(post.value.eventStartAt || post.value.createdAt)
  const end = calendarDate(post.value.eventEndAt || post.value.eventStartAt || post.value.createdAt)
  const title = encodeURIComponent(post.value.title || 'Event')
  const details = encodeURIComponent(post.value.content || '')
  const location = encodeURIComponent(post.value.venue || '')
  return `https://calendar.google.com/calendar/render?action=TEMPLATE&text=${title}&dates=${start}/${end}&details=${details}&location=${location}`
})

const outlookCalendarUrl = computed(() => {
  if (!post.value) return '#'
  const start = encodeURIComponent((post.value.eventStartAt || post.value.createdAt || '').replace(' ', 'T'))
  const end = encodeURIComponent((post.value.eventEndAt || post.value.eventStartAt || post.value.createdAt || '').replace(' ', 'T'))
  const title = encodeURIComponent(post.value.title || 'Event')
  const body = encodeURIComponent(post.value.content || '')
  const location = encodeURIComponent(post.value.venue || '')
  return `https://outlook.live.com/calendar/0/deeplink/compose?path=/calendar/action/compose&subject=${title}&startdt=${start}&enddt=${end}&body=${body}&location=${location}`
})

const typeLabel = computed(() => {
  const raw = String(post.value?.type || '').toUpperCase()
  if (raw === 'EVENT') return 'Event'
  if (raw === 'NEWS') return 'News'
  if (raw === 'ANNOUNCEMENT') return 'Announcement'
  return 'Event'
})

onMounted(loadDetail)
watch(() => route.params.id, loadDetail)

async function loadDetail() {
  loading.value = true
  try {
    const data = await getPost(route.params.id)
    post.value = data
  } catch (error) {
    post.value = null
    ElMessage.error(error?.message || 'Failed to load event details')
  } finally {
    loading.value = false
  }
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function formatEventDateRange(startAt, endAt) {
  const start = startAt ? new Date(startAt) : null
  const end = endAt ? new Date(endAt) : null
  if (!start || Number.isNaN(start.getTime())) return '-'
  if (!end || Number.isNaN(end.getTime())) return start.toLocaleString()
  return `${start.toLocaleDateString()} - ${end.toLocaleDateString()}`
}

function calendarDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const yyyy = date.getUTCFullYear()
  const mm = String(date.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(date.getUTCDate()).padStart(2, '0')
  const hh = String(date.getUTCHours()).padStart(2, '0')
  const min = String(date.getUTCMinutes()).padStart(2, '0')
  const sec = String(date.getUTCSeconds()).padStart(2, '0')
  return `${yyyy}${mm}${dd}T${hh}${min}${sec}Z`
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    ElMessage.success('Link copied')
  } catch {
    ElMessage.warning('Unable to copy link on this browser')
  }
}

function shareByMail() {
  if (!post.value) return
  const subject = encodeURIComponent(post.value.title || 'Event')
  const body = encodeURIComponent(`${post.value.summary || ''}\n\n${window.location.href}`)
  window.location.href = `mailto:?subject=${subject}&body=${body}`
}
</script>

<style scoped>
.event-detail-page {
  margin: -30px auto 0;
}

.hero {
  background:
    linear-gradient(140deg, rgba(141, 21, 58, 0.08) 0%, rgba(141, 21, 58, 0.02) 45%, rgba(0, 0, 0, 0.04) 45%, rgba(0, 0, 0, 0.02) 100%),
    #f2f3f5;
  border-left: 8px solid #8d153a;
  min-height: 180px;
  display: flex;
  align-items: center;
}

.hero-inner {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 24px;
}

.breadcrumb {
  color: #8c8c8c;
  font-size: 12px;
}

h1 {
  margin: 10px 0 0;
  font-size: 44px;
  color: #8d153a;
  font-weight: 500;
}

.type {
  margin-top: 8px;
  color: #8d153a;
  font-size: 20px;
}

.detail-wrap {
  max-width: 1200px;
  margin: 24px auto 0;
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.action-row {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.action-group {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
}

.action-group a {
  color: #8d153a;
  text-decoration: none;
}

.action-label {
  font-weight: 600;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.meta-item {
  background: #f7f7f8;
  border: 1px solid #ededed;
  padding: 12px;
}

.meta-label {
  color: #909399;
  font-size: 12px;
}

.meta-value {
  margin-top: 4px;
  color: #303133;
}

.summary,
.content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.8;
}

.muted {
  color: #909399;
}

@media (max-width: 900px) {
  .event-detail-page {
    margin-top: -16px;
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
