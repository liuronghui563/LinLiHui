import http from './http'

/**
 * 上门回收。
 *
 * 所有回收接口（含 GET /recycle/categories）都要求登录：
 * 前端 /recycle 路由是 requiresAuth，页面本身就进不去，
 * 因此后端没有为 /categories 单独开匿名白名单（见 community-service 的 SecurityConfig）。
 * 将来若要做成「先看价目表再决定登录」，需要同时放开前端路由，
 * 并让页面在无令牌时隐藏「我的预约」分区。
 */

/** 品类、参考单价、计价说明与上门时段。 */
export function fetchRecycleGuide() {
  return http.get('/recycle/categories')
}

/** 我的回收预约。status 不传表示全部状态（PENDING/CONFIRMED/DONE/CANCELLED）。 */
export function fetchMyRecycleOrders(params = {}) {
  return http.get('/recycle/orders', { params })
}

/** 预约详情。含详细地址与手机号，仅本人或管理员可查看。 */
export function fetchRecycleOrder(id) {
  return http.get(`/recycle/orders/${id}`)
}

/** 提交预约。category/appointSlot 用枚举编码，appointDate 不能早于今天，手机号需 1 开头 11 位。 */
export function createRecycleOrder(data) {
  return http.post('/recycle/orders', data)
}

/** 取消预约：仅本人，且订单处于待确认或已确认状态。 */
export function cancelRecycleOrder(id) {
  return http.post(`/recycle/orders/${id}/cancel`)
}
