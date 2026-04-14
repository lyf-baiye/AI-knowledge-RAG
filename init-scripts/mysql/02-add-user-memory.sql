-- 补充脚本：创建用户长期记忆表
-- 在原有数据库初始化脚本之后执行

CREATE TABLE IF NOT EXISTS `user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `memory_type` varchar(50) NOT NULL COMMENT '记忆类型：PREFERENCE-偏好, QUERY_HISTORY-查询历史, IMPORTANT_INFO-重要信息',
  `content` text NOT NULL COMMENT '记忆内容',
  `vector_id` varchar(100) DEFAULT NULL COMMENT '向量数据库中的ID',
  `metadata` text COMMENT '元数据（JSON格式）',
  `importance_score` double DEFAULT '0.5' COMMENT '重要性评分（0-1）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_memory_type` (`memory_type`),
  KEY `idx_importance` (`importance_score`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户长期记忆表';
