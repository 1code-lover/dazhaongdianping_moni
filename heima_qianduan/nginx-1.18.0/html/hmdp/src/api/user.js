import request from './request'

// 发送验证码（target 支持手机号或邮箱）
export const sendCode = (target) => {
  return request.post('/user/code', null, { params: { target } })
}

// 登录（target 支持手机号或邮箱）
export const login = (target, code) => {
  return request.post('/user/login', { target, code })
}

// 获取用户信息
export const getUserInfo = () => {
  return request.get('/user/me')
}
