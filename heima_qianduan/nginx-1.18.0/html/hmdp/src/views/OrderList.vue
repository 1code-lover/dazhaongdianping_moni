<template>
  <div class="order-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的订单</span>
        </div>
      </template>

      <!-- 订单状态筛选 -->
      <el-tabs v-model="activeStatus" @tab-click="handleTabClick">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待支付" name="0" />
        <el-tab-pane label="待使用" name="1" />
        <el-tab-pane label="已完成" name="2" />
      </el-tabs>

      <!-- 订单列表 -->
      <div class="order-items">
        <el-empty v-if="orders.length === 0" description="暂无订单" />
        <div v-for="order in orders" :key="order.id" class="order-item">
          <div class="order-header">
            <span class="order-no">订单号：{{ order.orderNo }}</span>
            <el-tag :type="getStatusType(order.status)">{{ getStatusText(order.status) }}</el-tag>
          </div>
          <div class="order-content">
            <div class="order-info">
              <h4>{{ order.title }}</h4>
              <p class="order-time">{{ order.createTime }}</p>
            </div>
            <div class="order-right">
              <p class="order-amount">¥{{ (order.amount / 100).toFixed(2) }}</p>
              <div class="order-actions">
                <el-button v-if="order.status === 0" type="primary" size="small" @click="payOrder(order)">支付</el-button>
                <el-button v-if="order.status === 0" size="small" @click="cancelOrder(order)">取消</el-button>
                <div v-if="order.status === 1" class="verify-code">
                  <span>核销码：</span>
                  <el-tag type="success">{{ order.verifyCode }}</el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyOrders, payOrder as payOrderApi, cancelOrder as cancelOrderApi } from '../api/order'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeStatus = ref('all')
const orders = ref([])

const getStatusType = (status) => {
  const typeMap = {
    0: 'warning',
    1: 'primary',
    2: 'success',
    3: 'info'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    0: '待支付',
    1: '待使用',
    2: '已核销',
    3: '已取消'
  }
  return textMap[status] || '未知'
}

const handleTabClick = () => {
  fetchOrders()
}

const fetchOrders = async () => {
  const status = activeStatus.value === 'all' ? undefined : parseInt(activeStatus.value)
  const res = await getMyOrders(status)
  if (res.success) {
    orders.value = res.data
  }
}

const payOrder = async (order) => {
  try {
    await ElMessageBox.confirm('确认支付该订单？', '提示')
    const res = await payOrderApi(order.id)
    if (res.success) {
      ElMessage.success('支付成功')
      fetchOrders()
    }
  } catch (error) {
    // 取消操作
  }
}

const cancelOrder = async (order) => {
  try {
    await ElMessageBox.confirm('确认取消该订单？', '提示')
    const res = await cancelOrderApi(order.id)
    if (res.success) {
      ElMessage.success('订单已取消')
      fetchOrders()
    }
  } catch (error) {
    // 取消操作
  }
}

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped>
.order-list {
  padding: 20px 0;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}

.order-items {
  margin-top: 20px;
}

.order-item {
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 15px;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.order-no {
  color: #999;
  font-size: 14px;
}

.order-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 15px;
}

.order-info h4 {
  font-size: 16px;
  margin-bottom: 8px;
}

.order-time {
  color: #999;
  font-size: 12px;
}

.order-right {
  text-align: right;
}

.order-amount {
  color: #f56c6c;
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 10px;
}

.order-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.verify-code {
  display: flex;
  align-items: center;
  gap: 5px;
}
</style>
