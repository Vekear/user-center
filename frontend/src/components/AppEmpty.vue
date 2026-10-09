<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    /** 等宽编号，例如「暂无记录 · 000」 */
    code?: string
  }>(),
  { description: '', code: '' },
)
</script>

<template>
  <div class="empty">
    <div class="empty__mark" aria-hidden="true" />
    <h3 class="empty__title">{{ title }}</h3>
    <p v-if="description" class="empty__desc">{{ description }}</p>
    <p v-if="code" class="empty__code mono">{{ code }}</p>
    <div class="empty__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: grid;
  justify-items: center;
  gap: var(--sp-3);
  padding: var(--sp-9) var(--sp-5);
  text-align: center;
  border: 1px dashed var(--rule-strong);
  border-radius: var(--radius-sm);
  background: var(--paper-tint);
}

.empty__mark {
  width: 34px;
  height: 26px;
  border: 1px solid var(--rule-strong);
  border-top: 3px solid var(--amber);
  background: var(--paper-raised);
}

.empty__title {
  font-size: var(--text-lg);
}

.empty__desc {
  max-width: 46ch;
  color: var(--ink-muted);
  font-size: var(--text-base);
}

.empty__code {
  font-size: var(--text-2xs);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.empty__action:empty {
  display: none;
}
</style>
