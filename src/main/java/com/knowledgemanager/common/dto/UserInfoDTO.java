package com.knowledgemanager.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private String role;
    private String status;
}
