package com.knowledgemanager.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgemanager.common.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
