import { createRouter, createWebHistory } from 'vue-router'
import type { RouteLocationNormalized, RouteRecordRaw } from 'vue-router'
import { onSessionExpired, useAuthStore } from '@/stores/auth'

export type GuardResult = true | { path: string; query?: Record<string, string> }

/**
 * 路由守卫（导出以便单测直接调用，无需构造真实导航）：
 *  - 未登录访问需登录页 → /login?redirect=<原地址>
 *  - 非管理员访问 /admin/* → /403
 *  - 已登录访问 /login、/register → 回资料页
 */
export async function authGuard(to: RouteLocationNormalized): Promise<GuardResult> {
  const auth = useAuthStore()
  const requiresAuth = to.meta.requiresAuth === true
  const requiresAdmin = to.meta.requiresAdmin === true

  if (!requiresAuth && !requiresAdmin) {
    if (to.meta.guestOnly === true && auth.isAuthenticated) {
      return { path: '/profile' }
    }
    return true
  }

  await auth.ensureReady()

  if (!auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (requiresAdmin && !auth.isAdmin) {
    return { path: '/403' }
  }

  return true
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guestOnly: true, title: '注册' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      { path: '', name: 'home', redirect: '/profile' },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { requiresAuth: true, title: '个人资料' },
      },
      {
        path: 'security',
        name: 'security',
        component: () => import('@/views/SecurityView.vue'),
        meta: { requiresAuth: true, title: '登录与安全' },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/NotificationsView.vue'),
        meta: { requiresAuth: true, title: '通知' },
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AppLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', name: 'admin-home', redirect: '/admin/users' },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/AdminUsersView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '用户管理' },
      },
      {
        path: 'audit',
        name: 'admin-audit',
        component: () => import('@/views/AdminAuditView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '审计日志' },
      },
      {
        path: 'notify',
        name: 'admin-notify',
        component: () => import('@/views/AdminNotifyView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '发送通知' },
      },
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/NotFoundView.vue'),
    props: { code: 403 },
    meta: { title: '无权限' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    props: { code: 404 },
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => authGuard(to))

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} · 用户中心` : '用户中心'
})

// refresh 失败（会话彻底失效）时清态并回登录页，避免用户停在半死的页面上
onSessionExpired(() => {
  const current = router.currentRoute.value
  if (current.name === 'login') return
  void router.replace({ path: '/login', query: { redirect: current.fullPath } })
})

export default router
