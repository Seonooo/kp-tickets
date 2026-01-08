package personal.ai.core.booking.adapter.out.cache;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cache Operation Logging Aspect
 *
 * @Cacheable 메서드의 전체 실행 시간 측정 (직렬화 포함)
 * 
 *            보안: raw args 대신 sanitized 정보만 로깅하여 PII 유출 방지
 */
@Slf4j
@Aspect
@Component
public class CacheOperationLoggingAspect {

    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object logCacheOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        // 보안: args 타입만 로깅 (실제 값 노출 방지)
        String argTypes = sanitizeArgs(args);

        log.debug("Cache operation started: method={}, argTypes={}", methodName, argTypes);

        try {
            Object result = joinPoint.proceed();
            long totalTimeMs = (System.nanoTime() - startTime) / 1_000_000;

            log.info("Cache operation completed: method={}, argCount={}, totalTime={}ms (includes serialization)",
                    methodName, args.length, totalTimeMs);

            // TRACE 레벨에서만 sanitized args 로깅
            if (log.isTraceEnabled()) {
                log.trace("Cache operation details: method={}, argTypes={}", methodName, argTypes);
            }

            return result;
        } catch (Throwable e) {
            long totalTimeMs = (System.nanoTime() - startTime) / 1_000_000;
            log.error("Cache operation failed: method={}, argCount={}, totalTime={}ms, error={}",
                    methodName, args.length, totalTimeMs, e.getMessage(), e);

            // TRACE 레벨에서만 sanitized args 로깅
            if (log.isTraceEnabled()) {
                log.trace("Failed cache operation details: method={}, argTypes={}", methodName, argTypes);
            }
            throw e;
        }
    }

    /**
     * args를 안전하게 변환하여 타입 정보만 반환
     * 실제 값은 노출하지 않아 PII 유출 방지
     */
    private String sanitizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        return Arrays.stream(args)
                .map(arg -> arg == null ? "null" : arg.getClass().getSimpleName())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
