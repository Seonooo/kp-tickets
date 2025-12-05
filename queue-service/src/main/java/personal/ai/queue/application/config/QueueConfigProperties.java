package personal.ai.queue.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Queue 설정 Properties
 * application.yml의 queue.* 설정을 바인딩
 */
@ConfigurationProperties(prefix = "queue")
public record QueueConfigProperties(
        Active active,
        Scheduler scheduler,
        Polling polling
) {
    public record Active(
            int maxSize,
            int tokenTtlSeconds
    ) {}

    public record Scheduler(
            int activationIntervalMs,
            int cleanupIntervalMs
    ) {}

    public record Polling(
            long fastIntervalMs,
            long slowIntervalMs,
            int fastThreshold,
            long minIntervalMs,
            int rateLimitCapacity,
            double rateLimitRefillRate,  // Token Bucket: 초당 리필 토큰 수
            int executorPoolSize
    ) {}
}
