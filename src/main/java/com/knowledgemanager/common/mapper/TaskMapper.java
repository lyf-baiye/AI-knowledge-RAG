package com.knowledgemanager.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgemanager.common.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
