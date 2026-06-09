import request from './request'

// 创建订单
export const createOrder = (orderType, bizId, quantity = 1) => {
  return request.post('/order', null, { params: { orderType, bizId, quantity } })
}

// 支付订单
export const payOrder = (orderId) => {
  return request.post(`/order/${orderId}/pay`)
}

// 取消订单
export const cancelOrder = (orderId) => {
  return request.post(`/order/${orderId}/cancel`)
}

// 我的订单
export const getMyOrders = (status, current = 1, size = 10) => {
  return request.get('/order/list', { params: { status, current, size } })
}

// 订单详情
export const getOrderById = (orderId) => {
  return request.get(`/order/${orderId}`)
}
