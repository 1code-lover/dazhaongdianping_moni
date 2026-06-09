import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserInfo } from '../api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref({})

  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const fetchUserInfo = async () => {
    if (!token.value) return
    try {
      const res = await getUserInfo()
      if (res.success) {
        userInfo.value = res.data
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
    }
  }

  const logout = () => {
    token.value = ''
    userInfo.value = {}
    localStorage.removeItem('token')
  }

  return {
    token,
    userInfo,
    setToken,
    fetchUserInfo,
    logout
  }
})
