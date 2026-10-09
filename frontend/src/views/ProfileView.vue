<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import AppBadge from '@/components/AppBadge.vue'
import AppButton from '@/components/AppButton.vue'
import AppCard from '@/components/AppCard.vue'
import AppField from '@/components/AppField.vue'
import AppInput from '@/components/AppInput.vue'
import AppSelect from '@/components/AppSelect.vue'
import AppTextarea from '@/components/AppTextarea.vue'
import AvatarCropper from '@/components/AvatarCropper.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { useToast } from '@/composables/useToast'
import { toApiError } from '@/api/http'
import * as profileApi from '@/api/profile'
import { formatDateTime, formatRelative, formatBytes } from '@/utils/format'
import {
  firstFieldError,
  validateBio,
  validateBirthday,
  validateEmail,
  validateNickname,
  validatePhone,
} from '@/utils/validation'
import type { Gender } from '@/api/types'

const auth = useAuthStore()
const ui = useUiStore()
const toast = useToast()

const MAX_AVATAR_BYTES = 5 * 1024 * 1024

const form = reactive({
  nickname: '',
  email: '',
  phone: '',
  gender: '',
  birthday: '',
  bio: '',
})

const errors = reactive<Record<keyof typeof form, string | null>>({
  nickname: null,
  email: null,
  phone: null,
  gender: null,
  birthday: null,
  bio: null,
})

const saving = ref(false)
const avatarBusy = ref(false)
const avatarError = ref<string | null>(null)

const cropperOpen = ref(false)
const cropperSrc = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const genderOptions = [
  { label: '不填', value: '' },
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '不便透露', value: 'UNKNOWN' },
]

/** 用后端返回的用户填充表单 */
function hydrate(): void {
  const user = auth.user
  if (!user) return
  form.nickname = user.nickname ?? ''
  form.email = user.email ?? ''
  form.phone = user.phone ?? ''
  form.gender = user.gender ?? ''
  form.birthday = user.birthday ? user.birthday.slice(0, 10) : ''
  form.bio = user.bio ?? ''
}

watch(() => auth.user?.id, hydrate, { immediate: true })

const avatarUrl = computed(() => auth.user?.avatarUrl ?? '')
const initial = computed(() => (auth.displayName || '?').trim().charAt(0).toUpperCase())

const dirty = computed(() => {
  const user = auth.user
  if (!user) return false
  return (
    form.nickname !== (user.nickname ?? '') ||
    form.email !== (user.email ?? '') ||
    form.phone !== (user.phone ?? '') ||
    form.gender !== (user.gender ?? '') ||
    form.birthday !== (user.birthday ? user.birthday.slice(0, 10) : '') ||
    form.bio !== (user.bio ?? '')
  )
})

function validate(): boolean {
  errors.nickname = validateNickname(form.nickname)
  errors.email = validateEmail(form.email.trim())
  errors.phone = validatePhone(form.phone.trim())
  errors.birthday = validateBirthday(form.birthday)
  errors.bio = validateBio(form.bio)
  return Object.values(errors).every((message) => message === null)
}

async function save(): Promise<void> {
  if (!validate()) return
  saving.value = true
  try {
    const updated = await profileApi.updateProfile({
      nickname: form.nickname.trim() || null,
      email: form.email.trim(),
      phone: form.phone.trim() || null,
      gender: (form.gender || null) as Gender | null,
      birthday: form.birthday || null,
      bio: form.bio.trim() || null,
    })
    auth.patchUser(updated)
    hydrate()
    toast.success('资料已更新')
  } catch (caught) {
    const error = toApiError(caught)
    if (error.fields) {
      for (const key of Object.keys(error.fields)) {
        if (key in errors) errors[key as keyof typeof errors] = error.fields[key] ?? null
      }
      toast.error('部分内容未通过校验', firstFieldError(error.fields) ?? undefined)
    } else {
      toast.error('保存失败', error.message)
    }
  } finally {
    saving.value = false
  }
}

function resetForm(): void {
  hydrate()
  for (const key of Object.keys(errors) as Array<keyof typeof errors>) {
    errors[key] = null
  }
}

/* ── 头像 ─────────────────────────────────────────────────────────── */

function pickAvatar(): void {
  avatarError.value = null
  fileInput.value?.click()
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    avatarError.value = '只能选择图片文件（JPG / PNG / WebP）'
    toast.error('头像格式不支持', avatarError.value)
    return
  }
  if (file.size > MAX_AVATAR_BYTES) {
    avatarError.value = `图片不能超过 5 MB（当前 ${formatBytes(file.size)}）`
    toast.error('头像太大', avatarError.value)
    return
  }
  releaseCropperSrc()
  cropperSrc.value = URL.createObjectURL(file)
  cropperOpen.value = true
}

function releaseCropperSrc(): void {
  if (cropperSrc.value) {
    URL.revokeObjectURL(cropperSrc.value)
    cropperSrc.value = ''
  }
}

async function onCropped(blob: Blob): Promise<void> {
  avatarBusy.value = true
  avatarError.value = null
  try {
    const result = await profileApi.uploadAvatar(blob)
    auth.patchUser({ avatarUrl: result.avatarUrl })
    toast.success('头像已更新')
  } catch (caught) {
    const error = toApiError(caught)
    avatarError.value = error.message
    toast.error('头像上传失败', error.message)
  } finally {
    avatarBusy.value = false
    releaseCropperSrc()
  }
}

function onCropCancel(): void {
  releaseCropperSrc()
  toast.info('已取消裁剪，头像未改动')
}

function onCropError(message: string): void {
  avatarError.value = message
  toast.error('裁剪失败', message)
}

function removeAvatar(): void {
  ui.askConfirm({
    title: '移除头像',
    message: '移除后将回到默认的字母头像，可以随时重新上传。',
    confirmText: '移除',
    danger: true,
    onConfirm: async () => {
      await profileApi.deleteAvatar()
      auth.patchUser({ avatarUrl: null })
      toast.success('头像已移除')
    },
  })
}

onBeforeUnmount(releaseCropperSrc)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head__title">
        <p class="page-head__file mono">record 01 · identity</p>
        <h1>个人资料</h1>
        <p class="lede">这些字段会出现在你的公开档案里，改动即时生效。</p>
      </div>
      <div class="page-head__meta mono">{{ auth.user?.username }}</div>
    </header>

    <div class="grid-12">
      <div class="span-8">
        <AppCard tab="表二" title="基本资料" accent="primary">
          <form class="form" novalidate @submit.prevent="save">
            <div class="form__row">
              <AppField label="昵称" field-id="pf-nickname" :error="errors.nickname">
                <AppInput id="pf-nickname" v-model="form.nickname" :invalid="Boolean(errors.nickname)" />
              </AppField>

              <AppField label="性别" field-id="pf-gender" :error="errors.gender">
                <AppSelect id="pf-gender" v-model="form.gender" :options="genderOptions" placeholder="" />
              </AppField>
            </div>

            <div class="form__row">
              <AppField label="邮箱" field-id="pf-email" :error="errors.email" required>
                <AppInput
                  id="pf-email"
                  v-model="form.email"
                  type="email"
                  :invalid="Boolean(errors.email)"
                />
              </AppField>

              <AppField label="手机号" field-id="pf-phone" :error="errors.phone" note="可留空">
                <AppInput
                  id="pf-phone"
                  v-model="form.phone"
                  type="tel"
                  mono
                  placeholder="11 位手机号"
                  :invalid="Boolean(errors.phone)"
                />
              </AppField>
            </div>

            <div class="form__row">
              <AppField label="生日" field-id="pf-birthday" :error="errors.birthday" note="可留空">
                <AppInput
                  id="pf-birthday"
                  v-model="form.birthday"
                  type="date"
                  mono
                  :invalid="Boolean(errors.birthday)"
                />
              </AppField>
            </div>

            <AppField label="个人简介" field-id="pf-bio" :error="errors.bio" note="最长 200 字">
              <AppTextarea
                id="pf-bio"
                v-model="form.bio"
                :rows="4"
                :maxlength="200"
                placeholder="一句话介绍你自己"
                :invalid="Boolean(errors.bio)"
              />
            </AppField>

            <div class="form__actions">
              <AppButton type="submit" variant="primary" :loading="saving" :disabled="!dirty">
                保存资料
              </AppButton>
              <AppButton variant="secondary" :disabled="!dirty || saving" @click="resetForm">
                撤销改动
              </AppButton>
              <span v-if="dirty" class="form__dirty mono">有未保存的改动</span>
            </div>
          </form>
        </AppCard>
      </div>

      <div class="span-4">
        <div class="side">
          <AppCard tab="照片" title="头像" accent="amber">
            <div class="avatar">
              <div class="avatar__frame">
                <img v-if="avatarUrl" :src="avatarUrl" alt="当前头像" class="avatar__img" />
                <span v-else class="avatar__letter" aria-hidden="true">{{ initial }}</span>
              </div>

              <p class="avatar__hint">
                建议上传正方形图片，裁剪后会统一输出 512×512 的 JPEG。
              </p>

              <p v-if="avatarError" class="avatar__error" role="alert">{{ avatarError }}</p>

              <div class="avatar__actions">
                <AppButton size="sm" variant="primary" :loading="avatarBusy" @click="pickAvatar">
                  {{ avatarUrl ? '更换头像' : '上传头像' }}
                </AppButton>
                <AppButton v-if="avatarUrl" size="sm" variant="danger" :disabled="avatarBusy" @click="removeAvatar">
                  移除
                </AppButton>
              </div>

              <input
                ref="fileInput"
                class="visually-hidden"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                @change="onFileChange"
              />
            </div>
          </AppCard>

          <AppCard tab="底册" title="账号档案" accent="none">
            <div class="ledger ledger--compact">
              <div class="ledger__row">
                <span class="ledger__label">用户 ID</span>
                <span class="ledger__value mono">{{ auth.user?.id ?? '—' }}</span>
              </div>
              <div class="ledger__row">
                <span class="ledger__label">用户名</span>
                <span class="ledger__value mono">{{ auth.user?.username ?? '—' }}</span>
              </div>
              <div class="ledger__row">
                <span class="ledger__label">状态</span>
                <span class="ledger__value">
                  <AppBadge :status="auth.user?.status" />
                </span>
              </div>
              <div class="ledger__row">
                <span class="ledger__label">角色</span>
                <span class="ledger__value mono">{{ auth.user?.roles?.join(' / ') || '—' }}</span>
              </div>
              <div class="ledger__row">
                <span class="ledger__label">建册时间</span>
                <span class="ledger__value mono">{{ formatDateTime(auth.user?.createdAt) }}</span>
              </div>
              <div class="ledger__row">
                <span class="ledger__label">最近登录</span>
                <span class="ledger__value mono">
                  {{ formatRelative(auth.user?.lastLoginAt) }}
                  <span class="faint"> · {{ formatDateTime(auth.user?.lastLoginAt) }}</span>
                </span>
              </div>
            </div>
          </AppCard>
        </div>
      </div>
    </div>

    <AvatarCropper
      v-model:open="cropperOpen"
      :src="cropperSrc"
      @cropped="onCropped"
      @cancel="onCropCancel"
      @error="onCropError"
    />
  </div>
</template>

<style scoped>
.page-head__meta {
  font-size: var(--text-sm);
  color: var(--ink-faint);
}

.side {
  display: grid;
  gap: var(--grid-gap);
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

.form__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: var(--border-hair);
}

.form__dirty {
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  color: var(--amber-ink);
}

.avatar {
  display: grid;
  gap: var(--sp-3);
  justify-items: start;
}

.avatar__frame {
  display: grid;
  place-items: center;
  width: 112px;
  height: 112px;
  border: var(--border-strong);
  border-radius: var(--radius-sm);
  border-top: var(--border-accent) solid var(--amber);
  background: var(--paper-sunken);
  overflow: hidden;
}

.avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar__letter {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  color: var(--ink-muted);
}

.avatar__hint {
  font-size: var(--text-sm);
  color: var(--ink-faint);
  max-width: 30ch;
}

.avatar__error {
  font-size: var(--text-sm);
  color: var(--danger);
}

.avatar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

@media (max-width: 1024px) {
  .form__row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
