import request from './request'

// 发送验证码
export const sendCode = (phone) => {
  return request.post('/user/code', null, { params: { phone } })
}

// 登录
export const login = (phone, code) => {
  return request.post('/user/login', { phone, code })
}

// 获取用户信息
export const getUserInfo = () => {
  return request.get('/user/me')
}
