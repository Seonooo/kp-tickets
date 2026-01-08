package personal.ai.queue.adapter.out.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import personal.ai.queue.application.port.out.SchedulerLockPort;

import java.time.Duration;

/**
 * Scheduler Lock Adapter Factory
 * 환경변수에 따라 적절한 SchedulerLockPort 구현체를 생성
 *
 * 설정:
 * - scheduler.lock.strategy=none → NoLockAdapter (로컬 개발)
 * - scheduler.lock.strategy=cluster → ClusterLockAdapter (운영)
 */
@Slf4j
@Configuration
public class SchedulerLockAdapterFactory {

    /**
     * NoLockAdapter 빈 생성
     * scheduler.lock.strategy=none 또는 설정이 없는 경우 (기본값)
     */
    @Bean
    @ConditionalOnProperty(name = "scheduler.lock.strategy", havingValue = "none", matchIfMissing = true)
    public SchedulerLockPort noLockAdapter() {
        log.info("Creating NoLockAdapter - No distributed lock will be used");
        return new NoLockAdapter();
    }

    /**
     * ClusterLockAdapter 빈 생성
     * scheduler.lock.strategy=cluster 인 경우
     */
    @Bean
    @ConditionalOnProperty(name = "scheduler.lock.strategy", havingValue = "cluster")
    public SchedulerLockPort clusterLockAdapter(
            StringRedisTemplate redisTemplate,
            SchedulerLockProperties properties) {

        log.info("Creating ClusterLockAdapter - TTL: {}s", properties.getTtlSeconds());
        return new ClusterLockAdapter(
                redisTemplate,
                Duration.ofSeconds(properties.getTtlSeconds()));
    }
}
