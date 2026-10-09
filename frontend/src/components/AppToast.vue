<script setup lang="ts">
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
</script>

<template>
  <Teleport to="body">
    <div class="toasts" aria-live="polite">
      <div
        v-for="toast in ui.toasts"
        :key="toast.id"
        class="toast"
        :class="`toast--${toast.type}`"
        role="status"
      >
        <div class="toast__body">
          <p class="toast__message">{{ toast.message }}</p>
          <p v-if="toast.detail" class="toast__detail">{{ toast.detail }}</p>
        </div>
        <button class="toast__close" type="button" aria-label="关闭提示" @click="ui.dismissToast(toast.id)">
          ×
        </button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.toasts {
  position: fixed;
  top: var(--sp-4);
  right: var(--sp-4);
  z-index: var(--z-toast);
  display: grid;
  gap: var(--sp-2);
  width: min(360px, calc(100vw - var(--sp-8)));
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-3) var(--sp-3) var(--sp-4);
  background: var(--paper-raised);
  border: var(--border-hair);
  border-left: var(--border-accent) solid var(--ink-faint);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-overlay);
}

.toast--success {
  border-left-color: var(--success);
}
.toast--error {
  border-left-color: var(--danger);
}
.toast--info {
  border-left-color: var(--indigo);
}

.toast__body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}

.toast__message {
  font-size: var(--text-base);
  color: var(--ink);
}

.toast__detail {
  font-size: var(--text-sm);
  color: var(--ink-muted);
  overflow-wrap: anywhere;
}

.toast__close {
  flex: none;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--ink-faint);
  font-size: var(--text-md);
  line-height: 1;
  cursor: pointer;
}

.toast__close:hover {
  color: var(--ink);
  background: var(--paper-sunken);
}
</style>
