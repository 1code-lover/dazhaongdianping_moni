import request from './request'

// 商户类型列表
export const getShopTypes = () => {
  return request.get('/shop-type/list')
}

// 商户详情
export const getShopById = (id) => {
  return request.get(`/shop/${id}`)
}

// 商户搜索
export const searchShops = (keyword, current = 1, size = 10) => {
  return request.get('/shop/search', { params: { keyword, current, size } })
}

// 按类型查询商户
export const getShopsByType = (typeId, current = 1) => {
  return request.get('/shop/of/type', { params: { typeId, current } })
}
