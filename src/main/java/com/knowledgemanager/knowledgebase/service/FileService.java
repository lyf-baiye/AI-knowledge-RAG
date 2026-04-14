package com.knowledgemanager.knowledgebase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgemanager.common.dto.FileDTO;
import com.knowledgemanager.common.entity.Chunk;
import com.knowledgemanager.common.entity.File;
import com.knowledgemanager.common.entity.KnowledgeBase;
import com.knowledgemanager.common.entity.ProcessingRule;
import com.knowledgemanager.common.exception.BusinessException;
import com.knowledgemanager.common.exception.NotFoundException;
import com.knowledgemanager.common.mapper.FileMapper;
import com.knowledgemanager.common.mapper.KnowledgeBaseMapper;
import com.knowledgemanager.common.mapper.ProcessingRuleMapper;
import com.knowledgemanager.common.mapper.UserMapper;
import com.knowledgemanager.vector.service.HybridVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件服务 - 处理文件上传、存储、自动处理流程
 */
@Slf4j
@Service
public class FileService {

    @Resource
    private FileMapper fileMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private ProcessingRuleMapper processingRuleMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private HybridVectorService hybridVectorService;

    @Value("${storage.local.path:./storage/files}")
    private String storagePath;

    @Value("${file.max-size:104857600}") // 100MB
    private long maxFileSize;

    /**
     * 上传文件并自动处理（提取→分片→向量化）
     */
    @Transactional
    public FileDTO uploadAndProcess(MultipartFile file, Long knowledgeBaseId, Long ruleId) {
        // 1. 校验文件
        validateFile(file);

        // 2. 校验知识库
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null || kb.getDeleted() == 1) {
            throw new NotFoundException("知识库不存在");
        }

        // 3. 获取处理规则
        ProcessingRule rule = getProcessingRule(ruleId, knowledgeBaseId);

        // 4. 保存文件到本地存储
        String fileType = getFileExtension(file.getOriginalFilename());
        String storagePath = saveFileToLocal(file, knowledgeBaseId, fileType);

        // 5. 创建文件记录
        File fileEntity = new File();
        fileEntity.setKnowledgeBaseId(knowledgeBaseId);
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFileFormat(fileType.toUpperCase());
        fileEntity.setFileSize(file.getSize());
        fileEntity.setCosPath(storagePath);
        fileEntity.setProcessStatus("PENDING");
        fileEntity.setUploadTime(LocalDateTime.now());
        fileEntity.setDeleted(0);

        fileMapper.insert(fileEntity);
        log.info("File record created: fileId={}, fileName={}", fileEntity.getId(), file.getOriginalFilename());

        // 6. 自动处理文件（提取→分片→向量化）
        try {
            fileEntity.setProcessStatus("PROCESSING");
            fileMapper.updateById(fileEntity);

            // 调用 HybridVectorService 处理文件 (提取->分片->双重向量化->存储)
            List<Chunk> chunks = hybridVectorService.processAndUpsertFile(
                file, 
                fileEntity.getId(), 
                knowledgeBaseId, 
                rule
            );

            // 更新状态
            fileEntity.setProcessStatus("COMPLETED");
            fileEntity.setProcessTime(LocalDateTime.now());
            fileMapper.updateById(fileEntity);

            log.info("File processed successfully: fileId={}, chunks={}", fileEntity.getId(), chunks.size());
        } catch (Exception e) {
            log.error("File processing failed: fileId={}, error={}", fileEntity.getId(), e.getMessage(), e);
            fileEntity.setProcessStatus("FAILED");
            fileEntity.setErrorMessage(e.getMessage());
            fileMapper.updateById(fileEntity);
            throw new BusinessException("文件处理失败: " + e.getMessage());
        }

        return convertToDTO(fileEntity);
    }

    /**
     * 获取知识库的文件列表
     */
    public List<FileDTO> getFilesByKnowledgeBase(Long knowledgeBaseId, String status) {
        LambdaQueryWrapper<File> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(File::getKnowledgeBaseId, knowledgeBaseId)
               .eq(File::getDeleted, 0);
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq(File::getProcessStatus, status);
        }
        
        wrapper.orderByDesc(File::getUploadTime);

        List<File> files = fileMapper.selectList(wrapper);
        return files.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取文件详情
     */
    public FileDTO getFileById(Long fileId) {
        File file = fileMapper.selectById(fileId);
        if (file == null || file.getDeleted() == 1) {
            throw new NotFoundException("文件不存在");
        }
        return convertToDTO(file);
    }

    /**
     * 删除文件及其所有分片向量
     */
    @Transactional
    public void deleteFile(Long fileId) {
        File file = fileMapper.selectById(fileId);
        if (file == null || file.getDeleted() == 1) {
            throw new NotFoundException("文件不存在");
        }

        // 删除所有分片向量
        hybridVectorService.deleteByFileId(fileId);

        // 标记文件为删除
        file.setDeleted(1);
        fileMapper.updateById(file);

        log.info("File deleted: fileId={}", fileId);
    }

    /**
     * 校验文件
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        if (file.getSize() > maxFileSize) {
            throw new BusinessException("文件大小不能超过100MB");
        }

        String fileType = getFileExtension(file.getOriginalFilename());
        if (!isValidFileType(fileType)) {
            throw new BusinessException("不支持的文件类型，仅支持PDF、Word、Markdown、TXT");
        }
    }

    /**
     * 获取处理规则
     */
    private ProcessingRule getProcessingRule(Long ruleId, Long knowledgeBaseId) {
        // 优先使用指定的规则
        if (ruleId != null) {
            return processingRuleMapper.selectById(ruleId);
        }

        // 否则使用知识库的默认规则
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb != null && kb.getDefaultRuleId() != null) {
            return processingRuleMapper.selectById(kb.getDefaultRuleId());
        }

        // 否则使用全局默认规则
        LambdaQueryWrapper<ProcessingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessingRule::getIsDefault, 1)
               .isNull(ProcessingRule::getKnowledgeBaseId)
               .last("LIMIT 1");
        
        return processingRuleMapper.selectOne(wrapper);
    }

    /**
     * 保存文件到本地存储
     */
    private String saveFileToLocal(MultipartFile file, Long knowledgeBaseId, String fileType) {
        try {
            // 创建存储路径：./storage/files/{knowledgeBaseId}/{date}/{uuid}.{ext}
            String dateStr = LocalDateTime.now().toString().substring(0, 10);
            String fileName = UUID.randomUUID().toString() + "." + fileType;
            Path dirPath = Paths.get(storagePath, knowledgeBaseId.toString(), dateStr);
            
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path filePath = dirPath.resolve(fileName);
            file.transferTo(filePath.toFile());

            return filePath.toString();
        } catch (IOException e) {
            log.error("Failed to save file to local storage: {}", e.getMessage(), e);
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException("文件名无效");
        }
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            throw new BusinessException("文件无扩展名");
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * 验证文件类型
     */
    private boolean isValidFileType(String fileType) {
        String[] supportedTypes = {"pdf", "doc", "docx", "md", "markdown", "txt"};
        for (String type : supportedTypes) {
            if (type.equals(fileType.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 转换为DTO
     */
    private FileDTO convertToDTO(File file) {
        FileDTO dto = new FileDTO();
        dto.setId(file.getId());
        dto.setKnowledgeBaseId(file.getKnowledgeBaseId());
        dto.setFileName(file.getFileName());
        dto.setFileFormat(file.getFileFormat());
        dto.setFileSize(file.getFileSize());
        dto.setCosPath(file.getCosPath());
        dto.setUploadTime(file.getUploadTime());
        dto.setProcessStatus(file.getProcessStatus());
        dto.setProcessTime(file.getProcessTime());
        dto.setErrorMessage(file.getErrorMessage());
        return dto;
    }
}
