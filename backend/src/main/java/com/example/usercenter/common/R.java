package com.example.usercenter.common;

import lombok.Data;
import lombok.experimental.Accessors;
import org.slf4j.MDC;

/**
 * 统一响应包装。traceId 取自 MDC（由 TraceIdFilter 写入），便于把前端报错和日志对上。
 */
@Data
@Accessors(chain = true)
public class R<T> {

    public static final String TRACE_ID_KEY = "traceId";

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> R<T> ok(T data) {
        return new R<T>()
                .setCode(ErrorCode.SUCCESS.getCode())
                .setMessage(ErrorCode.SUCCESS.getMessage())
                .setData(data)
                .setTraceId(currentTraceId());
    }

    public static <T> R<T> ok() {
        return R.<T>ok(null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message, T data) {
        return new R<T>()
                .setCode(errorCode.getCode())
                .setMessage(message == null ? errorCode.getMessage() : message)
                .setData(data)
                .setTraceId(currentTraceId());
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return R.<T>fail(errorCode, errorCode.getMessage(), null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return R.<T>fail(errorCode, message, null);
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }
}
