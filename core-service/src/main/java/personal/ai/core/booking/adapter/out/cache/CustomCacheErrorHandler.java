package personal.ai.core.booking.adapter.out.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Custom Cache Error Handler
 *
 * 캐시 작업 중 발생하는 예외를 로깅하여 silent failure 감지
 */
@Slf4j
public class CustomCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.error("Cache GET error - cache: {}, key: {}, error: {}",
                cache.getName(), key, exception.getMessage(), exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.error("Cache PUT error - cache: {}, key: {}, valueType: {}, error: {}",
                cache.getName(), key, value != null ? value.getClass().getSimpleName() : "null",
                exception.getMessage(), exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.error("Cache EVICT error - cache: {}, key: {}, error: {}",
                cache.getName(), key, exception.getMessage(), exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.error("Cache CLEAR error - cache: {}, error: {}",
                cache.getName(), exception.getMessage(), exception);
    }
}
