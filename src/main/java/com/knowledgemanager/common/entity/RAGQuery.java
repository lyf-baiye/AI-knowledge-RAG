package com.knowledgemanager.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("rag_query")
public class RAGQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String appId;

    private String query;

    private String knowledgeBaseIds;

    private Integer topK;

    private String results;

    private LocalDateTime queryTime;

    private Integer responseTime;

    private String status;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
