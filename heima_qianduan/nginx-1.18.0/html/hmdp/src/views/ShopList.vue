<template>
  <div class="page-shell">
    <div class="page-container shop-list-page">
      <section class="filter-panel section-block">
        <div class="section-header">
          <div>
            <span class="eyebrow">商户列表</span>
            <h1 class="section-title">发现附近适合去的店</h1>
            <p class="section-subtitle">支持按分类和关键词查看当前平台已有商户。</p>
          </div>
          <div class="search-bar">
            <el-input
              v-model="keyword"
              placeholder="搜索商户名称"
              clearable
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button @click="handleSearch">搜索</el-button>
              </template>
            </el-input>
          </div>
        </div>
      </section>

      <section class="result-panel section-block">
        <!-- 加载中 -->
        <div v-if="loading" class="loading-state">
          <el-skeleton :rows="3" animated />
        </div>

        <!-- 加载失败 -->
        <div v-else-if="error" class="error-state">
          <el-empty description="加载失败，请稍后重试">
            <el-button type="primary" @click="fetchShops">重新加载</el-button>
          </el-empty>
        </div>

        <!-- 空数据 -->
        <div v-else-if="shops.length === 0" class="empty-state">
          <el-empty description="当前没有找到匹配的商户" />
        </div>

        <!-- 数据列表 -->
        <div v-else class="shop-items">
          <article v-for="shop in shops" :key="shop.id" class="shop-item" @click="goDetail(shop.id)">
            <div class="shop-image media-placeholder">
              <img v-if="resolveShopImage(shop)" :src="resolveShopImage(shop)" :alt="shop.name">
              <el-icon v-else :size="42"><Service /></el-icon>
            </div>

            <div class="shop-info">
              <div class="shop-main">
                <div>
                  <h3>{{ shop.name }}</h3>
                  <p class="shop-address">
                    <el-icon><Location /></el-icon>
                    <span>{{ shop.address || '暂无地址信息' }}</span>
                  </p>
                </div>
                <div class="shop-price">¥{{ shop.avgPrice || 0 }}/人</div>
              </div>

              <div class="shop-bottom">
                <el-rate :model-value="shop.score" disabled show-score />
                <el-button type="primary" plain>查看详情</el-button>
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
 * 商户列表组件
 * 支持根据分类或关键字浏览商户
 *
 * @author ethan
 * @date 2026-06-21
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Location, Service } from '@element-plus/icons-vue'
import { getShopsByType, searchShops } from '../api/shop'

const route = useRoute()
const router = useRouter()
const keyword = ref(route.query.keyword || '')
const shops = ref([])
const loading = ref(false)
const error = ref(false)

/**
 * 解析商户图片
 */
const resolveShopImage = (shop) => {
  if (!shop?.images) {
    return ''
  }
  return shop.images.split(',')[0] || ''
}

/**
 * 跳转商户详情页
 */
const goDetail = (id) => {
  router.push(`/shop/${id}`)
}

/**
 * 执行关键字搜索
 */
const handleSearch = async () => {
  const value = keyword.value.trim()
  if (!value) {
    await fetchShops()
    return
  }

  router.replace({ path: '/shop', query: { keyword: value } })
}

/**
 * 拉取商户列表
 */
const fetchShops = async () => {
  loading.value = true
  error.value = false

  try {
    const typeId = route.query.typeId
    const searchKeyword = route.query.keyword

    if (searchKeyword) {
      keyword.value = searchKeyword
      const res = await searchShops(searchKeyword)
      if (res.success) {
        shops.value = res.data || []
      }
      return
    }

    if (typeId) {
      const res = await getShopsByType(typeId)
      if (res.success) {
        shops.value = res.data || []
      }
      return
    }

    shops.value = []
  } catch (err) {
    console.error('加载商户列表失败:', err)
    error.value = true
    shops.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query,
  () => {
    fetchShops()
  }
)

onMounted(() => {
  fetchShops()
})
</script>

<style scoped>
.shop-list-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.filter-panel,
.result-panel {
  padding: 28px;
}

.search-bar {
  width: min(360px, 100%);
}

.shop-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.shop-item {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  padding: 18px;
  border-radius: 24px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.84);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.shop-item:hover {
  transform: translateY(-3px);
  border-color: rgba(255, 107, 53, 0.2);
  box-shadow: 0 20px 34px rgba(15, 23, 42, 0.08);
}

.shop-image {
  height: 168px;
  border-radius: 22px;
  overflow: hidden;
}

.shop-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.shop-info {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 18px;
}

.shop-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.shop-main h3 {
  margin: 0 0 12px;
  font-size: 24px;
}

.shop-address {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  color: var(--text-color-secondary);
  line-height: 1.7;
}

.shop-price {
  white-space: nowrap;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(229, 72, 77, 0.1);
  color: var(--danger-color);
  font-weight: 700;
}

.shop-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

@media (max-width: 768px) {
  .filter-panel,
  .result-panel {
    padding: 22px;
  }

  .shop-item {
    grid-template-columns: 1fr;
  }

  .shop-main,
  .shop-bottom {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
