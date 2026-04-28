package com.knowledgemanager.memory.controller;

import com.knowledgemanager.common.entity.UserMemory;
import com.knowledgemanager.common.response.ApiResponse;
import com.knowledgemanager.memory.service.LongTermMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    @Resource
    private LongTermMemoryService longTermMemoryService;

    @Resource
    private HttpServletRequest request;

    private Long getCurrentUserId() {
        Long userId = (Long) request.getAttribute("userId");
        return userId != null ? userId : 1L;
    }

    @GetMapping("/long-term")
    public ApiResponse<List<UserMemory>> list() {
        return ApiResponse.success(longTermMemoryService.getUserMemories(getCurrentUserId()));
    }

    @PostMapping("/long-term")
    public ApiResponse<String> add(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        String type = (String) body.getOrDefault("memoryType", "PREFERENCE");
        String content = (String) body.get("content");
        Double importance = body.get("importanceScore") != null
            ? ((Number) body.get("importanceScore")).doubleValue()
            : 0.5;

        if (content == null || content.trim().isEmpty()) {
            return ApiResponse.error("记忆内容不能为空");
        }

        longTermMemoryService.addMemory(userId, type, content, importance);
        return ApiResponse.success("记忆添加成功");
    }

    @DeleteMapping("/long-term/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        longTermMemoryService.deleteMemory(id, getCurrentUserId());
        return ApiResponse.success("记忆删除成功");
    }
}
