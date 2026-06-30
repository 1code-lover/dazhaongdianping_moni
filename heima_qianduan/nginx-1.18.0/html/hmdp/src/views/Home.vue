<template>
  <div class="page-shell">
    <div class="page-container home-page">
      <section class="hero section-block">
        <div class="hero-copy">
          <span class="eyebrow">城市精选推荐</span>
          <h1>把附近值得去的店，整理成一份更好逛的生活提案。</h1>
          <p>
            从热门美食到休闲娱乐，按品类快速发现高评分商户，直接查看套餐、价格和营业信息。
          </p>
          <div class="hero-actions">
            <el-button type="primary" size="large" @click="goShopList(primaryTypeId)">立即逛逛</el-button>
            <div class="hero-stats">
              <span class="stat-chip">分类 {{ shopTypes.length || 0 }}</span>
              <span class="stat-chip">推荐商户 {{ hotShops.length || 0 }}</span>
            </div>
          </div>
        </div>
        <div class="hero-banner">
          <div
            v-for="item in banners"
            :key="item.id"
            class="banner-card"
            :style="{ '--card-gradient': item.gradient }"
          >
            <span>{{ item.tag }}</span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.desc }}</p>
          </div>
        </div>
      </section>

      <section class="category-section section-block">
        <div class="section-header">
          <div>
            <h2 class="section-title">热门分类</h2>
            <p class="section-subtitle">优先展示当前平台里的核心消费场景</p>
          </div>
        </div>
        <div class="category-grid">
          <button
            v-for="type in shopTypes"
            :key="type.id"
            type="button"
            class="category-item"
            @click="goShopList(type.id)"
          >
            <div class="category-icon">
              <el-icon :size="26"><component :is="getIcon(type.name)" /></el-icon>
            </div>
            <div class="category-text">
              <strong>{{ type.name }}</strong>
              <span>{{ getCategoryDesc(type.name) }}</span>
            </div>
          </button>
        </div>
      </section>

      <section class="recommend-section section-block">
        <div class="section-header">
          <div>
            <h2 class="section-title">热门推荐</h2>
            <p class="section-subtitle">优先展示评分高、信息完整、适合新用户浏览的商户</p>
          </div>
          <el-button text @click="goShopList(primaryTypeId)">查看更多</el-button>
        </div>

        <div class="shop-grid">
          <article
            v-for="shop in hotShops"
            :key="shop.id"
            class="shop-card"
            @click="goShopDetail(shop.id)"
          >
            <div class="shop-cover media-placeholder">
              <img
                v-if="resolveShopImage(shop)"
                :src="resolveShopImage(shop)"
                :alt="shop.name"
              >
              <el-icon v-else :size="44"><component :is="getIcon('商户')" /></el-icon>
            </div>
            <div class="shop-content">
              <div class="shop-topline">
                <span class="shop-tag">精选商户</span>
                <span class="shop-price">¥{{ shop.avgPrice || 0 }}/人</span>
              </div>
              <h3>{{ shop.name }}</h3>
              <div class="shop-rating">
                <el-rate :model-value="shop.score" disabled show-score />
              </div>
              <p class="shop-address">{{ shop.address || '暂无详细地址' }}</p>
            </div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
/**
 * 首页组件
 * 展示推荐横幅、商户分类和热门商户列表
 *
 * @author ethan
 * @date 2026-06-21
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CoffeeCup, Dessert, ForkSpoon, Grid, House, Location, Mug, Opportunity, Service, Shop, Soccer } from '@element-plus/icons-vue'
import { getShopTypes, getShopsByType } from '../api/shop'

const router = useRouter()
const shopTypes = ref([])
const hotShops = ref([])

const banners = [
  {
    id: 1,
    tag: '新人福利',
    title: '首单立减 20 元',
    desc: '适合第一次下单快速体验平台服务。',
    gradient: 'linear-gradient(135deg, #ff7b54 0%, #ffb26b 100%)'
  },
  {
    id: 2,
    tag: '周末聚会',
    title: '多人套餐更划算',
    desc: 'KTV、聚餐和休闲娱乐一键浏览。',
    gradient: 'linear-gradient(135deg, #1d9bf0 0%, #77c8ff 100%)'
  },
  {
    id: 3,
    tag: '当季热点',
    title: '高评分商户优先看',
    desc: '减少盲选时间，优先浏览更值得点开的店。',
    gradient: 'linear-gradient(135deg, #7c5cff 0%, #ad8bff 100%)'
  }
]

const primaryTypeId = computed(() => {
  return shopTypes.value[0]?.id || 1
})

/**
 * 获取分类对应图标
 */
const getIcon = (name) => {
  const iconMap = {
    美食: ForkSpoon,
    KTV: Opportunity,
    丽人·美发: Dessert,
    美睫·美甲: Mug,
    按摩·足疗: CoffeeCup,
    美容SPA: House,
    亲子游乐: Soccer,
    酒吧: Grid,
    轰趴馆: Shop,
    健身运动: Location,
    商户: Service
  }
  return iconMap[name] || Service
}

/**
 * 获取分类描述文案
 */
const getCategoryDesc = (name) => {
  const descMap = {
    美食: '高频消费，适合先逛',
    KTV: '聚会场景热门选择',
    '丽人·美发': '到店项目展示更直观',
    '美睫·美甲': '适合活动前预约',
    '按摩·足疗': '夜间与周末需求高',
    '美容SPA': '重视套餐和口碑',
    亲子游乐: '周末家庭场景优先',
    酒吧: '夜生活场景聚合',
    轰趴馆: '多人活动重点品类',
    健身运动: '长期消费决策入口'
  }
  return descMap[name] || '浏览附近优质门店'
}

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
 * 跳转商户列表页
 */
const goShopList = (typeId) => {
  router.push(`/shop?typeId=${typeId}`)
}

/**
 * 跳转商户详情页
 */
const goShopDetail = (id) => {
  router.push(`/shop/${id}`)
}

/**
 * 初始化首页数据
 */
const loadHomeData = async () => {
  const typeRes = await getShopTypes()
  if (typeRes.success) {
    shopTypes.value = typeRes.data || []
  }

  const shopRes = await getShopsByType(primaryTypeId.value)
  if (shopRes.success) {
    hotShops.value = (shopRes.data || []).slice(0, 8)
  }
}

onMounted(() => {
  loadHomeData()
})
</script>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: 28px;
  padding: 34px;
}

.hero-copy h1 {
  margin: 18px 0 14px;
  font-size: clamp(30px, 4vw, 48px);
  line-height: 1.12;
}

.hero-copy p {
  max-width: 620px;
  margin: 0;
  font-size: 16px;
  line-height: 1.8;
  color: var(--text-color-secondary);
}

.hero-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 24px;
}

.hero-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-banner {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.banner-card {
  min-height: 150px;
  padding: 18px;
  border-radius: 24px;
  color: #fff;
  background: var(--card-gradient);
  box-shadow: 0 18px 34px rgba(31, 41, 55, 0.14);
}

.banner-card:nth-child(3) {
  grid-column: 1 / -1;
}

.banner-card span {
  display: inline-block;
  margin-bottom: 16px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  opacity: 0.92;
}

.banner-card strong {
  display: block;
  font-size: 24px;
  line-height: 1.2;
}

.banner-card p {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.6;
  opacity: 0.95;
}

.category-section,
.recommend-section {
  padding: 28px;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.category-item {
  width: 100%;
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.86);
  display: flex;
  align-items: center;
  gap: 14px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.category-item:hover {
  transform: translateY(-4px);
  border-color: rgba(255, 107, 53, 0.2);
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.08);
}

.category-icon {
  width: 54px;
  height: 54px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  background: linear-gradient(135deg, rgba(255, 107, 53, 0.16) 0%, rgba(255, 209, 102, 0.2) 100%);
}

.category-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.category-text strong {
  font-size: 15px;
}

.category-text span {
  font-size: 12px;
  color: var(--text-color-secondary);
}

.shop-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.shop-card {
  overflow: hidden;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.06);
  cursor: pointer;
  transition: transform 0.28s ease, box-shadow 0.28s ease;
}

.shop-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-hover);
}

.shop-cover {
  height: 214px;
  background: linear-gradient(135deg, #ff7b54 0%, #ffb26b 100%);
}

.shop-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.shop-content {
  padding: 18px;
}

.shop-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.shop-tag {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 107, 53, 0.12);
  color: var(--primary-color-dark);
  font-size: 12px;
  font-weight: 600;
}

.shop-price {
  color: var(--danger-color);
  font-size: 14px;
  font-weight: 700;
}

.shop-content h3 {
  margin: 0 0 12px;
  font-size: 18px;
  line-height: 1.4;
}

.shop-rating {
  margin-bottom: 10px;
}

.shop-address {
  margin: 0;
  min-height: 40px;
  color: var(--text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .category-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .shop-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .hero {
    grid-template-columns: 1fr;
    padding: 22px;
  }

  .hero-banner {
    grid-template-columns: 1fr;
  }

  .banner-card:nth-child(3) {
    grid-column: auto;
  }

  .category-section,
  .recommend-section {
    padding: 22px;
  }

  .category-grid,
  .shop-grid {
    grid-template-columns: 1fr;
  }
}
</style>
