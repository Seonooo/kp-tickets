package personal.ai.queue.adapter.out.lock;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Scheduler Lock 설정 Properties
 *
 * 설정 예시:
 * scheduler:
 * lock:
 * strategy: cluster # none | cluster
 * ttl-seconds: 30 # 락 TTL (초)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "scheduler.lock")
public class SchedulerLockProperties {

    /**
     * 락 전략
     * - none: 락 사용 안 함 (로컬 개발)
     * - cluster: Redis Cluster + 콘서트별 락 (운영)
     */
    private String strategy = "none";

    /**
     * 락 TTL (초)
     * 기본값: 30초 (스케줄러 최대 실행 시간 + 안전 마진)
     */
    private int ttlSeconds = 30;
}
