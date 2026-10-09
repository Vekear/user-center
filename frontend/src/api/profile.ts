import { api, uploadFile } from './http'
import type { ProfilePayload, User } from './types'

export function updateProfile(payload: ProfilePayload): Promise<User> {
  return api.put<User>('/profile', payload)
}

/** 上传裁剪后的头像 blob（multipart 字段名固定为 file） */
export function uploadAvatar(blob: Blob, filename = 'avatar.jpg'): Promise<{ avatarUrl: string }> {
  return uploadFile<{ avatarUrl: string }>('/profile/avatar', blob, 'file', filename)
}

export function deleteAvatar(): Promise<void> {
  return api.delete<void>('/profile/avatar')
}

/** 修改密码：成功后其他设备会话失效，当前会话保留 */
export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return api.put<void>('/profile/password', { oldPassword, newPassword })
}
