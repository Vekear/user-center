<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppBadge from '@/components/AppBadge.vue'
import AppButton from '@/components/AppButton.vue'
import AppCard from '@/components/AppCard.vue'
import AppField from '@/components/AppField.vue'
import AppInput from '@/components/AppInput.vue'
import AppPagination from '@/components/AppPagination.vue'
import AppSelect from '@/components/AppSelect.vue'
import AppTable from '@/components/AppTable.vue'
import { useToast } from '@/composables/useToast'
import { toApiError } from '@/api/http'
import * as adminApi from '@/api/admin'
import { usePagination } from '@/composables/usePagination'
import { formatDateTime } from '@/utils/format'
import type { AuditLog } from '@/api/types'

const toast = useToast()

const rows = ref<AuditLog[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const filters = reactive<{ userId: string; action: string; success: string; from: string; to: string }>({
  userId: '',
  action: '',
  success: '',
  from: '',
  to: '',
})

const successOptions = [
  { label: '全部结果', value: '' },
  { label: '成功', value: 'true' },
  { label: '失败', value: 'false' },
]

const pager = usePagination({ page: 1, size: 20, onChange: () => void load() })

const columns = [
  { key: 'id', title: 'ID', width: '76px', mono: true },
  { key: 'username', title: '操作者' },
  { key: 'action', title: '动作', mono: true },
  { key: 'target', title: '对象', mono: true, secondary: true },
  { key: 'ip', title: 'IP', mono: true, secondary: true },
  { key: 'success', title: '结果', width: '84px' },
  { key: 'createdAt', title: '时间', mono: true },
  { key: 'detail', title: '详情', secondary: true },
]

/** 丢弃过期响应：快速翻页时只认最后一次请求的结果 */
let loadToken = 0

async function load(): Promise<void> {
  const token = ++loadToken
  loading.value = true
  error.value = null
  try {
    const result = await adminApi.listAuditLogs({
      userId: filters.userId.trim() ? Number(filters.userId.trim()) : null,
      action: filters.action.trim(),
      success: filters.success === '' ? null : filters.success === 'true',
      from: filters.from || undefined,
      to: filters.to || undefined,
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

function search(): void {
  if (filters.userId.trim() && Number.isNaN(Number(filters.userId.trim()))) {
    toast.error('用户 ID 需要是数字')
    return
  }
  if (pager.page === 1) {
    void load()
    return
  }
  pager.setPage(1)
}

function resetFilters(): void {
  filters.userId = ''
  filters.action = ''
  filters.success = ''
  filters.from = ''
  filters.to = ''
  search()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head__title">
        <p class="page-head__file mono">registry · audit</p>
        <h1>审计日志</h1>
        <p class="lede">谁在什么时候动了什么，逐条留痕，只读不改。</p>
      </div>
      <div class="page-head__meta mono">read only</div>
    </header>

    <AppCard tab="检索" title="筛选条件" accent="none">
      <form class="filters" @submit.prevent="search">
        <AppField label="用户 ID" field-id="au-user" note="留空为全部">
          <AppInput id="au-user" v-model="filters.userId" mono placeholder="例如 12" @enter="search" />
        </AppField>

        <AppField label="动作" field-id="au-action" note="留空为全部">
          <AppInput id="au-action" v-model="filters.action" mono placeholder="例如 LOGIN" @enter="search" />
        </AppField>

        <AppField label="结果" field-id="au-success">
          <AppSelect id="au-success" v-model="filters.success" :options="successOptions" placeholder="" />
        </AppField>

        <AppField label="起始日期" field-id="au-from">
          <AppInput id="au-from" v-model="filters.from" type="date" mono />
        </AppField>

        <AppField label="结束日期" field-id="au-to">
          <AppInput id="au-to" v-model="filters.to" type="date" mono />
        </AppField>

        <div class="filters__actions">
          <AppButton type="submit" variant="primary" :loading="loading">查询</AppButton>
          <AppButton variant="secondary" :disabled="loading" @click="resetFilters">重置</AppButton>
        </div>
      </form>
    </AppCard>

    <AppCard tab="留痕" title="日志明细" accent="primary" flush>
      <template #actions>
        <AppButton size="sm" variant="secondary" :disabled="loading" @click="load">刷新</AppButton>
      </template>

      <AppTable
        :columns="columns"
        :rows="rows"
        row-key="id"
        :loading="loading"
        :error="error"
        empty-title="没有匹配的日志"
        empty-description="换个时间范围或动作关键词再试。"
        min-width="980px"
        @retry="load"
      >
        <template #cell-target="{ row }">
          {{ row.targetType || '—' }}<span v-if="row.targetId">#{{ row.targetId }}</span>
        </template>

        <template #cell-success="{ row }">
          <AppBadge :tone="row.success ? 'success' : 'danger'" :label="row.success ? '成功' : '失败'" />
        </template>

        <template #cell-createdAt="{ row }">{{ formatDateTime(row.createdAt) }}</template>

        <template #cell-detail="{ row }">
          <span class="detail">{{ row.detail || '—' }}</span>
        </template>
      </AppTable>

      <div class="pager-wrap">
        <AppPagination :pager="pager" :page-sizes="[20, 50, 100]" :disabled="loading" />
      </div>
    </AppCard>
  </div>
</template>

<style scoped>
.page-head__meta {
  font-size: var(--text-2xs);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.filters {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr)) auto;
  gap: var(--sp-4);
  align-items: end;
}

.filters__actions {
  display: flex;
  gap: var(--sp-2);
}

.pager-wrap {
  border-top: var(--border-hair);
  background: var(--paper-tint);
}

.detail {
  display: inline-block;
  max-width: 40ch;
  color: var(--ink-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

@media (max-width: 1024px) {
  .filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .filters {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
