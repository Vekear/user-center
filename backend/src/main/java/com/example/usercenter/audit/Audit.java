package com.example.usercenter.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要写审计日志的方法。
 * targetId 解析规则：方法参数里第一个 Number 类型参数（通常就是路径上的 id）；
 * 需要更精确的 detail 时，服务层直接调用 {@link AuditRecorder}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    /** 动作码，例如 PROFILE_UPDATE、ADMIN_USER_DISABLE */
    String action();

    /** 目标类型，例如 USER、NOTIFICATION */
    String targetType() default "";
}
