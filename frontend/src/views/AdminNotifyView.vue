<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import AppBadge from '@/components/AppBadge.vue'
import AppButton from '@/components/AppButton.vue'
import AppCard from '@/components/AppCard.vue'
import AppField from '@/components/AppField.vue'
import AppInput from '@/components/AppInput.vue'
import AppTextarea from '@/components/AppTextarea.vue'
import { useToast } from '@/composables/useToast'
import { toApiError } from '@/api/http'
import * as adminApi from '@/api/admin'
import { validateRequired } from '@/utils/validation'

const toast = useToast()

const form = reactive({
  all: true,
  userIds: '',
  title: '',
  content: '',
})

const errors = reactive<{ userIds: string | null; title: string | null; content: string | null }>({
  userIds: null,
  title: null,
  content: null,
})

const sending = ref(false)
const sentAt = ref<Date | null>(null)

/** 支持逗号、分号、空格、换行混合分隔，去重后只保留正整数 */
const parsedIds = computed<number[]>(() => {
  const parts = form.userIds
    .split(/[\s,，;；]+/)
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
  const ids = parts.map((part) => Number(part)).filter((id) => Number.isInteger(id) && id > 0)
  return Array.from(new Set(ids))
})

const audience = computed(() =>
  form.all ? '全部用户' : parsedIds.value.length > 0 ? `${parsedIds.value.length} 位指定用户` : '尚未指定',
)

function validate(): boolean {
  errors.title = validateRequired(form.title, '标题')
  if (!errors.title && form.title.trim().length > 80) errors.title = '标题最长 80 个字符'

  errors.content = validateRequired(form.content, '正文')
  if (!errors.content && form.content.length > 500) errors.content = '正文最长 500 个字符'

  if (form.all) {
    errors.userIds = null
  } else if (parsedIds.value.length === 0) {
    errors.userIds = '请填写至少一个有效的用户 ID'
  } else {
    errors.userIds = null
  }

  return errors.title === null && errors.content === null && errors.userIds === null
}

async function submit(): Promise<void> {
  if (!validate()) return
  sending.value = true
  try {
    await adminApi.sendNotification({
      ...(form.all ? { all: true } : { userIds: parsedIds.value }),
      title: form.title.trim(),
      content: form.content.trim(),
    })
    sentAt.value = new Date()
    toast.success('通知已发送', `发送对象：${audience.value}`)
    form.title = ''
    form.content = ''
    form.userIds = ''
  } catch (caught) {
    toast.error('发送失败', toApiError(caught).message)
  } finally {
    sending.value = false
  }
}

function reset(): void {
  form.all = true
  form.userIds = ''
  form.title = ''
  form.content = ''
  sentAt.value = null
  errors.userIds = null
  errors.title = null
  errors.content = null
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head__title">
        <p class="page-head__file mono">registry · broadcast</p>
        <h1>发送通知</h1>
        <p class="lede">一条通知写给全部用户，或点名单发。发送前先在右侧核对一遍。</p>
      </div>
      <div class="page-head__meta mono">admin only</div>
    </header>

    <div class="grid-12">
      <div class="span-7">
        <AppCard tab="起草" title="通知内容" accent="amber">
          <form class="form" novalidate @submit.prevent="submit">
            <fieldset class="scope">
              <legend class="scope__legend label-strip">发送对象</legend>
              <label class="scope__item">
                <input v-model="form.all" type="radio" :value="true" name="audience" />
                <span>全部用户</span>
              </label>
              <label class="scope__item">
                <input v-model="form.all" type="radio" :value="false" name="audience" />
                <span>指定用户</span>
              </label>
            </fieldset>

            <AppField
              v-if="!form.all"
              label="用户 ID"
              field-id="an-ids"
              :error="errors.userIds"
              hint="逗号、空格或换行分隔，例如 12, 34, 57"
            >
              <AppTextarea
                id="an-ids"
                v-model="form.userIds"
                :rows="3"
                :counter="false"
                mono
                placeholder="12, 34, 57"
                :invalid="Boolean(errors.userIds)"
              />
            </AppField>

            <AppField label="标题" field-id="an-title" :error="errors.title" note="最长 80 字" required>
              <AppInput
                id="an-title"
                v-model="form.title"
                :maxlength="80"
                placeholder="例如：系统维护通知"
                :invalid="Boolean(errors.title)"
              />
            </AppField>

            <AppField label="正文" field-id="an-content" :error="errors.content" note="最长 500 字" required>
              <AppTextarea
                id="an-content"
                v-model="form.content"
                :rows="6"
                :maxlength="500"
                placeholder="写清楚发生了什么、需要用户做什么"
                :invalid="Boolean(errors.content)"
              />
            </AppField>

            <div class="form__actions">
              <AppButton type="submit" variant="primary" :loading="sending">发送通知</AppButton>
              <AppButton variant="secondary" :disabled="sending" @click="reset">清空</AppButton>
              <span class="form__audience mono">→ {{ audience }}</span>
            </div>

            <p v-if="sentAt" class="form__sent" role="status">
              上一条已于 {{ sentAt.toLocaleTimeString('zh-CN', { hour12: false }) }} 发出。
            </p>
          </form>
        </AppCard>
      </div>

      <div class="span-5">
        <AppCard tab="样张" title="用户看到的样子" accent="none">
          <div class="preview">
            <AppBadge tone="pending" label="未读" />
            <h3 class="preview__title">{{ form.title || '（这里是标题）' }}</h3>
            <p class="preview__content">{{ form.content || '（这里是正文，用户会在通知列表里读到它。）' }}</p>
            <p class="preview__meta mono">刚刚 · 收件人：{{ audience }}</p>
          </div>
        </AppCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-head__meta {
  font-size: var(--text-2xs);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.form {
  display: grid;
  gap: var(--sp-5);
}

.scope {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-5);
  margin: 0;
  padding: var(--sp-3) var(--sp-4);
  border: var(--border-hair);
  border-radius: var(--radius-xs);
  background: var(--paper-tint);
}

.scope__legend {
  padding: 0 var(--sp-2);
}

.scope__item {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--text-base);
  cursor: pointer;
}

.scope__item input {
  accent-color: var(--indigo);
}

.form__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: var(--border-hair);
}

.form__audience {
  font-size: var(--text-2xs);
  letter-spacing: 0.06em;
  color: var(--ink-faint);
}

.form__sent {
  font-size: var(--text-sm);
  color: var(--success);
}

.preview {
  display: grid;
  gap: var(--sp-3);
  justify-items: start;
  padding: var(--sp-4);
  border: var(--border-hair);
  border-left: var(--border-accent) solid var(--indigo);
  border-radius: var(--radius-xs);
  background: var(--paper-tint);
}

.preview__title {
  font-size: var(--text-md);
  overflow-wrap: anywhere;
}

.preview__content {
  color: var(--ink-muted);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.preview__meta {
  font-size: var(--text-2xs);
  color: var(--ink-faint);
}
</style>
