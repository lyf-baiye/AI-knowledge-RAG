# AI 工程辅助中心 — 团队智能问答 RAG 知识库系统

面向设计团队的智能问答知识库系统，支持文档上传、语义分片、混合向量化、Pinecone 混合检索、重排序、上下文记忆和 LLM 智能问答。

## 核心能力

```
文件上传 → Tika 文本提取 → 语义相似度分片 → 双重向量化(Dense+BM25)
  → Pinecone 混合存储 → 混合检索 → 重排序 → LLM 回答
         ↑                                    ↑
    短期记忆(Redis) ←→ Query Rewriting    对话历史+原始问题
    长期记忆(MySQL) ←→ 语义召回            → Prompt 融合
```

| 模块 | 实现 |
|------|------|
| 语义分片 | 句子级 embedding → 余弦相似度合并（≥0.7 合并，<0.7 切断） |
| 混合检索 | text-embedding-v3 稠密向量 + BM25 稀疏向量 → Pinecone 融合打分 |
| 重排序 | keyword 关键词位置加权（可选 llm / score） |
| 短期记忆 | Redis List · 32k 字符滑动窗口 · 超限自动 LLM 摘要 · 24h TTL |
| 长期记忆 | LLM 自动提取 + 手动录入 · MySQL JSON 存向量 · 余弦相似度召回 |
| Query Rewriting | 基于短期记忆上下文的 LLM 重写 → 仅用于检索，不进 Prompt |
| Prompt 构建 | System(RAG+长期记忆) + 对话历史(短期记忆) + 原始问题 |

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.18 |
| LLM 编排 | LangChain4j 0.36.0 |
| ORM | MyBatis-Plus 3.5.3 |
| LLM | DeepSeek (deepseek-chat)，兼容 OpenAI 协议 |
| 向量模型 | 阿里云 DashScope text-embedding-v3 |
| 向量数据库 | Pinecone (REST API) |
| 文档解析 | Apache Tika 2.9.1 |
| 数据库 | MySQL 8.0 + Redis 6.0 |
| 前端 | Vue3 + Vite + Element Plus |
| 认证 | JWT |

## 项目结构

```
AI-knowledge-RAG/
├── src/main/java/com/knowledgemanager/
│   ├── KnowledgeManagerApplication.java
│   ├── auth/                   认证模块（注册/登录/JWT）
│   ├── knowledgebase/          知识库 CRUD + 文件上传
│   ├── vector/                 向量化（提取/分片/混合检索/重排序/BM25）
│   ├── rag/                    RAG 查询编排
│   ├── memory/                 短期记忆(Redis) + 长期记忆(MySQL)
│   ├── llm/                    DeepSeek 调用封装
│   └── common/                 实体/DTO/配置/拦截器/异常
├── frontend/                   Vue3 + Vite 前端工程
├── init-scripts/mysql/         数据库初始化脚本
└── pom.xml
```

## 快速开始

### 1. 环境要求
- JDK 8+ / Maven 3.6+
- MySQL 8.0+ / Redis 6.0+
- Node.js 18+ (前端开发)

### 2. 配置环境变量

```bash
export DASHSCOPE_API_KEY=sk-xxx      # 阿里云 DashScope
export DEEPSEEK_API_KEY=sk-xxx       # DeepSeek
export MYSQL_PASSWORD=xxx
export PINECONE_API_KEY=xxx          # 可选，生产环境
```

### 3. 初始化数据库

```bash
mysql -u root -p < init-scripts/mysql/01-init-database.sql
```

### 4. 启动后端

```bash
mvn spring-boot:run
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev       # 开发模式 → http://localhost:3000
npm run build     # 构建 → src/main/resources/static/
```

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 刷新 Token |
| POST | `/api/knowledge-bases` | 创建知识库 |
| GET | `/api/knowledge-bases` | 知识库列表 |
| POST | `/api/files/upload` | 上传文件 |
| GET | `/api/files?knowledgeBaseId=` | 文件列表 |
| DELETE | `/api/files/{id}` | 删除文件 + 向量 |
| POST | `/api/rag/query` | 智能问答 |
| GET | `/api/memory/long-term` | 长期记忆列表 |
| POST | `/api/memory/long-term` | 手动录入记忆 |

## 使用示例

```bash
# 上传文件
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@设计规范.pdf" \
  -F "knowledgeBaseId=1"

# 智能问答
curl -X POST http://localhost:8080/api/rag/query \
  -H "Authorization: Bearer <token>" \
  -H "X-Session-Id: session-123" \
  -H "Content-Type: application/json" \
  -d '{"query":"我们的色彩规范是什么？","knowledgeBaseIds":[1],"topK":5}'

# 多轮对话
# 第一轮: query="设计规范有哪些"
# 第二轮: query="那色彩部分呢？"
# 系统通过短期记忆理解"那"指代"设计规范"
```
