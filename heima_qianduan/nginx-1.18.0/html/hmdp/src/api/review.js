/**
 * 评价模块 API 封装
 * 提供评价相关的接口调用
 *
 * @author ethan
 * @date 2026-07-01
 */
import request from './request'

/**
 * 提交评价
 * @param {Object} data - 评价数据
 * @param {number} data.orderId - 订单ID
 * @param {number} data.orderType - 订单类型：1优惠券 2套餐
 * @param {number} data.score - 评分：1-5星
 * @param {string} data.content - 评价内容（不超过500字）
 * @param {string} data.images - 评价图片（最多9张，逗号分隔）
 * @returns {Promise}
 */
export const submitReview = (data) => {
  return request({
    url: '/review',
    method: 'POST',
    data
  })
}

/**
 * 查看单条评价详情
 * @param {number} id - 评价ID
 * @returns {Promise}
 */
export const getReviewById = (id) => {
  return request({
    url: `/review/${id}`,
    method: 'GET'
  })
}

/**
 * 我的评价列表
 * @param {number} current - 当前页
 * @param {number} size - 每页大小
 * @returns {Promise}
 */
export const getMyReviews = (current = 1, size = 10) => {
  return request({
    url: '/review/my',
    method: 'GET',
    params: { current, size }
  })
}

/**
 * 商户评价列表
 * @param {number} shopId - 商户ID
 * @param {number} current - 当前页
 * @param {number} size - 每页大小
 * @returns {Promise}
 */
export const getShopReviews = (shopId, current = 1, size = 10) => {
  return request({
    url: `/review/shop/${shopId}`,
    method: 'GET',
    params: { current, size }
  })
}
