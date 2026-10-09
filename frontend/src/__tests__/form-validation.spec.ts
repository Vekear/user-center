import { describe, expect, it } from 'vitest'
import {
  firstFieldError,
  validateBio,
  validateBirthday,
  validateConfirmPassword,
  validateEmail,
  validateNickname,
  validatePassword,
  validatePhone,
  validateUsername,
} from '@/utils/validation'

describe('表单校验规则', () => {
  describe('用户名：3–20 位字母数字下划线', () => {
    it('接受合法用户名', () => {
      expect(validateUsername('ada_99')).toBeNull()
      expect(validateUsername('ABC')).toBeNull()
      expect(validateUsername('a1b2c3d4e5f6g7h8i9j0')).toBeNull()
    })

    it('拒绝空、过短、过长与非法字符', () => {
      expect(validateUsername('')).toBe('用户名不能为空')
      expect(validateUsername('ab')).toMatch(/3–20/)
      expect(validateUsername('a'.repeat(21))).toMatch(/3–20/)
      expect(validateUsername('ada lovelace')).toMatch(/3–20/)
      expect(validateUsername('中文名')).toMatch(/3–20/)
      expect(validateUsername('ada@example.com')).toMatch(/3–20/)
    })
  })

  describe('邮箱格式', () => {
    it('接受常见邮箱', () => {
      expect(validateEmail('ada@example.com')).toBeNull()
      expect(validateEmail('a.b+tag@sub.example.cn')).toBeNull()
    })

    it('拒绝缺失或不合法结构', () => {
      expect(validateEmail('')).toBe('邮箱不能为空')
      expect(validateEmail('ada')).toBe('邮箱格式不正确')
      expect(validateEmail('ada@')).toBe('邮箱格式不正确')
      expect(validateEmail('ada@example')).toBe('邮箱格式不正确')
      expect(validateEmail('ada @example.com')).toBe('邮箱格式不正确')
    })
  })

  describe('密码策略：8–64 位且同时含字母和数字', () => {
    it('接受合规密码', () => {
      expect(validatePassword('abc12345')).toBeNull()
      expect(validatePassword('P@ssw0rd!')).toBeNull()
      expect(validatePassword(`a1${'x'.repeat(62)}`)).toBeNull()
    })

    it('拒绝过短、过长、纯字母、纯数字与含空格', () => {
      expect(validatePassword('')).toBe('密码不能为空')
      expect(validatePassword('a1b2c3')).toMatch(/8–64/)
      expect(validatePassword(`a1${'x'.repeat(63)}`)).toMatch(/8–64/)
      expect(validatePassword('abcdefgh')).toBe('密码需同时包含字母和数字')
      expect(validatePassword('12345678')).toBe('密码需同时包含字母和数字')
      expect(validatePassword('abc 1234')).toBe('密码不能包含空格')
    })
  })

  describe('确认密码一致', () => {
    it('一致时通过', () => {
      expect(validateConfirmPassword('abc12345', 'abc12345')).toBeNull()
    })

    it('空值与不一致都要报错', () => {
      expect(validateConfirmPassword('abc12345', '')).toBe('确认密码不能为空')
      expect(validateConfirmPassword('abc12345', 'abc12346')).toBe('两次输入的密码不一致')
    })
  })

  describe('可选字段', () => {
    it('昵称可空但不能超长', () => {
      expect(validateNickname('')).toBeNull()
      expect(validateNickname('Ada')).toBeNull()
      expect(validateNickname('名'.repeat(21))).toBe('昵称最长 20 个字符')
    })

    it('手机号可空，填了必须合法', () => {
      expect(validatePhone('')).toBeNull()
      expect(validatePhone('13800138000')).toBeNull()
      expect(validatePhone('12345')).toBe('手机号格式不正确')
      expect(validatePhone('23800138000')).toBe('手机号格式不正确')
    })

    it('生日不能晚于今天', () => {
      const today = new Date(2026, 0, 15)
      expect(validateBirthday('', today)).toBeNull()
      expect(validateBirthday('1990-05-20', today)).toBeNull()
      expect(validateBirthday('2026-01-16', today)).toBe('生日不能晚于今天')
      expect(validateBirthday('not-a-date')).toBe('生日格式不正确')
    })

    it('简介最长 200 字', () => {
      expect(validateBio('')).toBeNull()
      expect(validateBio('x'.repeat(200))).toBeNull()
      expect(validateBio('x'.repeat(201))).toBe('简介最长 200 个字符')
    })
  })

  describe('后端字段错误表取首条', () => {
    it('把 400 的 data 变成一句汇总提示', () => {
      expect(firstFieldError({ email: '邮箱已存在' })).toBe('邮箱已存在')
      expect(firstFieldError({})).toBeNull()
      expect(firstFieldError(null)).toBeNull()
    })
  })
})
