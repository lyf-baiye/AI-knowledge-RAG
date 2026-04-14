package com.knowledgemanager.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("permission")
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String resourceType;

    private Long resourceId;

    private String permission;

    private LocalDateTime grantTime;

    private Long grantedBy;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
