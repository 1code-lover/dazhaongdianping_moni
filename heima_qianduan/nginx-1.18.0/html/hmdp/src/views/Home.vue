<template>
  <div class="home">
    <!-- 分类导航 -->
    <div class="category-nav">
      <div
        v-for="type in shopTypes"
        :key="type.id"
        class="category-item"
        @click="goShopList(type.id)"
      >
        <el-icon :size="32"><component :is="getIcon(type.name)" /></el-icon>
        <span>{{ type.name }}</span>
      </div>
    </div>

    <!-- 轮播图 -->
    <el-carousel :interval="4000" height="300px" class="banner">
      <el-carousel-item v-for="item in banners" :key="item.id">
        <div class="banner-item" :style="{ backgroundColor: item.color }">
          <h2>{{ item.title }}</h2>
          <p>{{ item.desc }}</p>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- 热门推荐 -->
    <div class="section">
      <h3 class="section-title">热门推荐</h3>
      <el-row :gutter="20">
        <el-col :span="6" v-for="shop in hotShops" :key="shop.id">
          <el-card :body-style="{ padding: '0px' }" class="shop-card" @click="goShopDetail(shop.id)">
            <div class="shop-image" :style="{ backgroundColor: '#f0f0f0' }">
              <el-icon :size="48"><component :is="getIcon('商户')" /></el-icon>
            </div>
            <div class="shop-info">
              <h4>{{ shop.name }}</h4>
              <div class="shop-meta">
                <el-rate v-model="shop.score" disabled show-score />
                <span class="price">¥{{ shop.avgPrice }}/人</span>
              </div>
              <p class="address">{{ shop.address }}</p>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getShopTypes, getShopsByType } from '../api/shop'
import { Food, Coffee, Film, ShoppingBag, Location, Service } from '@element-plus/icons-vue'

const router = useRouter()
const shopTypes = ref([])
const hotShops = ref([])

const banners = [
  { id: 1, title: '新用户专享优惠', desc: '首次下单立减20元', color: '#409eff' },
  { id: 2, title: '周末特惠套餐', desc: '精选套餐低至5折', color: '#67c23a' },
  { id: 3, title: '美食节活动', desc: '海量优惠券等你领', color: '#e6a23c' }
]

const getIcon = (name) => {
  const iconMap = {
    '美食': Food,
    '咖啡': Coffee,
    '电影': Film,
    '购物': ShoppingBag,
    '酒店': Location,
    '商户': Service
  }
  return iconMap[name] || Service
}

const goShopList = (typeId) => {
  router.push(`/shop?typeId=${typeId}`)
}

const goShopDetail = (id) => {
  router.push(`/shop/${id}`)
}

onMounted(async () => {
  // 获取商户类型
  const typeRes = await getShopTypes()
  if (typeRes.success) {
    shopTypes.value = typeRes.data
  }

  // 获取热门商户
  const shopRes = await getShopsByType(1)
  if (shopRes.success) {
    hotShops.value = shopRes.data.slice(0, 8)
  }
})
</script>

<style scoped>
.home {
  padding: 20px 0;
}

.category-nav {
  display: flex;
  justify-content: space-around;
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  color: #666;
  transition: color 0.3s;
}

.category-item:hover {
  color: #409eff;
}

.category-item span {
  margin-top: 8px;
  font-size: 14px;
}

.banner {
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 20px;
}

.banner-item {
  height: 300px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #fff;
}

.banner-item h2 {
  font-size: 32px;
  margin-bottom: 10px;
}

.banner-item p {
  font-size: 18px;
}

.section {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.section-title {
  font-size: 20px;
  margin-bottom: 20px;
  color: #333;
}

.shop-card {
  cursor: pointer;
  transition: transform 0.3s;
}

.shop-card:hover {
  transform: translateY(-5px);
}

.shop-image {
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shop-info {
  padding: 15px;
}

.shop-info h4 {
  font-size: 16px;
  margin-bottom: 10px;
  color: #333;
}

.shop-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.price {
  color: #f56c6c;
  font-weight: bold;
}

.address {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
