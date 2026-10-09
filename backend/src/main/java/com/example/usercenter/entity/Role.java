package com.example.usercenter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("role")
public class Role {

    /** 普通用户 */
    public static final String ROLE_USER = "ROLE_USER";
    /** 管理员 */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String description;
}
