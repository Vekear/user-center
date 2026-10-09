<script setup lang="ts">
/**
 * 载入指示：刻意做成静态的等宽状态条 —— 全站动效只保留两处
 * （头像裁剪面板、通知红点脉冲），所以这里不转圈。
 */
withDefaults(defineProps<{ label?: string; inline?: boolean }>(), {
  label: '正在载入',
  inline: false,
})
</script>

<template>
  <span class="loading mono" :class="{ 'loading--inline': inline }" role="status" aria-live="polite">
    {{ label }}<span class="loading__ticks" aria-hidden="true">···</span>
  </span>
</template>

<style scoped>
.loading {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  font-size: var(--text-sm);
  color: var(--ink-muted);
}

.loading--inline {
  font-size: var(--text-2xs);
}

.loading__ticks {
  color: var(--ink-faint);
  letter-spacing: 0.05em;
}
</style>
