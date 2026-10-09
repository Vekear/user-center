<script setup lang="ts">
interface SelectOption {
  label: string
  value: string
}

withDefaults(
  defineProps<{
    options: SelectOption[]
    id?: string
    name?: string
    placeholder?: string
    invalid?: boolean
    disabled?: boolean
    mono?: boolean
  }>(),
  {
    id: undefined,
    name: undefined,
    placeholder: '请选择',
    invalid: false,
    disabled: false,
    mono: false,
  },
)

const model = defineModel<string>({ default: '' })
</script>

<template>
  <div class="select">
    <select
      :id="id"
      v-model="model"
      class="uc-control select__control"
      :class="{ 'uc-control--mono': mono }"
      :name="name"
      :disabled="disabled"
      :aria-invalid="invalid ? 'true' : undefined"
    >
      <option v-if="placeholder" value="">{{ placeholder }}</option>
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
    <span class="select__caret" aria-hidden="true" />
  </div>
</template>

<style scoped>
.select {
  position: relative;
  display: block;
  min-width: 0;
}

.select__control {
  appearance: none;
  padding-right: 28px;
}

.select__caret {
  position: absolute;
  top: 50%;
  right: 11px;
  width: 0;
  height: 0;
  margin-top: -1px;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid var(--ink-muted);
  pointer-events: none;
}
</style>
