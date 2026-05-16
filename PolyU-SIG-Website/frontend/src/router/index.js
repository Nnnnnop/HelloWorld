import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import HomePage from '../pages/HomePage.vue'
import AboutSigPage from '../pages/AboutSigPage.vue'
import FileListPage from '../pages/FileListPage.vue'
import SearchPage from '../pages/SearchPage.vue'
import FileDetailPage from '../pages/FileDetailPage.vue'
import UploadPage from '../pages/UploadPage.vue'
import UploadFolderPage from '../pages/UploadFolderPage.vue'
import LoginPage from '../pages/LoginPage.vue'
import RegisterPage from '../pages/RegisterPage.vue'
import EventsPage from '../pages/EventsPage.vue'
import EventDetailPage from '../pages/EventDetailPage.vue'
import NewsPage from '../pages/NewsPage.vue'
import NewsDetailPage from '../pages/NewsDetailPage.vue'
import AnnouncementsPage from '../pages/AnnouncementsPage.vue'
import AdminApprovalPage from '../pages/AdminApprovalPage.vue'
import AdminAuditPage from '../pages/AdminAuditPage.vue'
import AdminPostPage from '../pages/AdminPostPage.vue'
import AuthCallbackPage from '../pages/AuthCallbackPage.vue'
import PendingPage from '../pages/PendingPage.vue'
import InternalPortalPage from '../pages/InternalPortalPage.vue'
import PublicPortalPage from '../pages/PublicPortalPage.vue'
import JoinUsPage from '../pages/JoinUsPage.vue'
import AdminJoinPage from '../pages/AdminJoinPage.vue'
import AdminUsersPage from '../pages/AdminUsersPage.vue'
import FavouritesPage from '../pages/FavouritesPage.vue'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'home', component: HomePage },
  { path: '/about', name: 'about', component: AboutSigPage },
  { path: '/events', name: 'events', component: EventsPage },
  { path: '/events/:id', name: 'event-detail', component: EventDetailPage },
  { path: '/news', name: 'news', component: NewsPage },
  { path: '/news/:id', name: 'news-detail', component: NewsDetailPage },
  { path: '/announcements', name: 'announcements', component: AnnouncementsPage },
  { path: '/files', name: 'files', component: FileListPage, meta: { adminOnly: true } },
  { path: '/files/:id', name: 'file-detail', component: FileDetailPage, meta: { requireAuth: true } },
  { path: '/search', name: 'search', component: SearchPage, meta: { requireAuth: true } },
  { path: '/home/internal_portal/home', name: 'internal-home', component: HomePage, meta: { requireAuth: true } },
  { path: '/home/internal_portal/files', name: 'internal-files', component: FileListPage, meta: { adminOnly: true } },
  { path: '/home/internal_portal/files/:id', name: 'internal-file-detail', component: FileDetailPage, meta: { requireAuth: true } },
  { path: '/home/internal_portal/search', name: 'internal-search', component: SearchPage, meta: { requireAuth: true } },
  {
    path: '/home/internal_portal/favourites',
    name: 'internal-favourites',
    component: FavouritesPage,
    meta: { requireAuth: true }
  },
  {
    path: '/home/internal_portal/join-us',
    name: 'internal-join-us',
    component: JoinUsPage,
    meta: { requireAuth: true }
  },
  { path: '/home/internal_portal/files/uploads', name: 'upload', component: UploadPage, meta: { adminOnly: true } },
  { path: '/home/internal_portal/files/uploads-folder', name: 'upload-folder', component: UploadFolderPage, meta: { adminOnly: true } },
  { path: '/login', name: 'login', component: LoginPage, meta: { guestOnly: true } },
  { path: '/register', name: 'register', component: RegisterPage, meta: { guestOnly: true } },
  { path: '/pending', name: 'pending', component: PendingPage, meta: { requireAuth: true } },
  { path: '/auth/callback', name: 'auth-callback', component: AuthCallbackPage },
  { path: '/internal-portal', name: 'internal-portal', component: InternalPortalPage },
  { path: '/public-portal', name: 'public-portal', component: PublicPortalPage },
  { path: '/admin/posts', name: 'admin-posts', component: AdminPostPage, meta: { adminOnly: true } },
  {
    path: '/admin/posts/news',
    name: 'admin-posts-news',
    component: AdminPostPage,
    props: { presetType: 'NEWS', lockType: true, pageTitle: 'News Management' },
    meta: { adminOnly: true }
  },
  {
    path: '/admin/posts/announcements',
    name: 'admin-posts-announcements',
    component: AdminPostPage,
    props: { presetType: 'ANNOUNCEMENT', lockType: true, pageTitle: 'Announcement Management' },
    meta: { adminOnly: true }
  },
  {
    path: '/admin/posts/events',
    name: 'admin-posts-events',
    component: AdminPostPage,
    props: { presetType: 'EVENT', lockType: true, pageTitle: 'Event Management' },
    meta: { adminOnly: true }
  },
  { path: '/admin/approval', name: 'admin-approval', component: AdminApprovalPage, meta: { adminOnly: true } },
  { path: '/admin/users', name: 'admin-users', component: AdminUsersPage, meta: { adminOnly: true } },
  { path: '/admin/audit', name: 'admin-audit', component: AdminAuditPage, meta: { adminOnly: true } },
  {
    path: '/admin/membership',
    name: 'admin-membership',
    component: AdminJoinPage,
    meta: { adminOnly: true }
  },
  { path: '/admin/join', redirect: '/admin/membership' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (!authStore.loaded) {
    await authStore.fetchMe()
  }

  if (to.meta.guestOnly && authStore.isLoggedIn) {
    return '/home'
  }

  if (to.meta.requireAuth && !authStore.isLoggedIn) {
    return '/login'
  }

  if (to.meta.adminOnly) {
    if (!authStore.isLoggedIn) {
      return '/login'
    }
    if (!authStore.isAdmin) {
      return to.path.startsWith('/home/internal_portal') ? '/home/internal_portal/search' : '/search'
    }
  }

  if (to.name === 'internal-join-us' && authStore.isAdmin) {
    return '/home/internal_portal/home'
  }

  return true
})

export default router
