/**
 * 后端契约类型（严格按照 docs 里的接口契约字面实现）。
 * 统一响应：{ code, message, data, traceId? }，code === 0 为成功。
 */

/** 统一响应信封 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

/** 分页数据 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/** 400 校验失败时 data 是字段错误表 */
export type FieldErrors = Record<string, string>

export type UserStatus = 'ACTIVE' | 'DISABLED'
export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN'
export type RoleCode = 'USER' | 'ADMIN'

export interface User {
  id: number
  username: string
  email: string
  phone: string | null
  nickname: string | null
  avatarUrl: string | null
  gender: Gender | null
  birthday: string | null
  bio: string | null
  status: UserStatus
  roles: string[]
  createdAt: string
  lastLoginAt: string | null
}

export interface LoginPayload {
  username: string
  password: string
  remember?: boolean
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  nickname?: string
}

export interface AuthResult {
  accessToken: string
  user: User
}

export interface ProfilePayload {
  nickname: string | null
  email: string
  phone: string | null
  gender: Gender | null
  birthday: string | null
  bio: string | null
}

export interface Session {
  id: string
  current: boolean
  deviceName: string | null
  userAgent: string | null
  ip: string | null
  lastSeenAt: string | null
  createdAt: string
  expiresAt: string | null
}

export interface Notification {
  id: number
  title: string
  content: string
  read: boolean
  createdAt: string
  readAt?: string | null
}

export interface NotificationQuery {
  page?: number
  size?: number
  unreadOnly?: boolean
}

export interface UnreadCount {
  count: number
}

export interface AdminUser {
  id: number
  username: string
  email: string
  phone: string | null
  nickname: string | null
  avatarUrl: string | null
  status: UserStatus
  roles: string[]
  lastLoginAt: string | null
  createdAt: string
}

export interface AdminUserQuery {
  keyword?: string
  status?: UserStatus | ''
  role?: string
  page?: number
  size?: number
}

export interface AuditLog {
  id: number
  username: string
  action: string
  targetType: string | null
  targetId: string | null
  ip: string | null
  success: boolean
  detail: string | null
  createdAt: string
}

export interface AuditQuery {
  userId?: number | null
  action?: string
  success?: boolean | null
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface AdminStats {
  userTotal: number
  userActive: number
  userDisabled: number
  notificationUnread: number
  auditToday: number
  newUsers7d: number
}

export interface AdminNotifyPayload {
  userIds?: number[]
  all?: boolean
  title: string
  content: string
}
