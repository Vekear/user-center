package com.example.usercenter.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_session")
public class UserSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** refresh token 的 SHA-256，明文只在 Cookie 里 */
    private String refreshTokenHash;

    private String deviceName;
    private String userAgent;
    private String ip;
    private LocalDateTime lastSeenAt;
    private LocalDateTime expiresAt;
    private Boolean revoked;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
