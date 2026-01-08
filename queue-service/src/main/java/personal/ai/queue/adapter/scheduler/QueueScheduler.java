package personal.ai.queue.adapter.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import personal.ai.queue.application.port.in.CleanupExpiredTokensUseCase;
import personal.ai.queue.application.port.in.GetActiveConcertsUseCase;
import personal.ai.queue.application.port.in.MoveToActiveQueueUseCase;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.application.port.out.SchedulerLockPort;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Queue Scheduler
 * Wait -> Active 전환 및 만료 토큰 정리를 주기적으로 실행
 * 
 * 헥사고날 아키텍처:
 * - SchedulerLockPort를 통해 분산 락 전략을 주입받음
 * - 환경변수로 전략 교체 가능 (none / cluster)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final MoveToActiveQueueUseCase moveToActiveQueueUseCase;
    private final CleanupExpiredTokensUseCase cleanupExpiredTokensUseCase;
    private final GetActiveConcertsUseCase getActiveConcertsUseCase;
    private final SchedulerLockPort schedulerLockPort;
    private final QueueRepository queueRepository;
    private final MeterRegistry meterRegistry;

    private static final String MOVE_SCHEDULER = "move";
    private static final String CLEANUP_SCHEDULER = "cleanup";

    // 콘서트별 처리 속도 저장 (Gauge가 참조할 값)
    private final ConcurrentHashMap<String, AtomicReference<Double>> throughputMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<Double>> estimatedWaitMap = new ConcurrentHashMap<>();

    /**
     * Wait Queue -> Active Queue 전환 스케줄러
     * 주기: application.yml의 queue.scheduler.activation-interval-ms
     * 기본값: 5초
     */
    @Scheduled(fixedDelayString = "${queue.scheduler.activation-interval-ms:5000}")
    public void moveWaitingUsersToActive() {
        try {
            log.debug("Starting move scheduler with strategy: {}", schedulerLockPort.getStrategyName());

            // 활성화된 콘서트 목록 조회
            List<String> concertIds = getActiveConcertsUseCase.getActiveConcerts();

            if (concertIds.isEmpty()) {
                log.debug("No active concerts found");
                return;
            }

            // 처리할 콘서트 수 기록 (인스턴스별 부하 분산 확인용)
            Gauge.builder("scheduler.concerts.count", concertIds, List::size)
                    .tag("scheduler_type", MOVE_SCHEDULER)
                    .description("Number of concerts processed by this scheduler instance")
                    .register(meterRegistry);

            int totalMoved = 0;

            // 각 콘서트별로 처리 (콘서트별 락 적용)
            for (String concertId : concertIds) {
                // 락 획득 시도
                if (!schedulerLockPort.tryAcquire(MOVE_SCHEDULER, concertId)) {
                    // 락 획득 실패 카운트 (다른 인스턴스가 처리 중)
                    Counter.builder("scheduler.lock.acquire.failures")
                            .tag("scheduler_type", MOVE_SCHEDULER)
                            .tag("concert_id", concertId)
                            .description("Number of lock acquisition failures (another instance processing)")
                            .register(meterRegistry)
                            .increment();

                    log.debug("Skipping concertId={} (another instance is processing)", concertId);
                    continue;
                }

                try {
                    // 처리 시간 측정 (Throughput 계산용)
                    long startTime = System.currentTimeMillis();
                    Timer.Sample sample = Timer.start(meterRegistry);

                    int moved = moveToActiveQueueUseCase.moveWaitingToActive(concertId);

                    long durationMs = System.currentTimeMillis() - startTime;
                    sample.stop(Timer.builder("scheduler.move.duration")
                            .tag("concert_id", concertId)
                            .description("Time taken to move users from wait to active queue")
                            .register(meterRegistry));

                    totalMoved += moved;

                    // Counter로 이동된 사용자 수 기록
                    Counter.builder("scheduler.move.users")
                            .tag("concert_id", concertId)
                            .description("Number of users moved from wait to active queue")
                            .register(meterRegistry)
                            .increment(moved);

                    // Throughput 계산 (초당 처리 인원)
                    double throughput = 0.0;
                    if (durationMs > 0 && moved > 0) {
                        throughput = (double) moved / (durationMs / 1000.0);
                    }

                    // Wait Queue 크기 조회
                    Long waitQueueSize = queueRepository.getWaitQueueSize(concertId);

                    // Estimated Wait Time 계산 (예상 대기 시간)
                    double estimatedWaitSeconds = 0.0;
                    if (throughput > 0 && waitQueueSize != null && waitQueueSize > 0) {
                        estimatedWaitSeconds = waitQueueSize / throughput;
                    }

                    // Map에 저장 (Gauge가 참조)
                    throughputMap.computeIfAbsent(concertId, k -> {
                        AtomicReference<Double> ref = new AtomicReference<>(0.0);
                        Gauge.builder("queue.throughput.users_per_second", ref, AtomicReference::get)
                                .tag("concert_id", concertId)
                                .description("Users processed per second (Wait → Active)")
                                .register(meterRegistry);
                        return ref;
                    }).set(throughput);

                    estimatedWaitMap.computeIfAbsent(concertId, k -> {
                        AtomicReference<Double> ref = new AtomicReference<>(0.0);
                        Gauge.builder("queue.estimated.wait.seconds", ref, AtomicReference::get)
                                .tag("concert_id", concertId)
                                .description("Estimated wait time for last user in queue")
                                .register(meterRegistry);
                        return ref;
                    }).set(estimatedWaitSeconds);

                    // 성능 측정 로그 (throughput 추가)
                    log.info("[PERF] MoveToActive: concertId={}, movedUsers={}, throughput={:.1f} users/sec, estimatedWait={:.1f}s",
                            concertId, moved, throughput, estimatedWaitSeconds);

                } catch (Exception e) {
                    log.error("Failed to move users for concertId={}", concertId, e);
                } finally {
                    // 락 해제
                    schedulerLockPort.release(MOVE_SCHEDULER, concertId);
                }
            }

            if (totalMoved > 0) {
                log.info("Move scheduler completed: totalMoved={}, concerts={}",
                        totalMoved, concertIds.size());
            }

        } catch (Exception e) {
            log.error("Move scheduler failed", e);
        }
    }

    /**
     * 만료된 토큰 정리 스케줄러
     * 주기: application.yml의 queue.scheduler.cleanup-interval-ms
     * 기본값: 60초 (1분)
     */
    @Scheduled(fixedDelayString = "${queue.scheduler.cleanup-interval-ms:60000}")
    public void cleanupExpiredTokens() {
        try {
            log.debug("Starting cleanup scheduler with strategy: {}", schedulerLockPort.getStrategyName());

            // 활성화된 콘서트 목록 조회
            List<String> concertIds = getActiveConcertsUseCase.getActiveConcerts();

            if (concertIds.isEmpty()) {
                log.debug("No active concerts found");
                return;
            }

            // 처리할 콘서트 수 기록
            Gauge.builder("scheduler.concerts.count", concertIds, List::size)
                    .tag("scheduler_type", CLEANUP_SCHEDULER)
                    .description("Number of concerts processed by this scheduler instance")
                    .register(meterRegistry);

            long totalRemoved = 0;

            // 각 콘서트별로 처리 (콘서트별 락 적용)
            for (String concertId : concertIds) {
                // 락 획득 시도
                if (!schedulerLockPort.tryAcquire(CLEANUP_SCHEDULER, concertId)) {
                    // 락 획득 실패 카운트
                    Counter.builder("scheduler.lock.acquire.failures")
                            .tag("scheduler_type", CLEANUP_SCHEDULER)
                            .tag("concert_id", concertId)
                            .description("Number of lock acquisition failures (another instance processing)")
                            .register(meterRegistry)
                            .increment();

                    log.debug("Skipping cleanup for concertId={} (another instance is processing)", concertId);
                    continue;
                }

                try {
                    long removed = cleanupExpiredTokensUseCase.cleanupExpired(concertId);
                    totalRemoved += removed;

                    // Counter로 삭제된 토큰 수 기록
                    if (removed > 0) {
                        Counter.builder("scheduler.cleanup.removed")
                                .tag("concert_id", concertId)
                                .description("Number of expired tokens removed")
                                .register(meterRegistry)
                                .increment(removed);

                        log.info("Cleaned up expired tokens: concertId={}, count={}",
                                concertId, removed);
                    }
                } catch (Exception e) {
                    log.error("Failed to cleanup tokens for concertId={}", concertId, e);
                } finally {
                    // 락 해제
                    schedulerLockPort.release(CLEANUP_SCHEDULER, concertId);
                }
            }

            if (totalRemoved > 0) {
                log.info("Cleanup scheduler completed: totalRemoved={}, concerts={}",
                        totalRemoved, concertIds.size());
            }

        } catch (Exception e) {
            log.error("Cleanup scheduler failed", e);
        }
    }
}
