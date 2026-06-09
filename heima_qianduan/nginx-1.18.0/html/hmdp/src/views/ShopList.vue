<template>
  <div class="shop-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>商户列表</span>
          <div class="search-bar">
            <el-input
              v-model="keyword"
              placeholder="搜索商户..."
              clearable
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button @click="handleSearch">搜索</el-button>
              </template>
            </el-input>
          </div>
        </div>
      </template>

      <el-empty v-if="shops.length === 0" description="暂无商户" />

      <div class="shop-items">
        <div v-for="shop in shops" :key="shop.id" class="shop-item" @click="goDetail(shop.id)">
          <div class="shop-image">
            <el-icon :size="48"><Service /></el-icon>
          </div>
          <div class="shop-info">
            <h3>{{ shop.name }}</h3>
            <div class="shop-meta">
              <el-rate v-model="shop.score" disabled show-score />
              <span class="price">¥{{ shop.avgPrice }}/人</span>
            </div>
            <p class="address">
              <el-icon><Location /></el-icon>
              {{ shop.address }}
            </p>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchShops, getShopsByType } from '../api/shop'
import { Service, Location } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const keyword = ref(route.query.keyword || '')
const shops = ref([])

const goDetail = (id) => {
  router.push(`/shop/${id}`)
}

const handleSearch = async () => {
  if (keyword.value) {
    const res = await searchShops(keyword.value)
    if (res.success) {
      shops.value = res.data
    }
  }
}

const fetchShops = async () => {
  const typeId = route.query.typeId
  if (typeId) {
    const res = await getShopsByType(typeId)
    if (res.success) {
      shops.value = res.data
    }
  } else if (keyword.value) {
    await handleSearch()
  }
}

onMounted(() => {
  fetchShops()
})
</script>

<style scoped>
.shop-list {
  padding: 20px 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  width: 300px;
}

.shop-items {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.shop-item {
  display: flex;
  gap: 20px;
  padding: 15px;
  border: 1px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.shop-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.shop-image {
  width: 120px;
  height: 120px;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.shop-info {
  flex: 1;
}

.shop-info h3 {
  font-size: 18px;
  margin-bottom: 10px;
}

.shop-meta {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 10px;
}

.price {
  color: #f56c6c;
  font-weight: bold;
}

.address {
  color: #666;
  display: flex;
  align-items: center;
  gap: 5px;
}
</style>
