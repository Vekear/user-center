import { useUiStore } from '@/stores/ui'
import type { ToastType } from '@/stores/ui'

/** 全局提示：任何组件里 useToast().success('已保存') */
export function useToast() {
  const ui = useUiStore()

  function push(type: ToastType, message: string, detail?: string): number {
    return ui.pushToast(type, message, detail)
  }

  return {
    push,
    success: (message: string, detail?: string) => push('success', message, detail),
    error: (message: string, detail?: string) => push('error', message, detail),
    info: (message: string, detail?: string) => push('info', message, detail),
    dismiss: (id: number) => ui.dismissToast(id),
    clear: () => ui.clearToasts(),
  }
}
