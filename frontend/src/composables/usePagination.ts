import { computed, reactive, ref } from 'vue'

export const MAX_PAGE_SIZE = 100

export interface UsePaginationOptions {
  page?: number
  size?: number
  /** 页码或每页条数变化时回调（通常是重新拉列表） */
  onChange?: (page: number, size: number) => void
}

/**
 * 返回 reactive 对象（内部 ref 自动解包），模板里直接写 pager.page，
 * 视图与 AppPagination 共用同一个实例，分页逻辑只有这一处。
 */
export interface UsePagination {
  page: number
  size: number
  total: number
  totalPages: number
  pages: Array<number | 'gap'>
  setPage: (value: number) => void
  setSize: (value: number) => void
  setTotal: (value: number) => void
  next: () => void
  prev: () => void
  reset: () => void
}

export function clampPageSize(value: number): number {
  const numeric = Number.isFinite(value) ? Math.trunc(value) : 10
  return Math.min(MAX_PAGE_SIZE, Math.max(1, numeric))
}

export function usePagination(options: UsePaginationOptions = {}): UsePagination {
  const page = ref(Math.max(1, Math.trunc(options.page ?? 1)))
  const size = ref(clampPageSize(options.size ?? 10))
  const total = ref(0)

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

  /** 最多 7 个槽位的页码窗口，中间用 gap 占位 */
  const pages = computed<Array<number | 'gap'>>(() => {
    const last = totalPages.value
    const current = Math.min(Math.max(1, page.value), last)
    if (last <= 7) {
      return Array.from({ length: last }, (_, index) => index + 1)
    }
    const result: Array<number | 'gap'> = [1]
    const start = Math.max(2, current - 1)
    const end = Math.min(last - 1, current + 1)
    if (start > 2) result.push('gap')
    for (let index = start; index <= end; index += 1) result.push(index)
    if (end < last - 1) result.push('gap')
    result.push(last)
    return result
  })

  function emitChange(): void {
    options.onChange?.(page.value, size.value)
  }

  function setPage(value: number): void {
    const next = Math.min(Math.max(1, Math.trunc(value) || 1), totalPages.value)
    if (next === page.value) return
    page.value = next
    emitChange()
  }

  function setSize(value: number): void {
    const next = clampPageSize(value)
    if (next === size.value) return
    size.value = next
    page.value = 1
    emitChange()
  }

  function setTotal(value: number): void {
    total.value = Math.max(0, Math.trunc(value) || 0)
    if (page.value > totalPages.value) {
      page.value = totalPages.value
    }
  }

  function next(): void {
    setPage(page.value + 1)
  }

  function prev(): void {
    setPage(page.value - 1)
  }

  function reset(): void {
    page.value = 1
    total.value = 0
  }

  return reactive({
    page,
    size,
    total,
    totalPages,
    pages,
    setPage,
    setSize,
    setTotal,
    next,
    prev,
    reset,
  }) as UsePagination
}
