package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.entity.AuditLog;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("SELECT COUNT(*) FROM audit_log WHERE created_at >= #{since}")
    long countSince(LocalDateTime since);

    @Select("SELECT COUNT(*) FROM audit_log WHERE DATE(created_at) = #{day}")
    long countOn(LocalDate day);
}
