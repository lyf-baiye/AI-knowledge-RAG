package com.knowledgemanager.knowledgebase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgemanager.auth.service.PermissionService;
import com.knowledgemanager.common.constant.Constants;
import com.knowledgemanager.common.dto.KnowledgeBaseCreateDTO;
import com.knowledgemanager.common.dto.KnowledgeBaseDTO;
import com.knowledgemanager.common.entity.KnowledgeBase;
import com.knowledgemanager.common.exception.BusinessException;
import com.knowledgemanager.common.exception.ForbiddenException;
import com.knowledgemanager.common.exception.NotFoundException;
import com.knowledgemanager.common.mapper.KnowledgeBaseMapper;
import com.knowledgemanager.common.mapper.UserMapper;
import com.knowledgemanager.common.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseService {

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PermissionService permissionService;

    @Transactional
    public KnowledgeBase createKnowledgeBase(KnowledgeBaseCreateDTO dto, Long userId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setCreatorId(userId);
        kb.setStatus(Constants.KnowledgeBaseStatus.ACTIVE);
        kb.setVisibility(dto.getVisibility());
        kb.setDeleted(0);

        knowledgeBaseMapper.insert(kb);

        // 为创建者授予MANAGE权限
        permissionService.grantPermission(userId, Constants.ResourceType.KNOWLEDGE_BASE, kb.getId(), Constants.Permission.MANAGE, userId);

        log.info("Knowledge base created: id={}, name={}, userId={}", kb.getId(), kb.getName(), userId);
        return kb;
    }

    public KnowledgeBaseDTO getKnowledgeBase(Long kbId, Long userId) {
        // 检查权限
        if (!permissionService.hasPermission(userId, Constants.ResourceType.KNOWLEDGE_BASE, kbId, Constants.Permission.READ)) {
            throw new ForbiddenException("没有访问权限");
        }

        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || kb.getDeleted() == 1) {
            throw new NotFoundException("知识库不存在");
        }

        return convertToDTO(kb);
    }

    public Page<KnowledgeBaseDTO> getUserKnowledgeBases(Long userId, int pageNum, int pageSize) {
        // 获取用户有权限的知识库ID列表
        List<Long> kbIds = permissionService.getUserKnowledgeBases(userId, Constants.Permission.READ);

        Page<KnowledgeBaseDTO> page = new Page<>(pageNum, pageSize);
        if (kbIds.isEmpty()) {
            return page;
        }

        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KnowledgeBase::getId, kbIds)
               .eq(KnowledgeBase::getDeleted, 0)
               .orderByDesc(KnowledgeBase::getCreateTime);

        Page<KnowledgeBase> kbPage = knowledgeBaseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<KnowledgeBaseDTO> resultPage = new Page<>(pageNum, pageSize, kbPage.getTotal());
        resultPage.setRecords(kbPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));

        return resultPage;
    }

    @Transactional
    public void updateKnowledgeBase(Long kbId, KnowledgeBaseCreateDTO dto, Long userId) {
        permissionService.checkPermission(userId, Constants.ResourceType.KNOWLEDGE_BASE, kbId, Constants.Permission.WRITE);

        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || kb.getDeleted() == 1) {
            throw new NotFoundException("知识库不存在");
        }

        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        knowledgeBaseMapper.updateById(kb);

        log.info("Knowledge base updated: id={}, userId={}", kbId, userId);
    }

    @Transactional
    public void deleteKnowledgeBase(Long kbId, Long userId) {
        permissionService.checkPermission(userId, Constants.ResourceType.KNOWLEDGE_BASE, kbId, Constants.Permission.MANAGE);

        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || kb.getDeleted() == 1) {
            throw new NotFoundException("知识库不存在");
        }

        kb.setDeleted(1);
        kb.setStatus(Constants.KnowledgeBaseStatus.DELETED);
        knowledgeBaseMapper.updateById(kb);

        log.info("Knowledge base deleted: id={}, userId={}", kbId, userId);
    }

    private KnowledgeBaseDTO convertToDTO(KnowledgeBase kb) {
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setId(kb.getId());
        dto.setName(kb.getName());
        dto.setDescription(kb.getDescription());
        dto.setCreatorId(kb.getCreatorId());
        dto.setStatus(kb.getStatus());
        dto.setVisibility(kb.getVisibility());
        dto.setDefaultRuleId(kb.getDefaultRuleId());
        dto.setCreateTime(kb.getCreateTime() != null ? kb.getCreateTime().toString() : null);
        dto.setUpdateTime(kb.getUpdateTime() != null ? kb.getUpdateTime().toString() : null);

        User creator = userMapper.selectById(kb.getCreatorId());
        dto.setCreatorName(creator != null ? creator.getUsername() : "Unknown");

        return dto;
    }
}
