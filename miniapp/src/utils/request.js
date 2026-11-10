// 统一请求封装：token 注入、R 结构解析、401 跳登录
const BASE = '/api'

export function getToken() {
  return uni.getStorageSync('token') || ''
}

export function setToken(token) {
  uni.setStorageSync('token', token)
}

export function getUserInfo() {
  try {
    return JSON.parse(uni.getStorageSync('userInfo') || 'null')
  } catch (e) {
    return null
  }
}

export function setUserInfo(user) {
  uni.setStorageSync('userInfo', JSON.stringify(user))
}

export function logout() {
  uni.removeStorageSync('token')
  uni.removeStorageSync('userInfo')
  uni.reLaunch({ url: '/pages/login/login' })
}

function request({ url, method = 'GET', data = {} }) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + getToken()
      },
      success: (res) => {
        const body = res.data
        if (body && body.code === 200) {
          resolve(body.data)
        } else if (body && (body.code === 401 || body.code === 4011 || body.code === 403)) {
          if (body.code === 4011) {
            uni.$emit('wx-unbound', body.msg)
            reject(body)
          } else {
            uni.showToast({ title: body.msg || '请重新登录', icon: 'none' })
            logout()
            reject(body)
          }
        } else {
          uni.showToast({ title: (body && body.msg) || '请求失败', icon: 'none' })
          reject(body)
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常，请检查连接', icon: 'none' })
        reject(err)
      }
    })
  })
}

export const get = (url, params) => {
  if (params && Object.keys(params).length) {
    const qs = Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&')
    url = url + (url.includes('?') ? '&' : '?') + qs
  }
  return request({ url, method: 'GET' })
}
export const post = (url, data) => request({ url, method: 'POST', data })
export const put = (url, data) => request({ url, method: 'PUT', data })
export const del = (url, params) => {
  if (params && Object.keys(params).length) {
    const qs = Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&')
    url = url + (url.includes('?') ? '&' : '?') + qs
  }
  return request({ url, method: 'DELETE' })
}
export default { get, post, put, del, getToken, setToken, getUserInfo, setUserInfo, logout }
