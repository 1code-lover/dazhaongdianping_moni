/**
 * 我的评价列表页
 * 展示当前用户发表的所有评价
 *
 * @author ethan
 * @date 2026-07-01
 */
<template>
  <div class="page-shell">
    <div class="page-container my-reviews-page">
      <section class="review-card section-block">
        <div class="section-header">
          <div>
            <span class="eyebrow">评价管理</span>
            <h1 class="section-title">我的评价</h1>
            <p class="section-subtitle">查看我发表的所有评价记录</p>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="loading">
          <el-skeleton :rows="3" animated />
        </div>

        <!-- 加载失败 -->
        <div v-else-if="error" class="error-state">
          <el-empty description="加载失败，请稍后重试">
            <el-button type="primary" @click="fetchReviews">重新加载</el-button>
          </el-empty>
        </div>

        <!-- 空数据 -->
        <div v-else-if="reviews.length === 0" class="empty-state">
          <el-empty description="您还没有发表过评价" />
        </div>

        <!-- 评价列表 -->
        <div v-else class="review-list">
          <article v-for="review in reviews" :key="review.id" class="review-item">
            <div class="review-top">
              <div class="shop-info">
                <strong>{{ review.shopName || '未知商户' }}</strong>
                <el-rate :model-value="review.score" disabled size="small" />
              </div>
              <span class="review-time">{{ review.createTime }}</span>
            </div>

            <div class="review-content">
              <p>{{ review.content }}</p>
              <div v-if="review.images && review.images.length > 0" class="review-images">
                <img
                  v-for="(img, idx) in review.images"
                  :key="idx"
                  :src="img"
                  alt="评价图片"
                  @click="previewImage(review.images, idx)"
                >
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
          v-if="total > pageSize"
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="handlePageChange"
          style="margin-top: 20px; justify-content: center;"
        />
      </section>
    </div>

    <!-- 图片预览 -->
    <el-image-viewer
      v-if="previewVisible"
      :url-list="previewList"
      :initial-index="previewIndex"
      @close="previewVisible = false"
    />
  </div>
</template>

<script setup>
/**
 * 我的评价列表组件
 * 展示当前用户的评价历史
 */
import { ref, onMounted } from 'vue'
import { getMyReviews } from '../api/review'

const reviews = ref([])
const loading = ref(false)
const error = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const previewVisible = ref(false)
const previewList = ref([])
const previewIndex = ref(0)

/**
 * 预览图片
 */
const previewImage = (images, index) => {
  previewList.value = images
  previewIndex.value = index
  previewVisible.value = true
}

/**
 * 获取我的评价列表
 */
const fetchReviews = async () => {
  loading.value = true
  error.value = false

  try {
    const res = await getMyReviews(currentPage.value, pageSize.value)
    if (res.success && res.data) {
      // 处理图片字段：逗号分隔的字符串转数组
      reviews.value = (res.data.records || res.data || []).map(review => ({
        ...review,
        images: review.images ? review.images.split(',').filter(Boolean) : []
      }))
      total.value = res.data.total || reviews.value.length
    } else {
      error.value = true
    }
  } catch (err) {
    console.error('加载我的评价列表失败:', err)
    error.value = true
    reviews.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 分页切换
 */
const handlePageChange = (page) => {
  currentPage.value = page
  fetchReviews()
}

onMounted(() => {
  fetchReviews()
})
</script>

<style scoped>
.my-reviews-page {
  max-width: 900px;
  margin: 0 auto;
}

.review-card {
  padding: 28px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 18px;
}

.review-item {
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.84);
}

.review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.shop-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.shop-info strong {
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
  .review-card {
    padding: 22px;
  }

  .review-top {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
