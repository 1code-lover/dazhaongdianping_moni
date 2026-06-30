<template>
  <div class="page-shell">
    <!-- 加载中 -->
    <div v-if="loading" class="page-container">
      <section class="section-block" style="padding: 28px;">
        <el-skeleton :rows="5" animated />
      </section>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="error" class="page-container">
      <section class="section-block" style="padding: 28px;">
        <el-empty description="加载失败，请稍后重试">
          <el-button type="primary" @click="fetchDetail">重新加载</el-button>
        </el-empty>
      </section>
    </div>

    <!-- 商户详情 -->
    <div v-else-if="shop" class="page-container shop-detail-page">
      <section class="hero-card section-block">
        <div class="hero-media media-placeholder">
          <img v-if="coverImage" :src="coverImage" :alt="shop.name">
          <el-icon v-else :size="58"><Service /></el-icon>
        </div>

        <div class="hero-content">
          <span class="eyebrow">商户详情</span>
          <h1>{{ shop.name }}</h1>
          <div class="meta-row">
            <el-rate :model-value="shop.score" disabled show-score />
            <span class="price-badge">¥{{ shop.avgPrice || 0 }}/人</span>
          </div>
          <p class="meta-item">
            <el-icon><Location /></el-icon>
            <span>{{ shop.address || '暂无地址信息' }}</span>
          </p>
          <p v-if="shop.openHours" class="meta-item">
            <el-icon><Clock /></el-icon>
            <span>营业时间：{{ shop.openHours }}</span>
          </p>
        </div>
      </section>

      <section v-if="combos.length > 0" class="combo-card section-block">
        <div class="section-header">
          <div>
            <h2 class="section-title">优惠套餐</h2>
            <p class="section-subtitle">直接展示当前商户可购买的套餐信息与优惠力度。</p>
          </div>
        </div>

        <div class="combo-list">
          <article v-for="combo in combos" :key="combo.id" class="combo-item">
            <div class="combo-main">
              <h3>{{ combo.title }}</h3>
              <p class="combo-desc">{{ combo.subTitle }}</p>
              <div class="combo-meta">
                <span class="current-price">¥{{ (combo.price / 100).toFixed(2) }}</span>
                <span class="original-price">¥{{ (combo.originalPrice / 100).toFixed(2) }}</span>
                <el-tag type="danger" size="small">
                  省 ¥{{ ((combo.originalPrice - combo.price) / 100).toFixed(2) }}
                </el-tag>
              </div>
              <p class="combo-sales">已售 {{ combo.sales }} 份</p>
            </div>

            <el-button type="primary" size="large" @click="buyCombo(combo)">立即购买</el-button>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
/**
 * 商户详情组件
 * 展示商户基本信息和可购买套餐
 *
 * @author ethan
 * @date 2026-06-21
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, Location, Service } from '@element-plus/icons-vue'
import { getCombosByShop } from '../api/combo'
import { getShopById } from '../api/shop'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const shop = ref(null)
const combos = ref([])
const loading = ref(false)
const error = ref(false)

const coverImage = computed(() => {
  if (!shop.value?.images) {
    return ''
  }
  return shop.value.images.split(',')[0] || ''
})

/**
 * 购买套餐
 */
const buyCombo = (combo) => {
  if (!userStore.token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  router.push(`/order/confirm?type=2&bizId=${combo.id}&shopId=${shop.value.id}`)
}

/**
 * 加载商户和套餐详情
 */
const fetchDetail = async () => {
  loading.value = true
  error.value = false

  try {
    const shopId = route.params.id
    const shopRes = await getShopById(shopId)
    if (shopRes.success) {
      shop.value = shopRes.data
    } else {
      error.value = true
      return
    }

    const comboRes = await getCombosByShop(shopId)
    if (comboRes.success) {
      combos.value = comboRes.data || []
    }
  } catch (err) {
    console.error('加载商户详情失败:', err)
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.shop-detail-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.hero-card {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: 28px;
  padding: 30px;
}

.hero-media {
  min-height: 320px;
  border-radius: 28px;
  overflow: hidden;
}

.hero-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-content h1 {
  margin: 18px 0 16px;
  font-size: 38px;
  line-height: 1.2;
}

.meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}

.price-badge {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(229, 72, 77, 0.1);
  color: var(--danger-color);
  font-weight: 700;
}

.meta-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0 0 12px;
  color: var(--text-color-secondary);
  line-height: 1.7;
}

.combo-card {
  padding: 28px;
}

.combo-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.combo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.84);
}

.combo-main h3 {
  margin: 0 0 10px;
  font-size: 22px;
}

.combo-desc {
  margin: 0 0 12px;
  color: var(--text-color-secondary);
}

.combo-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.current-price {
  color: var(--danger-color);
  font-size: 26px;
  font-weight: 800;
}

.original-price {
  color: var(--text-color-muted);
  text-decoration: line-through;
}

.combo-sales {
  margin: 0;
  color: var(--text-color-secondary);
}

@media (max-width: 768px) {
  .hero-card,
  .combo-item {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-card,
  .combo-card {
    padding: 22px;
  }

  .hero-content h1 {
    font-size: 30px;
  }

  .hero-media {
    min-height: 240px;
  }
}
</style>
