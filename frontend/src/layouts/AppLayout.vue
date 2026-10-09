<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import AppButton from '@/components/AppButton.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import { useToast } from '@/composables/useToast'

interface NavItem {
  label: string
  to: string
  icon: string
}

const auth = useAuthStore()
const notifications = useNotificationsStore()
const router = useRouter()
const toast = useToast()

/** 只画必要的线条：16px 网格上手写的 stroke 图标，不引图标库 */
const ICONS: Record<string, string> = {
  profile: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-8 8a8 8 0 0 1 16 0',
  security: 'M12 3l7 3v6c0 4.5-3 7.4-7 9-4-1.6-7-4.5-7-9V6l7-3Z',
  bell: 'M6 16v-5a6 6 0 1 1 12 0v5l1.5 2.5h-15L6 16Zm4 3.5a2 2 0 0 0 4 0',
  users: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm-6 8a6 6 0 0 1 12 0M16 5.5a3.5 3.5 0 0 1 0 7M18 19a6 6 0 0 0-2-4.5',
  audit: 'M5 6h14M5 12h14M5 18h9',
  send: 'M4 12l16-7-6 16-2.5-6.5L4 12Z',
  logout: 'M15 12H4m0 0 3.5-3.5M4 12l3.5 3.5M11 5h6a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-6',
}

const primaryNav: NavItem[] = [
  { label: '个人资料', to: '/profile', icon: 'profile' },
  { label: '登录与安全', to: '/security', icon: 'security' },
  { label: '通知', to: '/notifications', icon: 'bell' },
]

const adminNav: NavItem[] = [
  { label: '用户管理', to: '/admin/users', icon: 'users' },
  { label: '审计日志', to: '/admin/audit', icon: 'audit' },
  { label: '发送通知', to: '/admin/notify', icon: 'send' },
]

const unread = computed(() => notifications.unreadCount)
const unreadLabel = computed(() => (unread.value > 99 ? '99+' : String(unread.value)))
const initial = computed(() => (auth.displayName || '?').trim().charAt(0).toUpperCase())
const archiveNo = computed(() => (auth.user ? `UC-${String(auth.user.id).padStart(6, '0')}` : 'UC-______'))

onMounted(() => {
  if (auth.isAuthenticated) void notifications.fetchUnreadCount()
})

watch(
  () => auth.user?.id,
  (id) => {
    if (id) void notifications.fetchUnreadCount()
  },
)

async function logout(): Promise<void> {
  await auth.logout()
  notifications.reset()
  toast.info('已退出登录')
  await router.push('/login')
}
</script>

<template>
  <div class="shell">
    <nav class="nav" aria-label="主导航">
      <div class="nav__brand">
        <span class="nav__brand-mark" aria-hidden="true" />
        <span class="nav__brand-text">
          <span class="nav__brand-title">个人档案</span>
          <span class="nav__brand-no mono">{{ archiveNo }}</span>
        </span>
      </div>

      <div class="nav__body">
        <div class="nav__group">
          <p class="nav__group-title">我的档案</p>
          <RouterLink
            v-for="item in primaryNav"
            :key="item.to"
            class="nav__item"
            active-class="nav__item--active"
            :to="item.to"
          >
            <svg class="nav__icon" viewBox="0 0 24 24" aria-hidden="true">
              <path :d="ICONS[item.icon]" />
            </svg>
            <span class="nav__label">{{ item.label }}</span>
            <span
              v-if="item.to === '/notifications' && unread > 0"
              class="nav__dot"
              data-test="notify-badge"
              :aria-label="`${unread} 条未读通知`"
            >
              {{ unreadLabel }}
            </span>
          </RouterLink>
        </div>

        <div v-if="auth.isAdmin" class="nav__group">
          <p class="nav__group-title">管理</p>
          <RouterLink
            v-for="item in adminNav"
            :key="item.to"
            class="nav__item"
            active-class="nav__item--active"
            :to="item.to"
          >
            <svg class="nav__icon" viewBox="0 0 24 24" aria-hidden="true">
              <path :d="ICONS[item.icon]" />
            </svg>
            <span class="nav__label">{{ item.label }}</span>
          </RouterLink>
        </div>
      </div>

      <div class="nav__foot">
        <div class="nav__user">
          <span class="nav__avatar" aria-hidden="true">{{ initial }}</span>
          <span class="nav__user-text">
            <span class="nav__user-name">{{ auth.displayName }}</span>
            <span class="nav__user-role mono">{{ auth.user?.roles?.join(' / ') || '—' }}</span>
          </span>
        </div>
        <AppButton size="sm" variant="ghost" @click="logout">
          <svg class="nav__icon" viewBox="0 0 24 24" aria-hidden="true">
            <path :d="ICONS.logout" />
          </svg>
          退出登录
        </AppButton>
      </div>
    </nav>

    <main class="main">
      <div class="main__inner">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--nav-width) minmax(0, 1fr);
  min-height: 100vh;
  background: var(--paper);
}

/* ── 左侧档案脊 ─────────────────────────────────────────────────────── */

.nav {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--nav-bg);
  color: var(--nav-fg);
  overflow-y: auto;
}

.nav__brand {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-5) var(--sp-4) var(--sp-4);
  border-bottom: 1px solid var(--nav-rule);
}

.nav__brand-mark {
  flex: none;
  width: 26px;
  height: 32px;
  border: 1px solid var(--nav-fg-muted);
  border-top: 3px solid var(--amber);
  background: transparent;
}

.nav__brand-text {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.nav__brand-title {
  font-family: var(--font-display);
  font-size: var(--text-md);
  font-weight: var(--fw-semibold);
  color: var(--nav-fg);
}

.nav__brand-no {
  font-size: var(--text-2xs);
  letter-spacing: 0.1em;
  color: var(--nav-fg-muted);
}

.nav__body {
  flex: 1;
  display: grid;
  align-content: start;
  gap: var(--sp-5);
  padding: var(--sp-5) var(--sp-3);
}

.nav__group {
  display: grid;
  gap: 2px;
}

.nav__group-title {
  padding: 0 var(--sp-3) var(--sp-2);
  font-size: var(--text-2xs);
  font-weight: var(--fw-semibold);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--nav-fg-muted);
}

.nav__item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 8px var(--sp-3);
  border-left: 3px solid transparent;
  border-radius: var(--radius-xs);
  color: var(--nav-fg);
  font-size: var(--text-base);
  text-decoration: none;
}

.nav__item:hover {
  background: var(--nav-hover-bg);
  color: var(--nav-fg);
  text-decoration: none;
}

.nav__item--active {
  background: var(--nav-active-bg);
  border-left-color: var(--amber);
}

.nav__icon {
  flex: none;
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.nav__label {
  flex: 1;
  min-width: 0;
}

/* 未读红点：全站保留的两处动效之一 */
.nav__dot {
  flex: none;
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--danger);
  color: var(--paper-on-dark);
  font-family: var(--font-mono);
  font-size: var(--text-2xs);
  line-height: 16px;
  text-align: center;
  animation: dot-pulse 2.4s var(--motion-ease) infinite;
}

@keyframes dot-pulse {
  0%,
  70%,
  100% {
    opacity: 1;
  }
  85% {
    opacity: 0.45;
  }
}

.nav__foot {
  display: grid;
  gap: var(--sp-3);
  padding: var(--sp-4);
  border-top: 1px solid var(--nav-rule);
}

.nav__user {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-width: 0;
}

.nav__avatar {
  flex: none;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--nav-fg-muted);
  border-radius: var(--radius-xs);
  background: var(--nav-hover-bg);
  font-family: var(--font-display);
  font-size: var(--text-base);
  color: var(--nav-fg);
}

.nav__user-text {
  display: grid;
  min-width: 0;
}

.nav__user-name {
  font-size: var(--text-base);
  color: var(--nav-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav__user-role {
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  color: var(--nav-fg-muted);
}

/* ── 内容区 ─────────────────────────────────────────────────────────── */

.main {
  min-width: 0;
  padding: var(--sp-7) var(--page-pad-x) var(--sp-9);
}

.main__inner {
  max-width: var(--content-max);
  margin: 0 auto;
}

/* ── ≤768px：侧栏折叠成顶部图标条，内容区可横向滚动表格 ─────────────── */

@media (max-width: 768px) {
  .shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .nav {
    position: sticky;
    top: 0;
    flex-direction: row;
    align-items: center;
    height: var(--nav-strip-height);
    padding: 0 var(--sp-2);
    overflow-x: auto;
    overflow-y: hidden;
  }

  .nav__brand {
    flex: none;
    padding: 0 var(--sp-3) 0 0;
    margin-right: var(--sp-2);
    border-bottom: 0;
    border-right: 1px solid var(--nav-rule);
  }

  .nav__brand-text,
  .nav__group-title,
  .nav__foot {
    display: none;
  }

  .nav__body {
    display: flex;
    align-items: center;
    gap: var(--sp-1);
    padding: 0;
  }

  .nav__group {
    display: flex;
    align-items: center;
    gap: var(--sp-1);
  }

  .nav__item {
    flex: none;
    flex-direction: column;
    gap: 2px;
    padding: 6px 8px;
    border-left: 0;
    border-bottom: 2px solid transparent;
    font-size: var(--text-2xs);
  }

  .nav__item--active {
    border-bottom-color: var(--amber);
  }

  .nav__label {
    flex: none;
    white-space: nowrap;
  }

  .main {
    padding: var(--sp-5) var(--page-pad-x) var(--sp-8);
  }
}
</style>
