<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppBadge from '@/components/AppBadge.vue'
import AppButton from '@/components/AppButton.vue'
import AppCard from '@/components/AppCard.vue'
import AppField from '@/components/AppField.vue'
import AppInput from '@/components/AppInput.vue'
import AppModal from '@/components/AppModal.vue'
import AppPagination from '@/components/AppPagination.vue'
import AppSelect from '@/components/AppSelect.vue'
import AppTable from '@/components/AppTable.vue'
import { useUiStore } from '@/stores/ui'
import { useToast } from '@/composables/useToast'
import { toApiError } from '@/api/http'
import * as adminApi from '@/api/admin'
import { usePagination } from '@/composables/usePagination'
import { formatDateTime, formatRelative } from '@/utils/format'
import type { AdminStats, AdminUser, RoleCode, UserStatus } from '@/api/types'

const ui = useUiStore()
const toast = useToast()

const rows = ref<AdminUser[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const stats = ref<AdminStats | null>(null)

const filters = reactive<{ keyword: string; status: string; role: string }>({
  keyword: '',
  status: '',
  role: '',
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: 'ACTIVE' },
  { label: '已禁用', value: 'DISABLED' },
]

const roleOptions = [
  { label: '全部角色', value: '' },
  { label: 'USER', value: 'USER' },
  { label: 'ADMIN', value: 'ADMIN' },
]

const pager = usePagination({ page: 1, size: 10, onChange: () => void load() })

const columns = [
  { key: 'id', title: 'ID', width: '72px', mono: true },
  { key: 'username', title: '用户名' },
  { key: 'nickname', title: '昵称', secondary: true },
  { key: 'status', title: '状态', width: '96px' },
  { key: 'roles', title: '角色', width: '120px', mono: true },
  { key: 'lastLoginAt', title: '最近登录', mono: true, secondary: true },
  { key: 'actions', title: '操作', align: 'right' as const, width: '220px' },
]

const statsTiles = [
  { key: 'userTotal', label: '用户总数' },
  { key: 'userActive', label: '正常' },
  { key: 'userDisabled', label: '已禁用' },
  { key: 'newUsers7d', label: '7 日新增' },
  { key: 'auditToday', label: '今日审计' },
  { key: 'notificationUnread', label: '未读通知' },
] as const

/** 丢弃过期响应：快速翻页时只认最后一次请求的结果 */
let loadToken = 0

async function load(): Promise<void> {
  const token = ++loadToken
  loading.value = true
  error.value = null
  try {
    const result = await adminApi.listUsers({
      keyword: filters.keyword.trim(),
      status: filters.status as UserStatus | '',
      role: filters.role,
      page: pager.page,
      size: pager.size,
    })
    if (token !== loadToken) return
    rows.value = result.list

    const requested = pager.page
    pager.setTotal(result.total)
    if (pager.page !== requested) {
      void load()
      return
    }
  } catch (caught) {
    if (token !== loadToken) return
    error.value = toApiError(caught).message
    rows.value = []
  } finally {
    if (token === loadToken) loading.value = false
  }
}

async function loadStats(): Promise<void> {
  try {
    stats.value = await adminApi.fetchStats()
  } catch {
    stats.value = null
  }
}

function search(): void {
  // 回第一页：页码真的变化时由 onChange 触发 load，否则直接查，避免发两次请求
  if (pager.page === 1) {
    void load()
    return
  }
  pager.setPage(1)
}

function resetFilters(): void {
  filters.keyword = ''
  filters.status = ''
  filters.role = ''
  search()
}

function rowClass(row: AdminUser): string {
  return row.status === 'DISABLED' ? 'row--disabled' : ''
}

/** 禁用账号的印章右上角加斜纹角标 */
function statusHatch(row: AdminUser): boolean {
  return row.status === 'DISABLED'
}

function toggleStatus(user: AdminUser): void {
  const next: UserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  ui.askConfirm({
    title: next === 'DISABLED' ? '禁用账号' : '启用账号',
    message:
      next === 'DISABLED'
        ? `禁用后 ${user.username} 将无法登录，已登录的会话会在下次请求时被拒绝。`
        : `恢复 ${user.username} 的登录权限。`,
    confirmText: next === 'DISABLED' ? '禁用' : '启用',
    danger: next === 'DISABLED',
    onConfirm: async () => {
      await adminApi.updateUserStatus(user.id, next)
      toast.success(next === 'DISABLED' ? '账号已禁用' : '账号已启用')
      await Promise.all([load(), loadStats()])
    },
  })
}

/* ── 角色编辑 ─────────────────────────────────────────────────────── */

const roleModalOpen = ref(false)
const roleTarget = ref<AdminUser | null>(null)
const roleDraft = ref<RoleCode[]>([])
const roleSaving = ref(false)

function openRoles(user: AdminUser): void {
  roleTarget.value = user
  roleDraft.value = user.roles.filter((role): role is RoleCode => role === 'USER' || role === 'ADMIN')
  roleModalOpen.value = true
}

function toggleRole(role: RoleCode): void {
  roleDraft.value = roleDraft.value.includes(role)
    ? roleDraft.value.filter((item) => item !== role)
    : [...roleDraft.value, role]
}

async function saveRoles(): Promise<void> {
  const target = roleTarget.value
  if (!target) return
  if (roleDraft.value.length === 0) {
    toast.error('至少要保留一个角色')
    return
  }
  roleSaving.value = true
  try {
    await adminApi.updateUserRoles(target.id, roleDraft.value)
    toast.success('角色已更新')
    roleModalOpen.value = false
    await load()
  } catch (caught) {
    toast.error('角色更新失败', toApiError(caught).message)
  } finally {
    roleSaving.value = false
  }
}

/* ── 重置密码 ─────────────────────────────────────────────────────── */

const tempPassword = ref<string | null>(null)
const tempPasswordUser = ref<string>('')
const resetting = ref(false)

function resetPassword(user: AdminUser): void {
  ui.askConfirm({
    title: '重置密码',
    message: `将为 ${user.username} 生成一次性临时密码，原密码立即失效。`,
    confirmText: '生成临时密码',
    danger: true,
    onConfirm: async () => {
      resetting.value = true
      try {
        const result = await adminApi.resetPassword(user.id)
        tempPassword.value = result.tempPassword
        tempPasswordUser.value = user.username
      } finally {
        resetting.value = false
      }
    },
  })
}

async function copyTempPassword(): Promise<void> {
  if (!tempPassword.value) return
  try {
    await navigator.clipboard.writeText(tempPassword.value)
    toast.success('临时密码已复制')
  } catch {
    toast.error('复制失败', '请手动选中密码文本复制')
  }
}

onMounted(() => {
  void load()
  void loadStats()
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head__title">
        <p class="page-head__file mono">registry · users</p>
        <h1>用户管理</h1>
        <p class="lede">全局用户底册：停用、角色、临时密码都记在这里。</p>
      </div>
      <div class="page-head__meta mono">admin only</div>
    </header>

    <!-- 统计条：一格一项，像档案柜上的计数牌 -->
    <section class="docket" aria-label="用户统计">
      <div v-for="tile in statsTiles" :key="tile.key" class="docket__cell">
        <p class="docket__label label-strip">{{ tile.label }}</p>
        <p class="docket__value mono">{{ stats ? stats[tile.key] : '—' }}</p>
      </div>
    </section>

    <AppCard tab="检索" title="筛选条件" accent="none">
      <form class="filters" @submit.prevent="search">
        <AppField label="关键词" field-id="ad-kw">
          <AppInput
            id="ad-kw"
            v-model="filters.keyword"
            type="search"
            placeholder="用户名 / 邮箱 / 昵称"
            @enter="search"
          />
        </AppField>

        <AppField label="状态" field-id="ad-status">
          <AppSelect id="ad-status" v-model="filters.status" :options="statusOptions" placeholder="" />
        </AppField>

        <AppField label="角色" field-id="ad-role">
          <AppSelect id="ad-role" v-model="filters.role" :options="roleOptions" placeholder="" />
        </AppField>

        <div class="filters__actions">
          <AppButton type="submit" variant="primary" :loading="loading">查询</AppButton>
          <AppButton variant="secondary" :disabled="loading" @click="resetFilters">重置</AppButton>
        </div>
      </form>
    </AppCard>

    <AppCard tab="底册" title="用户列表" accent="primary" flush>
      <template #actions>
        <AppButton size="sm" variant="secondary" :disabled="loading" @click="load">刷新</AppButton>
      </template>

      <AppTable
        :columns="columns"
        :rows="rows"
        row-key="id"
        :loading="loading"
        :error="error"
        empty-title="没有匹配的用户"
        empty-description="试试放宽筛选条件，或清空关键词。"
        min-width="900px"
        :row-class="rowClass"
        @retry="load"
      >
        <template #cell-nickname="{ row }">{{ row.nickname || '—' }}</template>

        <template #cell-status="{ row }">
          <span class="status-cell" :class="{ hatch: statusHatch(row) }">
            <AppBadge :status="row.status" />
          </span>
        </template>

        <template #cell-roles="{ row }">{{ row.roles.join(' / ') || '—' }}</template>

        <template #cell-lastLoginAt="{ row }">
          {{ formatRelative(row.lastLoginAt) }}
          <span class="faint"> · {{ formatDateTime(row.lastLoginAt) }}</span>
        </template>

        <template #cell-actions="{ row }">
          <div class="rowactions">
            <AppButton
              size="sm"
              :variant="row.status === 'ACTIVE' ? 'danger' : 'primary'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </AppButton>
            <AppButton size="sm" variant="secondary" @click="openRoles(row)">角色</AppButton>
            <AppButton size="sm" variant="ghost" :disabled="resetting" @click="resetPassword(row)">
              重置密码
            </AppButton>
          </div>
        </template>
      </AppTable>

      <div class="pager-wrap">
        <AppPagination :pager="pager" :disabled="loading" />
      </div>
    </AppCard>

    <!-- 角色编辑 -->
    <AppModal
      v-model:open="roleModalOpen"
      size="sm"
      :title="`编辑角色 · ${roleTarget?.username ?? ''}`"
      labelled-by="role-modal-title"
    >
      <div class="roles">
        <p class="roles__hint">至少保留一个角色。ADMIN 可以进入管理端。</p>
        <label class="roles__item">
          <input
            type="checkbox"
            :checked="roleDraft.includes('USER')"
            @change="toggleRole('USER')"
          />
          <span class="roles__name mono">USER</span>
          <span class="roles__desc">普通用户：使用个人档案的全部功能</span>
        </label>
        <label class="roles__item">
          <input
            type="checkbox"
            :checked="roleDraft.includes('ADMIN')"
            @change="toggleRole('ADMIN')"
          />
          <span class="roles__name mono">ADMIN</span>
          <span class="roles__desc">管理员：用户管理、审计日志、发送通知</span>
        </label>
      </div>

      <template #footer>
        <AppButton variant="secondary" :disabled="roleSaving" @click="roleModalOpen = false">取消</AppButton>
        <AppButton variant="primary" :loading="roleSaving" @click="saveRoles">保存角色</AppButton>
      </template>
    </AppModal>

    <!-- 临时密码 -->
    <AppModal
      :open="tempPassword !== null"
      size="sm"
      title="临时密码"
      @close="tempPassword = null"
    >
      <div class="temp">
        <p class="temp__hint">
          请把临时密码交给 <span class="mono">{{ tempPasswordUser }}</span>，并提醒对方登录后立即修改。
        </p>
        <p class="temp__value mono">{{ tempPassword }}</p>
        <AppButton size="sm" variant="secondary" @click="copyTempPassword">复制密码</AppButton>
      </div>

      <template #footer>
        <AppButton variant="primary" @click="tempPassword = null">我已记下</AppButton>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.page-head__meta {
  font-size: var(--text-2xs);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.docket {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border: var(--border-hair);
  border-radius: var(--radius-sm);
  background: var(--paper-raised);
  border-top: var(--border-accent) solid var(--amber);
}

.docket__cell {
  display: grid;
  gap: var(--sp-2);
  padding: var(--sp-4) var(--sp-4);
  border-left: var(--border-hair);
}

.docket__cell:first-child {
  border-left: 0;
}

.docket__value {
  font-size: var(--text-xl);
  color: var(--ink);
}

.filters {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr auto;
  gap: var(--sp-4);
  align-items: end;
}

.filters__actions {
  display: flex;
  gap: var(--sp-2);
}

.rowactions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--sp-2);
}

.status-cell {
  --hatch-size: 14px;
  padding-right: 10px;
}

.pager-wrap {
  border-top: var(--border-hair);
  background: var(--paper-tint);
}

.roles {
  display: grid;
  gap: var(--sp-3);
}

.roles__hint {
  font-size: var(--text-sm);
  color: var(--ink-muted);
}

.roles__item {
  display: grid;
  grid-template-columns: auto 68px 1fr;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3);
  border: var(--border-hair);
  border-radius: var(--radius-xs);
  cursor: pointer;
}

.roles__item:hover {
  border-color: var(--rule-strong);
  background: var(--paper-tint);
}

.roles__item input {
  accent-color: var(--indigo);
}

.roles__desc {
  font-size: var(--text-sm);
  color: var(--ink-muted);
}

.temp {
  display: grid;
  gap: var(--sp-3);
  justify-items: start;
}

.temp__hint {
  color: var(--ink-muted);
}

.temp__value {
  width: 100%;
  padding: var(--sp-3);
  border: var(--border-hair);
  border-left: var(--border-accent) solid var(--indigo);
  border-radius: var(--radius-xs);
  background: var(--indigo-tint);
  font-size: var(--text-md);
  letter-spacing: 0.06em;
  overflow-wrap: anywhere;
}

@media (max-width: 1024px) {
  .docket {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .docket__cell:nth-child(4) {
    border-left: 0;
  }
  .docket__cell:nth-child(n + 4) {
    border-top: var(--border-hair);
  }
  .filters {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .docket {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .docket__cell:nth-child(odd) {
    border-left: 0;
  }
  .docket__cell:nth-child(n + 3) {
    border-top: var(--border-hair);
  }
}
</style>
