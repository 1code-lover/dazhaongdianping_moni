<template>
  <div class="user-center">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="user-card">
          <div class="user-avatar">
            <el-icon :size="64"><User /></el-icon>
          </div>
          <h3>{{ userStore.userInfo.nickName || '用户' }}</h3>
          <p class="user-id">ID: {{ userStore.userInfo.id }}</p>
        </el-card>

        <el-card class="menu-card">
          <el-menu :default-active="activeMenu" @select="handleMenuSelect">
            <el-menu-item index="order">
              <el-icon><Document /></el-icon>
              <span>我的订单</span>
            </el-menu-item>
            <el-menu-item index="coupon">
              <el-icon><Ticket /></el-icon>
              <span>我的优惠券</span>
            </el-menu-item>
            <el-menu-item index="favorite">
              <el-icon><Star /></el-icon>
              <span>我的收藏</span>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <el-col :span="18">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ menuTitle }}</span>
            </div>
          </template>

          <!-- 订单内容 -->
          <div v-if="activeMenu === 'order'">
            <el-empty description="暂无订单" />
          </div>

          <!-- 优惠券内容 -->
          <div v-else-if="activeMenu === 'coupon'">
            <el-empty description="暂无优惠券" />
          </div>

          <!-- 收藏内容 -->
          <div v-else-if="activeMenu === 'favorite'">
            <el-empty description="暂无收藏" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { User, Document, Ticket, Star } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('order')

const menuTitle = computed(() => {
  const titleMap = {
    order: '我的订单',
    coupon: '我的优惠券',
    favorite: '我的收藏'
  }
  return titleMap[activeMenu.value]
})

const handleMenuSelect = (index) => {
  if (index === 'order') {
    router.push('/order')
  } else {
    activeMenu.value = index
  }
}
</script>

<style scoped>
.user-center {
  padding: 20px 0;
}

.user-card {
  text-align: center;
  margin-bottom: 20px;
}

.user-avatar {
  margin-bottom: 15px;
}

.user-card h3 {
  font-size: 18px;
  margin-bottom: 5px;
}

.user-id {
  color: #999;
  font-size: 14px;
}

.menu-card {
  margin-bottom: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}
</style>
