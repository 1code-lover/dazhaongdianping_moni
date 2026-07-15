<template>
  <div class="page-shell">
    <div class="page-container user-center-page">
      <aside class="profile-panel section-block">
        <div class="profile-hero">
          <div class="profile-avatar">
            {{ displayInitial }}
          </div>
          <div class="profile-copy">
            <span class="eyebrow">个人中心</span>
            <h1>{{ userStore.userInfo.nickName || '用户' }}</h1>
            <p>用户 ID：{{ userStore.userInfo.id || '--' }}</p>
          </div>
        </div>

        <div class="profile-stats">
          <div class="stat-card">
            <strong>订单</strong>
            <span>查看下单记录与核销状态</span>
          </div>
          <div class="stat-card">
            <strong>优惠券</strong>
            <span>后续可扩展优惠券展示入口</span>
          </div>
          <div class="stat-card">
            <strong>收藏</strong>
            <span>保留常看商户的聚合位置</span>
          </div>
        </div>
      </aside>

      <section class="content-panel">
        <div class="menu-strip section-block">
          <button
            v-for="item in menuItems"
            :key="item.key"
            type="button"
            class="menu-item"
            :class="{ active: activeMenu === item.key }"
            @click="handleMenuSelect(item.key)"
          >
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </button>
        </div>

        <div class="detail-card section-block">
          <div class="section-header">
            <div>
              <h2 class="section-title">{{ menuTitle }}</h2>
              <p class="section-subtitle">{{ menuDescription }}</p>
            </div>
          </div>

          <div v-if="activeMenu === 'order'" class="empty-state">
            <el-empty description="订单列表已独立到订单页面，点击下方按钮即可查看" />
            <div class="action-row">
              <el-button type="primary" @click="router.push('/order')">进入订单页</el-button>
            </div>
          </div>

          <div v-else-if="activeMenu === 'coupon'" class="empty-state">
            <el-empty description="当前还没有优惠券数据，后续可以接入优惠券接口" />
          </div>

          <div v-else class="empty-state">
            <el-empty description="当前还没有收藏记录，后续可以接入收藏列表接口" />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
/**
 * 个人中心组件
 * 展示用户概览和订单、优惠券、收藏入口
 *
 * @author ethan
 * @date 2026-06-21
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Collection, Document, Ticket } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('order')

const menuItems = [
  { key: 'order', label: '我的订单', icon: Document },
  { key: 'coupon', label: '我的优惠券', icon: Ticket },
  { key: 'favorite', label: '我的收藏', icon: Collection }
]

const displayInitial = computed(() => {
  const name = userStore.userInfo.nickName || '用户'
  return name.slice(0, 1)
})

const menuTitle = computed(() => {
  const titleMap = {
    order: '我的订单',
    coupon: '我的优惠券',
    favorite: '我的收藏'
  }
  return titleMap[activeMenu.value]
})

const menuDescription = computed(() => {
  const descMap = {
    order: '订单页用于查看支付状态、核销码和历史记录。',
    coupon: '优惠券区域建议后续接入可用券、已使用券和已过期券。',
    favorite: '收藏区域建议后续接入常看商户和最近浏览。'
  }
  return descMap[activeMenu.value]
})

/**
 * 切换个人中心菜单
 */
const handleMenuSelect = (key) => {
  activeMenu.value = key
}
</script>

<style scoped>
.user-center-page {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 24px;
}

.profile-panel {
  padding: 28px;
}

.profile-hero {
  padding: 26px;
  border-radius: 28px;
  background: linear-gradient(145deg, #ff7b54 0%, #ffb26b 100%);
  color: #fff;
}

.profile-avatar {
  width: 74px;
  height: 74px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
  font-size: 28px;
  font-weight: 800;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
}

.profile-copy h1 {
  margin: 16px 0 8px;
  font-size: 30px;
}

.profile-copy p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.profile-stats {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.stat-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.stat-card strong {
  display: block;
  margin-bottom: 6px;
  font-size: 16px;
}

.stat-card span {
  color: var(--text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.content-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.menu-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding: 18px;
}

.menu-item {
  min-width: 0;
  padding: 16px 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.84);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-color);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.menu-item.active,
.menu-item:hover {
  transform: translateY(-2px);
  color: var(--primary-color-dark);
  border-color: rgba(255, 107, 53, 0.18);
  box-shadow: 0 18px 30px rgba(15, 23, 42, 0.07);
}

.detail-card {
  padding: 28px;
}

.action-row {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

@media (max-width: 992px) {
  .user-center-page {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .profile-panel,
  .detail-card {
    padding: 22px;
  }

  .menu-strip {
    grid-template-columns: 1fr;
  }
}
</style>
