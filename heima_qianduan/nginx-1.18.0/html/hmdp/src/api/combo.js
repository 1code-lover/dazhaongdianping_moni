import request from './request'

// 商户套餐列表
export const getCombosByShop = (shopId, current = 1, size = 10) => {
  return request.get(`/combo/list/${shopId}`, { params: { current, size } })
}

// 套餐详情
export const getComboById = (id) => {
  return request.get(`/combo/${id}`)
}
