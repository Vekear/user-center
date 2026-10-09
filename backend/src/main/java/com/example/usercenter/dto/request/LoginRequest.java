package com.example.usercenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 勾选后 refresh token 有效期更长（14 天 vs 1 天），用于"记住我" */
    private Boolean remember;
}
