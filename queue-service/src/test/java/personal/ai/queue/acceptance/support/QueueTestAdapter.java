package personal.ai.queue.acceptance.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import personal.ai.queue.adapter.in.consumer.PaymentCompletedEvent;
import personal.ai.queue.adapter.in.web.dto.QueuePositionResponse;
import personal.ai.queue.adapter.in.web.dto.QueueTokenResponse;
import personal.ai.queue.application.port.in.*;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.service.QueueDomainService;

import java.time.Instant;
import java.util.UUID;

/**
 * Queue Test Adapter
 * 테스트를 위한 헬퍼 클래스
 */
@Component
@RequiredArgsConstructor
public class QueueTestAdapter {

    private final EnterQueueUseCase enterQueueUseCase;
    private final GetQueueStatusUseCase getQueueStatusUseCase;
    private final ActivateTokenUseCase activateTokenUseCase;
    private final ExtendTokenUseCase extendTokenUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final QueueRepository queueRepository;
    private final QueueDomainService domainService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 대기열 진입 (UseCase 호출)
     */
    public void enterQueue(String concertId, String userId) {
        var command = new EnterQueueUseCase.EnterQueueCommand(concertId, userId);
        enterQueueUseCase.enter(command);
    }

    /**
     * 대기열 진입 (HTTP 요청 시뮬레이션)
     */
    public ResponseEntity<?> enterQueueRequest(String concertId, String userId) {
        try {
            var command = new EnterQueueUseCase.EnterQueueCommand(concertId, userId);
            var position = enterQueueUseCase.enter(command);
            var response = QueuePositionResponse.from(position);
            return ResponseEntity.status(201)
                    .body(personal.ai.common.dto.ApiResponse.success("대기열 진입 성공", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 대기열 상태 조회
     */
    public QueueTokenResponse getQueueStatus(String concertId, String userId) {
        var query = new GetQueueStatusUseCase.GetQueueStatusQuery(concertId, userId);
        var token = getQueueStatusUseCase.getStatus(query);
        return QueueTokenResponse.from(token);
    }

    /**
     * 토큰 활성화
     */
    public QueueTokenResponse activateToken(String concertId, String userId) {
        var command = new ActivateTokenUseCase.ActivateTokenCommand(concertId, userId);
        var token = activateTokenUseCase.activate(command);
        return QueueTokenResponse.from(token);
    }

    /**
     * 토큰 연장
     */
    public QueueTokenResponse extendToken(String concertId, String userId) {
        var command = new ExtendTokenUseCase.ExtendTokenCommand(concertId, userId);
        var token = extendTokenUseCase.extend(command);
        return QueueTokenResponse.from(token);
    }

    /**
     * 토큰 검증
     */
    public void validateToken(String concertId, String userId, String token) {
        var query = new ValidateTokenUseCase.ValidateTokenQuery(concertId, userId, token);
        validateTokenUseCase.validate(query);
    }

    /**
     * 만료된 토큰 추가 (테스트용)
     */
    public void addExpiredToken(String concertId, String userId, Instant expiredAt) {
        // 프로덕션 Lua 스크립트와 동일한 형식으로 토큰 생성: {concertId}:{userId}:{uniqueId}
        String token = concertId + ":" + userId + ":" + System.nanoTime();
        queueRepository.addToActiveQueue(concertId, userId, token, expiredAt);
    }

    /**
     * 결제 완료 이벤트 발행 (테스트용)
     */
    public void publishPaymentCompletedEvent(String concertId, String userId) {
        try {
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    UUID.randomUUID().toString(),
                    concertId,
                    userId,
                    "BOOKING-" + UUID.randomUUID(),
                    10000L,
                    Instant.now());

            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("booking.payment.completed", message);

        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    /**
     * 모든 대기열 초기화 (테스트 준비)
     */
    public void clearAllQueues() {
        // Redis 전체 초기화는 TestContainersConfiguration에서 처리
    }
}
