/**
 * 头像地址解析。
 *
 * 原实现用 `https://picsum.photos/seed/user-{id}/200/200` 作为占位头像。
 * 问题：picsum 在受限网络下不可达，占位图本身就成了裂图；
 * 而且图片加载失败后的回退仍然指向同一个地址，等于没有回退。
 *
 * 现在改为**本地生成的 SVG data URI**：取昵称首字，按用户 ID 稳定取色。
 * 完全不依赖网络，同一用户每次颜色一致，也不需要额外的图片资源。
 */

/** 由字符串得到稳定的整数散列，保证同一用户每次颜色相同 */
function hashCode(input) {
  const text = String(input ?? '')
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) | 0
  }
  return hash
}

/**
 * 默认头像：渐变底 + 首字。
 * @param {string|number} seed  用于取色的种子（通常传用户 ID）
 * @param {string} label        用于取首字的文字（通常传昵称）
 */
export function defaultAvatar(seed, label = '邻') {
  const hue = Math.abs(hashCode(seed ?? label)) % 360
  const char = String(label ?? '').trim().charAt(0) || '邻'
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100" height="100">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
<stop offset="0" stop-color="hsl(${hue},58%,64%)"/>
<stop offset="1" stop-color="hsl(${(hue + 42) % 360},54%,46%)"/>
</linearGradient></defs>
<rect width="100" height="100" fill="url(#g)"/>
<text x="50" y="52" text-anchor="middle" dominant-baseline="central" font-family="system-ui,-apple-system,Segoe UI,Microsoft YaHei,sans-serif" font-size="46" font-weight="600" fill="#ffffff">${char}</text>
</svg>`
  // 注意：`#` 必须交给 encodeURIComponent 处理，不能预先写成 %23（会被二次编码）
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/** 有自定义头像则用自定义头像，否则用本地生成的首字头像 */
export function displayAvatar(user) {
  if (user?.avatar) return user.avatar
  return defaultAvatar(user?.id ?? user?.nickname, user?.nickname)
}

export function userHomePath(userId) {
  return `/users/${userId}`
}
