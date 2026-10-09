package com.example.usercenter.controller;

import com.example.usercenter.common.R;
import com.example.usercenter.dto.request.LoginRequest;
import com.example.usercenter.dto.request.RegisterRequest;
import com.example.usercenter.dto.response.LoginResponse;
import com.example.usercenter.dto.response.UserResponse;
import com.example.usercenter.security.CookieService;
import com.example.usercenter.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public R<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    @Operation(summary = "登录：access token 在 body，refresh token 在 httpOnly Cookie")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        return R.ok(authService.login(request, httpRequest, httpResponse));
    }

    @Operation(summary = "刷新：读取 Cookie 中的 refresh token 并轮换")
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawToken = cookieService.readRefreshToken(httpRequest);
        return R.ok(authService.refresh(rawToken, httpRequest, httpResponse));
    }

    @Operation(summary = "登出：吊销当前会话并清 Cookie")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(cookieService.readRefreshToken(httpRequest), httpResponse);
        return R.ok();
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public R<UserResponse> me() {
        return R.ok(authService.me());
    }
}
