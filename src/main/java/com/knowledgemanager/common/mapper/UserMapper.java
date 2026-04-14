package com.knowledgemanager.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgemanager.common.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
