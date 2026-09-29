/**
 * 评价提交表单组件
 * 用于用户对已完成订单发表评价
 *
 * @author ethan
 * @date 2026-07-01
 */
<template>
  <el-dialog
    v-model="visible"
    title="发表评价"
    width="600px"
    :before-close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
    >
      <el-form-item label="评分" prop="score">
        <el-rate v-model="form.score" :max="5" show-text />
      </el-form-item>

      <el-form-item label="评价内容" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="5"
          maxlength="500"
          show-word-limit
          placeholder="请输入您的评价（不超过500字）"
        />
      </el-form-item>

      <el-form-item label="上传图片">
        <el-upload
          v-model:file-list="fileList"
          action="/api/upload/review"
          list-type="picture-card"
          :limit="9"
          :on-exceed="handleExceed"
          :on-preview="handlePreview"
          :on-remove="handleRemove"
          :on-success="handleUploadSuccess"
          :headers="uploadHeaders"
        >
          <el-icon><Plus /></el-icon>
        </el-upload>
        <div class="upload-tip">最多上传9张图片</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        提交评价
      </el-button>
    </template>

    <!-- 图片预览 -->
    <el-image-viewer
      v-if="previewVisible"
      :url-list="previewList"
      :initial-index="previewIndex"
      @close="previewVisible = false"
    />
  </el-dialog>
</template>

<script setup>
/**
 * 评价表单组件
 * 支持评分、文字、图片上传（真实上传到服务器）
 */
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { submitReview } from '../api/review'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  orderId: {
    type: Number,
    required: true
  },
  orderType: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref(null)
const form = ref({
  score: 5,
  content: '',
  images: ''
})

const rules = {
  score: [
    { required: true, message: '请选择评分', trigger: 'change' }
  ],
  content: [
    { max: 500, message: '评价内容不超过500字', trigger: 'blur' }
  ]
}

const fileList = ref([])
const submitting = ref(false)
const previewVisible = ref(false)
const previewList = ref([])
const previewIndex = ref(0)

// 上传请求头（携带 token）
const uploadHeaders = computed(() => ({
  authorization: userStore.token || ''
}))

/**
 * 超出文件数量限制
 */
const handleExceed = () => {
  ElMessage.warning('最多上传9张图片')
}

/**
 * 预览图片
 */
const handlePreview = (file) => {
  previewList.value = fileList.value.map(f => f.url || URL.createObjectURL(f.raw))
  previewIndex.value = fileList.value.findIndex(f => f.uid === file.uid)
  previewVisible.value = true
}

/**
 * 移除图片
 */
const handleRemove = (file) => {
  const index = fileList.value.findIndex(f => f.uid === file.uid)
  if (index > -1) {
    fileList.value.splice(index, 1)
  }
}

/**
 * 图片上传成功回调
 */
const handleUploadSuccess = (response, file) => {
  if (response.success) {
    // 后端返回图片 URL，存到 file 对象上
    file.serverUrl = response.data
  } else {
    ElMessage.error('图片上传失败')
    handleRemove(file)
  }
}

/**
 * 关闭对话框
 */
const handleClose = () => {
  visible.value = false
  resetForm()
}

/**
 * 重置表单
 */
const resetForm = () => {
  formRef.value?.resetFields()
  fileList.value = []
  form.value = {
    score: 5,
    content: '',
    images: ''
  }
}

/**
 * 提交评价
 */
const handleSubmit = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  // 检查是否有图片还在上传中
  const uploading = fileList.value.some(file => file.status === 'uploading')
  if (uploading) {
    ElMessage.warning('图片正在上传中，请稍候')
    return
  }

  submitting.value = true

  try {
    // 收集已上传成功的图片 URL
    const imageUrls = fileList.value
      .filter(file => file.serverUrl)
      .map(file => file.serverUrl)

    const data = {
      orderId: props.orderId,
      orderType: props.orderType,
      score: form.value.score,
      content: form.value.content,
      images: imageUrls.join(',')
    }

    const res = await submitReview(data)
    if (res.success) {
      ElMessage.success('评价成功')
      emit('success')
      handleClose()
    } else {
      ElMessage.error(res.errorMsg || '评价失败')
    }
  } catch (error) {
    console.error('提交评价失败:', error)
    ElMessage.error('评价失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.upload-tip {
  margin-top: 8px;
  color: var(--text-color-secondary);
  font-size: 12px;
}

:deep(.el-upload--picture-card) {
  width: 100px;
  height: 100px;
}

:deep(.el-upload-list--picture-card .el-upload-list__item) {
  width: 100px;
  height: 100px;
}
</style>
