<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/AppButton.vue'
import AppField from '@/components/AppField.vue'
import AppInput from '@/components/AppInput.vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { toApiError } from '@/api/http'
import { firstFieldError, validateRequired, validateUsername } from '@/utils/validation'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const form = reactive({ username: '', password: '', remember: true })
const errors = reactive<{ username: string | null; password: string | null }>({
  username: null,
  password: null,
})
const submitError = ref<string | null>(null)

const redirect = computed(() =>
  typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
    ? route.query.redirect
    : '/profile',
)

function validate(): boolean {
  errors.username = validateUsername(form.username.trim())
  errors.password = validateRequired(form.password, '密码')
  return errors.username === null && errors.password === null
}

async function submit(): Promise<void> {
  submitError.value = null
  if (!validate()) return
  try {
    const user = await auth.login({
      username: form.username.trim(),
      password: form.password,
      remember: form.remember,
    })
    toast.success(`欢迎回来，${user.nickname || user.username}`)
    await router.replace(redirect.value)
  } catch (caught) {
    const error = toApiError(caught)
    if (error.status === 401) {
      submitError.value = '用户名或密码不正确'
    } else if (error.status === 429) {
      submitError.value = '尝试次数过多，账号已被临时锁定，请稍后再试'
    } else if (error.status === 403) {
      submitError.value = '该账号已被禁用，请联系管理员'
    } else if (error.fields) {
      submitError.value = firstFieldError(error.fields) ?? error.message
    } else {
      submitError.value = error.message
    }
  }
}
</script>

<template>
  <div class="auth">
    <!-- 封面页：档案册的壳，横格纸 + 衬线刊头 -->
    <section class="cover" aria-hidden="false">
      <div class="cover__top">
        <span class="cover__mark" aria-hidden="true" />
        <span class="cover__no mono">UC-000001 / 第 1 册</span>
      </div>

      <div class="cover__mid">
        <h1 class="cover__title">个人档案</h1>
        <p class="cover__lede">
          一份关于你的账号底册：资料、登录设备、通知与权限，逐条在案。
        </p>
      </div>

      <ul class="cover__contents">
        <li class="cover__item">
          <span class="cover__item-no mono">01</span>
          <span>资料与头像</span>
        </li>
        <li class="cover__item">
          <span class="cover__item-no mono">02</span>
          <span>登录设备与密码</span>
        </li>
        <li class="cover__item">
          <span class="cover__item-no mono">03</span>
          <span>通知与审计留痕</span>
        </li>
      </ul>
    </section>

    <!-- 登录表单 -->
    <section class="panel">
      <header class="panel__head">
        <span class="panel__tab">登录</span>
        <h2 class="panel__title">调阅档案</h2>
      </header>

      <form class="panel__form" novalidate @submit.prevent="submit">
        <AppField label="用户名" field-id="login-username" :error="errors.username" required>
          <AppInput
            id="login-username"
            v-model="form.username"
            name="username"
            autocomplete="username"
            placeholder="3–20 位字母、数字或下划线"
            :invalid="Boolean(errors.username)"
          />
        </AppField>

        <AppField label="密码" field-id="login-password" :error="errors.password" required>
          <AppInput
            id="login-password"
            v-model="form.password"
            type="password"
            name="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :invalid="Boolean(errors.password)"
            @enter="submit"
          />
        </AppField>

        <label class="panel__remember">
          <input v-model="form.remember" type="checkbox" />
          <span>在这台设备上保持登录（30 天）</span>
        </label>

        <p v-if="submitError" class="panel__error" role="alert">{{ submitError }}</p>

        <AppButton type="submit" variant="primary" size="lg" block :loading="auth.pending">
          登录
        </AppButton>
      </form>

      <footer class="panel__foot">
        <span class="muted">还没有档案？</span>
        <RouterLink to="/register">新建一份</RouterLink>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  min-height: 100vh;
}

/* ── 封面 ───────────────────────────────────────────────────────────── */

.cover {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--sp-8);
  padding: var(--sp-9) var(--sp-8);
  background-color: var(--ink);
  background-image: linear-gradient(var(--rule-on-dark) 1px, transparent 1px);
  background-size: 100% 30px;
  color: var(--paper);
}

.cover__top {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.cover__mark {
  width: 22px;
  height: 28px;
  border: 1px solid var(--nav-fg-muted);
  border-top: 3px solid var(--amber);
}

.cover__no {
  font-size: var(--text-2xs);
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--nav-fg-muted);
}

.cover__mid {
  display: grid;
  gap: var(--sp-4);
  max-width: 26ch;
}

.cover__title {
  font-size: var(--text-3xl);
  font-weight: var(--fw-semibold);
  color: var(--paper);
}

.cover__lede {
  font-family: var(--font-display);
  font-size: var(--text-md);
  line-height: var(--leading-normal);
  color: var(--nav-fg-soft);
}

.cover__contents {
  display: grid;
  gap: var(--sp-2);
  border-top: 1px solid var(--nav-rule);
  padding-top: var(--sp-4);
}

.cover__item {
  display: flex;
  align-items: baseline;
  gap: var(--sp-3);
  font-size: var(--text-base);
  color: var(--nav-fg-soft);
}

.cover__item-no {
  font-size: var(--text-2xs);
  color: var(--amber);
}

/* ── 表单面板 ───────────────────────────────────────────────────────── */

.panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--sp-6);
  padding: var(--sp-9) var(--sp-8);
  background: var(--paper);
}

.panel__head {
  display: grid;
  gap: var(--sp-3);
  justify-items: start;
}

.panel__tab {
  padding: 2px 8px;
  border: var(--border-hair);
  border-left: var(--border-accent) solid var(--indigo);
  border-radius: var(--radius-xs);
  background: var(--paper-raised);
  font-size: var(--text-2xs);
  font-weight: var(--fw-semibold);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--ink-muted);
}

.panel__title {
  font-size: var(--text-2xl);
}

.panel__form {
  display: grid;
  gap: var(--sp-5);
  width: min(380px, 100%);
}

.panel__remember {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--text-sm);
  color: var(--ink-muted);
  cursor: pointer;
}

.panel__remember input {
  accent-color: var(--indigo);
}

.panel__error {
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--danger);
  border-left-width: var(--border-accent);
  border-radius: var(--radius-xs);
  background: var(--danger-tint);
  color: var(--danger);
  font-size: var(--text-sm);
}

.panel__foot {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--text-base);
  color: var(--ink-muted);
}

@media (max-width: 900px) {
  .auth {
    grid-template-columns: minmax(0, 1fr);
    min-height: auto;
  }

  .cover {
    gap: var(--sp-6);
    padding: var(--sp-7) var(--page-pad-x);
  }

  .cover__title {
    font-size: var(--text-2xl);
  }

  .cover__mid {
    max-width: 34ch;
  }

  .cover__contents {
    display: none;
  }

  .panel {
    padding: var(--sp-7) var(--page-pad-x) var(--sp-9);
  }

  .panel__form {
    width: 100%;
  }
}
</style>
