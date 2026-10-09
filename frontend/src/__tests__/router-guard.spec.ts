import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  refresh: vi.fn(async () => {
    throw new Error('no session')
  }),
  logout: vi.fn(),
  me: vi.fn(),
}))

import { authGuard } from '@/router'
import { resetHttpBridge } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { User } from '@/api/types'

function makeUser(roles: string[] = ['USER']): User {
  return {
    id: 1,
    username: 'ada',
    email: 'ada@example.com',
    phone: null,
    nickname: 'Ada',
    avatarUrl: null,
    gender: null,
    birthday: null,
    bio: null,
    status: 'ACTIVE',
    roles,
    createdAt: '2026-01-01T08:00:00Z',
    lastLoginAt: null,
  }
}

function route(
  path: string,
  meta: Record<string, unknown> = {},
): RouteLocationNormalized {
  return {
    path,
    fullPath: path,
    hash: '',
    query: {},
    params: {},
    matched: [],
    meta,
    name: undefined,
    redirectedFrom: undefined,
  } as unknown as RouteLocationNormalized
}

describe('路由守卫三种去向', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resetHttpBridge()
    vi.clearAllMocks()
  })

  it('未登录访问需登录页 → 跳 /login 并带上 redirect', async () => {
    const auth = useAuthStore()
    auth.ready = true // 已尝试过静默续期且失败

    const result = await authGuard(route('/profile', { requiresAuth: true }))

    expect(result).toEqual({ path: '/login', query: { redirect: '/profile' } })
    expect(auth.isAuthenticated).toBe(false)
  })

  it('已登录但非管理员访问 /admin/* → 跳 /403', async () => {
    const auth = useAuthStore()
    auth.ready = true
    auth.accessToken = 'token'
    auth.user = makeUser(['USER'])

    const result = await authGuard(route('/admin/users', { requiresAuth: true, requiresAdmin: true }))

    expect(result).toEqual({ path: '/403' })
  })

  it('管理员访问 /admin/* → 放行', async () => {
    const auth = useAuthStore()
    auth.ready = true
    auth.accessToken = 'token'
    auth.user = makeUser(['USER', 'ADMIN'])

    const result = await authGuard(route('/admin/users', { requiresAuth: true, requiresAdmin: true }))

    expect(result).toBe(true)
  })

  it('已登录访问游客页（/login）→ 回个人资料', async () => {
    const auth = useAuthStore()
    auth.ready = true
    auth.accessToken = 'token'
    auth.user = makeUser()

    const result = await authGuard(route('/login', { guestOnly: true }))

    expect(result).toEqual({ path: '/profile' })
  })

  it('公开页面直接放行，不做静默续期', async () => {
    const auth = useAuthStore()
    const ensureReady = vi.spyOn(auth, 'ensureReady')

    const result = await authGuard(route('/register', { guestOnly: true }))

    expect(result).toBe(true)
    expect(ensureReady).not.toHaveBeenCalled()
  })

  it('未登录但 refresh 成功时可放行受保护页面', async () => {
    const authApi = await import('@/api/auth')
    vi.mocked(authApi.refresh).mockResolvedValue({ accessToken: 'fresh', user: makeUser() })

    const result = await authGuard(route('/security', { requiresAuth: true }))

    expect(result).toBe(true)
    expect(authApi.refresh).toHaveBeenCalledTimes(1)
    expect(useAuthStore().accessToken).toBe('fresh')
  })
})
