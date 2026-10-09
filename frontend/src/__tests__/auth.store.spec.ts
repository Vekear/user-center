import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
  me: vi.fn(),
}))

import * as authApi from '@/api/auth'
import { resetHttpBridge } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { User } from '@/api/types'

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 7,
    username: 'ada',
    email: 'ada@example.com',
    phone: null,
    nickname: 'Ada',
    avatarUrl: null,
    gender: null,
    birthday: null,
    bio: null,
    status: 'ACTIVE',
    roles: ['USER'],
    createdAt: '2026-01-01T08:00:00Z',
    lastLoginAt: '2026-01-02T08:00:00Z',
    ...overrides,
  }
}

describe('auth store 状态流转', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resetHttpBridge()
    vi.clearAllMocks()
  })

  it('登录成功后写入内存 token 与用户，并暴露是否管理员', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'token-1', user: makeUser() })

    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)

    await auth.login({ username: 'ada', password: 'secret123', remember: true })

    expect(authApi.login).toHaveBeenCalledWith({
      username: 'ada',
      password: 'secret123',
      remember: true,
    })
    expect(auth.accessToken).toBe('token-1')
    expect(auth.user?.username).toBe('ada')
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.isAdmin).toBe(false)
    expect(auth.displayName).toBe('Ada')
  })

  it('refresh 成功时静默续期，且并发只触发一次', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      accessToken: 'token-2',
      user: makeUser({ roles: ['USER', 'ADMIN'] }),
    })

    const auth = useAuthStore()
    const [first, second] = await Promise.all([auth.ensureReady(), auth.ensureReady()])

    expect(authApi.refresh).toHaveBeenCalledTimes(1)
    expect(first).toBe(true)
    expect(second).toBe(true)
    expect(auth.accessToken).toBe('token-2')
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.isAdmin).toBe(true)
    expect(auth.ready).toBe(true)
  })

  it('refresh 失败时清空登录态并回落为未登录', async () => {
    vi.mocked(authApi.refresh).mockRejectedValue(new Error('401'))

    const auth = useAuthStore()
    auth.accessToken = 'stale'
    auth.user = makeUser()

    const ok = await auth.ensureReady()

    expect(ok).toBe(false)
    expect(auth.accessToken).toBeNull()
    expect(auth.user).toBeNull()
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.ready).toBe(true)
  })

  it('登出即使后端报错也要清空本地状态', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'token-3', user: makeUser() })
    vi.mocked(authApi.logout).mockRejectedValue(new Error('network down'))

    const auth = useAuthStore()
    await auth.login({ username: 'ada', password: 'secret123' })
    expect(auth.isAuthenticated).toBe(true)

    await auth.logout()

    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(auth.accessToken).toBeNull()
    expect(auth.user).toBeNull()
    expect(auth.isAuthenticated).toBe(false)
  })
})
