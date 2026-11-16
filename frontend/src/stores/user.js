import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 用户信息 store
 * 目前为 mock 数据，后续接入登录后替换 fetchUser / login 即可。
 */
export const useUserStore = defineStore('user', () => {
  const userName = ref('王女士')
  const userId = ref('mock-001')
  const avatar = ref('')
  const roles = ref(['运营人员'])

  const displayName = computed(() => userName.value || '访客')

  async function fetchUser() {
    // TODO: 接入真实接口
    // const res = await fetch('/api/user/profile')
    // const data = await res.json()
    // userName.value = data.name
    // userId.value = data.id
    // avatar.value = data.avatar
    // roles.value = data.roles || []
  }

  function setUser(payload = {}) {
    if (payload.name) userName.value = payload.name
    if (payload.id) userId.value = payload.id
    if (payload.avatar !== undefined) avatar.value = payload.avatar
    if (payload.roles) roles.value = payload.roles
  }

  function logout() {
    userName.value = ''
    userId.value = ''
    avatar.value = ''
    roles.value = []
  }

  return { userName, userId, avatar, roles, displayName, fetchUser, setUser, logout }
})
