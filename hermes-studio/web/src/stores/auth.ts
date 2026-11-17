import { defineStore } from 'pinia'
import { api, getToken, setToken, clearToken, TOKEN_KEY, USER_KEY } from '../api/client'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    username: localStorage.getItem(USER_KEY) || ''
  }),
  getters: {
    isLoggedIn: (s) => !!s.token
  },
  actions: {
    async login(username: string, password: string) {
      const data = await api<{ token: string; username: string }>('/api/auth/login', {
        method: 'POST',
        body: { username, password }
      })
      setToken(data.token, data.username || username)
      this.token = data.token
      this.username = data.username || username
    },
    logout() {
      clearToken()
      this.token = ''
      this.username = ''
    }
  }
})
