import { api } from './http'
import type { Session } from './types'

export function listSessions(): Promise<Session[]> {
  return api.get<Session[]>('/security/sessions')
}

export function revokeSession(id: string): Promise<void> {
  return api.delete<void>(`/security/sessions/${encodeURIComponent(id)}`)
}

export function revokeOtherSessions(): Promise<void> {
  return api.delete<void>('/security/sessions/others')
}

/** 注销账号（逻辑删除）：成功后清空登录态并跳登录页 */
export function deleteAccount(password: string): Promise<void> {
  return api.delete<void>('/security/account', { password })
}
