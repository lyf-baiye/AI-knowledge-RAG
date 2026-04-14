package com.knowledgemanager.common.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class FileUploadDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "文件不能为空")
    private MultipartFile file;
    @NotNull(message = "知识库ID不能为空")
    private Long knowledgeBaseId;
    private Long ruleId;
}
