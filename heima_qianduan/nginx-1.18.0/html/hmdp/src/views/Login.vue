<template>
  <div class="login-page">
    <div class="page-container login-layout">
      <section class="login-copy section-block">
        <span class="eyebrow">账号登录</span>
        <h1>登录后，收藏、订单、套餐和个人中心都能无缝联动。</h1>
        <p>
          使用手机号验证码快速登录，适合本地联调用户态、下单流程、秒杀流程和个人中心页展示。
        </p>

        <div class="login-highlights">
          <article class="highlight-card">
            <strong>验证码登录</strong>
            <span>接 Redis 登录态，便于测试完整鉴权链路</span>
          </article>
          <article class="highlight-card">
            <strong>订单联动</strong>
            <span>登录后可直接进入订单、套餐购买和个人页</span>
          </article>
          <article class="highlight-card">
            <strong>页面展示友好</strong>
            <span>适合做 README 截图、项目答辩和联调演示</span>
          </article>
        </div>
      </section>

      <el-card class="login-card">
        <template #header>
          <div class="card-header">
            <div>
              <h2>欢迎回来</h2>
              <p>输入手机号并完成验证码校验</p>
            </div>
          </div>
        </template>

        <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
          </el-form-item>

          <el-form-item label="验证码" prop="code">
            <div class="code-input">
              <el-input v-model="form.code" placeholder="请输入验证码" maxlength="6" />
              <el-button
                type="primary"
                plain
                :disabled="countdown > 0"
                @click="sendCode"
              >
                {{ countdown > 0 ? `${countdown}s后重试` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <div class="tip-row">
            未接入真实短信平台时，可直接通过 Redis 或后端日志查看验证码完成本地测试。
          </div>

          <el-form-item>
            <el-button type="primary" class="login-btn" @click="handleLogin" :loading="loading">
              登录并进入首页
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { sendCode as sendCodeApi, login as loginApi } from '../api/user'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const countdown = ref(0)

const form = reactive({
  phone: '',
  code: ''
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位', trigger: 'blur' }
  ]
}

const sendCode = async () => {
  if (!form.phone) {
    ElMessage.warning('请先输入手机号')
    return
  }

  try {
    const res = await sendCodeApi(form.phone)
    if (res.success) {
      ElMessage.success('验证码发送成功')
      countdown.value = 60
      const timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0) {
          clearInterval(timer)
        }
      }, 1000)
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
  }
}

const handleLogin = async () => {
  try {
    await formRef.value.validate()
    loading.value = true

    const res = await loginApi(form.phone, form.code)
    if (res.success) {
      userStore.setToken(res.data)
      await userStore.fetchUserInfo()
      ElMessage.success('登录成功')
      router.push('/')
    }
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: calc(100vh - 100px);
}

.login-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(380px, 0.95fr);
  gap: 28px;
  align-items: stretch;
}

.login-copy {
  padding: 34px;
}

.login-copy h1 {
  margin: 18px 0 14px;
  font-size: clamp(32px, 4vw, 46px);
  line-height: 1.16;
}

.login-copy p {
  margin: 0;
  max-width: 620px;
  color: var(--text-color-secondary);
  line-height: 1.9;
}

.login-highlights {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 28px;
}

.highlight-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 132px;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.82) 0%, rgba(255, 250, 245, 0.9) 100%);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.highlight-card strong {
  font-size: 16px;
}

.highlight-card span {
  color: var(--text-color-secondary);
  line-height: 1.7;
  font-size: 13px;
}

.login-card {
  width: 100%;
  align-self: stretch;
}

.card-header h2 {
  margin: 0 0 8px;
  font-size: 28px;
}

.card-header p {
  margin: 0;
  color: var(--text-color-secondary);
}

.code-input {
  display: flex;
  gap: 10px;
}

.code-input .el-input {
  flex: 1;
}

.tip-row {
  margin: -4px 0 18px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 107, 53, 0.08);
  color: var(--text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.login-btn {
  width: 100%;
  min-height: 46px;
  font-size: 15px;
}

@media (max-width: 992px) {
  .login-layout {
    grid-template-columns: 1fr;
  }

  .login-highlights {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .login-copy,
  .login-card {
    padding: 0;
  }

  .login-copy {
    padding: 24px;
  }

  .code-input {
    flex-direction: column;
  }
}
</style>
