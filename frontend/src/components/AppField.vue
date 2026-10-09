<script setup lang="ts">
withDefaults(
  defineProps<{
    label?: string
    fieldId?: string
    error?: string | null
    hint?: string
    required?: boolean
    /** 标签右侧的补充说明，例如「可留空」 */
    note?: string
  }>(),
  { label: '', fieldId: undefined, error: null, hint: '', required: false, note: '' },
)
</script>

<template>
  <div class="field">
    <div v-if="label" class="field__head">
      <label class="field__label" :for="fieldId">
        {{ label }}
        <span v-if="required" class="field__required" aria-hidden="true">·</span>
      </label>
      <span v-if="note" class="field__note">{{ note }}</span>
    </div>

    <slot />

    <p v-if="error" class="field__error" role="alert">{{ error }}</p>
    <p v-else-if="hint" class="field__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.field {
  display: grid;
  gap: var(--sp-2);
  min-width: 0;
}

.field__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-3);
}

.field__label {
  font-size: var(--text-2xs);
  font-weight: var(--fw-semibold);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--ink-muted);
}

.field__required {
  color: var(--amber);
}

.field__note {
  font-size: var(--text-2xs);
  color: var(--ink-faint);
}

.field__error {
  font-size: var(--text-sm);
  color: var(--danger);
}

.field__hint {
  font-size: var(--text-sm);
  color: var(--ink-faint);
}
</style>
