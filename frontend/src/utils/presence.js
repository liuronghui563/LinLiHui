export const PRESENCE_OPTIONS = [
  { value: 'ONLINE', label: '在线' },
  { value: 'BUSY', label: '忙碌' },
  { value: 'AWAY', label: '离开' },
  { value: 'STUDYING', label: '学习中' },
  { value: 'OFFLINE', label: '离线' }
]

export const GENDER_OPTIONS = [
  { value: 'UNKNOWN', label: '保密' },
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' }
]

export function presenceLabel(status) {
  return PRESENCE_OPTIONS.find((item) => item.value === status)?.label || '离线'
}

export function genderLabel(gender) {
  return GENDER_OPTIONS.find((item) => item.value === gender)?.label || '保密'
}

export function presenceClass(status) {
  const key = String(status || 'OFFLINE').toLowerCase()
  return `presence-${key}`
}
