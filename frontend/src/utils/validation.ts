/**
 * 表单校验规则（自己写，不引 vee-validate / zod）。
 * 每个规则返回 null 表示通过，否则返回给用户看的中文错误串。
 */

export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,20}$/
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[A-Za-z]{2,}$/
export const PHONE_PATTERN = /^1[3-9]\d{9}$/

export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 64

export function validateRequired(value: unknown, label = '该项'): string | null {
  if (value === null || value === undefined) return `${label}不能为空`
  if (typeof value === 'string' && value.trim() === '') return `${label}不能为空`
  return null
}

/** 用户名：3–20 位字母、数字或下划线 */
export function validateUsername(value: string): string | null {
  const required = validateRequired(value, '用户名')
  if (required) return required
  if (!USERNAME_PATTERN.test(value)) {
    return '用户名需为 3–20 位字母、数字或下划线'
  }
  return null
}

/** 邮箱格式 */
export function validateEmail(value: string): string | null {
  const required = validateRequired(value, '邮箱')
  if (required) return required
  if (value.length > 120) return '邮箱过长'
  if (!EMAIL_PATTERN.test(value)) return '邮箱格式不正确'
  return null
}

/** 密码：8–64 位，且同时包含字母和数字，不允许空白字符 */
export function validatePassword(value: string): string | null {
  const required = validateRequired(value, '密码')
  if (required) return required
  if (value.length < PASSWORD_MIN || value.length > PASSWORD_MAX) {
    return `密码需为 ${PASSWORD_MIN}–${PASSWORD_MAX} 位`
  }
  if (/\s/.test(value)) return '密码不能包含空格'
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return '密码需同时包含字母和数字'
  }
  return null
}

/** 两次密码一致 */
export function validateConfirmPassword(password: string, confirm: string): string | null {
  const required = validateRequired(confirm, '确认密码')
  if (required) return required
  if (password !== confirm) return '两次输入的密码不一致'
  return null
}

/** 昵称：可空，最长 20 字符 */
export function validateNickname(value: string): string | null {
  if (!value) return null
  if (value.trim().length > 20) return '昵称最长 20 个字符'
  return null
}

/** 手机号：可空，填了就按中国大陆手机号校验 */
export function validatePhone(value: string): string | null {
  if (!value) return null
  if (!PHONE_PATTERN.test(value)) return '手机号格式不正确'
  return null
}

/** 生日：可空，不能晚于今天 */
export function validateBirthday(value: string, today = new Date()): string | null {
  if (!value) return null
  const parsed = new Date(`${value}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) return '生日格式不正确'
  const limit = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  if (parsed.getTime() > limit.getTime()) return '生日不能晚于今天'
  return null
}

/** 个人简介：最长 200 字符 */
export function validateBio(value: string): string | null {
  if (!value) return null
  if (value.length > 200) return '简介最长 200 个字符'
  return null
}

/** 从字段错误表里取第一条，用作汇总提示 */
export function firstFieldError(fields: Record<string, string> | null | undefined): string | null {
  if (!fields) return null
  const keys = Object.keys(fields)
  return keys.length > 0 ? (fields[keys[0] as string] ?? null) : null
}
