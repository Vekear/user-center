<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppBadge from '@/components/AppBadge.vue'
import AppButton from '@/components/AppButton.vue'
import AppCard from '@/components/AppCard.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppPagination from '@/components/AppPagination.vue'
import AppSpinner from '@/components/AppSpinner.vue'
import { useNotificationsStore } from '@/stores/notifications'
import { useToast } from '@/composables/useToast'
import { toApiError } from '@/api/http'
import { usePagination } from '@/composables/usePagination'
import { formatDateTime, formatRelative } from '@/utils/format'

const notifications = useNotificationsStore()
const toast = useToast()

/** 分页状态只有一个来源：usePagination */
const pager = usePagination({
  page: 1,
  size: 10,
  onChange: () => void load(),
})

const scope = ref<'all' | 'unread'>('all')

const unreadLabel = computed(() =>
  notifications.unreadCount > 99 ? '99+' : String(notifications.unreadCount),
)

async function load(): Promise<void> {
  notifications.page = pager.page
  notifications.size = pager.size
  notifications.unreadOnly = scope.value === 'unread'
  await notifications.fetchList()

  // 总数变小导致页码被夹回时，按夹回后的页码再取一次，避免"页显示 1、数据是空的"
  const requested = pager.page
  pager.setTotal(notifications.total)
  if (pager.page !== requested) {
    await load()
    return
  }

  await notifications.fetchUnreadCount()
}

function switchScope(next: 'all' | 'unread'): void {
  if (scope.value === next) return
  scope.value = next
  // setPage 变化时由 usePagination 的 onChange 触发 load，避免重复请求
  if (pager.page === 1) void load()
  else pager.setPage(1)
}

async function markRead(id: number): Promise<void> {
  try {
    await notifications.markRead(id)
    toast.success('已标记为已读')
    if (scope.value === 'unread') await load()
  } catch (caught) {
    toast.error('操作失败', toApiError(caught).message)
  }
}

async function markAllRead(): Promise<void> {
  try {
    await notifications.markAllRead()
    toast.success('全部标记为已读')
    await load()
  } catch (caught) {
    toast.error('操作失败', toApiError(caught).message)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head__title">
        <p class="page-head__file mono">record 03 · notices</p>
        <h1>通知</h1>
        <p class="lede">系统消息按时间倒序留档，读过的会盖上已读章。</p>
      </div>

      <div class="page-head__meta">
        <AppBadge
          :tone="notifications.unreadCount > 0 ? 'pending' : 'neutral'"
          :label="`未读 ${unreadLabel}`"
        />
      </div>
    </header>

    <AppCard tab="索引" accent="none" flush>
      <template #actions>
        <div class="scope" role="group" aria-label="筛选范围">
          <button
            class="scope__btn"
            type="button"
            :class="{ 'scope__btn--active': scope === 'all' }"
            :aria-pressed="scope === 'all'"
            @click="switchScope('all')"
          >
            全部
          </button>
          <button
            class="scope__btn"
            type="button"
            :class="{ 'scope__btn--active': scope === 'unread' }"
            :aria-pressed="scope === 'unread'"
            @click="switchScope('unread')"
          >
            仅未读
          </button>
        </div>
        <AppButton
          size="sm"
          variant="secondary"
          :disabled="notifications.loading || notifications.unreadCount === 0"
          @click="markAllRead"
        >
          全部已读
        </AppButton>
      </template>

      <div v-if="notifications.loading" class="state">
        <AppSpinner label="正在调阅通知" />
      </div>

      <div v-else-if="notifications.error" class="state state--error" role="alert">
        <p>{{ notifications.error }}</p>
        <AppButton size="sm" variant="secondary" @click="load">重新载入</AppButton>
      </div>

      <AppEmpty
        v-else-if="notifications.items.length === 0"
        :title="scope === 'unread' ? '没有未读通知' : '还没有通知'"
        :description="
          scope === 'unread' ? '所有消息都已读过。' : '系统消息、管理员公告都会出现在这里。'
        "
        code="no notices"
      >
        <template #action>
          <AppButton v-if="scope === 'unread'" size="sm" variant="secondary" @click="switchScope('all')">
            看全部通知
          </AppButton>
        </template>
      </AppEmpty>

      <ul v-else class="list">
        <li v-for="item in notifications.items" :key="item.id" class="notice" :class="{ 'notice--unread': !item.read }">
          <div class="notice__side">
            <AppBadge :tone="item.read ? 'neutral' : 'pending'" :label="item.read ? '已读' : '未读'" />
          </div>

          <div class="notice__body">
            <h2 class="notice__title">{{ item.title }}</h2>
            <p class="notice__content">{{ item.content }}</p>
            <p class="notice__meta mono">
              {{ formatDateTime(item.createdAt) }}
              <span class="faint"> · {{ formatRelative(item.createdAt) }}</span>
            </p>
          </div>

          <div class="notice__actions">
            <AppButton v-if="!item.read" size="sm" variant="ghost" @click="markRead(item.id)">
              标记已读
            </AppButton>
            <span v-else class="faint mono">—</span>
          </div>
        </li>
      </ul>

      <div class="pager-wrap">
        <AppPagination :pager="pager" :disabled="notifications.loading" />
      </div>
    </AppCard>
  </div>
</template>

<style scoped>
.page-head__meta {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.scope {
  display: inline-flex;
  border: var(--border-hair);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.scope__btn {
  padding: 5px 12px;
  border: 0;
  background: var(--paper-raised);
  color: var(--ink-muted);
  font-size: var(--text-sm);
  cursor: pointer;
}

.scope__btn + .scope__btn {
  border-left: var(--border-hair);
}

.scope__btn--active {
  background: var(--indigo-tint);
  color: var(--indigo-active);
  font-weight: var(--fw-medium);
}

.state {
  display: grid;
  justify-items: center;
  gap: var(--sp-3);
  padding: var(--sp-8) var(--sp-5);
}

.state--error {
  color: var(--danger);
}

.list {
  display: grid;
}

.pager-wrap {
  border-top: var(--border-hair);
  background: var(--paper-tint);
}

.notice {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: var(--sp-4);
  align-items: start;
  padding: var(--sp-4) var(--sp-5);
  border-top: var(--border-hair);
}

.notice:first-child {
  border-top: 0;
}

.notice--unread {
  border-left: var(--border-accent) solid var(--indigo);
  background: var(--paper-tint);
}

.notice__body {
  display: grid;
  gap: var(--sp-2);
  min-width: 0;
}

.notice__title {
  font-size: var(--text-md);
}

.notice__content {
  color: var(--ink-muted);
  overflow-wrap: anywhere;
  max-width: 78ch;
}

.notice__meta {
  font-size: var(--text-2xs);
  color: var(--ink-faint);
}

.notice__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 88px;
}

@media (max-width: 768px) {
  .notice {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--sp-2);
    padding: var(--sp-4);
  }

  .notice__actions {
    justify-content: flex-start;
  }
}
</style>
