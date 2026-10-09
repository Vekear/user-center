package com.example.usercenter.common;

import lombok.Getter;

/**
 * 业务错误码。code 为 0 表示成功；httpStatus 决定响应状态码。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, 200, "ok"),

    BAD_REQUEST(1001, 400, "参数校验失败"),
    NOT_FOUND(1002, 404, "资源不存在"),

    UNAUTHORIZED(2001, 401, "未登录或登录已过期"),
    TOKEN_EXPIRED(2002, 401, "登录已过期，请重新登录"),

    FORBIDDEN(3001, 403, "没有权限执行该操作"),
    ACCOUNT_DISABLED(3002, 403, "账号已被禁用"),
    ACCOUNT_LOCKED(3003, 403, "账号已锁定，请稍后再试"),

    USERNAME_EXISTS(4001, 409, "用户名已存在"),
    EMAIL_EXISTS(4002, 409, "邮箱已被注册"),
    PHONE_EXISTS(4003, 409, "手机号已被注册"),
    BAD_CREDENTIALS(4004, 401, "用户名或密码错误"),
    OLD_PASSWORD_WRONG(4005, 400, "原密码不正确"),
    PASSWORD_POLICY(4006, 400, "密码强度不足"),
    CONFLICT(4090, 409, "数据冲突，请重试"),
    LAST_ADMIN(4091, 409, "系统至少需要保留一个可用的管理员"),
    SELF_OPERATION(4092, 409, "不能对自己执行该操作"),

    FILE_INVALID(5001, 400, "文件不合法"),
    FILE_TOO_LARGE(5002, 413, "文件过大"),

    SERVER_ERROR(5000, 500, "服务器内部错误，请稍后再试");

    private final int code;
    private final int httpStatus;
    private final String message;

    ErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
