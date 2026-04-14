package com.knowledgemanager.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String taskType;

    private Long fileId;

    private Long knowledgeBaseId;

    private Long userId;

    private String status;

    private Integer progress;

    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String errorMessage;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
