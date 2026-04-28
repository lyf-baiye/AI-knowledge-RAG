<template>
  <div>
    <el-button type="primary" style="width: 100%; margin-bottom: 15px" @click="showAddDialog = true">
      + 录入记忆
    </el-button>

    <div v-if="memories.length === 0" style="text-align: center; color: #bbb; padding: 30px 0">
      暂无记忆，点击上方按钮手动录入
    </div>

    <div v-for="mem in memories" :key="mem.id" class="file-item">
      <div class="file-info">
        <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px">
          <el-tag size="small" :type="tagType(mem.memoryType)">{{ mem.memoryType }}</el-tag>
          <span style="font-size: 12px; color: #999">重要性: {{ (mem.importanceScore * 100).toFixed(0) }}%</span>
        </div>
        <div style="font-size: 13px; line-height: 1.5; color: #555">{{ mem.content }}</div>
      </div>
      <el-button type="danger" text size="small" @click="deleteMemory(mem.id)">删除</el-button>
    </div>

    <!-- 添加记忆弹窗 -->
    <el-dialog v-model="showAddDialog" title="录入记忆" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="form.memoryType" style="width: 100%">
            <el-option label="偏好 (PREFERENCE)" value="PREFERENCE" />
            <el-option label="重要信息 (IMPORTANT_INFO)" value="IMPORTANT_INFO" />
            <el-option label="查询历史 (QUERY_HISTORY)" value="QUERY_HISTORY" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="输入要记住的内容..." />
        </el-form-item>
        <el-form-item label="重要性">
          <el-slider v-model="form.importanceScore" :min="0" :max="1" :step="0.1" show-input style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="addMemory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { memoryAPI } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  guestMode: { type: Boolean, default: false }
})

const memories = ref([])
const showAddDialog = ref(false)
const submitting = ref(false)
const form = reactive({
  memoryType: 'PREFERENCE',
  content: '',
  importanceScore: 0.6
})

const loadMemories = async () => {
  if (props.guestMode) return
  try {
    const res = await memoryAPI.list()
    memories.value = res.data?.data || []
  } catch (e) {
    memories.value = []
  }
}

const addMemory = async () => {
  if (!form.content.trim()) {
    ElMessage.warning('请输入记忆内容')
    return
  }
  if (props.guestMode) {
    // 游客模式：存到本地
    const guestMem = {
      id: Date.now(),
      memoryType: form.memoryType,
      content: form.content,
      importanceScore: form.importanceScore
    }
    memories.value.unshift(guestMem)
    ElMessage.success('记忆已录入（游客模式，仅本次有效）')
    showAddDialog.value = false
    form.content = ''
    return
  }

  submitting.value = true
  try {
    await memoryAPI.add({ ...form })
    ElMessage.success('记忆录入成功')
    showAddDialog.value = false
    form.content = ''
    loadMemories()
  } catch (e) {
    ElMessage.error('录入失败: ' + (e.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

const deleteMemory = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该记忆吗？', '确认删除', { type: 'warning' })
    if (props.guestMode) {
      memories.value = memories.value.filter(m => m.id !== id)
      ElMessage.success('已删除')
      return
    }
    await memoryAPI.delete(id)
    ElMessage.success('已删除')
    loadMemories()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const tagType = (type) => {
  return type === 'IMPORTANT_INFO' ? 'danger' : type === 'PREFERENCE' ? 'warning' : ''
}

onMounted(loadMemories)
</script>
