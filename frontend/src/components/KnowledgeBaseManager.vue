<template>
  <el-tabs v-model="activeTab">
    <!-- 文件上传 -->
    <el-tab-pane label="文件上传" name="upload">
      <el-select
        v-model="selectedKB"
        placeholder="选择知识库"
        style="width: 100%; margin-bottom: 15px"
        @change="loadFiles"
      >
        <el-option
          v-for="kb in knowledgeBases"
          :key="kb.id"
          :label="kb.name"
          :value="kb.id"
        />
      </el-select>

      <el-upload
        drag
        :action="uploadUrl"
        :headers="uploadHeaders"
        :data="uploadData"
        :on-success="onUploadSuccess"
        :on-error="onUploadError"
        :before-upload="beforeUpload"
        multiple
      >
        <div class="upload-icon">📁</div>
        <div class="el-upload__text">
          拖拽文件到此处或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF、Word、Markdown、TXT，最大 100MB
          </div>
        </template>
      </el-upload>

      <div class="file-list" v-if="files.length > 0">
        <h4 style="margin-bottom: 10px; color: #555">已上传文件 ({{ files.length }})</h4>
        <div v-for="file in files" :key="file.id" class="file-item">
          <div class="file-info">
            <div class="file-name">{{ file.fileName }}</div>
            <div class="file-meta">
              {{ file.fileFormat }} · {{ formatFileSize(file.fileSize) }}
            </div>
          </div>
          <div>
            <span
              class="status-badge"
              :class="'status-' + (file.processStatus || 'pending').toLowerCase()"
            >
              {{ file.processStatus || 'PENDING' }}
            </span>
            <el-button
              type="danger"
              text
              size="small"
              style="margin-left: 8px"
              @click="deleteFile(file.id)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </el-tab-pane>

    <!-- 知识库管理 -->
    <el-tab-pane label="知识库" name="knowledge">
      <el-button type="primary" style="width: 100%; margin-bottom: 15px" @click="showCreateDialog = true">
        + 创建知识库
      </el-button>

      <div v-for="kb in knowledgeBases" :key="kb.id" class="file-item" style="cursor: pointer" @click="selectKB(kb.id)">
        <div class="file-info">
          <div class="file-name">{{ kb.name }}</div>
          <div class="file-meta" v-if="kb.description">{{ kb.description }}</div>
        </div>
        <el-button type="danger" text size="small" @click.stop="deleteKB(kb.id)">删除</el-button>
      </div>
    </el-tab-pane>

    <!-- 长期记忆 -->
    <el-tab-pane label="长期记忆" name="memory">
      <long-term-memory :guest-mode="props.guestMode" />
    </el-tab-pane>
  </el-tabs>

  <!-- 创建知识库弹窗 -->
  <el-dialog v-model="showCreateDialog" title="创建知识库">
    <el-form :model="newKB" label-width="80px">
      <el-form-item label="名称">
        <el-input v-model="newKB.name" placeholder="请输入知识库名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="newKB.description" type="textarea" :rows="3" placeholder="请输入描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showCreateDialog = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="createKB">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { knowledgeBaseAPI, fileAPI } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import LongTermMemory from './LongTermMemory.vue'

const props = defineProps({
  guestMode: { type: Boolean, default: false },
  guestKnowledgeBases: { type: Array, default: () => [] }
})

const emit = defineEmits(['select-kb', 'refresh-kb'])

const activeTab = ref('upload')
const knowledgeBases = ref([])
const selectedKB = ref(null)
const files = ref([])
const showCreateDialog = ref(false)
const creating = ref(false)
const newKB = ref({ name: '', description: '' })

const uploadUrl = '/api/files/upload'
const uploadHeaders = computedHeaders()
const uploadData = computed(() => ({ knowledgeBaseId: selectedKB.value }))

function computedHeaders() {
  const token = localStorage.getItem('accessToken')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

const loadKB = async () => {
  if (props.guestMode) {
    knowledgeBases.value = props.guestKnowledgeBases || []
    if (knowledgeBases.value.length > 0 && !selectedKB.value) {
      selectedKB.value = knowledgeBases.value[0].id
      emit('select-kb', selectedKB.value)
    }
    return
  }
  try {
    const res = await knowledgeBaseAPI.list()
    if (res.data?.data?.records) {
      knowledgeBases.value = res.data.data.records
      if (knowledgeBases.value.length > 0 && !selectedKB.value) {
        selectedKB.value = knowledgeBases.value[0].id
        emit('select-kb', selectedKB.value)
        loadFiles()
      }
    }
  } catch (e) {
    console.error('Failed to load knowledge bases:', e)
  }
}

const loadFiles = async () => {
  if (!selectedKB.value || props.guestMode) return
  try {
    const res = await fileAPI.list(selectedKB.value)
    files.value = res.data?.data || []
  } catch (e) {
    files.value = []
  }
}

const selectKB = (id) => {
  selectedKB.value = id
  emit('select-kb', id)
  activeTab.value = 'upload'
  loadFiles()
}

const beforeUpload = (file) => {
  const allowed = ['pdf', 'doc', 'docx', 'md', 'markdown', 'txt']
  const ext = file.name.split('.').pop().toLowerCase()
  if (!allowed.includes(ext)) {
    ElMessage.error(`不支持的文件格式: .${ext}`)
    return false
  }
  if (file.size > 100 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 100MB')
    return false
  }
  if (!selectedKB.value) {
    ElMessage.warning('请先选择知识库')
    return false
  }
  return true
}

const onUploadSuccess = () => {
  ElMessage.success('文件上传成功，正在处理...')
  setTimeout(loadFiles, 2000)
}

const onUploadError = (err) => {
  ElMessage.error('文件上传失败: ' + (err.message || '未知错误'))
}

const deleteFile = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该文件吗？相关向量数据也将被清除。', '确认删除', {
      type: 'warning'
    })
    await fileAPI.delete(id)
    ElMessage.success('文件已删除')
    loadFiles()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

const createKB = async () => {
  if (!newKB.value.name) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  creating.value = true
  try {
    await knowledgeBaseAPI.create(newKB.value)
    ElMessage.success('知识库创建成功')
    showCreateDialog.value = false
    newKB.value = { name: '', description: '' }
    loadKB()
    emit('refresh-kb')
  } catch (e) {
    ElMessage.error('创建失败: ' + (e.response?.data?.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

const deleteKB = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该知识库吗？', '确认删除', { type: 'warning' })
    await knowledgeBaseAPI.delete(id)
    ElMessage.success('知识库已删除')
    if (selectedKB.value === id) {
      selectedKB.value = knowledgeBases.value[0]?.id || null
    }
    loadKB()
    emit('refresh-kb')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

onMounted(() => {
  loadKB()
})
</script>
