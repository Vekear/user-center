<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    /** 卡片左上角的分类签，像档案袋脊上的标签 */
    tab?: string
    description?: string
    accent?: 'primary' | 'amber' | 'none'
    /** 表格类卡片：内容区不留内边距 */
    flush?: boolean
  }>(),
  { title: '', tab: '', description: '', accent: 'primary', flush: false },
)
</script>

<template>
  <section class="card" :class="[`card--${accent}`, { 'card--flush': flush }]">
    <header v-if="title || tab || $slots.actions" class="card__head">
      <div class="card__headline">
        <span v-if="tab" class="card__tab">{{ tab }}</span>
        <h2 v-if="title" class="card__title">{{ title }}</h2>
      </div>
      <div v-if="$slots.actions" class="card__actions">
        <slot name="actions" />
      </div>
    </header>

    <p v-if="description" class="card__desc">{{ description }}</p>

    <div class="card__body">
      <slot />
    </div>

    <footer v-if="$slots.footer" class="card__foot">
      <slot name="footer" />
    </footer>
  </section>
</template>

<style scoped>
.card {
  position: relative;
  display: grid;
  gap: 0;
  min-width: 0;
  background: var(--paper-raised);
  border: var(--border-hair);
  border-radius: var(--radius-sm);
  border-top: var(--border-accent) solid var(--indigo);
}

.card--amber {
  border-top-color: var(--amber);
}

.card--none {
  border-top-width: 1px;
  border-top-color: var(--rule);
}

.card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-5) var(--sp-3);
}

.card__headline {
  display: flex;
  align-items: baseline;
  gap: var(--sp-3);
  min-width: 0;
}

.card__tab {
  flex: none;
  padding: 1px 6px;
  border: 1px solid var(--rule);
  border-left: 3px solid var(--indigo);
  border-radius: var(--radius-xs);
  background: var(--paper-tint);
  font-size: var(--text-2xs);
  font-weight: var(--fw-semibold);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--ink-muted);
}

.card--amber .card__tab {
  border-left-color: var(--amber);
}

.card__title {
  font-size: var(--text-lg);
  min-width: 0;
}

.card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-2);
}

.card__desc {
  padding: 0 var(--sp-5) var(--sp-2);
  color: var(--ink-muted);
  font-size: var(--text-base);
  max-width: 74ch;
}

.card__body {
  padding: var(--sp-4) var(--sp-5) var(--sp-5);
  min-width: 0;
}

.card--flush .card__body {
  padding: 0;
}

.card__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-5) var(--sp-4);
  border-top: var(--border-hair);
}

@media (max-width: 768px) {
  .card__head,
  .card__body,
  .card__desc,
  .card__foot {
    padding-left: var(--sp-4);
    padding-right: var(--sp-4);
  }
  .card--flush .card__body {
    padding-left: 0;
    padding-right: 0;
  }
}
</style>
