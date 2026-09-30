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

        <div class="profile-tips">
          <span class="tip-pill">已登录用户态</span>
          <span class="tip-pill">适合联调订单 / 套餐</span>
          <span class="tip-pill">适合 README 截图</span>
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

          <div class="summary-grid">
            <article class="summary-card">
              <strong>当前模块</strong>
              <p>{{ menuTitle }}</p>
            </article>
            <article class="summary-card">
              <strong>页面定位</strong>
              <p>个人资产、订单与后续扩展入口集中展示</p>
            </article>
            <article class="summary-card">
              <strong>演示建议</strong>
              <p>登录页 → 个人中心 → 订单页，适合串联用户链路</p>
            </article>
          </div>

          <div v-if="activeMenu === 'order'">
            <div v-if="orderLoading">
              <el-skeleton :rows="3" animated />
            </div>

            <div v-else-if="recentOrders.length > 0" class="recent-orders">
              <article v-for="order in recentOrders" :key="order.id" class="order-preview-card">
                <div class="preview-top">
                  <strong>{{ order.title }}</strong>
                  <span class="preview-status">{{ getStatusText(order.status) }}</span>
                </div>
                <p class="preview-time">{{ order.createTime }}</p>
                <div class="preview-meta">
                  <span class="preview-pill">订单号 {{ order.orderNo }}</span>
                  <span class="preview-pill">金额 ¥{{ (order.amount / 100).toFixed(2) }}</span>
                  <span v-if="order.verifyCode" class="preview-pill">核销码 {{ order.verifyCode }}</span>
                </div>
              </article>

              <div class="action-row">
                <el-button type="primary" @click="router.push('/order')">进入订单页</el-button>
              </div>
            </div>

            <div v-else class="empty-state">
              <el-empty description="当前还没有订单记录，后续下单后会在这里展示最近订单" />
              <div class="action-row">
                <el-button type="primary" @click="router.push('/order')">进入订单页</el-button>
              </div>
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Collection, Document, Ticket } from '@element-plus/icons-vue'
import { getMyOrders } from '../api/order'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('order')
const orderLoading = ref(false)
const recentOrders = ref([])

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
 * 获取最近订单预览
 */
const fetchRecentOrders = async () => {
  orderLoading.value = true
  try {
    const res = await getMyOrders(undefined, 1, 3)
    if (res.success) {
      recentOrders.value = res.data || []
    }
  } catch (error) {
    console.error('加载最近订单失败:', error)
    recentOrders.value = []
  } finally {
    orderLoading.value = false
  }
}

/**
 * 获取订单状态文案
 */
const getStatusText = (status) => {
  const textMap = {
    0: '待支付',
    1: '待使用',
    2: '已核销',
    3: '已取消'
  }
  return textMap[status] || '未知状态'
}

/**
 * 切换个人中心菜单
 */
const handleMenuSelect = (key) => {
  activeMenu.value = key
}

onMounted(() => {
  fetchRecentOrders()
})
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

.profile-tips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.tip-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.summary-card {
  min-height: 126px;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.summary-card strong {
  display: block;
  margin-bottom: 10px;
  font-size: 15px;
}

.summary-card p {
  margin: 0;
  color: var(--text-color-secondary);
  line-height: 1.8;
}

.action-row {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.recent-orders {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-preview-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.preview-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.preview-top strong {
  font-size: 18px;
}

.preview-status,
.preview-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(15, 23, 42, 0.06);
  color: var(--text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.preview-time {
  margin: 0 0 12px;
  color: var(--text-color-secondary);
}

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
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

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
