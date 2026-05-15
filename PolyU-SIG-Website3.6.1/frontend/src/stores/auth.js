import { defineStore } from 'pinia'
import { csrf, login, logout, me, oauthUrl, register } from '../api/authApi'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    loaded: false
  }),
  getters: {
    isLoggedIn: (state) => !!state.user,
    role: (state) => state.user?.role || 'GUEST',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isMember: (state) => ['MEMBER', 'ADMIN'].includes(state.user?.role),
    isApproved: (state) => state.user?.status === 'APPROVED'
  },
  actions: {
    async fetchMe() {
      try {
        await csrf()
        this.user = await me()
      } catch {
        this.user = null
      } finally {
        this.loaded = true
      }
    },
    async localRegister(payload) {
      const user = await register(payload)
      await csrf().catch(() => {})
      this.user = user
      return user
    },
    async localLogin(payload) {
      const user = await login(payload)
      await csrf().catch(() => {})
      this.user = user
      return user
    },
    async oauthLogin() {
      const data = await oauthUrl()
      window.location.href = data.url
    },
    async doLogout() {
      // Ensure CSRF cookie exists for logout POST.
      await csrf().catch(() => {})
      try {
        await logout()
      } catch (error) {
        const message = error?.message || ''
        // If network is unavailable or session is already invalid on server,
        // still clear client state to avoid trapping user in UI.
        if (
          !message.includes('Failed to fetch') &&
          !message.includes('NetworkError') &&
          !message.includes('(401)') &&
          !message.includes('(403)')
        ) {
          throw error
        }
      } finally {
        this.user = null
      }
    },
    clearSession() {
      this.user = null
      this.loaded = true
    }
  }
})
