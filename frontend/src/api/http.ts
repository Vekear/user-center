import axios from 'axios'
import type {
  AxiosError,
  AxiosInstance,
  AxiosRequestConfig,
  InternalAxiosRequestConfig,
} from 'axios'
import type { ApiEnvelope, FieldErrors } from './types'

/* ============================================================================
   http.ts — axios 实例 + 统一信封拆包 + 401 单飞刷新
   规则：
   1. accessToken 只存在于内存（Pinia），这里通过 bridge 读取，不落 localStorage；
   2. 并发 401 只发一次 refresh，其余请求等同一个 Promise；
   3. refresh 失败 → onAuthFailure()（清登录态并跳登录），原始请求以原错误 reject。
   ========================================================================== */

const BASE_URL = import.meta.env.VITE_API_BASE ?? '/api'
const RETRY_FLAG = '_dshRetried' as const

type RetriableConfig = InternalAxiosRequestConfig & { [RETRY_FLAG]?: boolean }

export interface ApiErrorOptions {
  code: number
  status: number
  fields?: FieldErrors | null
  traceId?: string | null
  cause?: unknown
}

export class ApiError extends Error {
  readonly code: number
  readonly status: number
  readonly fields: FieldErrors | null
  readonly traceId: string | null

  constructor(message: string, options: ApiErrorOptions) {
    super(message, options.cause === undefined ? undefined : { cause: options.cause })
    this.name = 'ApiError'
    this.code = options.code
    this.status = options.status
    this.fields = options.fields ?? null
    this.traceId = options.traceId ?? null
  }

  /** HTTP 400：data 是字段错误表 */
  get isValidation(): boolean {
    return this.status === 400
  }
  get isUnauthorized(): boolean {
    return this.status === 401
  }
  get isForbidden(): boolean {
    return this.status === 403
  }
  get isConflict(): boolean {
    return this.status === 409
  }
  get isLocked(): boolean {
    return this.status === 429
  }
  get isNetwork(): boolean {
    return this.status === 0
  }
}

/** 认证桥：由 auth store 注册，避免 http ↔ store 的循环依赖 */
export interface AuthBridge {
  getAccessToken: () => string | null
  refresh: () => Promise<string | null>
  onAuthFailure: (reason?: unknown) => void
}

const defaultBridge: AuthBridge = {
  getAccessToken: () => null,
  refresh: async () => null,
  onAuthFailure: () => undefined,
}

let bridge: AuthBridge = { ...defaultBridge }
let refreshPromise: Promise<string | null> | null = null

export function configureHttp(partial: Partial<AuthBridge>): void {
  bridge = { ...bridge, ...partial }
}

/** 测试用：还原桥接与单飞状态 */
export function resetHttpBridge(): void {
  bridge = { ...defaultBridge }
  refreshPromise = null
}

/** 没有拦截器的裸实例：只用于 /auth/refresh，避免刷新请求自己触发刷新 */
export const rawHttp: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
  timeout: 20000,
})

export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
  timeout: 20000,
  headers: { Accept: 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = bridge.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function statusMessage(status: number): string {
  switch (status) {
    case 0:
      return '无法连接服务，请确认后端已在 http://localhost:8080 启动'
    case 400:
      return '提交的内容没有通过校验'
    case 401:
      return '登录状态已失效，请重新登录'
    case 403:
      return '当前账号没有访问权限'
    case 404:
      return '请求的资源不存在'
    case 409:
      return '与已有数据冲突'
    case 429:
      return '尝试过于频繁，请稍后再试'
    case 500:
      return '服务端出错了，请稍后再试'
    default:
      return `请求失败（HTTP ${status}）`
  }
}

/** 任意异常 → ApiError */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error

  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<Partial<ApiEnvelope<unknown>>>
    const status = axiosError.response?.status ?? 0
    const body = axiosError.response?.data
    const message =
      (typeof body?.message === 'string' && body.message.trim()) ||
      (status === 0 ? statusMessage(0) : statusMessage(status))

    let fields: FieldErrors | null = null
    if (status === 400 && body?.data && typeof body.data === 'object' && !Array.isArray(body.data)) {
      fields = body.data as FieldErrors
    }

    return new ApiError(message, {
      code: typeof body?.code === 'number' ? body.code : status,
      status,
      fields,
      traceId: typeof body?.traceId === 'string' ? body.traceId : null,
      cause: axiosError,
    })
  }

  if (error instanceof Error) {
    return new ApiError(error.message || '未知错误', { code: -1, status: 0, cause: error })
  }
  return new ApiError('未知错误', { code: -1, status: 0, cause: error })
}

/** 拆信封：code === 0 取 data，否则按业务错误抛出 */
function unwrap<T>(body: ApiEnvelope<T> | T): T {
  if (body && typeof body === 'object' && 'code' in (body as Record<string, unknown>)) {
    const envelope = body as ApiEnvelope<T>
    if (envelope.code !== 0) {
      throw new ApiError(envelope.message || '请求失败', {
        code: envelope.code,
        status: 200,
        traceId: envelope.traceId ?? null,
        fields: null,
      })
    }
    return envelope.data
  }
  return body as T
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined
    const status = error.response?.status
    const url = config?.url ?? ''

    // 非 401 / 已重试过 / 刷新接口自身 → 直接以 ApiError 抛出
    if (!config || status !== 401 || config[RETRY_FLAG] || url.includes('/auth/refresh')) {
      return Promise.reject(toApiError(error))
    }

    config[RETRY_FLAG] = true

    // 单飞：并发 401 共用同一个刷新 Promise
    if (!refreshPromise) {
      refreshPromise = Promise.resolve()
        .then(() => bridge.refresh())
        .finally(() => {
          refreshPromise = null
        })
    }

    let token: string | null = null
    try {
      token = await refreshPromise
    } catch (refreshError) {
      bridge.onAuthFailure(refreshError)
      return Promise.reject(toApiError(error))
    }

    if (!token) {
      bridge.onAuthFailure(error)
      return Promise.reject(toApiError(error))
    }

    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
    return http.request(config)
  },
)

/** 统一的请求入口：返回拆包后的 data */
export async function apiRequest<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiEnvelope<T>>(config)
    return unwrap<T>(response.data)
  } catch (error) {
    throw toApiError(error)
  }
}

/** 走裸实例（无拦截器）的请求入口：只给 /auth/refresh 用，避免刷新自触发 */
export async function rawRequest<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await rawHttp.request<ApiEnvelope<T>>(config)
    return unwrap<T>(response.data)
  } catch (error) {
    throw toApiError(error)
  }
}

export const api = {
  get: <T>(url: string, params?: unknown) => apiRequest<T>({ url, method: 'get', params }),
  post: <T>(url: string, data?: unknown) => apiRequest<T>({ url, method: 'post', data }),
  put: <T>(url: string, data?: unknown) => apiRequest<T>({ url, method: 'put', data }),
  patch: <T>(url: string, data?: unknown) => apiRequest<T>({ url, method: 'patch', data }),
  delete: <T>(url: string, data?: unknown) => apiRequest<T>({ url, method: 'delete', data }),
}

/** multipart 上传：不改 Content-Type，交给浏览器带 boundary */
export function uploadFile<T>(url: string, file: Blob, field = 'file', filename = 'avatar.jpg'): Promise<T> {
  const form = new FormData()
  form.append(field, file, filename)
  return apiRequest<T>({ url, method: 'post', data: form })
}

export default http
