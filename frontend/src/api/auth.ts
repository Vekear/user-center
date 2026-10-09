import { api, rawRequest } from './http'
import type { AuthResult, LoginPayload, RegisterPayload, User } from './types'

/** 注册。契约未规定 data 结构，按「返回创建的用户」处理，页面只关心成功与否。 */
export function register(payload: RegisterPayload): Promise<User | null> {
  return api.post<User | null>('/auth/register', payload)
}

/** 登录：accessToken 在响应体，refreshToken 在 httpOnly Cookie（前端读不到） */
export function login(payload: LoginPayload): Promise<AuthResult> {
  return api.post<AuthResult>('/auth/login', payload)
}

/** 静默续期：走裸实例，401 由调用方（auth store）处理，不触发拦截器的刷新逻辑 */
export function refresh(): Promise<AuthResult> {
  return rawRequest<AuthResult>({ url: '/auth/refresh', method: 'post' })
}

export function logout(): Promise<void> {
  return api.post<void>('/auth/logout')
}

export function me(): Promise<User> {
  return api.get<User>('/auth/me')
}
