package com.knowledgemanager.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

@Data
public class RAGQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "查询内容不能为空")
    private String query;
    private List<Long> knowledgeBaseIds;
    private Integer topK = 5;
    private Double scoreThreshold = 0.7;
}
