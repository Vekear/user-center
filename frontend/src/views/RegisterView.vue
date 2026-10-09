<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppButton from '@/components/AppButton.vue'
import AppCard from '@/components/AppCard.vue'
import AppField from '@/components/AppField.vue'
import AppInput from '@/components/AppInput.vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { toApiError } from '@/api/http'
import {
  firstFieldError,
  validateConfirmPassword,
  validateEmail,
  validateNickname,
  validatePassword,
  validateUsername,
} from '@/utils/validation'

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()

const form = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})

type FieldKey = keyof typeof form
const errors = reactive<Record<FieldKey, string | null>>({
  username: null,
  nickname: null,
  email: null,
  password: null,
  confirmPassword: null,
})

const submitError = ref<string | null>(null)

function validate(): boolean {
  errors.username = validateUsername(form.username.trim())
  errors.nickname = validateNickname(form.nickname)
  errors.email = validateEmail(form.email.trim())
  errors.password = validatePassword(form.password)
  errors.confirmPassword = validateConfirmPassword(form.password, form.confirmPassword)
  return Object.values(errors).every((message) => message === null)
}

function applyFieldErrors(fields: Record<string, string> | null): void {
  if (!fields) return
  for (const key of Object.keys(fields)) {
    if (key in errors) {
      errors[key as FieldKey] = fields[key] ?? null
    }
  }
}

async function submit(): Promise<void> {
  submitError.value = null
  if (!validate()) return
  try {
    await auth.register({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
      ...(form.nickname.trim() ? { nickname: form.nickname.trim() } : {}),
    })
    toast.success('档案已建立，请用新账号登录')
    await router.replace('/login')
  } catch (caught) {
    const error = toApiError(caught)
    if (error.status === 409) {
      submitError.value = error.message || '用户名或邮箱已被占用'
    } else if (error.fields) {
      applyFieldErrors(error.fields)
      submitError.value = firstFieldError(error.fields) ?? '提交的内容没有通过校验'
    } else {
      submitError.value = error.message
    }
  }
}
</script>

<template>
  <div class="register">
    <header class="masthead">
      <span class="masthead__tab">新建档案</span>
      <h1 class="masthead__title">填写底册</h1>
      <p class="masthead__lede">
        用户名与邮箱建册后不可随意变更；密码需 8–64 位，且同时包含字母与数字。
      </p>
    </header>

    <AppCard tab="表一" title="账号信息" accent="amber">
      <form class="form" novalidate @submit.prevent="submit">
        <div class="form__row">
          <AppField label="用户名" field-id="reg-username" :error="errors.username" required>
            <AppInput
              id="reg-username"
              v-model="form.username"
              name="username"
              autocomplete="username"
              placeholder="3–20 位字母、数字或下划线"
              :invalid="Boolean(errors.username)"
            />
          </AppField>

          <AppField label="昵称" field-id="reg-nickname" :error="errors.nickname" note="可留空">
            <AppInput
              id="reg-nickname"
              v-model="form.nickname"
              name="nickname"
              autocomplete="nickname"
              placeholder="最长 20 个字符"
              :invalid="Boolean(errors.nickname)"
            />
          </AppField>
        </div>

        <AppField label="邮箱" field-id="reg-email" :error="errors.email" required>
          <AppInput
            id="reg-email"
            v-model="form.email"
            type="email"
            name="email"
            autocomplete="email"
            placeholder="name@example.com"
            :invalid="Boolean(errors.email)"
          />
        </AppField>

        <div class="form__row">
          <AppField
            label="密码"
            field-id="reg-password"
            :error="errors.password"
            hint="8–64 位，含字母和数字"
            required
          >
            <AppInput
              id="reg-password"
              v-model="form.password"
              type="password"
              name="password"
              autocomplete="new-password"
              :invalid="Boolean(errors.password)"
            />
          </AppField>

          <AppField
            label="确认密码"
            field-id="reg-confirm"
            :error="errors.confirmPassword"
            required
          >
            <AppInput
              id="reg-confirm"
              v-model="form.confirmPassword"
              type="password"
              name="confirmPassword"
              autocomplete="new-password"
              :invalid="Boolean(errors.confirmPassword)"
              @enter="submit"
            />
          </AppField>
        </div>

        <p v-if="submitError" class="form__error" role="alert">{{ submitError }}</p>

        <div class="form__actions">
          <AppButton type="submit" variant="primary" :loading="auth.pending">建立档案</AppButton>
          <RouterLink class="form__link" to="/login">已有档案，去登录</RouterLink>
        </div>
      </form>
    </AppCard>
  </div>
</template>

<style scoped>
.register {
  display: grid;
  gap: var(--sp-7);
  width: min(680px, 100%);
  margin: 0 auto;
  padding: var(--sp-9) var(--page-pad-x);
}

.masthead {
  display: grid;
  gap: var(--sp-3);
  justify-items: start;
  padding-bottom: var(--sp-4);
  border-bottom: var(--border-strong);
}

.masthead__tab {
  padding: 2px 8px;
  border: var(--border-hair);
  border-left: var(--border-accent) solid var(--amber);
  border-radius: var(--radius-xs);
  background: var(--paper-raised);
  font-size: var(--text-2xs);
  font-weight: var(--fw-semibold);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--ink-muted);
}

.masthead__title {
  font-size: var(--text-2xl);
}

.masthead__lede {
  max-width: 52ch;
  color: var(--ink-muted);
}

.form {
  display: grid;
  gap: var(--sp-5);
}

.form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-4);
}

.form__error {
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--danger);
  border-left-width: var(--border-accent);
  border-radius: var(--radius-xs);
  background: var(--danger-tint);
  color: var(--danger);
  font-size: var(--text-sm);
}

.form__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-4);
  padding-top: var(--sp-2);
  border-top: var(--border-hair);
}

.form__link {
  font-size: var(--text-base);
}

@media (max-width: 620px) {
  .register {
    padding: var(--sp-6) var(--page-pad-x) var(--sp-8);
  }
  .form__row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
