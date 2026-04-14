package com.knowledgemanager.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgemanager.common.constant.Constants;
import com.knowledgemanager.common.entity.Permission;
import com.knowledgemanager.common.entity.User;
import com.knowledgemanager.common.exception.ForbiddenException;
import com.knowledgemanager.common.mapper.PermissionMapper;
import com.knowledgemanager.common.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PermissionService {

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private UserMapper userMapper;

    public boolean hasPermission(Long userId, String resourceType, Long resourceId, String permission) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        // ADMIN角色拥有所有权限
        if (Constants.UserRole.ADMIN.equals(user.getRole())) {
            return true;
        }

        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getUserId, userId)
               .eq(Permission::getResourceType, resourceType)
               .eq(Permission::getResourceId, resourceId)
               .eq(Permission::getPermission, permission)
               .eq(Permission::getDeleted, 0);

        Long count = permissionMapper.selectCount(wrapper);
        return count > 0;
    }

    public void checkPermission(Long userId, String resourceType, Long resourceId, String permission) {
        if (!hasPermission(userId, resourceType, resourceId, permission)) {
            throw new ForbiddenException("没有操作权限");
        }
    }

    public List<Long> getUserKnowledgeBases(Long userId, String permission) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getUserId, userId)
               .eq(Permission::getResourceType, Constants.ResourceType.KNOWLEDGE_BASE)
               .eq(Permission::getPermission, permission)
               .eq(Permission::getDeleted, 0);

        List<Permission> permissions = permissionMapper.selectList(wrapper);
        return permissions.stream()
                .map(Permission::getResourceId)
                .collect(Collectors.toList());
    }

    public void grantPermission(Long userId, String resourceType, Long resourceId, String permission, Long grantedBy) {
        // 检查是否已存在相同权限
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getUserId, userId)
               .eq(Permission::getResourceType, resourceType)
               .eq(Permission::getResourceId, resourceId)
               .eq(Permission::getPermission, permission)
               .eq(Permission::getDeleted, 0);
        
        Long count = permissionMapper.selectCount(wrapper);
        if (count > 0) {
            log.warn("Permission already exists: user={}, resource={}/{}, permission={}",
                    userId, resourceType, resourceId, permission);
            return;
        }

        Permission perm = new Permission();
        perm.setUserId(userId);
        perm.setResourceType(resourceType);
        perm.setResourceId(resourceId);
        perm.setPermission(permission);
        perm.setGrantedBy(grantedBy);
        perm.setGrantTime(LocalDateTime.now());
        perm.setDeleted(0);

        permissionMapper.insert(perm);
        log.info("Permission granted: user={}, resource={}/{}, permission={}",
                 userId, resourceType, resourceId, permission);
    }

    public void revokePermission(Long userId, String resourceType, Long resourceId, String permission) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getUserId, userId)
               .eq(Permission::getResourceType, resourceType)
               .eq(Permission::getResourceId, resourceId)
               .eq(Permission::getPermission, permission)
               .eq(Permission::getDeleted, 0);

        Permission perm = permissionMapper.selectOne(wrapper);
        if (perm != null) {
            perm.setDeleted(1);
            permissionMapper.updateById(perm);
            log.info("Permission revoked: user={}, resource={}/{}, permission={}",
                     userId, resourceType, resourceId, permission);
        }
    }
}
