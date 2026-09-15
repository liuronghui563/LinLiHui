/**
 * 首页问候语。
 *
 * 抽成纯函数是为了能给「几点算早上」这种边界写断言——这段逻辑最容易在
 * 改边界时改错，而错了只在特定的钟点才看得见。
 *
 * 四段（早 / 中 / 下午 / 晚）：
 *   05:00–10:59 早上好 · 11:00–12:59 中午好
 *   13:00–17:59 下午好 · 18:00–04:59 晚上好
 *
 * 「中午」只占 11–13 这两个小时：原来 11:00–17:59 一律叫「中午好」，
 * 于是下午三点也显示「中午好」。下午才是这一段里最长的时间，
 * 单独分出来才对得上人的实际感受。
 */

/** @returns {'早上好'|'中午好'|'下午好'|'晚上好'} */
export function greetingPart(date = new Date()) {
  const hour = date.getHours()
  if (hour >= 5 && hour < 11) return '早上好'
  if (hour >= 11 && hour < 13) return '中午好'
  if (hour >= 13 && hour < 18) return '下午好'
  return '晚上好'
}

/**
 * 带称呼的问候，例如「小明，早上好」。
 * 没有昵称时退化成单纯的问候，不留下一个孤零零的逗号。
 */
export function greetingFor(name, date = new Date()) {
  const part = greetingPart(date)
  const who = String(name || '').trim()
  return who ? `${who}，${part}` : part
}
