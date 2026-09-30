/**
 * 订单确认页组件
 * 补齐参数校验和坏路径兜底
 */
<template>
  <div class="page-shell">
    <div class="page-container order-confirm-page">
      <!-- 加载中 -->
      <el-card v-if="pageLoading" class="confirm-card">
        <el-skeleton :rows="5" animated />
      </el-card>

      <!-- 参数错误/不支持的类型/商品不存在 -->
      <el-card v-else-if="pageError" class="confirm-card">
        <el-result icon="warning" :title="errorTitle" :sub-title="errorMessage">
          <template #extra>
            <el-button type="primary" @click="goBack">返回</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 正常订单确认 -->
      <template v-else>
        <section class="confirm-hero section-block">
          <div>
            <span class="eyebrow">订单确认</span>
            <h1>确认本次购买信息后，即可进入下单与支付流程。</h1>
            <p>
              这个页面适合展示套餐购买链路，能很好地衔接商户详情页和订单列表页。
            </p>
            <div class="hero-chips">
              <span class="hero-chip">套餐下单</span>
              <span class="hero-chip">订单联调</span>
              <span class="hero-chip">适合截图展示</span>
            </div>
          </div>
        </section>

        <el-card class="confirm-card">
        <template #header>
          <div class="card-header">
            <div>
              <h2>确认订单</h2>
              <p>请核对商品、数量和金额</p>
            </div>
          </div>
        </template>

        <div class="order-info">
          <h3>{{ orderInfo.title }}</h3>
          <p class="order-desc">{{ orderInfo.desc }}</p>
          <div class="info-grid">
            <article class="info-card">
              <span class="info-label">订单类型</span>
              <strong>套餐订单</strong>
            </article>
            <article class="info-card">
              <span class="info-label">业务 ID</span>
              <strong>{{ route.query.bizId }}</strong>
            </article>
            <article class="info-card">
              <span class="info-label">店铺 ID</span>
              <strong>{{ route.query.shopId }}</strong>
            </article>
          </div>
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
      </template>
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
.order-confirm-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.confirm-hero {
  padding: 30px;
}

.confirm-hero h1 {
  margin: 18px 0 12px;
  font-size: clamp(30px, 4vw, 42px);
  line-height: 1.18;
}

.confirm-hero p {
  margin: 0;
  color: var(--text-color-secondary);
  line-height: 1.8;
}

.hero-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.hero-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(15, 23, 42, 0.06);
  color: var(--text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.confirm-card {
  max-width: 920px;
  width: 100%;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header h2 {
  margin: 0 0 8px;
  font-size: 26px;
}

.card-header p {
  margin: 0;
  color: var(--text-color-secondary);
}

.order-info {
  padding: 20px 0;
}

.order-info h3 {
  font-size: 20px;
  margin-bottom: 10px;
}

.order-desc {
  color: var(--text-color-secondary);
  margin-bottom: 20px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 22px;
}

.info-card {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.info-label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-color-muted);
  font-size: 12px;
  font-weight: 700;
}

.info-card strong {
  font-size: 18px;
}

.order-price, .order-quantity, .order-total {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.label {
  width: 100px;
  color: var(--text-color-secondary);
}

.price {
  color: var(--danger-color);
  font-size: 20px;
  font-weight: bold;
}

.total-price {
  color: var(--danger-color);
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

@media (max-width: 768px) {
  .confirm-hero {
    padding: 22px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .order-price,
  .order-quantity,
  .order-total {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .label {
    width: auto;
  }
}
</style>
