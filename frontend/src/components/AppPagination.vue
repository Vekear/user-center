<script setup lang="ts">
import type { UsePagination } from '@/composables/usePagination'

withDefaults(
  defineProps<{
    /** usePagination() 的实例：分页状态与逻辑只有这一份 */
    pager: UsePagination
    pageSizes?: number[]
    disabled?: boolean
  }>(),
  { pageSizes: () => [10, 20, 50, 100], disabled: false },
)
</script>

<template>
  <div class="pager">
    <p class="pager__summary mono">
      共 {{ pager.total }} 条 · 第 {{ pager.page }} / {{ pager.totalPages }} 页
    </p>

    <div class="pager__controls">
      <button
        class="pager__step"
        type="button"
        :disabled="disabled || pager.page <= 1"
        @click="pager.prev()"
      >
        上一页
      </button>

      <template v-for="(item, index) in pager.pages" :key="`${item}-${index}`">
        <span v-if="item === 'gap'" class="pager__gap" aria-hidden="true">…</span>
        <button
          v-else
          class="pager__num"
          :class="{ 'pager__num--active': item === pager.page }"
          type="button"
          :disabled="disabled"
          :aria-current="item === pager.page ? 'page' : undefined"
          @click="pager.setPage(item)"
        >
          {{ item }}
        </button>
      </template>

      <button
        class="pager__step"
        type="button"
        :disabled="disabled || pager.page >= pager.totalPages"
        @click="pager.next()"
      >
        下一页
      </button>

      <label class="pager__size">
        <span class="pager__size-label">每页</span>
        <select
          class="pager__select mono"
          :value="pager.size"
          :disabled="disabled"
          @change="pager.setSize(Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="option in pageSizes" :key="option" :value="option">{{ option }}</option>
        </select>
      </label>
    </div>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-5);
}

.pager__summary {
  font-size: var(--text-sm);
  color: var(--ink-muted);
}

.pager__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-1);
}

.pager__step,
.pager__num {
  min-width: 30px;
  height: 28px;
  padding: 0 8px;
  border: var(--border-hair);
  border-radius: var(--radius-xs);
  background: var(--paper-raised);
  color: var(--ink);
  font-size: var(--text-sm);
  cursor: pointer;
}

.pager__step:hover:not(:disabled),
.pager__num:hover:not(:disabled) {
  border-color: var(--rule-strong);
  background: var(--paper-tint);
}

.pager__step:disabled,
.pager__num:disabled {
  color: var(--ink-faint);
  cursor: not-allowed;
}

.pager__num--active {
  border-color: var(--indigo);
  background: var(--indigo);
  color: var(--ink-inverse);
}
.pager__num--active:hover {
  background: var(--indigo);
  border-color: var(--indigo);
}

.pager__gap {
  padding: 0 2px;
  color: var(--ink-faint);
}

.pager__size {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  margin-left: var(--sp-2);
}

.pager__size-label {
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--ink-faint);
}

.pager__select {
  height: 28px;
  border: var(--border-hair);
  border-radius: var(--radius-xs);
  background: var(--paper-raised);
  font-size: var(--text-sm);
  padding: 0 4px;
}

@media (max-width: 768px) {
  .pager {
    padding: var(--sp-3) var(--sp-4);
  }
}
</style>
