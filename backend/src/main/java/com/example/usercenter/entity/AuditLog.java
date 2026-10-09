package com.example.usercenter.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {

    public static final int DETAIL_MAX_LENGTH = 2000;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private String action;
    private String targetType;
    private String targetId;
    private String ip;
    private String userAgent;
    private Boolean success;
    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
