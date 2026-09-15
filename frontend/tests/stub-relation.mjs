// 关注 / 拉黑 的网络层替身：保留真实的业务逻辑（useRelation），只换掉 HTTP。
// 计数器让断言能针对「实际发出的请求次数」，而不是「看起来应该不会请求」。
const s = globalThis.__relationStub
if (!s) throw new Error('测试没有先安装 __relationStub')

export const followUser = (id) => s.follow(id)
export const unfollowUser = (id) => s.unfollow(id)
export const blockUser = (id) => s.block(id)
export const unblockUser = (id) => s.unblock(id)
export const fetchRelation = (id) => s.relation(id)
// 列表页用到的批量/列表接口在这里用不上，但导出保持模块形状一致
export const fetchRelations = async () => ({ code: 0, data: {} })
export const fetchFollowing = async () => ({ code: 0, data: { content: [] } })
export const fetchFollowers = async () => ({ code: 0, data: { content: [] } })
export const fetchBlocked = async () => ({ code: 0, data: { content: [] } })
