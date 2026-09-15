// Stub for `src/api/notify` used by the unread-count frequency test.
// Counts calls and lets the harness decide whether to succeed or fail, so the
// test can assert on the *number of HTTP requests*, not just final state.
const ctl = () => globalThis.__notifyStub

export function fetchUnreadCount() {
  return ctl().fetchUnreadCount()
}

// Not exercised by this test, but the module must export the full surface.
export const fetchNotifications = () => Promise.resolve({ code: 0, data: { content: [], totalElements: 0 } })
export const markNotificationRead = () => Promise.resolve({ code: 0 })
export const markAllNotificationsRead = () => Promise.resolve({ code: 0 })
export const deleteNotification = () => Promise.resolve({ code: 0 })
