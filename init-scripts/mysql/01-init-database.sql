-- =====================================================
-- Knowledge Manager 数据库初始化脚本
-- =====================================================

CREATE DATABASE IF NOT EXISTS `knowledge_manager`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `knowledge_manager`;

-- =====================================================
-- 1. 用户表
-- =====================================================
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `password` varchar(255) NOT NULL COMMENT '加密密码',
  `role` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =====================================================
-- 2. 知识库表
-- =====================================================
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL COMMENT '知识库名称',
  `description` text COMMENT '描述',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
  `visibility` varchar(20) DEFAULT 'PRIVATE' COMMENT '可见性: PUBLIC/PRIVATE/TEAM',
  `default_rule_id` bigint DEFAULT NULL COMMENT '默认处理规则ID',
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- =====================================================
-- 3. 文件表
-- =====================================================
CREATE TABLE IF NOT EXISTS `file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传者ID',
  `file_name` varchar(500) NOT NULL COMMENT '原始文件名',
  `file_format` varchar(20) NOT NULL COMMENT '文件格式: PDF/DOCX/MD/TXT等',
  `file_size` bigint DEFAULT 0 COMMENT '文件大小(字节)',
  `cos_path` varchar(500) DEFAULT NULL COMMENT '本地/对象存储路径',
  `cos_url` varchar(500) DEFAULT NULL COMMENT 'COS访问URL',
  `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `process_status` varchar(20) DEFAULT 'PENDING' COMMENT '处理状态: PENDING/PROCESSING/COMPLETED/FAILED',
  `process_time` datetime DEFAULT NULL COMMENT '处理完成时间',
  `error_message` text COMMENT '处理错误信息',
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_process_status` (`process_status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';

-- =====================================================
-- 4. 分块表
-- =====================================================
CREATE TABLE IF NOT EXISTS `chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` bigint NOT NULL COMMENT '所属文件ID',
  `chunk_index` int NOT NULL DEFAULT 0 COMMENT '分块序号',
  `content` longtext NOT NULL COMMENT '分块文本内容',
  `vector_id` varchar(100) DEFAULT NULL COMMENT '向量数据库中ID',
  `metadata` text COMMENT '元数据JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_file_id` (`file_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文本分块表';

-- =====================================================
-- 5. 处理规则表
-- =====================================================
CREATE TABLE IF NOT EXISTS `processing_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL COMMENT '规则名称',
  `description` varchar(500) DEFAULT NULL,
  `knowledge_base_id` bigint DEFAULT NULL COMMENT '所属知识库ID(NULL=全局规则)',
  `chunking_strategy` varchar(50) DEFAULT 'SEMANTIC' COMMENT '分块策略: FIXED_SIZE/SEMANTIC/PARAGRAPH/SENTENCE',
  `chunk_size` int DEFAULT 500 COMMENT '分块大小',
  `chunk_overlap` int DEFAULT 50 COMMENT '重叠大小',
  `embedding_model` varchar(100) DEFAULT 'text-embedding-v3' COMMENT '向量化模型',
  `embedding_dimension` int DEFAULT 1024 COMMENT '向量维度',
  `custom_params` text COMMENT '自定义参数JSON',
  `is_default` tinyint DEFAULT 0 COMMENT '是否默认规则',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_is_default` (`is_default`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='处理规则表';

-- 插入默认处理规则
INSERT INTO `processing_rule` (`name`, `description`, `chunking_strategy`, `chunk_size`, `chunk_overlap`, `is_default`)
VALUES ('默认语义分块规则', '使用语义分块策略，500字符/块，50字符重叠', 'SEMANTIC', 500, 50, 1);

-- =====================================================
-- 6. 任务表
-- =====================================================
CREATE TABLE IF NOT EXISTS `task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_type` varchar(50) NOT NULL COMMENT '任务类型: FILE_PROCESS/IMPORT/SYNC等',
  `file_id` bigint DEFAULT NULL COMMENT '关联文件ID',
  `knowledge_base_id` bigint DEFAULT NULL COMMENT '关联知识库ID',
  `user_id` bigint DEFAULT NULL COMMENT '执行用户ID',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/COMPLETED/FAILED',
  `progress` int DEFAULT 0 COMMENT '进度(0-100)',
  `message` varchar(500) DEFAULT NULL COMMENT '状态消息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `error_message` text COMMENT '错误信息',
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_file_id` (`file_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- =====================================================
-- 7. 权限表
-- =====================================================
CREATE TABLE IF NOT EXISTS `permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `resource_type` varchar(50) NOT NULL COMMENT '资源类型: KNOWLEDGE_BASE/FILE',
  `resource_id` bigint NOT NULL COMMENT '资源ID',
  `permission` varchar(50) NOT NULL COMMENT '权限: READ/WRITE/ADMIN',
  `grant_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `granted_by` bigint DEFAULT NULL COMMENT '授权人ID',
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_resource` (`resource_type`, `resource_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- =====================================================
-- 8. 知识库申请表
-- =====================================================
CREATE TABLE IF NOT EXISTS `knowledge_base_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applicant_id` bigint NOT NULL COMMENT '申请人ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `permission` varchar(50) DEFAULT 'READ' COMMENT '申请权限: READ/WRITE',
  `reason` varchar(500) DEFAULT NULL COMMENT '申请理由',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '审批状态: PENDING/APPROVED/REJECTED',
  `apply_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `review_time` datetime DEFAULT NULL COMMENT '审批时间',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `review_comment` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_applicant_id` (`applicant_id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库申请表';

-- =====================================================
-- 9. RAG查询记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `rag_query` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` varchar(100) DEFAULT NULL COMMENT '应用ID',
  `query` text NOT NULL COMMENT '查询文本',
  `knowledge_base_ids` varchar(500) DEFAULT NULL COMMENT '知识库ID列表(逗号分隔)',
  `top_k` int DEFAULT 5 COMMENT '检索数量',
  `results` text COMMENT '检索结果JSON',
  `query_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `response_time` int DEFAULT 0 COMMENT '响应时间(ms)',
  `status` varchar(20) DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS/FAILED',
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_app_id` (`app_id`),
  KEY `idx_query_time` (`query_time`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG查询记录表';

-- =====================================================
-- 10. 用户长期记忆表（含向量字段）
-- =====================================================
CREATE TABLE IF NOT EXISTS `user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `memory_type` varchar(50) NOT NULL COMMENT '记忆类型: PREFERENCE/QUERY_HISTORY/IMPORTANT_INFO',
  `content` text NOT NULL COMMENT '记忆内容',
  `vector_id` varchar(100) DEFAULT NULL COMMENT '向量数据库中的ID',
  `embedding` longtext DEFAULT NULL COMMENT '向量数据(JSON数组)，用于本地余弦相似度计算',
  `metadata` text COMMENT '元数据JSON',
  `importance_score` double DEFAULT 0.5 COMMENT '重要性评分(0-1)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_memory_type` (`memory_type`),
  KEY `idx_importance` (`importance_score`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户长期记忆表';
