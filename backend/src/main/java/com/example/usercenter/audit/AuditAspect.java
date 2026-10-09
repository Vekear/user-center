package com.example.usercenter.audit;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 声明式审计切面：成功与失败都记录，失败时把异常类型写进 detail（异常消息本身不含敏感数据）。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditRecorder auditRecorder;

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        String targetId = resolveTargetId(joinPoint);
        boolean success = false;
        String detail = null;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable ex) {
            detail = "异常: " + ex.getClass().getSimpleName();
            throw ex;
        } finally {
            auditRecorder.record(audit.action(), audit.targetType(), targetId, success, detail);
        }
    }

    private String resolveTargetId(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Number number) {
                return String.valueOf(number);
            }
        }
        if (joinPoint.getSignature() instanceof MethodSignature signature) {
            return signature.getMethod().getName();
        }
        return null;
    }
}
