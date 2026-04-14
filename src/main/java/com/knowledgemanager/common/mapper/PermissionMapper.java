package com.knowledgemanager.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgemanager.common.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
