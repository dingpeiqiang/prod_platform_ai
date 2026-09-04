/**
 * 认证 API —— /api/v1/auth/*
 */
import httpClient from './httpClient.js'

export function login(username, password) {
  return httpClient.post('/auth/login', { username, password }, { showLoading: false, silentError: true })
}

export function register(username, password, displayName) {
  return httpClient.post('/auth/register', { username, password, display_name: displayName }, { showLoading: false, silentError: true })
}

export function getMe() {
  return httpClient.get('/auth/me', { showLoading: false, silentError: true })
}

export function logout() {
  return httpClient.post('/auth/logout', {}, { showLoading: false, silentError: true })
}
