package com.knowledgemanager.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // 暂时禁用Spring Security的CSRF和认证，使用自定义JWT
        http.csrf().disable()
            .authorizeRequests()
            .antMatchers("/api/auth/**", "/actuator/**", "/api/rag/health").permitAll()
            .anyRequest().permitAll() // 暂时允许所有请求，后续可添加JWT拦截器
            .and()
            .httpBasic().disable();
    }
}
