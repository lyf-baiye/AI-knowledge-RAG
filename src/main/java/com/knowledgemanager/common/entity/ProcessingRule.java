package com.knowledgemanager.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("processing_rule")
public class ProcessingRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long knowledgeBaseId;

    private String chunkingStrategy;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private String embeddingModel;

    private Integer embeddingDimension;

    private String customParams;

    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    public static final String STRATEGY_FIXED_SIZE = "FIXED_SIZE";
    public static final String STRATEGY_SEMANTIC = "SEMANTIC";
    public static final String STRATEGY_CUSTOM = "CUSTOM";

    public static final String MODEL_TEXT_EMBEDDING_ADA_002 = "text-embedding-ada-002";
    public static final String MODEL_ALL_MINILM_L6_V2 = "all-MiniLM-L6-v2";
}
