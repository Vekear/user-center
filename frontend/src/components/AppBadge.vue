<script setup lang="ts">
import { computed } from 'vue'

type Tone = 'active' | 'disabled' | 'pending' | 'success' | 'danger' | 'neutral'

const props = withDefaults(
  defineProps<{
    /** 直接传后端状态码，如 ACTIVE / DISABLED */
    status?: string
    tone?: Tone
    label?: string
  }>(),
  { status: '', tone: undefined, label: '' },
)

const TONE_BY_STATUS: Record<string, Tone> = {
  ACTIVE: 'active',
  ENABLED: 'active',
  NORMAL: 'active',
  DISABLED: 'disabled',
  INACTIVE: 'disabled',
  LOCKED: 'disabled',
  PENDING: 'pending',
  WAITING: 'pending',
  SUCCESS: 'success',
  READ: 'success',
  DANGER: 'danger',
  FAILED: 'danger',
  FAIL: 'danger',
  UNREAD: 'pending',
}

const tone = computed<Tone>(() => props.tone ?? TONE_BY_STATUS[(props.status || '').toUpperCase()] ?? 'neutral')
const text = computed(() => props.label || props.status || '')
</script>

<template>
  <span class="stamp" :class="`stamp--${tone}`">
    <slot>{{ text }}</slot>
  </span>
</template>
