import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { nextTick } from 'vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { useNotificationsStore } from '@/stores/notifications'

const routes = [
  { path: '/', component: { template: '<div />' } },
  { path: '/profile', component: { template: '<div />' } },
  { path: '/security', component: { template: '<div />' } },
  { path: '/notifications', component: { template: '<div />' } },
  { path: '/admin/users', component: { template: '<div />' } },
  { path: '/admin/audit', component: { template: '<div />' } },
  { path: '/admin/notify', component: { template: '<div />' } },
]

async function mountLayout() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/profile')
  await router.isReady()
  const wrapper = mount(AppLayout, { global: { plugins: [pinia, router] } })
  return { wrapper, pinia }
}

describe('通知未读徽标', () => {
  it('没有未读时不渲染徽标', async () => {
    const { wrapper } = await mountLayout()
    const store = useNotificationsStore()
    store.unreadCount = 0
    await nextTick()

    expect(wrapper.find('[data-test="notify-badge"]').exists()).toBe(false)
  })

  it('有未读时渲染数量并带上无障碍标签', async () => {
    const { wrapper } = await mountLayout()
    const store = useNotificationsStore()

    store.unreadCount = 5
    await nextTick()

    const badge = wrapper.find('[data-test="notify-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('5')
    expect(badge.attributes('aria-label')).toBe('5 条未读通知')
  })

  it('未读超过 99 时显示 99+', async () => {
    const { wrapper } = await mountLayout()
    const store = useNotificationsStore()

    store.unreadCount = 150
    await nextTick()

    expect(wrapper.find('[data-test="notify-badge"]').text()).toBe('99+')
  })

  it('读完归零后徽标消失', async () => {
    const { wrapper } = await mountLayout()
    const store = useNotificationsStore()

    store.unreadCount = 3
    await nextTick()
    expect(wrapper.find('[data-test="notify-badge"]').exists()).toBe(true)

    store.unreadCount = 0
    await nextTick()
    expect(wrapper.find('[data-test="notify-badge"]').exists()).toBe(false)
  })
})
