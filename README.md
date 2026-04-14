# Knowledge Manager - AI工程辅助中心

## 🎯 项目简介

面向**设计团队**的智能问答知识库系统，支持文档上传、智能分片、向量化存储、混合检索、上下文记忆和LLM智能问答。

## ✨ 核心功能

### 1. 完整的RAG流程
```
文件上传 → 文本提取 → 智能分片 → 双重向量化(BM25+稠密) → 混合存储 → 混合检索 → 重排序 → LLM回答
```

### 2. 会话记忆系统
- **短期记忆**：Redis滑动窗口 + 自动摘要 + 32k字符限制 + 24h TTL
- **长期记忆**：MySQL + 向量化存储 + 语义相似度召回

### 3. 高级检索
- **混合检索**：BM25关键词 + 向量语义检索（稠密+稀疏向量）
- **查询重写**：基于对话上下文的Context Rewriting
- **结果重排序**：支持多种方式

### 4. 智能分片
- 语义分块（推荐）
- 固定大小分块
- 段落分块
- 句子分块

## 🛠️ 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.18 |
| ORM | MyBatis-Plus 3.5.3 |
| 数据库 | MySQL 8.0 + Redis |
| 向量化 | 阿里云text-embedding-v3（支持稠密+稀疏向量） |
| 向量存储 | 内存（开发）/ Pinecone（生产） |
| LLM | DeepSeek（deepseek-chat） |
| 文档解析 | Apache Tika |
| 前端 | Vue3 + Element Plus |
| 认证 | JWT |

## 📦 项目结构

```
AI-knowledge-RAG/
├── src/main/java/com/knowledgemanager/
│   ├── KnowledgeManagerApplication.java  # 启动类
│   ├── auth/                             # 认证模块
│   │   ├── controller/AuthController.java
│   │   └── service/
│   │       ├── AuthService.java
│   │       └── PermissionService.java
│   ├── knowledgebase/                    # 知识库模块
│   │   ├── controller/
│   │   │   ├── KnowledgeBaseController.java
│   │   │   └── FileController.java       # 文件上传
│   │   └── service/
│   │       ├── KnowledgeBaseService.java
│   │       └── FileService.java          # 文件处理
│   ├── vector/                           # 向量化模块
│   │   ├── config/VectorConfig.java      # 向量化配置
│   │   └── service/
│   │       ├── TextExtractionService.java  # 文本提取
│   │       ├── TextChunkService.java       # 智能分片
│   │       ├── HybridVectorService.java    # 混合向量处理
│   │       └── BM25Encoder.java            # BM25编码器
│   ├── rag/                              # RAG查询模块
│   │   ├── controller/RAGController.java
│   │   └── service/RAGService.java       # 完整RAG流程
│   ├── memory/                           # 记忆系统
│   │   ├── config/RedisConfig.java       # Redis配置
│   │   └── service/
│   │       ├── ShortTermMemoryService.java  # 短期记忆
│   │       └── LongTermMemoryService.java   # 长期记忆
│   ├── llm/                              # LLM模块
│   │   ├── config/LLMConfig.java
│   │   └── service/LLMService.java
│   └── common/                           # 公共模块
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   ├── WebMvcConfig.java         # MVC + JWT拦截器
│       │   └── AsyncConfig.java
│       ├── interceptor/JwtInterceptor.java
│       ├── entity/                       # 实体类
│       ├── dto/                          # DTO
│       ├── mapper/                       # Mapper
│       ├── util/                         # 工具类
│       └── exception/                    # 异常处理
├── src/main/resources/
│   └── application.yml
├── frontend/
│   └── index.html                        # Vue3前端
├── init-scripts/
│   └── mysql/02-add-user-memory.sql
└── pom.xml
```

## 🚀 快速开始

### 1. 环境要求
- JDK 8+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 2. 配置环境变量

```bash
# 数据库
MYSQL_HOST=localhost
MYSQL_PASSWORD=your_password

# 阿里云DashScope（text-embedding-v3，支持稠密+稀疏向量）
DASHSCOPE_API_KEY=your-dashscope-api-key

# DeepSeek（LLM对话）
DEEPSEEK_API_KEY=your-deepseek-api-key

# Pinecone（可选，生产环境使用）
PINECONE_API_KEY=your-pinecone-api-key
```

### 3. 初始化数据库

```bash
# 执行原始脚本
mysql -u root -p < ../knowledge-manager/init-scripts/mysql/01-init-database.sql

# 执行补充脚本
mysql -u root -p < init-scripts/mysql/02-add-user-memory.sql
```

### 4. 运行项目

```bash
# 构建
mvn clean install -DskipTests

# 运行
mvn spring-boot:run

# 或打包运行
java -jar target/AI-knowledge-RAG-1.0.0.jar
```

### 5. 访问系统

- **前端界面**：打开 `frontend/index.html`
- **API文档**：http://localhost:8080

## 📡 API端点

### 认证
- `POST /api/auth/register` - 注册
- `POST /api/auth/login` - 登录
- `POST /api/auth/refresh` - 刷新Token

### 知识库
- `POST /api/knowledge-bases` - 创建知识库
- `GET /api/knowledge-bases` - 列表
- `PUT /api/knowledge-bases/{id}` - 更新
- `DELETE /api/knowledge-bases/{id}` - 删除

### 文件
- `POST /api/files/upload` - 上传并处理文件
- `GET /api/files?knowledgeBaseId=1` - 文件列表
- `DELETE /api/files/{id}` - 删除文件

### RAG查询
- `POST /api/rag/query` - 智能问答（支持上下文记忆）
  - Headers: `X-Session-Id`（会话ID）
  - Headers: `Authorization: Bearer <token>`

## 💡 使用示例

### 上传文件
```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@设计规范.pdf" \
  -F "knowledgeBaseId=1"
```

### 智能问答
```bash
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "X-Session-Id: session-123" \
  -d '{
    "query": "我们的设计规范是什么？",
    "knowledgeBaseIds": [1],
    "topK": 5
  }'
```

### 多轮对话（短期记忆示例）
```bash
# 第一轮
curl -H "X-Session-Id: session-123" -d '{"query":"设计规范是什么？"}'
# 回复：我们的设计规范包括...

# 第二轮（系统会基于上下文理解"那"指的是设计规范）
curl -H "X-Session-Id: session-123" -d '{"query":"那色彩部分呢？"}'
# 回复：色彩规范包括...
```

## 🌟 特色功能

### 混合检索（Dense + Sparse Vectors）
- **稠密向量**：由text-embedding-v3生成，用于语义匹配
- **稀疏向量**：由BM25Encoder生成，用于关键词匹配
- **双重存储**：在Pinecone中同时存储稠密和稀疏向量
- **混合检索**：同时利用语义和关键词匹配能力

### 滑动窗口短期记忆
- **字符限制**：32k字符（而非token数）
- **自动摘要**：当超过32k字符时，将超出部分和前半部分生成摘要
- **保留后半部分**：保留窗口内的后一半内容，确保上下文连续性
