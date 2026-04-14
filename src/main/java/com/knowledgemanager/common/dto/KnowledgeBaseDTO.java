package com.knowledgemanager.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class KnowledgeBaseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private Long creatorId;
    private String creatorName;
    private String status;
    private String visibility;
    private Long defaultRuleId;
    private String createTime;
    private String updateTime;
    private Integer fileCount;
    private Integer chunkCount;
}
