package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.entity.User;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT COUNT(*) FROM `user` WHERE deleted = 0")
    long countAll();

    @Select("SELECT COUNT(*) FROM `user` WHERE deleted = 0 AND status = #{status}")
    long countByStatus(String status);

    @Select("SELECT COUNT(*) FROM `user` WHERE deleted = 0 AND created_at >= #{since}")
    long countCreatedSince(LocalDateTime since);
}
