import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：注入 JWT Token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, error => Promise.reject(error))

// 响应拦截器：Token 过期自动刷新
api.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        try {
          const res = await axios.post('/api/auth/refresh', {}, {
            headers: { Authorization: `Bearer ${refreshToken}` }
          })
          const { accessToken, refreshToken: newRefresh } = res.data.data
          localStorage.setItem('accessToken', accessToken)
          localStorage.setItem('refreshToken', newRefresh)
          originalRequest.headers.Authorization = `Bearer ${accessToken}`
          return api(originalRequest)
        } catch (e) {
          localStorage.clear()
          window.location.reload()
        }
      }
    }
    return Promise.reject(error)
  }
)

// --- 认证 ---
export const authAPI = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  refresh: () => api.post('/auth/refresh')
}

// --- 知识库 ---
export const knowledgeBaseAPI = {
  list: (pageNum = 1, pageSize = 50) =>
    api.get('/knowledge-bases', { params: { pageNum, pageSize } }),
  get: (id) => api.get(`/knowledge-bases/${id}`),
  create: (data) => api.post('/knowledge-bases', data),
  update: (id, data) => api.put(`/knowledge-bases/${id}`, data),
  delete: (id) => api.delete(`/knowledge-bases/${id}`)
}

// --- 文件 ---
export const fileAPI = {
  upload: (formData, onProgress) =>
    api.post('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress
    }),
  list: (knowledgeBaseId) =>
    api.get('/files', { params: { knowledgeBaseId } }),
  delete: (id) => api.delete(`/files/${id}`)
}

// --- 长期记忆 ---
export const memoryAPI = {
  list: () => api.get('/memory/long-term'),
  add: (data) => api.post('/memory/long-term', data),
  delete: (id) => api.delete(`/memory/long-term/${id}`)
}

// --- RAG 查询 ---
export const ragAPI = {
  query: (queryDTO, sessionId) =>
    api.post('/rag/query', queryDTO, {
      headers: { 'X-Session-Id': sessionId }
    }),
  health: () => api.get('/rag/health')
}

export default api
