package personal.ai.queue.adapter.out.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import personal.ai.queue.domain.model.QueueConfig;
import personal.ai.queue.domain.model.QueuePosition;
import personal.ai.queue.domain.model.QueueStatus;
import personal.ai.queue.domain.model.QueueToken;

import java.time.Instant;
import java.util.List;

/**
 * Phase 3-2 최적화: enter_queue.lua 스크립트 실행 Adapter
 *
 * 6회 Redis 호출을 1회 Lua 스크립트로 통합:
 *   - HGETALL active:token (Active 확인)
 *   - ZRANK queue:wait (Wait 확인)
 *   - ZCARD queue:wait (Wait 크기)
 *   - ZADD queue:wait (신규 진입)
 *   - ZRANK queue:wait (신규 순번)
 *   - ZCARD queue:wait (전체 크기)
 *
 * 효과:
 *   - 네트워크 RTT 5회 절약 (약 5ms)
 *   - 원자성 보장
 *   - 예상 TPS 향상: +30~50%
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEnterQueueAdapter {

    private static final int POSITION_DISPLAY_OFFSET = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<String> enterQueueScript;
    private final QueueConfig queueConfig;

    /**
     * 대기열 진입 (Lua 스크립트 통합)
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return QueuePosition (ACTIVE | WAITING | NEW)
     */
    public QueuePosition enterQueue(String concertId, String userId) {
        // Redis Keys
        String activeTokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);
        String waitQueueKey = RedisKeyGenerator.waitQueueKey(concertId);

        // Arguments
        long timestamp = System.currentTimeMillis();
        long currentTime = Instant.now().getEpochSecond();

        List<String> keys = List.of(activeTokenKey, waitQueueKey);
        List<String> args = List.of(
                userId,
                String.valueOf(timestamp),
                String.valueOf(currentTime)
        );

        // Lua 스크립트 실행 (단일 Redis 호출!)
        String jsonResult = redisTemplate.execute(enterQueueScript, keys,
                args.toArray(new String[0]));

        if (jsonResult == null || jsonResult.isEmpty()) {
            log.error("Enter queue script returned null or empty: concertId={}, userId={}",
                    concertId, userId);
            throw new IllegalStateException("Enter queue script failed");
        }

        // JSON 파싱 및 QueuePosition 변환
        return parseScriptResult(concertId, userId, jsonResult);
    }

    /**
     * Lua 스크립트 결과 파싱
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @param jsonResult Lua 스크립트 반환 JSON
     * @return QueuePosition
     */
    private QueuePosition parseScriptResult(String concertId, String userId, String jsonResult) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(jsonResult);

            String status = json.get("status").asText();
            long position = json.get("position").asLong();
            long totalWaiting = json.get("totalWaiting").asLong();

            log.debug("Enter queue script result: concertId={}, userId={}, status={}, position={}, total={}",
                    concertId, userId, status, position, totalWaiting);

            return switch (status) {
                case "ACTIVE" -> {
                    // 이미 활성화된 사용자
                    JsonNode tokenNode = json.get("token");
                    QueueToken token = parseQueueToken(concertId, userId, tokenNode);
                    yield QueuePosition.alreadyActive(token);
                }
                case "WAITING" -> {
                    // 이미 대기 중인 사용자 (재진입)
                    yield QueuePosition.alreadyWaiting(
                            concertId,
                            userId,
                            position + POSITION_DISPLAY_OFFSET,  // 1-based 표시
                            totalWaiting,
                            queueConfig.activeMaxSize(),
                            queueConfig.activationIntervalSeconds()
                    );
                }
                case "NEW" -> {
                    // 신규 진입 사용자
                    yield QueuePosition.newEntry(
                            concertId,
                            userId,
                            position + POSITION_DISPLAY_OFFSET,  // 1-based 표시
                            totalWaiting,
                            queueConfig.activeMaxSize(),
                            queueConfig.activationIntervalSeconds()
                    );
                }
                default -> throw new IllegalStateException("Unknown queue status: " + status);
            };

        } catch (Exception e) {
            log.error("Failed to parse enter queue script result: concertId={}, userId={}, json={}",
                    concertId, userId, jsonResult, e);
            throw new IllegalStateException("Failed to parse enter queue result", e);
        }
    }

    /**
     * Token JSON 파싱
     */
    private QueueToken parseQueueToken(String concertId, String userId, JsonNode tokenNode) {
        String token = tokenNode.get("token").asText();
        String statusStr = tokenNode.get("status").asText();
        int extendCount = tokenNode.get("extend_count").asInt();
        long expiredAt = tokenNode.get("expired_at").asLong();

        return new QueueToken(
                concertId,
                userId,
                token,
                QueueStatus.valueOf(statusStr),
                null,  // position은 Active 토큰에는 없음
                Instant.ofEpochSecond(expiredAt),
                extendCount
        );
    }
}
