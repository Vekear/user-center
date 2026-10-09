<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppModal from '@/components/AppModal.vue'
import AppButton from '@/components/AppButton.vue'
import AppInput from '@/components/AppInput.vue'
import AppField from '@/components/AppField.vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const password = ref('')

const open = computed(() => ui.confirmState !== null)
const state = computed(() => ui.confirmState)

watch(open, (value) => {
  if (value) password.value = ''
})

function close(): void {
  ui.closeConfirm()
}

async function confirm(): Promise<void> {
  await ui.runConfirm(password.value)
}
</script>

<template>
  <AppModal :open="open" :title="state?.title ?? ''" size="sm" @close="close">
    <div class="confirm">
      <p v-if="state?.message" class="confirm__message">{{ state.message }}</p>

      <AppField
        v-if="state?.requirePassword"
        label="账号密码"
        field-id="confirm-password"
        hint="这是不可撤销的操作，需要再次确认身份"
      >
        <AppInput
          id="confirm-password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          :invalid="Boolean(ui.confirmError)"
          @enter="confirm"
        />
      </AppField>

      <p v-if="ui.confirmError" class="confirm__error" role="alert">{{ ui.confirmError }}</p>
    </div>

    <template #footer>
      <AppButton variant="secondary" :disabled="ui.confirmBusy" @click="close">
        {{ state?.cancelText ?? '取消' }}
      </AppButton>
      <AppButton
        :variant="state?.danger ? 'danger' : 'primary'"
        :loading="ui.confirmBusy"
        @click="confirm"
      >
        {{ state?.confirmText ?? '确认' }}
      </AppButton>
    </template>
  </AppModal>
</template>

<style scoped>
.confirm {
  display: grid;
  gap: var(--sp-4);
}

.confirm__message {
  color: var(--ink-muted);
  line-height: var(--leading-normal);
}

.confirm__error {
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
