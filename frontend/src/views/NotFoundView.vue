<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import AppButton from '@/components/AppButton.vue'

const props = withDefaults(defineProps<{ code?: number }>(), { code: 404 })

const copy = computed(() => {
  if (props.code === 403) {
    return {
      tab: 'restricted',
      title: '这一册不对你开放',
      description: '管理端只有管理员角色可以进入。如果你是管理员，请重新登录以刷新角色。',
    }
  }
  return {
    tab: 'missing',
    title: '这一页不在册',
    description: '地址可能已经改动，或者这个档案编号从来没有存在过。',
  }
})
</script>

<template>
  <div class="notfound">
    <div class="notfound__card">
      <p class="notfound__tab">{{ copy.tab }}</p>
      <p class="notfound__code mono">{{ code }}</p>
      <h1 class="notfound__title">{{ copy.title }}</h1>
      <p class="notfound__desc">{{ copy.description }}</p>
      <div class="notfound__actions">
        <RouterLink to="/profile">
          <AppButton variant="primary">回到个人资料</AppButton>
        </RouterLink>
        <RouterLink v-if="code === 403" to="/login">
          <AppButton variant="secondary">重新登录</AppButton>
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notfound {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: var(--sp-8) var(--page-pad-x);
  background: var(--paper);
}

.notfound__card {
  display: grid;
  gap: var(--sp-3);
  justify-items: start;
  width: min(560px, 100%);
  padding: var(--sp-7);
  background: var(--paper-raised);
  border: var(--border-hair);
  border-radius: var(--radius-sm);
  border-top: var(--border-accent) solid var(--amber);
}

.notfound__tab {
  padding: 2px 8px;
  border: var(--border-hair);
  border-left: var(--border-accent) solid var(--ink-faint);
  border-radius: var(--radius-xs);
  background: var(--paper-tint);
  font-size: var(--text-2xs);
  font-weight: var(--fw-semibold);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--ink-muted);
}

.notfound__code {
  font-size: var(--text-3xl);
  line-height: 1;
  color: var(--ink);
}

.notfound__title {
  font-size: var(--text-xl);
}

.notfound__desc {
  color: var(--ink-muted);
  max-width: 46ch;
}

.notfound__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

.notfound__actions a {
  text-decoration: none;
}
</style>
