import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref<boolean>(localStorage.getItem('dept_sidebar_collapsed') === 'true')
  const globalLoading = ref<boolean>(false)

  const sidebarWidth = computed(() => (sidebarCollapsed.value ? '64px' : '220px'))

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('dept_sidebar_collapsed', String(sidebarCollapsed.value))
  }

  function setLoading(loading: boolean) {
    globalLoading.value = loading
  }

  return {
    sidebarCollapsed,
    globalLoading,
    sidebarWidth,
    toggleSidebar,
    setLoading
  }
})
