package com.example.usercenter.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 登录/刷新的返回：access token 在 body 里（前端只放内存），
 * refresh token 只出现在 Set-Cookie，不在这里。
 */
@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private long expiresIn;
    private UserResponse user;
}
