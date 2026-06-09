<template>
  <div class="shop-detail" v-if="shop">
    <!-- 商户信息 -->
    <el-card class="shop-info-card">
      <div class="shop-header">
        <div class="shop-image">
          <el-icon :size="64"><Service /></el-icon>
        </div>
        <div class="shop-meta">
          <h1>{{ shop.name }}</h1>
          <div class="rating">
            <el-rate v-model="shop.score" disabled show-score />
            <span class="price">¥{{ shop.avgPrice }}/人</span>
          </div>
          <p class="address">
            <el-icon><Location /></el-icon>
            {{ shop.address }}
          </p>
          <p class="hours" v-if="shop.openHours">
            <el-icon><Clock /></el-icon>
            营业时间：{{ shop.openHours }}
          </p>
        </div>
      </div>
    </el-card>

    <!-- 套餐列表 -->
    <el-card class="section-card" v-if="combos.length > 0">
      <template #header>
        <div class="card-header">
          <span>优惠套餐</span>
        </div>
      </template>
      <div class="combo-list">
        <div v-for="combo in combos" :key="combo.id" class="combo-item">
          <div class="combo-info">
            <h4>{{ combo.title }}</h4>
            <p class="combo-desc">{{ combo.subTitle }}</p>
            <div class="combo-price">
              <span class="current-price">¥{{ (combo.price / 100).toFixed(2) }}</span>
              <span class="original-price">¥{{ (combo.originalPrice / 100).toFixed(2) }}</span>
              <el-tag type="danger" size="small">省{{ ((combo.originalPrice - combo.price) / 100).toFixed(2) }}元</el-tag>
            </div>
            <p class="combo-sales">已售{{ combo.sales }}份</p>
          </div>
          <el-button type="primary" @click="buyCombo(combo)">立即购买</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShopById } from '../api/shop'
import { getCombosByShop } from '../api/combo'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import { Service, Location, Clock } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const shop = ref(null)
const combos = ref([])

const buyCombo = (combo) => {
  if (!userStore.token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push(`/order/confirm?type=2&bizId=${combo.id}&shopId=${shop.value.id}`)
}

onMounted(async () => {
  const shopId = route.params.id

  // 获取商户详情
  const shopRes = await getShopById(shopId)
  if (shopRes.success) {
    shop.value = shopRes.data
  }

  // 获取套餐列表
  const comboRes = await getCombosByShop(shopId)
  if (comboRes.success) {
    combos.value = comboRes.data
  }
})
</script>

<style scoped>
.shop-detail {
  padding: 20px 0;
}

.shop-info-card {
  margin-bottom: 20px;
}

.shop-header {
  display: flex;
  gap: 20px;
}

.shop-image {
  width: 200px;
  height: 200px;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.shop-meta {
  flex: 1;
}

.shop-meta h1 {
  font-size: 24px;
  margin-bottom: 10px;
}

.rating {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 10px;
}

.price {
  color: #f56c6c;
  font-weight: bold;
  font-size: 18px;
}

.address, .hours {
  color: #666;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.section-card {
  margin-bottom: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}

.combo-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 0;
  border-bottom: 1px solid #eee;
}

.combo-item:last-child {
  border-bottom: none;
}

.combo-info {
  flex: 1;
}

.combo-info h4 {
  font-size: 16px;
  margin-bottom: 8px;
}

.combo-desc {
  color: #999;
  font-size: 14px;
  margin-bottom: 8px;
}

.combo-price {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.current-price {
  color: #f56c6c;
  font-size: 20px;
  font-weight: bold;
}

.original-price {
  color: #999;
  text-decoration: line-through;
  font-size: 14px;
}

.combo-sales {
  color: #999;
  font-size: 12px;
}
</style>
