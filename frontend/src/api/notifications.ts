import { api } from './http'
import type { Notification, NotificationQuery, PageResult, UnreadCount } from './types'

export function listNotifications(query: NotificationQuery = {}): Promise<PageResult<Notification>> {
  return api.get<PageResult<Notification>>('/notifications', {
    page: query.page ?? 1,
    size: query.size ?? 10,
    unreadOnly: query.unreadOnly ?? false,
  })
}

export function fetchUnreadCount(): Promise<UnreadCount> {
  return api.get<UnreadCount>('/notifications/unread-count')
}

export function markRead(id: number): Promise<void> {
  return api.post<void>(`/notifications/${id}/read`)
}

export function markAllRead(): Promise<void> {
  return api.post<void>('/notifications/read-all')
}
