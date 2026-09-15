/**
 * 广告位申请的状态语义。
 *
 * 抽成纯函数的原因与问候语一样：这是「审核结果怎么呈现给用户」的唯一映射，
 * 而它只会在特定状态下才看得到——写错了没人发现，直到有申请被驳回。
 *
 * 注意：**状态中文名不在这里**。它由后端下发（AdApplicationResponse.statusLabel），
 * 前端不维护第二份映射表；这里只负责「用什么语气显示」和「能给什么提示」。
 */

/** 状态 → 标签语气 + 一句说明 */
const META = {
  PENDING: {
    tone: 'tag--open',
    hint: '已提交，等待管理员审核。'
  },
  APPROVED: {
    tone: 'tag--done',
    hint: '已通过，正在首页轮播中。'
  },
  REJECTED: {
    tone: 'tag--cancelled',
    hint: '未通过审核。'
  }
}

const UNKNOWN = { tone: 'tag--outline', hint: '' }

export function adStatusMeta(status) {
  return META[status] || UNKNOWN
}

/** 只有还在待审的申请能撤回；已审核的撤回不了，只能重新提交 */
export function canWithdraw(status) {
  return status === 'PENDING'
}

/**
 * 行内提示：有审核意见时优先显示意见（那是管理员给用户的话），
 * 否则回落到状态本身的说明。
 */
export function adStatusHint(item) {
  if (item?.reviewNote) return `审核意见：${item.reviewNote}`
  return adStatusMeta(item?.status).hint
}
