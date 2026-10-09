<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    type?: 'text' | 'password' | 'email' | 'tel' | 'date' | 'number' | 'search'
    id?: string
    name?: string
    placeholder?: string
    autocomplete?: string
    invalid?: boolean
    disabled?: boolean
    readonly?: boolean
    mono?: boolean
    maxlength?: number
    min?: string | number
    max?: string | number
    describedBy?: string
    /** 密码框是否提供「显示/隐藏」开关 */
    revealable?: boolean
  }>(),
  {
    type: 'text',
    id: undefined,
    name: undefined,
    placeholder: '',
    autocomplete: 'off',
    invalid: false,
    disabled: false,
    readonly: false,
    mono: false,
    maxlength: undefined,
    min: undefined,
    max: undefined,
    describedBy: undefined,
    revealable: true,
  },
)

const model = defineModel<string>({ default: '' })

const emit = defineEmits<{
  blur: [event: FocusEvent]
  enter: [event: KeyboardEvent]
}>()

const revealed = ref(false)

const isPassword = computed(() => props.type === 'password')
const canReveal = computed(() => isPassword.value && props.revealable)
const resolvedType = computed(() => {
  if (!isPassword.value) return props.type
  return revealed.value ? 'text' : 'password'
})
</script>

<template>
  <div class="input" :class="{ 'input--mono': mono }">
    <input
      :id="id"
      v-model="model"
      class="uc-control input__control"
      :class="{ 'uc-control--mono': mono, 'input__control--reveal': canReveal }"
      :type="resolvedType"
      :name="name"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      :disabled="disabled"
      :readonly="readonly"
      :maxlength="maxlength"
      :min="min"
      :max="max"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-describedby="describedBy"
      @blur="emit('blur', $event)"
      @keyup.enter="emit('enter', $event)"
    />
    <button
      v-if="canReveal"
      class="input__reveal"
      type="button"
      :aria-label="revealed ? '隐藏密码' : '显示密码'"
      :aria-pressed="revealed"
      @click="revealed = !revealed"
    >
      {{ revealed ? '隐藏' : '显示' }}
    </button>
  </div>
</template>

<style scoped>
.input {
  position: relative;
  display: block;
  min-width: 0;
}

.input__control--reveal {
  padding-right: 52px;
}

.input__reveal {
  position: absolute;
  top: 50%;
  right: 6px;
  transform: translateY(-50%);
  padding: 2px 6px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--ink-muted);
  font-size: var(--text-2xs);
  letter-spacing: 0.06em;
  cursor: pointer;
}

.input__reveal:hover {
  color: var(--indigo);
  background: var(--indigo-tint);
}
</style>
