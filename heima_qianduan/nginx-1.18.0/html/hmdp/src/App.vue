<template>
  <div id="app">
    <el-container class="app-shell">
      <el-header class="app-header">
        <div class="page-container header-content">
          <div class="brand-block" @click="goHome">
            <div class="brand-mark">食</div>
            <div class="brand-copy">
              <div class="brand-title">本地生活平台</div>
              <p class="brand-subtitle">吃喝玩乐，一站发现附近好店</p>
            </div>
          </div>

          <div class="search-panel">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索商户、美食、优惠"
              clearable
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button @click="handleSearch">搜索</el-button>
              </template>
            </el-input>
          </div>

          <div class="user-panel">
            <template v-if="userStore.token">
              <el-dropdown @command="handleCommand">
                <div class="user-entry">
                  <div class="user-avatar">
                    {{ displayInitial }}
                  </div>
                  <div class="user-copy">
                    <span class="user-label">欢迎回来</span>
                    <span class="user-name">
                      {{ userStore.userInfo.nickName || '用户' }}
                      <el-icon><ArrowDown /></el-icon>
                    </span>
                  </div>
                </div>
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

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
/**
 * 应用壳层组件
 * 负责顶部导航、搜索入口、用户快捷操作和应用初始化
 *
 * @author ethan
 * @date 2026-06-21
 */
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from './stores/user'
import './assets/styles.css'

const router = useRouter()
const userStore = useUserStore()
const searchKeyword = ref('')

// 应用启动时初始化用户态
onMounted(async () => {
  await userStore.init()
})

const displayInitial = computed(() => {
  const name = userStore.userInfo.nickName || '用户'
  return name.slice(0, 1)
})

/**
 * 返回首页
 */
const goHome = () => {
  router.push('/')
}

/**
 * 跳转登录页
 */
const goLogin = () => {
  router.push('/login')
}

/**
 * 执行商户搜索
 */
const handleSearch = () => {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    return
  }
  router.push(`/shop?keyword=${encodeURIComponent(keyword)}`)
}

/**
 * 处理用户下拉菜单操作
 */
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
    default:
      break
  }
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: transparent;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: auto;
  padding: 18px 0;
  background: rgba(255, 250, 245, 0.82);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.72);
}

.header-content {
  display: grid;
  grid-template-columns: 280px minmax(300px, 1fr) auto;
  align-items: center;
  gap: 20px;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
}

.brand-mark {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #ff6b35 0%, #ff9f43 100%);
  box-shadow: 0 14px 30px rgba(255, 107, 53, 0.3);
}

.brand-title {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-color);
}

.brand-subtitle {
  margin: 4px 0 0;
  color: var(--text-color-secondary);
  font-size: 12px;
}

.search-panel {
  width: 100%;
}

.user-panel {
  display: flex;
  justify-content: flex-end;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px 8px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(15, 23, 42, 0.06);
  cursor: pointer;
}

.user-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #ff7b54 0%, #ffb26b 100%);
}

.user-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-label {
  font-size: 11px;
  color: var(--text-color-muted);
}

.user-name {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-color);
}

.app-main {
  width: 100%;
  padding: 0;
  background: transparent;
}

@media (max-width: 992px) {
  .header-content {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .user-panel {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .app-header {
    padding: 14px 0;
  }

  .brand-subtitle {
    display: none;
  }

  .brand-mark {
    width: 46px;
    height: 46px;
  }

  .brand-title {
    font-size: 18px;
  }
}
</style>
