<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    id?: string
    name?: string
    rows?: number
    maxlength?: number
    placeholder?: string
    invalid?: boolean
    disabled?: boolean
    /** 是否在右下角显示字数 */
    counter?: boolean
    mono?: boolean
  }>(),
  {
    id: undefined,
    name: undefined,
    rows: 4,
    maxlength: undefined,
    placeholder: '',
    invalid: false,
    disabled: false,
    counter: true,
    mono: false,
  },
)

const model = defineModel<string>({ default: '' })

const length = computed(() => (model.value ?? '').length)
const showCounter = computed(() => props.counter && typeof props.maxlength === 'number')
</script>

<template>
  <div class="textarea">
    <textarea
      :id="id"
      v-model="model"
      class="uc-control textarea__control"
      :class="{ 'uc-control--mono': mono }"
      :name="name"
      :rows="rows"
      :maxlength="maxlength"
      :placeholder="placeholder"
      :disabled="disabled"
      :aria-invalid="invalid ? 'true' : undefined"
    />
    <span v-if="showCounter" class="textarea__counter mono">
      {{ length }}/{{ maxlength }}
    </span>
  </div>
</template>

<style scoped>
.textarea {
  position: relative;
  display: block;
  min-width: 0;
}

.textarea__control {
  padding-bottom: 20px;
  line-height: var(--leading-snug);
  resize: vertical;
}

.textarea__counter {
  position: absolute;
  right: 10px;
  bottom: 6px;
  font-size: var(--text-2xs);
  color: var(--ink-faint);
  pointer-events: none;
}
</style>
