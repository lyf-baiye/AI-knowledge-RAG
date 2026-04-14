package com.knowledgemanager.knowledgebase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgemanager.common.dto.KnowledgeBaseCreateDTO;
import com.knowledgemanager.common.dto.KnowledgeBaseDTO;
import com.knowledgemanager.common.entity.KnowledgeBase;
import com.knowledgemanager.common.response.ApiResponse;
import com.knowledgemanager.knowledgebase.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    // TODO: 添加JWT拦截器获取当前用户ID，这里暂时硬编码为1
    private Long getCurrentUserId() {
        return 1L;
    }

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        return ApiResponse.success(knowledgeBaseService.createKnowledgeBase(dto, getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseDTO> get(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.getKnowledgeBase(id, getCurrentUserId()));
    }

    @GetMapping
    public ApiResponse<Page<KnowledgeBaseDTO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(knowledgeBaseService.getUserKnowledgeBases(getCurrentUserId(), pageNum, pageSize));
    }

    @PutMapping("/{id}")
    public ApiResponse<String> update(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        knowledgeBaseService.updateKnowledgeBase(id, dto, getCurrentUserId());
        return ApiResponse.success("知识库更新成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id, getCurrentUserId());
        return ApiResponse.success("知识库删除成功");
    }
}
