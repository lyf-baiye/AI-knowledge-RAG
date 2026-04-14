package com.knowledgemanager.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_base_application")
public class KnowledgeBaseApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long applicantId;

    private Long knowledgeBaseId;

    private String permission;

    private String reason;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime applyTime;

    private LocalDateTime reviewTime;

    private Long reviewerId;

    private String reviewComment;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
