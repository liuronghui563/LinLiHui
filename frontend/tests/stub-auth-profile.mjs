// Stub for `src/api/auth` used by the profile-refresh frequency test.
// Only fetchMe is counted; the store's other calls are never exercised here.
const ctl = () => globalThis.__authProfileStub

export function fetchMe() {
  return ctl().fetchMe()
}

export const fetchCaptcha = () => Promise.resolve({ code: 0, data: {} })
export const verifyCaptcha = () => Promise.resolve({ code: 0, data: {} })
export const sendSms = () => Promise.resolve({ code: 0, data: {} })
export const register = () => Promise.resolve({ code: 0, data: {} })
export const loginByPassword = () => Promise.resolve({ code: 0, data: {} })
export const loginBySms = () => Promise.resolve({ code: 0, data: {} })
export const loginByInternal = () => Promise.resolve({ code: 0, data: {} })
export const refreshToken = () => Promise.resolve({ code: 0, data: {} })
export const logoutApi = () => Promise.resolve({ code: 0 })
export const fetchUserHome = () => Promise.resolve({ code: 0, data: {} })
export const fetchAdminDashboard = () => Promise.resolve({ code: 0, data: {} })
