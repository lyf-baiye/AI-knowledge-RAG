<template>
  <div v-if="!loggedIn" class="app-container">
    <login-form @login-success="onLoginSuccess" />
  </div>

  <div v-else class="app-container">
    <div class="header">
      <div>
        <h1>AI工程辅助中心</h1>
        <p>上传设计文档，智能问答，支持上下文记忆</p>
      </div>
      <div class="header-actions">
        <span class="user-info">{{ username }}</span>
        <el-button text style="color: white" @click="logout">退出登录</el-button>
      </div>
    </div>

    <div class="main-content">
      <div class="sidebar">
        <knowledge-base-manager
          :guest-mode="isGuest"
          :guest-knowledge-bases="guestData"
          @select-kb="selectedKBId = $event"
          @refresh-kb="loadKnowledgeBases"
        />
      </div>
      <div class="chat-container">
        <div class="chat-header">
          <h2>智能对话</h2>
          <el-select
            v-model="selectedKBId"
            placeholder="选择知识库"
            style="width: 200px"
            @change="onKBChange"
          >
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
        </div>
        <chat-area :knowledge-base-id="selectedKBId" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { knowledgeBaseAPI } from './api'
import LoginForm from './components/LoginForm.vue'
import KnowledgeBaseManager from './components/KnowledgeBaseManager.vue'
import ChatArea from './components/ChatArea.vue'

const loggedIn = ref(false)
const isGuest = ref(false)
const username = ref('')
const knowledgeBases = ref([])
const selectedKBId = ref(null)

// 游客模式模拟数据
const guestData = ref([
  { id: 1, name: '设计规范库', description: '公司设计规范文档' },
  { id: 2, name: '项目文档库', description: '项目相关技术文档' }
])

const onLoginSuccess = (user) => {
  username.value = user.username
  loggedIn.value = true
  isGuest.value = user.username === '游客'
  if (isGuest.value) {
    knowledgeBases.value = guestData.value
    selectedKBId.value = guestData.value[0].id
  } else {
    loadKnowledgeBases()
  }
}

const logout = () => {
  localStorage.clear()
  loggedIn.value = false
  isGuest.value = false
  username.value = ''
  knowledgeBases.value = []
  selectedKBId.value = null
}

const loadKnowledgeBases = async () => {
  if (isGuest.value) return
  try {
    const res = await knowledgeBaseAPI.list()
    if (res.data?.data?.records) {
      knowledgeBases.value = res.data.data.records
    }
    if (knowledgeBases.value.length > 0 && !selectedKBId.value) {
      selectedKBId.value = knowledgeBases.value[0].id
    }
  } catch (e) {
    console.error('Failed to load knowledge bases:', e)
  }
}

const onKBChange = () => {
  // ChatArea watches selectedKBId via prop
}

onMounted(() => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    loggedIn.value = true
    username.value = localStorage.getItem('username') || '用户'
    loadKnowledgeBases()
  }
})
</script>
