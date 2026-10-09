<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppBadge from '@/components/AppBadge.vue'
import AppButton from '@/components/AppButton.vue'
import AppCard from '@/components/AppCard.vue'
import AppField from '@/components/AppField.vue'
import AppInput from '@/components/AppInput.vue'
import AppTable from '@/components/AppTable.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { useToast } from '@/composables/useToast'
import { toApiError } from '@/api/http'
import * as profileApi from '@/api/profile'
import * as securityApi from '@/api/security'
import { formatDateTime, formatRelative } from '@/utils/format'
import { validateConfirmPassword, validatePassword, validateRequired } from '@/utils/validation'
import type { Session } from '@/api/types'

const auth = useAuthStore()
const ui = useUiStore()
const toast = useToast()
const router = useRouter()

/* ── 修改密码 ─────────────────────────────────────────────────────── */

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordErrors = reactive<Record<keyof typeof passwordForm, string | null>>({
  oldPassword: null,
  newPassword: null,
  confirmPassword: null,
})
const changingPassword = ref(false)

function validatePasswordForm(): boolean {
  passwordErrors.oldPassword = validateRequired(passwordForm.oldPassword, '当前密码')
  passwordErrors.newPassword = validatePassword(passwordForm.newPassword)
  passwordErrors.confirmPassword = validateConfirmPassword(
    passwordForm.newPassword,
    passwordForm.confirmPassword,
  )
  if (
    !passwordErrors.newPassword &&
    passwordForm.newPassword === passwordForm.oldPassword
  ) {
    passwordErrors.newPassword = '新密码不能与当前密码相同'
  }
  return Object.values(passwordErrors).every((message) => message === null)
}

async function changePassword(): Promise<void> {
  if (!validatePasswordForm()) return
  changingPassword.value = true
  try {
    await profileApi.changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    toast.success('密码已更新', '其他设备上的会话已失效，当前会话保留')
  } catch (caught) {
    const error = toApiError(caught)
    if (error.fields?.oldPassword) passwordErrors.oldPassword = error.fields.oldPassword
    if (error.fields?.newPassword) passwordErrors.newPassword = error.fields.newPassword
    toast.error('密码修改失败', error.message)
  } finally {
    changingPassword.value = false
  }
}

/* ── 登录设备 ─────────────────────────────────────────────────────── */

const sessions = ref<Session[]>([])
const sessionsLoading = ref(false)
const sessionsError = ref<string | null>(null)

const sessionColumns = [
  { key: 'device', title: '设备' },
  { key: 'ip', title: 'IP', mono: true, secondary: true },
  { key: 'lastSeenAt', title: '最近活动', mono: true },
  { key: 'expiresAt', title: '到期', mono: true, secondary: true },
  { key: 'actions', title: '操作', align: 'right' as const },
]

async function loadSessions(): Promise<void> {
  sessionsLoading.value = true
  sessionsError.value = null
  try {
    sessions.value = await securityApi.listSessions()
  } catch (caught) {
    sessionsError.value = toApiError(caught).message
  } finally {
    sessionsLoading.value = false
  }
}

function revokeSession(session: Session): void {
  ui.askConfirm({
    title: '撤销该设备',
    message: `${session.deviceName || '该设备'} 将立即退出登录，需要重新输入密码。`,
    confirmText: '撤销会话',
    danger: true,
    onConfirm: async () => {
      await securityApi.revokeSession(session.id)
      toast.success('会话已撤销')
      await loadSessions()
    },
  })
}

function revokeOthers(): void {
  ui.askConfirm({
    title: '撤销其他设备',
    message: '除当前设备外的所有会话都会失效。',
    confirmText: '全部撤销',
    danger: true,
    onConfirm: async () => {
      await securityApi.revokeOtherSessions()
      toast.success('其他设备已全部退出')
      await loadSessions()
    },
  })
}

/* ── 注销账号 ─────────────────────────────────────────────────────── */

function deleteAccount(): void {
  ui.askConfirm({
    title: '注销账号',
    message: '账号将被注销（逻辑删除），所有设备退出登录，且无法自行恢复。',
    confirmText: '确认注销',
    danger: true,
    requirePassword: true,
    onConfirm: async (password) => {
      await securityApi.deleteAccount(password ?? '')
      auth.clearSession()
      toast.info('账号已注销')
      await router.push('/login')
    },
  })
}

onMounted(loadSessions)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head__title">
        <p class="page-head__file mono">record 02 · access</p>
        <h1>登录与安全</h1>
        <p class="lede">改密码、看设备、必要时把这本册子收回去。</p>
      </div>
    </header>

    <div class="grid-12">
      <div class="span-6">
        <AppCard tab="表三" title="修改密码" accent="primary">
          <form class="form" novalidate @submit.prevent="changePassword">
            <AppField label="当前密码" field-id="sec-old" :error="passwordErrors.oldPassword" required>
              <AppInput
                id="sec-old"
                v-model="passwordForm.oldPassword"
                type="password"
                autocomplete="current-password"
                :invalid="Boolean(passwordErrors.oldPassword)"
              />
            </AppField>

            <AppField
              label="新密码"
              field-id="sec-new"
              :error="passwordErrors.newPassword"
              hint="8–64 位，含字母和数字"
              required
            >
              <AppInput
                id="sec-new"
                v-model="passwordForm.newPassword"
                type="password"
                autocomplete="new-password"
                :invalid="Boolean(passwordErrors.newPassword)"
              />
            </AppField>

            <AppField label="确认新密码" field-id="sec-confirm" :error="passwordErrors.confirmPassword" required>
              <AppInput
                id="sec-confirm"
                v-model="passwordForm.confirmPassword"
                type="password"
                autocomplete="new-password"
                :invalid="Boolean(passwordErrors.confirmPassword)"
                @enter="changePassword"
              />
            </AppField>

            <p class="form__note">
              修改成功后，其他设备上的会话会立即失效；当前设备保持登录。
            </p>

            <div class="form__actions">
              <AppButton type="submit" variant="primary" :loading="changingPassword">更新密码</AppButton>
            </div>
          </form>
        </AppCard>
      </div>

      <div class="span-6">
        <AppCard tab="注销" title="注销账号" accent="amber">
          <div class="danger-zone">
            <p class="danger-zone__text">
              注销后账号进入停用状态，无法再登录，档案仍留在册。这是不可撤销的操作，需要输入密码确认。
            </p>
            <AppButton variant="danger" @click="deleteAccount">注销我的账号</AppButton>
          </div>
        </AppCard>
      </div>
    </div>

    <AppCard tab="表四" title="登录设备" accent="none" flush>
      <template #actions>
        <AppBadge tone="neutral" :label="`${sessions.length} 台`" />
        <AppButton size="sm" variant="secondary" :disabled="sessionsLoading" @click="loadSessions">
          刷新
        </AppButton>
        <AppButton
          size="sm"
          variant="danger"
          :disabled="sessionsLoading || sessions.length <= 1"
          @click="revokeOthers"
        >
          撤销其他设备
        </AppButton>
      </template>

      <AppTable
        :columns="sessionColumns"
        :rows="sessions"
        row-key="id"
        :loading="sessionsLoading"
        :error="sessionsError"
        empty-title="没有其他登录记录"
        empty-description="当前只有这一台设备在使用你的档案。"
        min-width="640px"
        @retry="loadSessions"
      >
        <template #cell-device="{ row }">
          <div class="device">
            <span class="device__name">{{ row.deviceName || '未知设备' }}</span>
            <span class="device__ua mono">{{ row.userAgent || '—' }}</span>
          </div>
          <AppBadge v-if="row.current" status="ACTIVE" label="当前设备" />
        </template>

        <template #cell-lastSeenAt="{ row }">
          {{ formatRelative(row.lastSeenAt) }}
          <span class="faint"> · {{ formatDateTime(row.lastSeenAt) }}</span>
        </template>

        <template #cell-expiresAt="{ row }">{{ formatDateTime(row.expiresAt) }}</template>

        <template #cell-actions="{ row }">
          <AppButton
            v-if="!row.current"
            size="sm"
            variant="ghost"
            :disabled="sessionsLoading"
            @click="revokeSession(row)"
          >
            撤销
          </AppButton>
          <span v-else class="faint mono">本机</span>
        </template>
      </AppTable>
    </AppCard>
  </div>
</template>

<style scoped>
.form {
  display: grid;
  gap: var(--sp-4);
}

.form__note {
  padding: var(--sp-3);
  border: var(--border-hair);
  border-left: var(--border-accent) solid var(--amber);
  border-radius: var(--radius-xs);
  background: var(--amber-tint);
  color: var(--amber-ink);
  font-size: var(--text-sm);
}

.form__actions {
  display: flex;
  gap: var(--sp-3);
}

.danger-zone {
  display: grid;
  gap: var(--sp-4);
  justify-items: start;
}

.danger-zone__text {
  color: var(--ink-muted);
  max-width: 46ch;
}

.device {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.device__name {
  color: var(--ink);
}

.device__ua {
  font-size: var(--text-2xs);
  color: var(--ink-faint);
  max-width: 42ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
