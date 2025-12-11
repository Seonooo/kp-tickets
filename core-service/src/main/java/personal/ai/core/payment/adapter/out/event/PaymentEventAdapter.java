package personal.ai.core.payment.adapter.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.core.payment.adapter.out.kafka.PaymentCompletedEvent;
import personal.ai.core.payment.adapter.out.persistence.PaymentOutboxEventEntity;
import personal.ai.core.payment.application.port.out.PaymentEventPort;
import personal.ai.core.payment.application.port.out.PaymentOutboxRepository;
import personal.ai.core.payment.domain.model.Payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment Event Adapter
 * Outbox 패턴을 사용한 결제 이벤트 발행 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventAdapter implements PaymentEventPort {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishPaymentCompleted(Payment payment, String concertId) {
        try {
            var event = new PaymentCompletedEvent(
                    UUID.randomUUID().toString(),
                    concertId,
                    payment.userId().toString(),
                    payment.reservationId().toString(),
                    payment.id().toString(),
                    payment.amount().longValue(),
                    Instant.now());

            String payload = objectMapper.writeValueAsString(event);

            var outboxEvent = PaymentOutboxEventEntity.create(
                    "PAYMENT",
                    payment.id(),
                    "PAYMENT_COMPLETED",
                    payload);

            paymentOutboxRepository.save(outboxEvent.toDomain());
            log.debug("Payment completed event published: paymentId={}", payment.id());

        } catch (Exception e) {
            log.error("Failed to publish payment event: paymentId={}", payment.id(), e);
            throw new RuntimeException("Failed to publish payment event", e);
        }
    }
}
