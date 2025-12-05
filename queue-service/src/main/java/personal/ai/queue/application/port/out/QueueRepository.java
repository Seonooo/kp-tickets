package personal.ai.queue.application.port.out;

import personal.ai.queue.domain.model.QueueToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Queue Repository (Output Port)
 * Redis 대기열 저장소 인터페이스
 */
public interface QueueRepository {

    /**
     * Wait Queue에 유저 추가
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     * @return 대기 순번 (0-based)
     */
    Long addToWaitQueue(String concertId, String userId);

    /**
     * Wait Queue에서 유저의 순번 조회
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     * @return 순번 (없으면 null)
     */
    Long getWaitQueuePosition(String concertId, String userId);

    /**
     * Wait Queue 전체 인원 수
     * @param concertId 콘서트 ID
     * @return 대기 인원 수
     */
    Long getWaitQueueSize(String concertId);

    /**
     * Wait Queue에서 지정된 개수만큼 Pop
     * @param concertId 콘서트 ID
     * @param count 개수
     * @return 유저 ID 리스트
     */
    List<String> popFromWaitQueue(String concertId, int count);

    /**
     * Active Queue에 유저 추가
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     * @param token 토큰
     * @param expiredAt 만료 시간
     */
    void addToActiveQueue(String concertId, String userId, String token, Instant expiredAt);

    /**
     * Active Queue에서 유저의 토큰 조회
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     * @return 토큰 정보
     */
    Optional<QueueToken> getActiveToken(String concertId, String userId);

    /**
     * Active Token의 만료 시간 갱신
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     * @param expiredAt 새로운 만료 시간
     */
    void updateTokenExpiration(String concertId, String userId, Instant expiredAt);

    /**
     * Active Token의 상태 변경 (READY -> ACTIVE)
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     * @param status 새로운 상태
     */
    void updateTokenStatus(String concertId, String userId, personal.ai.queue.domain.model.QueueStatus status);

    /**
     * Active Token의 연장 횟수 증가
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     * @return 증가된 연장 횟수
     */
    Integer incrementExtendCount(String concertId, String userId);

    /**
     * Active Queue 전체 인원 수
     * @param concertId 콘서트 ID
     * @return 활성 인원 수
     */
    Long getActiveQueueSize(String concertId);

    /**
     * 만료된 토큰 제거 (Cleanup)
     * @param concertId 콘서트 ID
     * @return 제거된 개수
     */
    Long removeExpiredTokens(String concertId);

    /**
     * 특정 유저를 Active Queue에서 제거 (결제 완료 시)
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     */
    void removeFromActiveQueue(String concertId, String userId);

    /**
     * Wait Queue에서 유저 제거
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     */
    void removeFromWaitQueue(String concertId, String userId);

    /**
     * Wait Queue에서 Pop하고 Active Queue에 원자적으로 추가
     * 실패 시 자동 롤백으로 데이터 손실 방지
     * @param concertId 콘서트 ID
     * @param count 전환할 인원 수
     * @param expiredAt 만료 시간 (READY 상태)
     * @return 성공적으로 이동된 유저 ID 리스트
     */
    List<String> moveToActiveQueueAtomic(String concertId, int count, Instant expiredAt);

    /**
     * READY → ACTIVE 상태 전환 및 만료 시간 갱신을 원자적으로 처리
     * @param concertId 콘서트 ID
     * @param userId 유저 ID
     * @param newExpiredAt 새로운 만료 시간 (ACTIVE 상태)
     * @return true: 성공, false: 실패 (토큰 없음 또는 이미 ACTIVE)
     */
    boolean activateTokenAtomic(String concertId, String userId, Instant newExpiredAt);

    /**
     * 활성화된 콘서트 ID 목록 조회
     * Wait Queue 또는 Active Queue에 데이터가 있는 콘서트
     * @return 콘서트 ID 리스트
     */
    List<String> getActiveConcertIds();
}
