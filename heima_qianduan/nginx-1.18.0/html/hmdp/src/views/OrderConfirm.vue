/**
 * 订单确认页组件
 * 补齐参数校验和坏路径兜底
 */
<template>
  <div class="page-shell">
    <div class="page-container">
      <!-- 加载中 -->
      <el-card v-if="pageLoading">
        <el-skeleton :rows="5" animated />
      </el-card>

      <!-- 参数错误/不支持的类型/商品不存在 -->
      <el-card v-else-if="pageError">
        <el-result icon="warning" :title="errorTitle" :sub-title="errorMessage">
          <template #extra>
            <el-button type="primary" @click="goBack">返回</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 正常订单确认 -->
      <el-card v-else>
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
          <el-button type="primary" @click="submitOrder" :loading="submitting">立即支付</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
/**
 * 订单确认页组件
 * 补齐参数校验、类型校验、数据校验和错误兜底
 *
 * @author ethan
 * @date 2026-06-30
 */
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
const pageLoading = ref(true)
const pageError = ref(false)
const errorTitle = ref('')
const errorMessage = ref('')
const submitting = ref(false)

/**
 * 返回上一页
 */
const goBack = () => {
  router.back()
}

/**
 * 提交订单
 */
const submitOrder = async () => {
  submitting.value = true
  try {
    const { type, bizId, shopId } = route.query
    const res = await createOrder(parseInt(type), parseInt(bizId), quantity.value)
    if (res.success) {
      ElMessage.success('下单成功')
      router.push('/order')
    }
  } catch (error) {
    console.error('下单失败:', error)
    ElMessage.error('下单失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

/**
 * 初始化订单信息
 * 补齐完整的参数校验和错误处理
 */
const initOrderInfo = async () => {
  pageLoading.value = true
  pageError.value = false

  try {
    const { type, bizId, shopId } = route.query

    // 1. 校验必需参数
    if (!type || !bizId || !shopId) {
      pageError.value = true
      errorTitle.value = '参数缺失'
      errorMessage.value = '缺少必要的订单参数，请从商品详情页重新进入'
      return
    }

    // 2. 校验订单类型
    const orderType = parseInt(type)
    if (![1, 2].includes(orderType)) {
      pageError.value = true
      errorTitle.value = '不支持的订单类型'
      errorMessage.value = `当前订单类型 (${type}) 暂不支持，请联系客服`
      return
    }

    // 3. 根据类型加载商品信息
    if (orderType === 2) {
      // 套餐订单
      const res = await getComboById(bizId)
      if (res.success && res.data) {
        orderInfo.value = {
          title: res.data.title,
          desc: res.data.subTitle || '暂无描述',
          price: res.data.price
        }
      } else {
        pageError.value = true
        errorTitle.value = '商品不存在'
        errorMessage.value = '该套餐不存在或已下架'
        return
      }
    } else if (orderType === 1) {
      // 优惠券订单 - 暂不支持
      pageError.value = true
      errorTitle.value = '功能开发中'
      errorMessage.value = '优惠券订单功能正在开发中，敬请期待'
      return
    }
  } catch (err) {
    console.error('加载订单信息失败:', err)
    pageError.value = true
    errorTitle.value = '加载失败'
    errorMessage.value = '加载订单信息失败，请稍后重试'
  } finally {
    pageLoading.value = false
  }
}

onMounted(() => {
  initOrderInfo()
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
