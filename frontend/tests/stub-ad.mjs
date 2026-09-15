// 广告位申请的网络层替身：返回三种状态各一条，用来验证页面是否如实反映审核结果。
const s = globalThis.__adStub
if (!s) throw new Error('测试没有先安装 __adStub')

export const fetchMyAdApplications = async () => ({ code: 0, data: s.mine })

export const applyAdSlot = async (payload) => {
  s.calls.apply += 1
  return {
    code: 0,
    message: '已提交申请，等待管理员审核',
    data: {
      id: 99,
      title: payload.title,
      subtitle: payload.subtitle,
      imageUrl: payload.imageUrl,
      linkUrl: payload.linkUrl,
      status: 'PENDING',
      statusLabel: '待审核',
      applicantId: 7,
      createdAt: '2026-01-01T12:00:00'
    }
  }
}

export const withdrawAdApplication = async (id) => {
  s.calls.withdraw += 1
  return { code: 0, message: '已撤回申请', data: null }
}

export const fetchPendingAdApplications = async () => {
  s.calls.pending += 1
  return { code: 0, data: s.mine.filter((item) => item.status === 'PENDING') }
}

export const approveAdApplication = async (id, note) => {
  s.calls.approve += 1
  return { code: 0, message: '已通过', data: { ...s.mine[0], status: 'APPROVED', statusLabel: '已通过', reviewNote: note } }
}

export const rejectAdApplication = async (id, note) => {
  s.calls.reject += 1
  return { code: 0, message: '已驳回', data: { ...s.mine[0], status: 'REJECTED', statusLabel: '已驳回', reviewNote: note } }
}

// 首页轮播与管理员直投接口：本测试用不到，保持模块形状一致
export const fetchAdCarousel = async () => ({ code: 0, data: s.carousel })
export const clickAd = async () => ({ code: 0, data: null })
export const fetchAdList = async () => ({ code: 0, data: [] })
export const createAd = async () => ({ code: 0, data: null })
export const updateAd = async () => ({ code: 0, data: null })
export const deleteAd = async () => ({ code: 0, data: null })
