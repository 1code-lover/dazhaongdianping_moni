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

// 按类型查询商户（支持携带坐标做距离排序）
export const getShopsByType = (typeId, current = 1, x = null, y = null) => {
  const params = { typeId, current }
  if (x != null && y != null) {
    params.x = x
    params.y = y
  }
  return request.get('/shop/of/type', { params })
}
