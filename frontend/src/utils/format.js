/**
 * 时间与文本格式化。
 *
 * 此前 `formatTime` 在 CommunityView / PlazaView / UserHomeView / AidDetailView
 * 各写了一份，且都是 `String(value).replace('T',' ').slice(0,16)`——
 * 只做字符串裁剪，既不显示「刚刚 / 3 小时前」这类更有用的相对时间，
 * 也无法在跨年时补上年份。统一到这里。
 */

function toDate(value) {
  if (!value) return null
  if (value instanceof Date) return value
  // 后端返回 "2026-09-12T19:36:46" 这类无时区标记的本地时间字符串
  const normalized = typeof value === 'string' ? value.replace(' ', 'T') : value
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 相对时间：1 分钟内=刚刚，1 小时内=N 分钟前，24 小时内=N 小时前，7 天内=N 天前，更早=月-日 */
export function relativeTime(value) {
  const date = toDate(value)
  if (!date) return ''
  const diff = Date.now() - date.getTime()
  if (diff < 0) return formatDateTime(date)
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDateTime(date)
}

/** 绝对时间：YYYY-MM-DD HH:mm（跨年时带年份） */
export function formatDateTime(value) {
  const date = toDate(value)
  if (!date) return ''
  const pad = (n) => String(n).padStart(2, '0')
  const sameYear = date.getFullYear() === new Date().getFullYear()
  const ymd = sameYear
    ? `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    : `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `${ymd} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 仅日期 YYYY-MM-DD */
export function formatDate(value) {
  const date = toDate(value)
  if (!date) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 按字数截断，中文按字符计 */
export function truncate(text, max = 80) {
  const value = String(text ?? '').replace(/\s+/g, ' ').trim()
  return value.length > max ? `${value.slice(0, max)}…` : value
}
