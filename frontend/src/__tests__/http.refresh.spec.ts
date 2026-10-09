import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { configureHttp, http, resetHttpBridge } from '@/api/http'

/**
 * 401 单飞刷新：并发两个请求同时拿到 401 时，refresh 只能被调用一次，
 * 两个请求各自用新 token 重放成功。
 */

const originalAdapter = http.defaults.adapter

function unauthorized(config: InternalAxiosRequestConfig): AxiosError {
  const response = {
    data: { code: 401, message: '登录状态已失效', data: null },
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
  } as AxiosResponse
  return new AxiosError('Request failed with status code 401', 'ERR_BAD_REQUEST', config, null, response)
}

function headerOf(config: InternalAxiosRequestConfig): string {
  const headers = config.headers as unknown as Record<string, unknown> & {
    get?: (key: string) => unknown
  }
  const value = headers.Authorization ?? headers.get?.('Authorization')
  return typeof value === 'string' ? value : ''
}

describe('http 拦截器：401 单飞刷新', () => {
  let seen: string[]

  beforeEach(() => {
    seen = []
    resetHttpBridge()
    http.defaults.adapter = async (config) => {
      const url = config.url ?? ''
      const authorization = headerOf(config as InternalAxiosRequestConfig)
      seen.push(`${url} ${authorization}`)
      if (!authorization.startsWith('Bearer new-')) {
        throw unauthorized(config as InternalAxiosRequestConfig)
      }
      return {
        data: { code: 0, message: 'ok', data: { url } },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      } as AxiosResponse
    }
  })

  afterEach(() => {
    resetHttpBridge()
  })

  afterAll(() => {
    http.defaults.adapter = originalAdapter
  })

  it('并发 401 只调用一次 refresh，并且两个请求都重放成功', async () => {
    const refresh = vi.fn(async () => {
      await new Promise((resolve) => setTimeout(resolve, 5))
      return 'new-token'
    })
    const onAuthFailure = vi.fn()

    configureHttp({ getAccessToken: () => null, refresh, onAuthFailure })

    const [first, second] = await Promise.all([http.get('/a'), http.get('/b')])

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(onAuthFailure).not.toHaveBeenCalled()
    expect((first.data as { data: { url: string } }).data.url).toBe('/a')
    expect((second.data as { data: { url: string } }).data.url).toBe('/b')

    const retried = seen.filter((entry) => entry.endsWith('Bearer new-token'))
    expect(retried).toHaveLength(2)
  })

  it('refresh 返回 null 时判定为会话失效：调用 onAuthFailure，原请求以 401 错误 reject', async () => {
    const onAuthFailure = vi.fn()
    configureHttp({
      getAccessToken: () => null,
      refresh: vi.fn(async () => null),
      onAuthFailure,
    })

    await expect(http.get('/a')).rejects.toMatchObject({ status: 401 })
    expect(onAuthFailure).toHaveBeenCalledTimes(1)
  })

  it('refresh 抛错时同样登出，且不会无限重试', async () => {
    const onAuthFailure = vi.fn()
    const refresh = vi.fn(async () => {
      throw new Error('refresh failed')
    })
    configureHttp({ getAccessToken: () => null, refresh, onAuthFailure })

    await expect(http.get('/a')).rejects.toMatchObject({ status: 401 })
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(onAuthFailure).toHaveBeenCalledTimes(1)
  })

  it('请求会带上内存里的 accessToken', async () => {
    configureHttp({
      getAccessToken: () => 'new-initial',
      refresh: vi.fn(async () => 'new-refreshed'),
      onAuthFailure: vi.fn(),
    })

    await http.get('/c')
    expect(seen).toEqual(['/c Bearer new-initial'])
  })
})
