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
          <div class="highlight-row">
            <span v-if="shop.area" class="highlight-pill">{{ shop.area }}</span>
            <span class="highlight-pill">评分 {{ shop.score || 0 }}</span>
            <span class="highlight-pill">支持套餐购买</span>
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

      <section class="insight-card section-block">
        <div class="insight-grid">
          <article>
            <strong>适合什么场景</strong>
            <p>适合本地联调商户详情、套餐购买、订单确认和用户态跳转链路。</p>
          </article>
          <article>
            <strong>当前页适合截图</strong>
            <p>店铺图、评分、营业时间、价格和套餐区块同时展示，适合 README 或项目介绍页。</p>
          </article>
          <article>
            <strong>建议联调路径</strong>
            <p>首页 → 商户列表 → 商户详情 → 套餐购买 → 订单确认，这条路径最适合展示系统完整性。</p>
          </article>
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

      <!-- 评价列表 -->
      <section class="review-card section-block">
        <div class="section-header">
          <div>
            <h2 class="section-title">用户评价</h2>
            <p class="section-subtitle">已有 {{ reviewTotal }} 条评价，平均评分 {{ shop.avgScore || 0 }} 分</p>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="reviewLoading">
          <el-skeleton :rows="3" animated />
        </div>

        <!-- 空数据 -->
        <div v-else-if="reviews.length === 0" class="empty-state">
          <el-empty description="暂无评价" />
        </div>

        <!-- 评价列表 -->
        <div v-else class="review-list">
          <article v-for="review in reviews" :key="review.id" class="review-item">
            <div class="review-header">
              <div class="review-user">
                <strong>{{ review.userName || '匿名用户' }}</strong>
                <el-rate :model-value="review.score" disabled size="small" />
              </div>
              <span class="review-time">{{ review.createTime }}</span>
            </div>

            <div class="review-content">
              <p>{{ review.content }}</p>
              <div v-if="review.images && review.images.length > 0" class="review-images">
                <img v-for="(img, idx) in review.images" :key="idx" :src="img" alt="评价图片">
              </div>
            </div>

            <!-- 商家回复 -->
            <div v-if="review.reply" class="review-reply">
              <strong>商家回复：</strong>
              <p>{{ review.reply }}</p>
              <span class="reply-time">{{ review.replyTime }}</span>
            </div>
          </article>
        </div>

        <!-- 分页 -->
        <el-pagination
          v-if="reviewTotal > reviewPageSize"
          v-model:current-page="reviewCurrentPage"
          :page-size="reviewPageSize"
          :total="reviewTotal"
          layout="prev, pager, next"
          @current-change="handleReviewPageChange"
          style="margin-top: 20px; justify-content: center;"
        />
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
import { getShopReviews } from '../api/review'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const shop = ref(null)
const combos = ref([])
const loading = ref(false)
const error = ref(false)

// 评价相关
const reviews = ref([])
const reviewLoading = ref(false)
const reviewCurrentPage = ref(1)
const reviewPageSize = ref(10)
const reviewTotal = ref(0)

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
 * 加载评价列表
 */
const fetchReviews = async () => {
  reviewLoading.value = true
  try {
    const shopId = route.params.id
    const res = await getShopReviews(shopId, reviewCurrentPage.value, reviewPageSize.value)
    if (res.success && res.data) {
      // 处理图片字段：逗号分隔的字符串转数组
      reviews.value = (res.data.records || res.data || []).map(review => ({
        ...review,
        images: review.images ? review.images.split(',').filter(Boolean) : []
      }))
      reviewTotal.value = res.data.total || reviews.value.length
    }
  } catch (err) {
    console.error('加载评价列表失败:', err)
  } finally {
    reviewLoading.value = false
  }
}

/**
 * 评价分页切换
 */
const handleReviewPageChange = (page) => {
  reviewCurrentPage.value = page
  fetchReviews()
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

    // 加载评价列表
    fetchReviews()
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

.highlight-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0 0 18px;
}

.highlight-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(15, 23, 42, 0.06);
  color: var(--text-color-secondary);
  font-size: 13px;
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

.insight-card {
  padding: 26px 28px;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.insight-grid article {
  min-height: 138px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.insight-grid strong {
  display: block;
  margin-bottom: 10px;
  font-size: 16px;
}

.insight-grid p {
  margin: 0;
  color: var(--text-color-secondary);
  line-height: 1.8;
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

/* 评价卡片 */
.review-card {
  padding: 28px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-item {
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.84);
}

.review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.review-user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-user strong {
  font-size: 16px;
}

.review-time {
  color: var(--text-color-muted);
  font-size: 14px;
}

.review-content p {
  margin: 0 0 12px;
  color: var(--text-color-regular);
  line-height: 1.7;
}

.review-images {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-images img {
  width: 100px;
  height: 100px;
  border-radius: 12px;
  object-fit: cover;
  cursor: pointer;
  transition: transform 0.2s;
}

.review-images img:hover {
  transform: scale(1.05);
}

.review-reply {
  margin-top: 12px;
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(229, 72, 77, 0.05);
}

.review-reply strong {
  color: var(--danger-color);
  font-size: 14px;
}

.review-reply p {
  margin: 8px 0 4px;
  color: var(--text-color-secondary);
  line-height: 1.6;
}

.reply-time {
  color: var(--text-color-muted);
  font-size: 12px;
}

@media (max-width: 768px) {
  .hero-card,
  .combo-item {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-card,
  .combo-card,
  .review-card,
  .insight-card {
    padding: 22px;
  }

  .hero-content h1 {
    font-size: 30px;
  }

  .hero-media {
    min-height: 240px;
  }

  .review-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .insight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
