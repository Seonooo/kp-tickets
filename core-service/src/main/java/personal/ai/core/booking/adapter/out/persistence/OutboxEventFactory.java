package personal.ai.core.booking.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;
import personal.ai.core.booking.domain.model.Reservation;

/**
 * Outbox Event Factory (Adapter Layer)
 * Reservation을 OutboxEventEntity로 변환하는 팩토리
 * Infrastructure 관심사를 캡슐화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    /**
     * Reservation을 OutboxEventEntity로 변환
     */
    public OutboxEventEntity createReservationCreatedEvent(Reservation reservation) {
        try {
            // DTO 생성 (Domain Model만 사용)
            ReservationCreatedEvent event = new ReservationCreatedEvent(
                    reservation.id(),
                    reservation.userId(),
                    reservation.seatId(),
                    reservation.scheduleId(),
                    reservation.status().name(),
                    reservation.expiresAt().toString(),
                    reservation.createdAt().toString());

            String payload = objectMapper.writeValueAsString(event);

            return OutboxEventEntity.create(
                    "RESERVATION",
                    reservation.id(),
                    "RESERVATION_CREATED",
                    payload);
        } catch (Exception e) {
            log.error("Failed to create outbox event: reservationId={}", reservation.id(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to create outbox event");
        }
    }

    /**
     * Kafka 이벤트 DTO
     */
    public record ReservationCreatedEvent(
            Long reservationId,
            Long userId,
            Long seatId,
            Long scheduleId,
            String status,
            String expiresAt,
            String createdAt) {
    }
}
