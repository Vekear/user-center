<script setup lang="ts" generic="T extends object">
import AppEmpty from '@/components/AppEmpty.vue'
import AppButton from '@/components/AppButton.vue'

interface TableColumn {
  key: string
  title: string
  width?: string
  align?: 'left' | 'right' | 'center'
  mono?: boolean
  /** 次要列：≤768px 隐藏，窄屏只留关键信息 */
  secondary?: boolean
}

withDefaults(
  defineProps<{
    columns: TableColumn[]
    rows: T[]
    rowKey: string
    loading?: boolean
    error?: string | null
    emptyTitle?: string
    emptyDescription?: string
    minWidth?: string
    /** 给行加类名，例如把 DISABLED 行灰化 */
    rowClass?: (row: T) => string
  }>(),
  {
    loading: false,
    error: null,
    emptyTitle: '暂无记录',
    emptyDescription: '',
    minWidth: '620px',
    rowClass: undefined,
  },
)

const emit = defineEmits<{ retry: [] }>()

function valueOf(row: T, key: string): unknown {
  return (row as Record<string, unknown>)[key]
}

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (Array.isArray(value)) return value.length > 0 ? value.join(' / ') : '—'
  return String(value)
}

function keyOf(row: T, rowKey: string, index: number): string | number {
  const value = valueOf(row, rowKey)
  if (typeof value === 'string' || typeof value === 'number') return value
  return index
}
</script>

<template>
  <div class="table-wrap">
    <table class="table" :style="{ minWidth }">
      <thead>
        <tr>
          <th
            v-for="column in columns"
            :key="column.key"
            scope="col"
            :class="{ 'table__th--secondary': column.secondary }"
            :style="{ width: column.width, textAlign: column.align ?? 'left' }"
          >
            {{ column.title }}
          </th>
        </tr>
      </thead>

      <tbody>
        <tr v-if="loading">
          <td :colspan="columns.length" class="table__state">
            <span class="table__status mono" role="status">正在载入数据</span>
          </td>
        </tr>

        <tr v-else-if="error">
          <td :colspan="columns.length" class="table__state">
            <div class="table__error" role="alert">
              <span>{{ error }}</span>
              <AppButton size="sm" variant="secondary" @click="emit('retry')">重新载入</AppButton>
            </div>
          </td>
        </tr>

        <tr v-else-if="rows.length === 0">
          <td :colspan="columns.length" class="table__state table__state--empty">
            <AppEmpty :title="emptyTitle" :description="emptyDescription" code="no records" />
          </td>
        </tr>

        <template v-else>
          <tr
            v-for="(row, index) in rows"
            :key="keyOf(row, rowKey, index)"
            :class="rowClass?.(row)"
          >
            <td
              v-for="column in columns"
              :key="column.key"
              :class="{ 'table__td--secondary': column.secondary }"
              :style="{ textAlign: column.align ?? 'left' }"
            >
              <slot :name="`cell-${column.key}`" :row="row" :value="valueOf(row, column.key)" :index="index">
                <span :class="{ mono: column.mono }">{{ display(valueOf(row, column.key)) }}</span>
              </slot>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-wrap {
  width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-base);
}

.table th {
  padding: var(--sp-3) var(--sp-4);
  border-bottom: var(--border-strong);
  background: var(--paper-sunken);
  font-size: var(--text-2xs);
  font-weight: var(--fw-semibold);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--ink-muted);
  white-space: nowrap;
}

.table td {
  padding: var(--sp-3) var(--sp-4);
  border-bottom: var(--border-hair);
  vertical-align: middle;
  color: var(--ink);
}

.table tbody tr:hover td {
  background: var(--paper-tint);
}

.table__state {
  padding: var(--sp-6) var(--sp-4);
  text-align: center;
}

.table__state--empty {
  padding: var(--sp-4);
}

.table__status {
  font-size: var(--text-sm);
  color: var(--ink-muted);
}

.table__error {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-3);
  color: var(--danger);
}

@media (max-width: 768px) {
  .table__th--secondary,
  .table__td--secondary {
    display: none;
  }
}
</style>
