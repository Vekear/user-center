import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { configureHttp } from '@/api/http'
import type { AuthResult, LoginPayload, RegisterPayload, User } from '@/api/types'

/** 会话失效（refresh 失败）时的外部钩子：由 router 注册，负责跳登录页 */
let sessionExpiredHandler: (() => void) | null = null

export function onSessionExpired(handler: (() => void) | null): void {
  sessionExpiredHandler = handler
}

export const useAuthStore = defineStore('auth', () => {
  /** accessToken 只存在于内存，刷新页面即丢失，靠 /auth/refresh 静默续期 */
  const accessToken = ref<string | null>(null)
  const user = ref<User | null>(null)
  /** 首次静默续期是否已尝试过（无论成败），避免每次导航都打一次 refresh */
  const ready = ref(false)
  const pending = ref(false)

  let bootstrapPromise: Promise<boolean> | null = null

  const isAuthenticated = computed(() => accessToken.value !== null && user.value !== null)
  const isAdmin = computed(() => user.value?.roles?.includes('ADMIN') ?? false)
  const displayName = computed(() => user.value?.nickname || user.value?.username || '未登录')

  function clearSession(): void {
    accessToken.value = null
    user.value = null
  }

  function applyAuth(result: AuthResult): void {
    accessToken.value = result.accessToken
    if (result.user) user.value = result.user
  }

  /* ── http 拦截器桥接 ─────────────────────────────────────────────── */

  function provideAccessToken(): string | null {
    return accessToken.value
  }

  function onAuthFailure(): void {
    clearSession()
    sessionExpiredHandler?.()
  }

  configureHttp({
    getAccessToken: provideAccessToken,
    refresh: refreshSession,
    onAuthFailure,
  })

  /** 静默续期：成功返回新 accessToken，失败清空并返回 null */
  async function refreshSession(): Promise<string | null> {
    try {
      const result = await authApi.refresh()
      applyAuth(result)
      return result.accessToken
    } catch {
      clearSession()
      return null
    }
  }

  /** 应用启动/路由守卫调用：只尝试一次静默续期 */
  function ensureReady(): Promise<boolean> {
    if (ready.value) return Promise.resolve(accessToken.value !== null)
    if (!bootstrapPromise) {
      bootstrapPromise = refreshSession()
        .then((token) => {
          ready.value = true
          return token !== null
        })
        .finally(() => {
          bootstrapPromise = null
        })
    }
    return bootstrapPromise
  }

  /* ── 业务动作 ─────────────────────────────────────────────────────── */

  async function login(payload: LoginPayload): Promise<User> {
    pending.value = true
    try {
      const result = await authApi.login(payload)
      applyAuth(result)
      ready.value = true
      return result.user
    } finally {
      pending.value = false
    }
  }

  async function register(payload: RegisterPayload): Promise<User | null> {
    pending.value = true
    try {
      return await authApi.register(payload)
    } finally {
      pending.value = false
    }
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // 本地登出优先：后端不可达也要清掉内存态
    }
    clearSession()
  }

  async function fetchMe(): Promise<User> {
    const current = await authApi.me()
    user.value = current
    return current
  }

  function patchUser(partial: Partial<User>): void {
    if (!user.value) return
    user.value = { ...user.value, ...partial }
  }

  function hasRole(role: string): boolean {
    return user.value?.roles?.includes(role) ?? false
  }

  return {
    accessToken,
    user,
    ready,
    pending,
    isAuthenticated,
    isAdmin,
    displayName,
    ensureReady,
    refreshSession,
    login,
    register,
    logout,
    fetchMe,
    patchUser,
    hasRole,
    onAuthFailure,
    clearSession,
  }
})
