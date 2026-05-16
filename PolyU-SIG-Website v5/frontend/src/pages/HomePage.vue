<template>
  <div class="home-page">
    <section class="hero-banner">
      <el-carousel class="hero-carousel" :interval="6000" indicator-position="outside" arrow="always" height="360px">
        <el-carousel-item v-for="slide in heroSlides" :key="slide.title">
          <div class="hero-slide" :style="{ background: slide.background }">
            <div class="hero-content">
              <div class="hero-kicker">{{ slide.kicker }}</div>
              <h1 class="hero-title">{{ slide.title }}</h1>
              <p class="hero-subtitle">{{ slide.description }}</p>
              <div class="hero-actions">
                <el-button type="primary" @click="goPath(slide.primaryPath)">{{ slide.primaryText }}</el-button>
                <el-button plain @click="goPath(slide.secondaryPath)">{{ slide.secondaryText }}</el-button>
              </div>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>

      <div class="hero-search-wrap">
        <div class="hero-search">
          <el-input
            v-model="keyword"
            placeholder="Quick search for self-study materials (title/description/tags/content)"
            clearable
            @keyup.enter="goSearch"
          />
          <el-button type="primary" @click="goSearch">Search Materials</el-button>
        </div>
      </div>
    </section>

    <section class="section-card happening-card">
      <div class="section-head">
        <span>What's Happening</span>
      </div>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <div class="subsection-head">
            <span>Latest Announcements</span>
            <el-button link @click="router.push('/announcements')">More</el-button>
          </div>
          <el-skeleton v-if="loadingPosts" :rows="4" animated />
          <el-empty v-else-if="!announcements.length" description="No announcements yet." />
          <ul v-else class="list">
            <li v-for="item in announcements" :key="item.id">
              <button class="item-link" @click="router.push('/announcements')">{{ item.title }}</button>
              <span class="item-date">{{ formatDate(item.createdAt) }}</span>
            </li>
          </ul>
        </el-col>

        <el-col :xs="24" :md="12">
          <div class="subsection-head">
            <span>Latest News</span>
            <el-button link @click="router.push('/news')">More</el-button>
          </div>
          <el-skeleton v-if="loadingPosts" :rows="4" animated />
          <el-empty v-else-if="!news.length" description="No news yet." />
          <ul v-else class="list">
            <li v-for="item in news" :key="item.id">
              <button class="item-link" @click="router.push('/news')">{{ item.title }}</button>
              <span class="item-date">{{ formatDate(item.createdAt) }}</span>
            </li>
          </ul>
        </el-col>
      </el-row>
    </section>

    <el-row :gutter="16">
      <el-col :xs="24" :md="15">
        <el-card class="section-card" shadow="never">
          <template #header>
            <div class="section-head">
              <span>Self-Study Materials</span>
              <el-button link @click="goResourceArea">Resource Library</el-button>
            </div>
          </template>
          <div v-if="!authStore.isLoggedIn" class="resource-gate">
            <el-alert
              type="info"
              :closable="false"
              title="Please log in and pass permission checks before accessing self-study materials."
            />
            <div class="resource-gate-actions">
              <el-button type="primary" @click="router.push('/login')">Go to Login</el-button>
              <el-button @click="router.push('/register')">Create Account</el-button>
            </div>
          </div>
          <el-skeleton v-else-if="loadingResources" :rows="4" animated />
          <el-empty v-else-if="!resources.length" description="No resources available." />
          <ul v-else class="resource-list">
            <li v-for="item in resources" :key="item.id" class="resource-item">
              <button class="item-link" @click="goDetail(item.id)">{{ item.title }}</button>
              <div class="resource-meta">{{ item.category }} · {{ item.uploader }} · {{ formatDate(item.uploadTime) }}</div>
            </li>
          </ul>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="9">
        <el-card class="section-card" shadow="never">
          <template #header>
            <div class="section-head"><span>Quick Links</span></div>
          </template>
          <div class="quick-links">
            <el-button text @click="router.push('/home')">SIG Overview</el-button>
            <el-button text @click="router.push('/announcements')">All Announcements</el-button>
            <el-button text @click="router.push('/news')">All News</el-button>
            <el-button text @click="router.push('/search')">Search Materials</el-button>
            <el-button text @click="router.push('/about')">About SIG</el-button>
          </div>
        </el-card>

        <el-card class="section-card stats-card" shadow="never">
          <template #header>
            <div class="section-head"><span>At a Glance</span></div>
          </template>
          <div class="stats-grid">
            <div class="stat-item">
              <div class="stat-number">{{ announcements.length + news.length }}</div>
              <div class="stat-label">Latest Updates</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ authStore.isLoggedIn ? resources.length : '-' }}</div>
              <div class="stat-label">Visible Resources</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">24/7</div>
              <div class="stat-label">Online Access</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">SIG</div>
              <div class="stat-label">Community</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listFiles } from '../api/fileApi'
import { listPosts } from '../api/postApi'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const keyword = ref('')
const loadingPosts = ref(false)
const loadingResources = ref(false)
const announcements = ref([])
const news = ref([])
const resources = ref([])
const heroSlides = [
  {
    kicker: 'POLYU SIG',
    title: 'Connect, Learn, Build Together',
    description: 'A student-union portal that combines news, announcements, and self-study resource access.',
    primaryText: 'View Announcements',
    primaryPath: '/announcements',
    secondaryText: 'View News',
    secondaryPath: '/news',
    background: 'linear-gradient(120deg, #8d153a 0%, #bf1e2e 55%, #d63b48 100%)'
  },
  {
    kicker: 'WHAT IS NEW',
    title: "What's Happening in SIG",
    description: 'Stay updated on student-union activities, competitions, workshops, and community updates.',
    primaryText: 'Go to News',
    primaryPath: '/news',
    secondaryText: 'Back to Home',
    secondaryPath: '/home',
    background: 'linear-gradient(120deg, #7a1231 0%, #aa2037 52%, #d1505a 100%)'
  },
  {
    kicker: 'RESOURCE HUB',
    title: 'Self-study Materials on Demand',
    description: 'Log in to search by keyword, preview online, and download self-study materials.',
    primaryText: 'Search Materials',
    primaryPath: '/search',
    secondaryText: 'Internal Portal',
    secondaryPath: '/internal-portal',
    background: 'linear-gradient(120deg, #561632 0%, #8d153a 50%, #c73755 100%)'
  }
]

onMounted(async () => {
  await loadPosts()
  await loadResources()
})

watch(
  () => authStore.isLoggedIn,
  () => {
    loadResources()
  }
)

async function loadPosts() {
  loadingPosts.value = true
  try {
    const [announcementRows, newsRows] = await Promise.all([
      listPosts({ type: 'ANNOUNCEMENT', limit: 5 }),
      listPosts({ type: 'NEWS', limit: 5 })
    ])
    announcements.value = announcementRows
    news.value = newsRows
  } finally {
    loadingPosts.value = false
  }
}

async function loadResources() {
  if (!authStore.isLoggedIn) {
    resources.value = []
    return
  }
  loadingResources.value = true
  try {
    const rows = await listFiles()
    resources.value = rows.slice(0, 6)
  } catch {
    resources.value = []
  } finally {
    loadingResources.value = false
  }
}

function goSearch() {
  const query = keyword.value.trim() ? { keyword: keyword.value.trim() } : {}
  router.push({ name: 'search', query })
}

function goPath(path) {
  router.push(path)
}

function goResourceArea() {
  if (authStore.isAdmin) {
    router.push('/files')
  } else {
    router.push('/search')
  }
}

function goDetail(id) {
  router.push({ name: 'file-detail', params: { id } })
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString()
}
</script>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-banner {
  border-radius: 16px;
  overflow: hidden;
}

.hero-kicker {
  letter-spacing: 1.5px;
  font-size: 12px;
  opacity: 0.9;
}

.hero-title {
  font-size: 36px;
  font-weight: 700;
  margin: 10px 0 0;
}

.hero-subtitle {
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.95);
  line-height: 1.7;
}

.hero-search {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.hero-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.hero-carousel :deep(.el-carousel__container) {
  height: 360px;
}

.hero-slide {
  height: 100%;
  color: #fff;
  padding: 24px;
  display: flex;
  align-items: center;
}

.hero-content {
  max-width: 720px;
}

.hero-search-wrap {
  background: #fff;
  border: 1px solid #ebeef5;
  border-top: none;
  padding: 14px;
}

.section-card {
  border-radius: 12px;
}

.happening-card {
  padding-bottom: 8px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: 700;
}

.subsection-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}

.list,
.resource-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.list li,
.resource-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;
}

.list li:last-child,
.resource-item:last-child {
  border-bottom: none;
}

.item-link {
  border: none;
  background: transparent;
  color: #303133;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  padding: 0;
}

.item-link:hover {
  color: #409eff;
}

.item-date {
  font-size: 12px;
  color: #909399;
}

.resource-meta {
  color: #909399;
  font-size: 12px;
}

.resource-gate {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.resource-gate-actions {
  display: flex;
  gap: 8px;
}

.quick-links {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.stats-card {
  margin-top: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.stat-item {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 10px;
}

.stat-number {
  font-size: 22px;
  font-weight: 700;
  color: #8d153a;
}

.stat-label {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 768px) {
  .hero-slide {
    padding: 16px;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-carousel :deep(.el-carousel__container) {
    height: 320px;
  }

  .hero-search {
    grid-template-columns: 1fr;
  }

  .list li,
  .resource-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
