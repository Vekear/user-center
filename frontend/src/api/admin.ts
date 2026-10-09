import { api } from './http'
import type {
  AdminNotifyPayload,
  AdminStats,
  AdminUser,
  AdminUserQuery,
  AuditLog,
  AuditQuery,
  PageResult,
  RoleCode,
  UserStatus,
} from './types'

export function listUsers(query: AdminUserQuery = {}): Promise<PageResult<AdminUser>> {
  return api.get<PageResult<AdminUser>>('/admin/users', {
    keyword: query.keyword || undefined,
    status: query.status || undefined,
    role: query.role || undefined,
    page: query.page ?? 1,
    size: query.size ?? 10,
  })
}

export function updateUserStatus(id: number, status: UserStatus): Promise<void> {
  return api.patch<void>(`/admin/users/${id}/status`, { status })
}

export function updateUserRoles(id: number, roles: RoleCode[]): Promise<void> {
  return api.put<void>(`/admin/users/${id}/roles`, { roles })
}

export function resetPassword(id: number): Promise<{ tempPassword: string }> {
  return api.post<{ tempPassword: string }>(`/admin/users/${id}/reset-password`)
}

export function listAuditLogs(query: AuditQuery = {}): Promise<PageResult<AuditLog>> {
  return api.get<PageResult<AuditLog>>('/admin/audit-logs', {
    userId: query.userId ?? undefined,
    action: query.action || undefined,
    success: query.success === null || query.success === undefined ? undefined : query.success,
    from: query.from || undefined,
    to: query.to || undefined,
    page: query.page ?? 1,
    size: query.size ?? 10,
  })
}

export function sendNotification(payload: AdminNotifyPayload): Promise<void> {
  return api.post<void>('/admin/notifications', payload)
}

export function fetchStats(): Promise<AdminStats> {
  return api.get<AdminStats>('/admin/stats')
}
