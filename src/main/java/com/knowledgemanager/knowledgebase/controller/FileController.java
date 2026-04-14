package com.knowledgemanager.knowledgebase.controller;

import com.knowledgemanager.common.dto.FileDTO;
import com.knowledgemanager.common.dto.FileUploadDTO;
import com.knowledgemanager.common.entity.Chunk;
import com.knowledgemanager.common.entity.File;
import com.knowledgemanager.common.entity.ProcessingRule;
import com.knowledgemanager.common.exception.BusinessException;
import com.knowledgemanager.common.mapper.FileMapper;
import com.knowledgemanager.common.mapper.ProcessingRuleMapper;
import com.knowledgemanager.common.response.ApiResponse;
import com.knowledgemanager.knowledgebase.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Resource
    private FileService fileService;

    /**
     * 上传文件并自动处理（提取→分片→向量化）
     */
    @PostMapping("/upload")
    public ApiResponse<FileDTO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam(value = "ruleId", required = false) Long ruleId) {
        
        log.info("File upload request: fileName={}, knowledgeBaseId={}, ruleId={}", 
                file.getOriginalFilename(), knowledgeBaseId, ruleId);

        try {
            // 上传并处理文件
            FileDTO fileDTO = fileService.uploadAndProcess(file, knowledgeBaseId, ruleId);
            return ApiResponse.success("文件上传成功，正在处理中", fileDTO);
        } catch (Exception e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            return ApiResponse.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取知识库的文件列表
     */
    @GetMapping
    public ApiResponse<List<FileDTO>> getFiles(
            @RequestParam Long knowledgeBaseId,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(fileService.getFilesByKnowledgeBase(knowledgeBaseId, status));
    }

    /**
     * 获取文件详情
     */
    @GetMapping("/{id}")
    public ApiResponse<FileDTO> getFile(@PathVariable Long id) {
        return ApiResponse.success(fileService.getFileById(id));
    }

    /**
     * 删除文件及其所有分片向量
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return ApiResponse.success("文件删除成功");
    }
}
