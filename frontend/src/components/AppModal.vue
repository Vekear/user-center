<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

withDefaults(
  defineProps<{
    title?: string
    size?: 'sm' | 'md' | 'lg'
    closeOnBackdrop?: boolean
    /** 与面板标题元素 id 对应，用于 aria-labelledby */
    labelledBy?: string
  }>(),
  { title: '', size: 'md', closeOnBackdrop: true, labelledBy: undefined },
)

const open = defineModel<boolean>('open', { default: false })
const emit = defineEmits<{ close: [] }>()

const panel = ref<HTMLElement | null>(null)
let lastFocused: HTMLElement | null = null

function close(): void {
  open.value = false
  emit('close')
}

function onBackdrop(): void {
  close()
}

function focusable(): HTMLElement[] {
  if (!panel.value) return []
  const selector =
    'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
  return Array.from(panel.value.querySelectorAll<HTMLElement>(selector)).filter(
    (element) => element.offsetParent !== null || element === document.activeElement,
  )
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (event.key !== 'Tab') return
  const items = focusable()
  if (items.length === 0) return
  const first = items[0] as HTMLElement
  const last = items[items.length - 1] as HTMLElement
  const active = document.activeElement as HTMLElement | null
  if (event.shiftKey && (active === first || !panel.value?.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

let previousOverflow = ''

function bind(): void {
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('keydown', onKeydown, true)
}

function unbind(): void {
  document.body.style.overflow = previousOverflow
  window.removeEventListener('keydown', onKeydown, true)
}

watch(open, async (value) => {
  if (value) {
    lastFocused = document.activeElement as HTMLElement | null
    bind()
    await nextTick()
    const items = focusable()
    ;(items[0] ?? panel.value)?.focus()
  } else {
    unbind()
    lastFocused?.focus?.()
    lastFocused = null
  }
})

onBeforeUnmount(() => {
  if (open.value) unbind()
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" @click.self="closeOnBackdrop && onBackdrop()">
      <div
        ref="panel"
        class="modal"
        :class="`modal--${size}`"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="labelledBy"
        tabindex="-1"
      >
        <header v-if="title || $slots.head" class="modal__head">
          <slot name="head">
            <h2 :id="labelledBy" class="modal__title">{{ title }}</h2>
          </slot>
          <button class="modal__close" type="button" aria-label="关闭" @click="close">×</button>
        </header>

        <div class="modal__body">
          <slot />
        </div>

        <footer v-if="$slots.footer" class="modal__foot">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-5);
  background: var(--scrim);
}

.modal {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: calc(100vh - var(--sp-10));
  background: var(--paper-raised);
  border: var(--border-strong);
  border-radius: var(--radius-md);
  border-top: var(--border-accent) solid var(--indigo);
  box-shadow: var(--shadow-overlay);
  overflow: hidden;
}

.modal--sm {
  max-width: 420px;
}
.modal--md {
  max-width: 560px;
}
.modal--lg {
  max-width: 760px;
}

.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-4) var(--sp-5) var(--sp-3);
  border-bottom: var(--border-hair);
}

.modal__title {
  font-size: var(--text-lg);
  min-width: 0;
}

.modal__close {
  flex: none;
  width: 26px;
  height: 26px;
  border: var(--border-hair);
  border-radius: var(--radius-xs);
  background: var(--paper-raised);
  color: var(--ink-muted);
  font-size: var(--text-md);
  line-height: 1;
  cursor: pointer;
}

.modal__close:hover {
  border-color: var(--rule-strong);
  color: var(--ink);
}

.modal__body {
  padding: var(--sp-4) var(--sp-5);
  overflow-y: auto;
  min-height: 0;
}

.modal__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-5) var(--sp-4);
  border-top: var(--border-hair);
  background: var(--paper-tint);
}

@media (max-width: 560px) {
  .overlay {
    padding: var(--sp-3);
    align-items: flex-end;
  }
  .modal {
    max-height: calc(100vh - var(--sp-7));
  }
  .modal__foot {
    justify-content: stretch;
  }
}
</style>
