package personal.ai.queue.adapter.out.lock;

import lombok.extern.slf4j.Slf4j;
import personal.ai.queue.application.port.out.SchedulerLockPort;

/**
 * NoLock Adapter
 * 락을 사용하지 않는 어댑터 (현재 동작과 동일)
 *
 * 사용 환경:
 * - 로컬 개발 (단일 인스턴스)
 * - 단위 테스트
 *
 * 주의: 다중 인스턴스 운영 환경에서는 사용 금지!
 */
@Slf4j
public class NoLockAdapter implements SchedulerLockPort {

    @Override
    public boolean tryAcquire(String schedulerName, String concertId) {
        log.debug("[NoLock] Always allow: scheduler={}, concertId={}", schedulerName, concertId);
        return true;  // 항상 실행 허용
    }

    @Override
    public void release(String schedulerName, String concertId) {
        // No-op: 락이 없으므로 해제할 것도 없음
    }

    @Override
    public String getStrategyName() {
        return "none";
    }
}
