package com.knowledgemanager.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("file")
public class File implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long knowledgeBaseId;

    private Long uploaderId;

    private String fileName;

    private String fileFormat;

    private Long fileSize;

    private String cosPath;

    private String cosUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadTime;

    private String processStatus;

    private LocalDateTime processTime;

    private String errorMessage;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
