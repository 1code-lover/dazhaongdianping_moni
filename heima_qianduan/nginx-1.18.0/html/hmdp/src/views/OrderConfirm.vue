<template>
  <div class="order-confirm">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>确认订单</span>
        </div>
      </template>

      <div class="order-info">
        <h3>{{ orderInfo.title }}</h3>
        <p class="order-desc">{{ orderInfo.desc }}</p>
        <div class="order-price">
          <span class="label">支付金额：</span>
          <span class="price">¥{{ (orderInfo.price / 100).toFixed(2) }}</span>
        </div>
        <div class="order-quantity">
          <span class="label">购买数量：</span>
          <el-input-number v-model="quantity" :min="1" :max="10" />
        </div>
        <div class="order-total">
          <span class="label">合计：</span>
          <span class="total-price">¥{{ ((orderInfo.price * quantity) / 100).toFixed(2) }}</span>
        </div>
      </div>

      <div class="order-actions">
        <el-button @click="goBack">返回</el-button>
        <el-button type="primary" @click="submitOrder" :loading="loading">立即支付</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getComboById } from '../api/combo'
import { createOrder } from '../api/order'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const orderInfo = ref({
  title: '',
  desc: '',
  price: 0
})
const quantity = ref(1)
const loading = ref(false)

const goBack = () => {
  router.back()
}

const submitOrder = async () => {
  loading.value = true
  try {
    const { type, bizId } = route.query
    const res = await createOrder(parseInt(type), parseInt(bizId), quantity.value)
    if (res.success) {
      ElMessage.success('下单成功')
      router.push('/order')
    }
  } catch (error) {
    console.error('下单失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const { type, bizId } = route.query

  if (type === '2') {
    // 套餐订单
    const res = await getComboById(bizId)
    if (res.success) {
      orderInfo.value = {
        title: res.data.title,
        desc: res.data.subTitle,
        price: res.data.price
      }
    }
  }
})
</script>

<style scoped>
.order-confirm {
  padding: 20px 0;
  max-width: 600px;
  margin: 0 auto;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}

.order-info {
  padding: 20px 0;
}

.order-info h3 {
  font-size: 20px;
  margin-bottom: 10px;
}

.order-desc {
  color: #666;
  margin-bottom: 20px;
}

.order-price, .order-quantity, .order-total {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.label {
  width: 100px;
  color: #666;
}

.price {
  color: #f56c6c;
  font-size: 20px;
  font-weight: bold;
}

.total-price {
  color: #f56c6c;
  font-size: 24px;
  font-weight: bold;
}

.order-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}
</style>
