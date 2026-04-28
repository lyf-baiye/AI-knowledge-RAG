<template>
  <div class="login-container">
    <h2>{{ isRegister ? '注册' : '登录' }}</h2>
    <el-form :model="form" label-width="0" @submit.prevent>
      <el-form-item>
        <el-input v-model="form.username" placeholder="用户名" size="large" />
      </el-form-item>
      <el-form-item v-if="isRegister">
        <el-input v-model="form.email" placeholder="邮箱" size="large" />
      </el-form-item>
      <el-form-item>
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          size="large"
          show-password
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          size="large"
          style="width: 100%"
          :loading="loading"
          @click="submit"
        >
          {{ isRegister ? '注册' : '登录' }}
        </el-button>
      </el-form-item>
    </el-form>
    <el-button text type="primary" style="width: 100%" @click="isRegister = !isRegister">
      {{ isRegister ? '已有账号？立即登录' : '没有账号？立即注册' }}
    </el-button>
    <el-divider />
    <el-button type="success" size="large" style="width: 100%" @click="guestLogin">
      游客体验（跳过登录）
    </el-button>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { authAPI } from '../api'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['login-success'])

const isRegister = ref(false)
const loading = ref(false)
const form = reactive({
  username: '',
  email: '',
  password: ''
})

const submit = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (isRegister.value && !form.email) {
    ElMessage.warning('请填写邮箱')
    return
  }

  loading.value = true
  try {
    const api = isRegister.value ? authAPI.register : authAPI.login
    const payload = isRegister.value
      ? { username: form.username, email: form.email, password: form.password }
      : { username: form.username, password: form.password }

    const res = await api(payload)
    const data = res.data?.data
    if (data) {
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      localStorage.setItem('username', data.user?.username || form.username)
      ElMessage.success(isRegister.value ? '注册成功' : '登录成功')
      emit('login-success', data.user || { username: form.username })
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const guestLogin = () => {
  ElMessage.success('已进入游客体验模式')
  emit('login-success', { username: '游客' })
}
</script>
