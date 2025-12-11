package personal.ai.queue.adapter.out.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.queue.domain.exception.QueueDataCorruptionException;
import personal.ai.queue.domain.exception.QueueTokenInvalidException;
import personal.ai.queue.domain.model.QueueStatus;
import personal.ai.queue.domain.model.QueueToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Redis 데이터와 도메인 객체 간의 변환을 담당하는 컨버터
 * - Redis Hash ↔ QueueToken 변환
 * - TTL 계산
 * - JSON 파싱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenConverter {

    private final ObjectMapper objectMapper;

    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_EXTEND_COUNT = "extend_count";
    private static final String FIELD_EXPIRED_AT = "expired_at";
    private static final long TTL_BUFFER_SECONDS = 60L; // TTL 버퍼 (1분)

    /**
     * Redis Hash 데이터를 QueueToken 도메인 객체로 변환합니다.
     * 호출자는 redisHashData가 비어있지 않음을 보장해야 합니다.
     *
     * @param redisHashData Redis Hash 엔트리 (비어있지 않아야 함)
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return QueueToken 도메인 객체
     * @throws QueueTokenInvalidException Redis 데이터 파싱에 실패한 경우
     */
    public QueueToken toQueueToken(Map<Object, Object> redisHashData, String concertId, String userId) {
        try {
            var token = (String) redisHashData.get(FIELD_TOKEN);
            var statusStr = (String) redisHashData.get(FIELD_STATUS);
            var extendCountStr = (String) redisHashData.get(FIELD_EXTEND_COUNT);
            var expiredAtStr = (String) redisHashData.get(FIELD_EXPIRED_AT);

            var status = QueueStatus.valueOf(statusStr);
            var extendCount = Integer.parseInt(extendCountStr);
            var expiredAt = Instant.ofEpochSecond(Long.parseLong(expiredAtStr));

            return switch (status) {
                case READY -> QueueToken.ready(concertId, userId, token, expiredAt);
                case ACTIVE -> QueueToken.active(concertId, userId, token, expiredAt, extendCount);
                default -> QueueToken.notFound(concertId, userId);
            };

        } catch (Exception e) {
            log.error("Queue token data corruption detected");
            if (log.isDebugEnabled()) {
                log.debug("Token parse failed: concertId={}, userId={}, error={}",
                        concertId, userId, e.getMessage());
            }
            throw new QueueTokenInvalidException(concertId, userId);
        }
    }

    /**
     * 토큰의 남은 TTL을 초 단위로 계산합니다.
     * TTL이 음수인 경우 0을 반환합니다.
     *
     * @param expiredAt 토큰 만료 시각
     * @return 남은 TTL (초), 최소값 0
     */
    public long calculateRemainingTtlSeconds(Instant expiredAt) {
        long ttlSeconds = expiredAt.getEpochSecond() - Instant.now().getEpochSecond() + TTL_BUFFER_SECONDS;
        return Math.max(ttlSeconds, 0);
    }

    /**
     * Lua 스크립트가 반환한 JSON 배열을 사용자 ID 리스트로 파싱합니다.
     * Jackson ObjectMapper를 사용하여 올바르게 JSON을 역직렬화합니다.
     *
     * userId에 특수문자(", ,, [, ])가 포함되어도 안전하게 파싱됩니다.
     * 입력 형식 예시: ["USER-001","USER-002"] 또는 ["USER\"001","USER,002"]
     *
     * @param jsonArrayString Lua 스크립트 실행 결과 (JSON 배열 문자열)
     * @return 사용자 ID 리스트
     * @throws QueueDataCorruptionException JSON 파싱에 실패한 경우
     */
    public List<String> parseUserIdsFromJson(String jsonArrayString) {
        if (jsonArrayString == null || jsonArrayString.isEmpty() || jsonArrayString.equals("[]")) {
            return List.of();
        }

        try {
            // Jackson ObjectMapper로 안전하게 JSON 파싱
            return objectMapper.readValue(jsonArrayString, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse user IDs from JSON: jsonArrayString={}", jsonArrayString, e);
            throw new QueueDataCorruptionException(e);
        }
    }
}