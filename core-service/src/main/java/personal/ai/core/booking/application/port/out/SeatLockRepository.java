package personal.ai.core.booking.application.port.out;

/**
 * Seat Lock Repository (Output Port)
 * Redis SETNX 기반 좌석 선점 인터페이스
 */
public interface SeatLockRepository {

    /**
     * 좌석 선점 시도 (Redis SETNX)
     * Fail-Fast: 선점 실패 시 대기하지 않고 즉시 false 반환
     *
     * @param seatId     좌석 ID
     * @param userId     사용자 ID
     * @param ttlSeconds TTL (초 단위, 5분 = 300초)
     * @return true: 선점 성공, false: 이미 다른 사용자가 선점
     */
    boolean tryLock(Long seatId, Long userId, int ttlSeconds);

    /**
     * 좌석 선점 해제 (Redis Lua Script: 값 확인 후 삭제)
     *
     * @param seatId 좌석 ID
     * @param userId 사용자 ID (Lock 소유자 검증용)
     */
    void unlock(Long seatId, Long userId);

    /**
     * 좌석 선점 여부 확인 (Redis EXISTS)
     *
     * @param seatId 좌석 ID
     * @return true: 선점됨, false: 선점 안됨
     */
    boolean isLocked(Long seatId);
}
