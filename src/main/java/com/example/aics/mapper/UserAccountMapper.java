package com.example.aics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aics.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
