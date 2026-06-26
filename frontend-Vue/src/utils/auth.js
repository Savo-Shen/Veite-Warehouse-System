import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'
const RememberDeviceKey = 'rememberMe'
const RememberDeviceDays = 90

export function getToken() {
  return Cookies.get(TokenKey)
}

export function setToken(token, rememberDevice = true) {
  const options = {
    sameSite: 'Lax'
  }
  if (rememberDevice) {
    options.expires = RememberDeviceDays
  }
  return Cookies.set(TokenKey, token, options)
}

export function isRememberDevice() {
  return Cookies.get(RememberDeviceKey) !== 'false'
}

export function removeToken() {
  return Cookies.remove(TokenKey)
}
