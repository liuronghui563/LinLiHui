export function defaultAvatar(userId, fallback = 'guest') {
  const seed = userId == null ? fallback : `user-${userId}`
  return `https://picsum.photos/seed/${seed}/200/200`
}

export function displayAvatar(user) {
  if (user?.avatar) return user.avatar
  return defaultAvatar(user?.id)
}

export function userHomePath(userId) {
  return `/users/${userId}`
}
