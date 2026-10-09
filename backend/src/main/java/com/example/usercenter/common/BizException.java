package com.example.usercenter.common;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 业务异常。字段级错误放进 fieldErrors，前端按字段回显。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, String> fieldErrors;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), Collections.emptyMap());
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, Collections.emptyMap());
    }

    public BizException(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : fieldErrors;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    public static BizException field(String field, String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message, Map.of(field, message));
    }

    /**
     * 字段级错误 + 指定错误码（例如唯一约束冲突用 409 而不是 400）。
     * 前端既可以用 HTTP 状态码，也可以用 code 判定。
     */
    public static BizException field(ErrorCode errorCode, String field, String message) {
        return new BizException(errorCode, message, Map.of(field, message));
    }
}
