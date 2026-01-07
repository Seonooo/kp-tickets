package personal.ai.core.booking.adapter.out.cache;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Cache Operation Logging Aspect
 *
 * @Cacheable 메서드의 전체 실행 시간 측정 (직렬화 포함)
 */
@Slf4j
@Aspect
@Component
public class CacheOperationLoggingAspect {

    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object logCacheOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        log.debug("Cache operation started: method={}, args={}", methodName, args);

        try {
            Object result = joinPoint.proceed();
            long totalTime = System.currentTimeMillis() - startTime;

            log.info("Cache operation completed: method={}, args={}, totalTime={}ms (includes serialization)",
                    methodName, args, totalTime);

            return result;
        } catch (Throwable e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("Cache operation failed: method={}, args={}, totalTime={}ms, error={}",
                    methodName, args, totalTime, e.getMessage(), e);
            throw e;
        }
    }
}
