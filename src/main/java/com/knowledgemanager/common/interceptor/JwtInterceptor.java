package com.knowledgemanager.common.interceptor;

import com.knowledgemanager.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT认证拦截器
 * 从请求头中提取Token并解析用户信息，注入到请求属性中
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 公开路径不需要认证
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || 
            path.startsWith("/actuator/") ||
            path.equals("/api/rag/health")) {
            return true;
        }

        // 提取Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未提供认证令牌\"}");
            return false;
        }

        String token = authHeader.substring(7);
        
        try {
            // 验证Token
            if (JwtUtil.isTokenExpired(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"令牌已过期\"}");
                return false;
            }

            // 提取用户信息
            Long userId = JwtUtil.getUserId(token);
            String username = JwtUtil.getUsername(token);
            String role = JwtUtil.getRole(token);

            if (userId == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"无效的令牌\"}");
                return false;
            }

            // 注入用户信息到请求属性
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("role", role);

            log.debug("JWT authenticated: userId={}, username={}, path={}", userId, username, path);
            return true;

        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"认证失败\"}");
            return false;
        }
    }
}
