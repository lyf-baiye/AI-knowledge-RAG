package com.knowledgemanager.auth.controller;

import com.knowledgemanager.auth.service.AuthService;
import com.knowledgemanager.common.dto.LoginResponseDTO;
import com.knowledgemanager.common.dto.UserLoginDTO;
import com.knowledgemanager.common.dto.UserRegisterDTO;
import com.knowledgemanager.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/register")
    public ApiResponse<LoginResponseDTO> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        LoginResponseDTO response = authService.register(registerDTO);
        return ApiResponse.success(response);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(@Valid @RequestBody UserLoginDTO loginDTO) {
        LoginResponseDTO response = authService.login(loginDTO);
        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponseDTO> refreshToken(@RequestParam String refreshToken) {
        LoginResponseDTO response = authService.refreshToken(refreshToken);
        return ApiResponse.success(response);
    }
}
