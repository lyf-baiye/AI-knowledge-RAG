package com.knowledgemanager.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FileDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long knowledgeBaseId;
    private Long uploaderId;
    private String uploaderName;
    private String fileName;
    private String fileFormat;
    private Long fileSize;
    private String cosPath;
    private String cosUrl;
    private LocalDateTime uploadTime;
    private String processStatus;
    private LocalDateTime processTime;
    private String errorMessage;
}
