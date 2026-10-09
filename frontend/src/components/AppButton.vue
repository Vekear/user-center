<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'amber'
    size?: 'sm' | 'md' | 'lg'
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    loading?: boolean
    block?: boolean
  }>(),
  {
    variant: 'secondary',
    size: 'md',
    type: 'button',
    disabled: false,
    loading: false,
    block: false,
  },
)

const emit = defineEmits<{ click: [event: MouseEvent] }>()

function onClick(event: MouseEvent): void {
  emit('click', event)
}
</script>

<template>
  <button
    class="btn"
    :class="[`btn--${variant}`, `btn--${size}`, { 'btn--block': block }]"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading ? 'true' : undefined"
    @click="onClick"
  >
    <slot />
    <span v-if="loading" class="btn__busy">处理中</span>
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  min-height: var(--control-height);
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: var(--paper-raised);
  color: var(--ink);
  font-size: var(--text-base);
  font-weight: var(--fw-medium);
  line-height: 1;
  cursor: pointer;
}

.btn--sm {
  min-height: 26px;
  padding: 0 9px;
  font-size: var(--text-sm);
}

.btn--lg {
  min-height: var(--control-height-lg);
  padding: 0 18px;
}

.btn--block {
  width: 100%;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.btn--primary {
  background: var(--indigo);
  border-color: var(--indigo);
  color: var(--ink-inverse);
}
.btn--primary:hover:not(:disabled) {
  background: var(--indigo-hover);
  border-color: var(--indigo-hover);
}
.btn--primary:active:not(:disabled) {
  background: var(--indigo-active);
}

.btn--secondary {
  background: var(--paper-raised);
  border-color: var(--rule);
  color: var(--ink);
}
.btn--secondary:hover:not(:disabled) {
  border-color: var(--rule-strong);
  background: var(--paper-tint);
}

.btn--ghost {
  background: transparent;
  border-color: transparent;
  color: var(--indigo);
}
.btn--ghost:hover:not(:disabled) {
  background: var(--indigo-tint);
}

.btn--danger {
  background: transparent;
  border-color: var(--danger);
  color: var(--danger);
}
.btn--danger:hover:not(:disabled) {
  background: var(--danger);
  color: var(--paper-on-dark);
}

.btn--amber {
  background: var(--amber);
  border-color: var(--amber);
  color: var(--ink);
}
.btn--amber:hover:not(:disabled) {
  background: var(--amber-hover);
  border-color: var(--amber-hover);
}

.btn__busy {
  font-size: var(--text-sm);
  color: inherit;
  opacity: 0.8;
}
</style>
