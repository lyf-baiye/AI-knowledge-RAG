package com.knowledgemanager.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserInfoDTO user;

    public LoginResponseDTO() {
        this.tokenType = "Bearer";
        this.expiresIn = 7200L; // 2 hours
    }
}
