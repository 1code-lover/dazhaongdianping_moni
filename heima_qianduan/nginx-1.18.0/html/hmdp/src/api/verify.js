import request from './request'

// 核销订单
export const verifyOrder = (verifyCode) => {
  return request.post('/verify', null, { params: { verifyCode } })
}

// 验证核销码
export const checkVerifyCode = (verifyCode) => {
  return request.get(`/verify/check/${verifyCode}`)
}

// 核销记录
export const getVerifyRecords = (shopId, current = 1, size = 10) => {
  return request.get('/verify/record', { params: { shopId, current, size } })
}
