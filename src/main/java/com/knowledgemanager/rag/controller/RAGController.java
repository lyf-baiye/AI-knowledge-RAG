package com.knowledgemanager.rag.controller;

import com.knowledgemanager.common.dto.RAGQueryDTO;
import com.knowledgemanager.common.dto.RAGResultDTO;
import com.knowledgemanager.common.response.ApiResponse;
import com.knowledgemanager.rag.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/rag")
public class RAGController {

    @Resource
    private RAGService ragService;

    // TODO: 从JWT中获取userId和sessionId
    private Long getCurrentUserId() {
        return 1L;
    }

    private String getSessionId(String headerSessionId) {
        return headerSessionId != null ? headerSessionId : "default-session";
    }

    @PostMapping("/query")
    public ApiResponse<RAGResultDTO> query(
            @Valid @RequestBody RAGQueryDTO queryDTO,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {
        return ApiResponse.success(ragService.query(queryDTO, getCurrentUserId(), getSessionId(headerSessionId)));
    }

    @PostMapping("/batch-query")
    public ApiResponse<List<RAGResultDTO>> batchQuery(
            @RequestBody List<RAGQueryDTO> queries,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {
        return ApiResponse.success(ragService.batchQuery(queries, getCurrentUserId(), getSessionId(headerSessionId)));
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("RAG service is running");
    }
}
