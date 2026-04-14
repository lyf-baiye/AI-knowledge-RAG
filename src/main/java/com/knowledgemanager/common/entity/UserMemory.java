package com.knowledgemanager.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户长期记忆实体
 */
@Data
@TableName("user_memory")
public class UserMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 记忆类型：PREFERENCE-偏好, QUERY_HISTORY-查询历史, IMPORTANT_INFO-重要信息
     */
    private String memoryType;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 向量ID（在向量数据库中的ID）
     */
    private String vectorId;

    /**
     * 元数据（JSON格式）
     */
    private String metadata;

    /**
     * 重要性评分（0-1）
     */
    private Double importanceScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
