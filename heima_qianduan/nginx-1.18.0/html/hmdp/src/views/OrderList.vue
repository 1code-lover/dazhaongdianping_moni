<template>
  <div class="page-shell">
    <div class="page-container order-page">
      <section class="order-card section-block">
        <div class="section-header">
          <div>
            <span class="eyebrow">订单中心</span>
            <h1 class="section-title">我的订单</h1>
            <p class="section-subtitle">查看支付状态、核销码和订单处理结果。</p>
          </div>
        </div>

        <el-tabs v-model="activeStatus" @tab-click="handleTabClick">
          <el-tab-pane label="全部" name="all" />
          <el-tab-pane label="待支付" name="0" />
          <el-tab-pane label="待使用" name="1" />
          <el-tab-pane label="已完成" name="2" />
        </el-tabs>

        <div class="order-list">
          <!-- 加载中 -->
          <div v-if="loading">
            <el-skeleton :rows="3" animated />
          </div>

          <!-- 加载失败 -->
          <div v-else-if="error" class="error-state">
            <el-empty description="加载失败，请稍后重试">
              <el-button type="primary" @click="fetchOrders">重新加载</el-button>
            </el-empty>
          </div>

          <!-- 空数据 -->
          <div v-else-if="orders.length === 0" class="empty-state">
            <el-empty description="当前没有订单记录" />
          </div>

          <!-- 订单列表 -->
          <article v-for="order in orders" v-else :key="order.id" class="order-item">
            <div class="order-top">
              <div>
                <p class="order-label">订单号</p>
                <strong>{{ order.orderNo }}</strong>
              </div>
              <el-tag :type="getStatusType(order.status)" effect="light">
                {{ getStatusText(order.status) }}
              </el-tag>
            </div>

            <div class="order-body">
              <div class="order-main">
                <h3>{{ order.title }}</h3>
                <p class="order-time">{{ order.createTime }}</p>
              </div>

              <div class="order-side">
                <span class="order-amount">¥{{ (order.amount / 100).toFixed(2) }}</span>
                <div class="order-actions">
                  <el-button v-if="order.status === 0" type="primary" size="small" @click="payCurrentOrder(order)">
                    支付
                  </el-button>
                  <el-button v-if="order.status === 0" size="small" @click="cancelCurrentOrder(order)">
                    取消
                  </el-button>
                  <div v-if="order.status === 1" class="verify-code">
                    <span>核销码</span>
                    <el-tag type="success">{{ order.verifyCode }}</el-tag>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
/**
 * 订单列表组件
 * 展示订单状态并支持支付和取消操作
 *
 * @author ethan
 * @date 2026-06-21
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getMyOrders, payOrder } from '../api/order'

const activeStatus = ref('all')
const orders = ref([])
const loading = ref(false)
const error = ref(false)

/**
 * 获取订单状态标签类型
 */
const getStatusType = (status) => {
  const typeMap = {
    0: 'warning',
    1: 'primary',
    2: 'success',
    3: 'info'
  }
  return typeMap[status] || 'info'
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
 * 切换标签时刷新订单列表
 */
const handleTabClick = () => {
  fetchOrders()
}

/**
 * 获取当前用户订单
 */
const fetchOrders = async () => {
  loading.value = true
  error.value = false

  try {
    const status = activeStatus.value === 'all' ? undefined : Number(activeStatus.value)
    const res = await getMyOrders(status)
    if (res.success) {
      orders.value = res.data || []
    }
  } catch (err) {
    console.error('加载订单列表失败:', err)
    error.value = true
    orders.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 支付当前订单
 */
const payCurrentOrder = async (order) => {
  try {
    await ElMessageBox.confirm('确认支付该订单？', '提示')
    const res = await payOrder(order.id)
    if (res.success) {
      ElMessage.success('支付成功')
      fetchOrders()
    }
  } catch (error) {
    // 用户取消时不需要额外提示
  }
}

/**
 * 取消当前订单
 */
const cancelCurrentOrder = async (order) => {
  try {
    await ElMessageBox.confirm('确认取消该订单？', '提示')
    const res = await cancelOrder(order.id)
    if (res.success) {
      ElMessage.success('订单已取消')
      fetchOrders()
    }
  } catch (error) {
    // 用户取消时不需要额外提示
  }
}

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped>
.order-card {
  padding: 28px;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 18px;
}

.order-item {
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.84);
}

.order-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.order-label {
  margin: 0 0 6px;
  color: var(--text-color-muted);
  font-size: 12px;
}

.order-top strong {
  font-size: 16px;
}

.order-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.order-main h3 {
  margin: 0 0 8px;
  font-size: 22px;
}

.order-time {
  margin: 0;
  color: var(--text-color-secondary);
}

.order-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 14px;
}

.order-amount {
  color: var(--danger-color);
  font-size: 28px;
  font-weight: 800;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.verify-code {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-color-secondary);
}

@media (max-width: 768px) {
  .order-card {
    padding: 22px;
  }

  .order-top,
  .order-body {
    flex-direction: column;
    align-items: flex-start;
  }

  .order-side,
  .order-actions {
    align-items: flex-start;
    justify-content: flex-start;
  }
}
</style>
