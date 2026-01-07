package personal.ai.queue.application.port.out;

/**
 * 스케줄러 락 Port
 * 헥사고날 아키텍처의 Output Port로, 스케줄러 실행 제어를 담당
 *
 * 구현체:
 * - NoLockAdapter: 락 없이 항상 실행 (로컬 개발용)
 * - ClusterLockAdapter: Redis Cluster + 콘서트별 분산 락 (운영용)
 */
public interface SchedulerLockPort {

    /**
     * 스케줄러 실행 전 락 획득 시도
     *
     * @param schedulerName 스케줄러 이름 (예: "move", "cleanup")
     * @param concertId     콘서트 ID
     * @return true: 실행 가능, false: 스킵 (다른 인스턴스가 처리 중)
     */
    boolean tryAcquire(String schedulerName, String concertId);

    /**
     * 스케줄러 실행 후 락 해제
     *
     * @param schedulerName 스케줄러 이름
     * @param concertId     콘서트 ID
     */
    void release(String schedulerName, String concertId);

    /**
     * 전략 이름 반환 (로깅/모니터링용)
     *
     * @return 전략 이름 (예: "none", "cluster")
     */
    String getStrategyName();
}
