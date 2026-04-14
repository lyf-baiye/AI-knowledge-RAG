package com.knowledgemanager.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class KnowledgeBaseCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "知识库名称不能为空")
    private String name;
    private String description;
    private String visibility = "PRIVATE";
}
