import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as notificationsApi from '@/api/notifications'
import { toApiError } from '@/api/http'
import type { Notification } from '@/api/types'

export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref<Notification[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(10)
  const unreadOnly = ref(false)
  const unreadCount = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  /** 徽标轮询用：失败静默，不要打扰用户 */
  async function fetchUnreadCount(): Promise<void> {
    try {
      const result = await notificationsApi.fetchUnreadCount()
      unreadCount.value = result.count
    } catch {
      // 未登录或后端不可达时保持上一次的值
    }
  }

  /** 丢弃过期响应：快速翻页时只认最后一次请求的结果 */
  let requestToken = 0

  async function fetchList(): Promise<void> {
    const token = ++requestToken
    loading.value = true
    error.value = null
    try {
      const result = await notificationsApi.listNotifications({
        page: page.value,
        size: size.value,
        unreadOnly: unreadOnly.value,
      })
      if (token !== requestToken) return
      items.value = result.list
      total.value = result.total
    } catch (caught) {
      if (token !== requestToken) return
      const apiError = toApiError(caught)
      error.value = apiError.message
      items.value = []
      total.value = 0
    } finally {
      if (token === requestToken) loading.value = false
    }
  }

  async function markRead(id: number): Promise<void> {
    await notificationsApi.markRead(id)
    const target = items.value.find((item) => item.id === id)
    if (target && !target.read) {
      target.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  async function markAllRead(): Promise<void> {
    await notificationsApi.markAllRead()
    items.value = items.value.map((item) => ({ ...item, read: true }))
    unreadCount.value = 0
  }

  function reset(): void {
    items.value = []
    total.value = 0
    page.value = 1
    unreadOnly.value = false
    error.value = null
  }

  return {
    items,
    total,
    page,
    size,
    unreadOnly,
    unreadCount,
    loading,
    error,
    fetchUnreadCount,
    fetchList,
    markRead,
    markAllRead,
    reset,
  }
})
