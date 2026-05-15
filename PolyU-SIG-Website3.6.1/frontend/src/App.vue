<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="topbar">
        <div class="brand">
          <div class="brand-kicker">POLYU STUDENT UNION</div>
          <button class="title-link" @click="go('/home')">SIG Hub</button>
        </div>

        <div class="header-right desktop-only">
          <template v-if="authStore.isLoggedIn">
            <el-tag>{{ authStore.user.username }} / {{ authStore.user.role }}</el-tag>
            <el-dropdown v-if="authStore.isAdmin" trigger="click">
              <el-button size="small">Admin</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="go('/admin/users')">Site roles</el-dropdown-item>
                  <el-dropdown-item @click="go('/admin/posts')">Publish News/Announcement/Event</el-dropdown-item>
                  <el-dropdown-item @click="go('/admin/audit')">Audit Logs</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" @click="handleLogout">Logout</el-button>
          </template>
          <template v-else>
            <el-button size="small" @click="go('/login')">Login</el-button>
            <el-button size="small" type="primary" @click="go('/register')">Register</el-button>
          </template>
        </div>

        <div class="mobile-only">
          <el-button text @click="drawerVisible = true">Menu</el-button>
        </div>
      </div>

      <div v-if="!internalPortalMode" class="nav-row desktop-only">
        <el-menu :default-active="activePath" mode="horizontal" router class="main-menu">
          <el-sub-menu index="/news-events" popper-class="top-nav-popper">
            <template #title>News & Events</template>
            <el-menu-item index="/events">Event</el-menu-item>
            <el-menu-item index="/news">News</el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/announcements">Announcement</el-menu-item>
          <el-menu-item index="/about">About SIG</el-menu-item>
          <el-menu-item index="/internal-portal">Internal Portal</el-menu-item>
        </el-menu>
      </div>
      <div v-else class="nav-row desktop-only">
        <el-menu :default-active="activePath" mode="horizontal" router class="main-menu">
          <el-menu-item index="/home/internal_portal/home">Home</el-menu-item>
          <el-menu-item v-if="showStorageNav" index="/home/internal_portal/files">Source Management</el-menu-item>
          <el-menu-item index="/home/internal_portal/search">Search</el-menu-item>
          <el-menu-item index="/home/internal_portal/favourites">Favourites</el-menu-item>
          <el-menu-item v-if="!authStore.isAdmin" index="/home/internal_portal/join-us">Join Us</el-menu-item>
          <el-menu-item v-if="authStore.isAdmin" index="/admin/membership">
            Membership Management
          </el-menu-item>
          <el-menu-item v-if="authStore.isAdmin" index="/admin/users">Site roles</el-menu-item>
          <el-menu-item index="/public-portal">Public Portal</el-menu-item>
        </el-menu>
      </div>
    </el-header>

    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>

  <el-drawer v-model="drawerVisible" title="Navigation" direction="rtl" size="78%">
    <div class="drawer-user">
      <template v-if="authStore.isLoggedIn">
        <el-tag>{{ authStore.user.username }} / {{ authStore.user.role }}</el-tag>
        <el-button size="small" @click="onDrawerLogout">Logout</el-button>
      </template>
      <template v-else>
        <el-button size="small" @click="goFromDrawer('/login')">Login</el-button>
        <el-button size="small" type="primary" @click="goFromDrawer('/register')">Register</el-button>
      </template>
    </div>

    <div class="drawer-links">
      <template v-if="!internalPortalMode">
        <div class="drawer-group">
          <div class="drawer-group-title">News & Events</div>
          <el-button text @click="goFromDrawer('/events')">Event</el-button>
          <el-button text @click="goFromDrawer('/news')">News</el-button>
        </div>
        <el-button text @click="goFromDrawer('/announcements')">Announcement</el-button>
        <el-button text @click="goFromDrawer('/about')">About SIG</el-button>
        <el-button text @click="enterInternalPortalFromDrawer">Internal Portal</el-button>
        <el-button v-if="authStore.isAdmin" text @click="goFromDrawer('/admin/users')">Site roles</el-button>
        <el-button v-if="authStore.isAdmin" text @click="goFromDrawer('/admin/posts')">Publish News/Announcement/Event</el-button>
        <el-button v-if="authStore.isAdmin" text @click="goFromDrawer('/admin/audit')">Audit Logs</el-button>
      </template>
      <template v-else>
        <el-button text @click="goFromDrawer('/home/internal_portal/home')">Home</el-button>
        <el-button v-if="showStorageNav" text @click="goFromDrawer('/home/internal_portal/files')">Source Management</el-button>
        <el-button text @click="goFromDrawer('/home/internal_portal/search')">Search</el-button>
        <el-button text @click="goFromDrawer('/home/internal_portal/favourites')">Favourites</el-button>
        <el-button v-if="!authStore.isAdmin" text @click="goFromDrawer('/home/internal_portal/join-us')">Join Us</el-button>
        <el-button v-if="authStore.isAdmin" text @click="goFromDrawer('/admin/membership')">Membership Management</el-button>
        <el-button v-if="authStore.isAdmin" text @click="goFromDrawer('/admin/users')">Site roles</el-button>
        <el-button text @click="exitInternalPortalFromDrawer">Public Portal</el-button>
        <el-button v-if="authStore.isAdmin" text @click="goFromDrawer('/admin/posts')">Publish News/Announcement/Event</el-button>
        <el-button v-if="authStore.isAdmin" text @click="goFromDrawer('/admin/audit')">Audit Logs</el-button>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const drawerVisible = ref(false)
const internalPortalMode = ref(false)
/** Source Management (file list) is admin-only; keep nav item and highlights consistent. */
const showStorageNav = computed(() => authStore.isAdmin)
const activePath = computed(() => {
  if (internalPortalMode.value) {
    if (route.path.startsWith('/admin/membership')) return '/admin/membership'
    if (route.path.startsWith('/admin/users')) return '/admin/users'
    if (route.path.startsWith('/home/internal_portal/files')) {
      return showStorageNav.value ? '/home/internal_portal/files' : '/home/internal_portal/search'
    }
    if (route.path === '/home/internal_portal/search') return '/home/internal_portal/search'
    if (route.path === '/home/internal_portal/favourites') return '/home/internal_portal/favourites'
    if (route.path === '/home/internal_portal/join-us') return '/home/internal_portal/join-us'
    if (route.path === '/home/internal_portal/home') return '/home/internal_portal/home'
    return '/home/internal_portal/home'
  } else {
    if (route.path.startsWith('/events')) return '/events'
    if (route.path.startsWith('/news')) return '/news'
    if (route.path.startsWith('/admin/membership')) return '/admin/membership'
    if (route.path.startsWith('/admin/users')) return '/admin/users'
    return route.path
  }
})
const IDLE_TIMEOUT_MS = 15 * 60 * 1000

function handleEnterInternalPortal() {
  internalPortalMode.value = true
}

function handleExitInternalPortal() {
  internalPortalMode.value = false
}

window.addEventListener('enterInternalPortal', handleEnterInternalPortal)
window.addEventListener('exitInternalPortal', handleExitInternalPortal)
const ACTIVITY_EVENTS = ['click', 'mousemove', 'keydown', 'scroll', 'touchstart']
let idleTimer = null
let listenersBound = false

function go(path) {
  router.push(path)
}

function goFromDrawer(path) {
  drawerVisible.value = false
  router.push(path)
}

function enterInternalPortal() {
  router.push('/internal-portal')
}

function exitInternalPortal() {
  internalPortalMode.value = false
  router.push('/')
}

function enterInternalPortalFromDrawer() {
  drawerVisible.value = false
  router.push('/internal-portal')
}

function exitInternalPortalFromDrawer() {
  drawerVisible.value = false
  router.push('/public-portal')
}

async function onDrawerLogout() {
  drawerVisible.value = false
  await handleLogout()
}

async function handleLogout() {
  try {
    stopIdleTracking()
    const serverOk = await authStore.doLogout()
    if (serverOk) {
      ElMessage.success('Logged out successfully')
    } else {
      ElMessage.warning(
        'Signed out here, but the server could not be reached. If the API is down, fix it or clear site cookies later so the session is fully ended.'
      )
    }
    router.push('/login')
  } catch (error) {
    ElMessage.error(error?.message || 'Logout failed, please try again.')
    authStore.clearSession()
    router.push('/login')
  }
}

function handleUserActivity() {
  if (!authStore.isLoggedIn) return
  scheduleIdleLogout()
}

function scheduleIdleLogout() {
  if (idleTimer) {
    clearTimeout(idleTimer)
  }
  idleTimer = setTimeout(async () => {
    if (!authStore.isLoggedIn) return
    const nextPath = route.fullPath || '/home'
    stopIdleTracking()
    try {
      await authStore.doLogout()
    } catch {
      authStore.clearSession()
    }
    ElMessage.warning('You were logged out automatically due to inactivity. Please log in again.')
    router.replace({
      name: 'login',
      query: {
        reason: 'timeout',
        next: nextPath
      }
    })
  }, IDLE_TIMEOUT_MS)
}

function startIdleTracking() {
  if (!listenersBound) {
    ACTIVITY_EVENTS.forEach((eventName) => {
      window.addEventListener(eventName, handleUserActivity, { passive: true })
    })
    listenersBound = true
  }
  scheduleIdleLogout()
}

function stopIdleTracking() {
  if (idleTimer) {
    clearTimeout(idleTimer)
    idleTimer = null
  }
  if (listenersBound) {
    ACTIVITY_EVENTS.forEach((eventName) => {
      window.removeEventListener(eventName, handleUserActivity)
    })
    listenersBound = false
  }
}

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      startIdleTracking()
      const pending = sessionStorage.getItem('pendingInternalPortal')
      if (pending) {
        sessionStorage.removeItem('pendingInternalPortal')
        internalPortalMode.value = true
        router.push('/home/internal_portal/home')
      }
    } else {
      stopIdleTracking()
      internalPortalMode.value = false // reset on logout
    }
  },
  { immediate: true }
)

watch(
  () => route.path,
  (path) => {
    if (internalPortalMode.value) {
      const staysInInternalChrome =
          path.startsWith('/home/internal_portal/') || path.startsWith('/admin/')
      if (!staysInInternalChrome) {
        internalPortalMode.value = false
      }
    }
  }
)

onBeforeUnmount(() => {
  stopIdleTracking()
  window.removeEventListener('enterInternalPortal', handleEnterInternalPortal)
  window.removeEventListener('exitInternalPortal', handleExitInternalPortal)
})
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: #f5f7fa;
  overflow-x: hidden;
}

.header {
  height: auto;
  padding: 0;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px 10px;
  border-bottom: 1px solid #ebeef5;
}

.brand-kicker {
  font-size: 11px;
  letter-spacing: 1px;
  color: #909399;
  margin-bottom: 2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-link {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  font-size: 22px;
  font-weight: 700;
  color: #8d153a;
  white-space: nowrap;
}

.title-link:hover {
  color: #a01744;
}

.nav-row {
  padding: 0 8px;
}

.main-menu {
  border-bottom: none;
}

.main {
  max-width: 1200px;
  width: 100%;
  margin: 30px auto 0;
  padding: 0 12px;
  overflow-x: hidden;
  min-width: 0;
}

.drawer-user {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.drawer-links {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.drawer-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 6px;
  padding-bottom: 6px;
}

.drawer-group-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.mobile-only {
  display: none;
}

@media (max-width: 900px) {
  .desktop-only {
    display: none;
  }

  .mobile-only {
    display: block;
  }

  .topbar {
    padding: 10px 12px;
  }

  .title-link {
    font-size: 18px;
  }

  .main {
    margin-top: 16px;
    padding: 0 10px;
  }
}
</style>