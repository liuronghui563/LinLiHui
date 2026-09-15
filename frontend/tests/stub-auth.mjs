// Stub for `src/api/auth` — aliased in by the verification harness so the gate
// logic can run in Node without axios, the router or a browser.
// Behaviour is driven through globalThis.__gateStub so the harness can flip
// responses between cases.
const ctl = () => globalThis.__gateStub

export function fetchCaptcha() {
  return ctl().fetchCaptcha()
}

export function verifyCaptcha(data) {
  return ctl().verifyCaptcha(data)
}

// The views/stores also import these; the gate flow never calls them.
export const sendSms = () => Promise.resolve({ code: 0, data: {} })
export const register = () => Promise.resolve({ code: 0, data: {} })
export const loginByPassword = () => Promise.resolve({ code: 0, data: {} })
export const loginBySms = () => Promise.resolve({ code: 0, data: {} })
export const loginByInternal = () => Promise.resolve({ code: 0, data: {} })
export const refreshToken = () => Promise.resolve({ code: 0, data: {} })
export const fetchMe = () => Promise.resolve({ code: 0, data: {} })
export const logoutApi = () => Promise.resolve({ code: 0, data: {} })
export const fetchUserHome = () => Promise.resolve({ code: 0, data: {} })
export const fetchAdminDashboard = () => Promise.resolve({ code: 0, data: {} })
