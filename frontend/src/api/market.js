import http from './http'

/**
 * 集市（二手闲置）。
 *
 * 全部接口都需要登录：浏览也要求登录是本项目的统一策略（见后端 MarketController），
 * 集市不做例外，避免出现匿名可枚举全站用户的入口。
 * 状态取值与中文名都由后端下发（statusLabel），前端不再维护一份映射表。
 */

/** 商品列表。status 不传时后端只返回在售商品（集市首页应有的默认视角）。 */
export function fetchGoodsList(params = {}) {
  return http.get('/market/goods/list', { params })
}

/** 我发布的商品，含已售出与已下架，按发布时间倒序。 */
export function fetchMyGoods(params = {}) {
  return http.get('/market/goods/mine', { params })
}

/** 商品详情。后端会累加一次浏览量，用于给卖家反馈关注度。 */
export function fetchGoodsDetail(id) {
  return http.get(`/market/goods/${id}`)
}

/** 发布商品。title/price 必填，originalPrice、category、images（≤9 张）可选。 */
export function createGoods(data) {
  return http.post('/market/goods', data)
}

/** 编辑商品。images 传 null 表示本次不改图，传空数组表示清空配图。 */
export function updateGoods(id, data) {
  return http.put(`/market/goods/${id}`, data)
}

/** 改商品状态：ON_SALE 在售 / RESERVED 已预定 / SOLD 已售出 / OFF 已下架。 */
export function changeGoodsStatus(id, status) {
  return http.post(`/market/goods/${id}/status`, { status })
}

/** 删除商品。仅卖家或管理员可删除。 */
export function deleteGoods(id) {
  return http.delete(`/market/goods/${id}`)
}
