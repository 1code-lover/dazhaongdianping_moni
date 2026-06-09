<template>
  <div id="app">
    <el-container>
      <el-header>
        <div class="header-content">
          <div class="logo" @click="goHome">本地生活平台</div>
          <div class="search-box">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索商户、美食、优惠..."
              clearable
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button @click="handleSearch">搜索</el-button>
              </template>
            </el-input>
          </div>
          <div class="user-info">
            <template v-if="userStore.token">
              <el-dropdown @command="handleCommand">
                <span class="user-name">
                  {{ userStore.userInfo.nickName || '用户' }}
                  <el-icon><arrow-down /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="order">我的订单</el-dropdown-item>
                    <el-dropdown-item command="user">个人中心</el-dropdown-item>
                    <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <template v-else>
              <el-button type="primary" @click="goLogin">登录</el-button>
            </template>
          </div>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from './stores/user'
import { ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const searchKeyword = ref('')

const goHome = () => {
  router.push('/')
}

const goLogin = () => {
  router.push('/login')
}

const handleSearch = () => {
  if (searchKeyword.value) {
    router.push(`/shop?keyword=${searchKeyword.value}`)
  }
}

const handleCommand = (command) => {
  switch (command) {
    case 'order':
      router.push('/order')
      break
    case 'user':
      router.push('/user')
      break
    case 'logout':
      userStore.logout()
      router.push('/')
      break
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
  background-color: #f5f5f5;
}

#app {
  min-height: 100vh;
}

.el-header {
  background-color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  color: #409eff;
  cursor: pointer;
}

.search-box {
  width: 400px;
}

.user-name {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.el-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  min-height: calc(100vh - 60px);
}
</style>
