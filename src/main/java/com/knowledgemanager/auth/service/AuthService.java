package com.knowledgemanager.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgemanager.common.constant.Constants;
import com.knowledgemanager.common.dto.LoginResponseDTO;
import com.knowledgemanager.common.dto.UserInfoDTO;
import com.knowledgemanager.common.dto.UserLoginDTO;
import com.knowledgemanager.common.dto.UserRegisterDTO;
import com.knowledgemanager.common.entity.User;
import com.knowledgemanager.common.exception.BusinessException;
import com.knowledgemanager.common.mapper.UserMapper;
import com.knowledgemanager.common.util.JwtUtil;
import com.knowledgemanager.common.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AuthService {

    @Resource
    private UserMapper userMapper;

    public LoginResponseDTO register(UserRegisterDTO registerDTO) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> usernameQuery = new LambdaQueryWrapper<>();
        usernameQuery.eq(User::getUsername, registerDTO.getUsername());
        if (userMapper.selectCount(usernameQuery) > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 检查邮箱是否已存在
        LambdaQueryWrapper<User> emailQuery = new LambdaQueryWrapper<>();
        emailQuery.eq(User::getEmail, registerDTO.getEmail());
        if (userMapper.selectCount(emailQuery) > 0) {
            throw new BusinessException("邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        user.setPassword(PasswordUtil.encrypt(registerDTO.getPassword()));
        user.setRole(Constants.UserRole.USER);
        user.setStatus(Constants.UserStatus.ACTIVE);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);

        userMapper.insert(user);
        log.info("User registered successfully: userId={}, username={}", user.getId(), user.getUsername());

        // 注册成功后自动登录
        return generateTokenResponse(user);
    }

    public LoginResponseDTO login(UserLoginDTO loginDTO) {
        // 查询用户
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        query.eq(User::getUsername, loginDTO.getUsername());
        User user = userMapper.selectOne(query);

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 验证密码
        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 检查账户状态
        if (!Constants.UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new BusinessException("账户已被禁用，请联系管理员");
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("User logged in successfully: userId={}, username={}", user.getId(), user.getUsername());

        return generateTokenResponse(user);
    }

    public LoginResponseDTO refreshToken(String refreshToken) {
        // 验证refresh token
        if (JwtUtil.isTokenExpired(refreshToken)) {
            throw new BusinessException("Refresh Token已过期，请重新登录");
        }

        Long userId = JwtUtil.getUserId(refreshToken);
        if (userId == null) {
            throw new BusinessException("无效的Refresh Token");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!Constants.UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new BusinessException("账户已被禁用，请联系管理员");
        }

        log.info("Token refreshed successfully: userId={}", userId);
        return generateTokenResponse(user);
    }

    private LoginResponseDTO generateTokenResponse(User user) {
        String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        UserInfoDTO userInfo = new UserInfoDTO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setRole(user.getRole());
        userInfo.setStatus(user.getStatus());

        LoginResponseDTO response = new LoginResponseDTO();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUser(userInfo);

        return response;
    }
}
