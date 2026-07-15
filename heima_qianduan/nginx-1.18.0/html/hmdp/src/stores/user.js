/**
 * 用户状态管理
 * 统一管理 token 和用户信息，提供初始化恢复能力
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserInfo } from '../api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref({})
  const isHydrated = ref(false) // 标记是否已完成初始化恢复

  /**
   * 设置 token
   */
  const setToken = (newToken) => {
    token.value = newToken
    if (newToken) {
      localStorage.setItem('token', newToken)
    } else {
      localStorage.removeItem('token')
    }
  }

  /**
   * 获取用户信息
   */
  const fetchUserInfo = async () => {
    if (!token.value) return
    try {
      const res = await getUserInfo()
      if (res.success) {
        userInfo.value = res.data
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
      throw error
    }
  }

  /**
   * 初始化恢复：应用启动时如果有 token 则恢复用户信息
   */
  const init = async () => {
    if (isHydrated.value) return
    if (token.value) {
      try {
        await fetchUserInfo()
      } catch (error) {
        // 如果恢复失败，清空 token(可能已过期)
        logout()
      }
    }
    isHydrated.value = true
  }

  /**
   * 登出：清空状态和 localStorage
   */
  const logout = () => {
    token.value = ''
    userInfo.value = {}
    isHydrated.value = false
    localStorage.removeItem('token')
  }

  return {
    token,
    userInfo,
    isHydrated,
    setToken,
    fetchUserInfo,
    init,
    logout
  }
})
