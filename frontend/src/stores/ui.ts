import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
  detail?: string
}

export interface ConfirmOptions {
  title: string
  message?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  /** 需要用户输入的确认（例如注销账号要输密码） */
  requirePassword?: boolean
  onConfirm: (password?: string) => void | Promise<void>
}

let toastSeed = 0

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<ToastItem[]>([])
  const confirmState = ref<ConfirmOptions | null>(null)
  const confirmBusy = ref(false)
  const confirmError = ref<string | null>(null)

  function pushToast(type: ToastType, message: string, detail?: string): number {
    const id = ++toastSeed
    toasts.value = [...toasts.value, { id, type, message, detail }]
    if (type !== 'error') {
      window.setTimeout(() => dismissToast(id), 4500)
    }
    return id
  }

  function dismissToast(id: number): void {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  function clearToasts(): void {
    toasts.value = []
  }

  function askConfirm(options: ConfirmOptions): void {
    confirmError.value = null
    confirmBusy.value = false
    confirmState.value = options
  }

  function closeConfirm(): void {
    if (confirmBusy.value) return
    confirmState.value = null
    confirmError.value = null
  }

  async function runConfirm(password?: string): Promise<void> {
    const options = confirmState.value
    if (!options) return
    confirmBusy.value = true
    confirmError.value = null
    try {
      await options.onConfirm(password)
      confirmState.value = null
    } catch (error) {
      confirmError.value = error instanceof Error ? error.message : '操作失败'
    } finally {
      confirmBusy.value = false
    }
  }

  return {
    toasts,
    confirmState,
    confirmBusy,
    confirmError,
    pushToast,
    dismissToast,
    clearToasts,
    askConfirm,
    closeConfirm,
    runConfirm,
  }
})
