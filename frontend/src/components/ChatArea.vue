<template>
  <div class="message-list" ref="msgListRef">
    <div v-if="messages.length === 0" class="empty-chat">
      <h3>欢迎使用团队智能问答系统</h3>
      <p>上传设计文档后，我可以基于知识库回答您的问题</p>
    </div>

    <div
      v-for="(msg, idx) in messages"
      :key="idx"
      :class="['message', msg.role]"
    >
      <div class="message-header">
        <span>{{ msg.role === 'user' ? '您' : 'AI 助手' }}</span>
        <span>{{ msg.time }}</span>
      </div>
      <div class="message-content">{{ msg.content }}</div>

      <div v-if="msg.sources && msg.sources.length > 0" class="knowledge-sources">
        <h4>参考来源：</h4>
        <ul>
          <li v-for="(src, i) in msg.sources" :key="i">
            {{ src.fileName ? `[${src.fileName}] ` : '' }}{{ truncate(src.content, 120) }}
            <span style="color: #999">({{ (src.score * 100).toFixed(1) }}%)</span>
          </li>
        </ul>
      </div>
    </div>
  </div>

  <div class="input-area">
    <el-input
      v-model="userInput"
      type="textarea"
      :rows="3"
      placeholder="输入问题... (Enter 发送，Shift+Enter 换行)"
      @keydown="onKeyDown"
    />
    <el-button type="primary" :loading="loading" size="large" @click="send">
      发送
    </el-button>
  </div>

  <div class="chat-controls">
    <el-button @click="clearChat" size="small">清空对话</el-button>
    <span style="font-size: 12px; color: #999">会话ID: {{ sessionId.slice(-8) }}</span>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { ragAPI } from '../api'
import { ElMessage } from 'element-plus'

const props = defineProps({
  knowledgeBaseId: { type: Number, default: null }
})

const messages = ref([])
const userInput = ref('')
const loading = ref(false)
const msgListRef = ref(null)
const sessionId = ref('session-' + Date.now())

const send = async () => {
  const question = userInput.value.trim()
  if (!question || loading.value) return
  userInput.value = ''

  messages.value.push({
    role: 'user',
    content: question,
    time: fmtTime()
  })

  loading.value = true
  try {
    const res = await ragAPI.query(
      {
        query: question,
        knowledgeBaseIds: props.knowledgeBaseId ? [props.knowledgeBaseId] : [],
        topK: 5
      },
      sessionId.value
    )

    const data = res.data?.data
    messages.value.push({
      role: 'assistant',
      content: data?.answer || '抱歉，我暂时无法回答这个问题。',
      time: fmtTime(),
      sources: data?.results || []
    })
  } catch (e) {
    ElMessage.error('查询失败: ' + (e.response?.data?.message || e.message))
    messages.value.push({
      role: 'assistant',
      content: '抱歉，处理请求时出现错误，请稍后重试。',
      time: fmtTime()
    })
  } finally {
    loading.value = false
    await nextTick()
    scrollBottom()
  }
}

const onKeyDown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

const clearChat = () => {
  messages.value = []
  sessionId.value = 'session-' + Date.now()
}

const scrollBottom = () => {
  const el = msgListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

const fmtTime = () => {
  const d = new Date()
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

const truncate = (text, len) => {
  if (!text) return ''
  return text.length > len ? text.substring(0, len) + '...' : text
}

// 切换知识库不影响当前对话
watch(() => props.knowledgeBaseId, () => {
  // Knowledge base change — keep current conversation
})
</script>
