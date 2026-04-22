package personal.ai.core.booking.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personal.ai.core.booking.application.port.out.OutboxEventRepository;
import personal.ai.core.booking.application.port.out.ReservationEventPublisher;
import personal.ai.core.booking.domain.model.OutboxEvent;
import personal.ai.core.booking.domain.model.OutboxEvent.OutboxStatus;
import personal.ai.core.booking.domain.model.OutboxPolicy;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventProcessor 단위 테스트")
class OutboxEventProcessorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ReservationEventPublisher eventPublisher;

    @InjectMocks
    private OutboxEventProcessor outboxEventProcessor;

    private OutboxEvent pendingEvent(String eventType, int retryCount) {
        return new OutboxEvent(1L, "RESERVATION", 100L, eventType,
                "{\"reservationId\":100}", OutboxStatus.PENDING, retryCount,
                LocalDateTime.now(), null);
    }

    @Test
    @DisplayName("PENDING 이벤트 발행 성공 시 PUBLISHED로 저장되고 true를 반환한다")
    void processEvent_success_savesPublished() {
        // given
        OutboxEvent event = pendingEvent("RESERVATION_CREATED", 0);
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = outboxEventProcessor.processEvent(event);

        // then
        assertThat(result).isTrue();
        verify(eventPublisher).publishRaw(eq("reservation.created"), anyString(), anyString());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("발행 실패 시 retryCount가 증가하고 PENDING 상태를 유지하며 false를 반환한다")
    void processEvent_publishFails_incrementsRetry() {
        // given
        OutboxEvent event = pendingEvent("RESERVATION_CREATED", 0);
        willThrow(new RuntimeException("Kafka error"))
                .given(eventPublisher).publishRaw(anyString(), anyString(), anyString());
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = outboxEventProcessor.processEvent(event);

        // then
        assertThat(result).isFalse();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().retryCount()).isEqualTo(1);
        assertThat(captor.getValue().status()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("retryCount가 MAX에 도달하면 FAILED 상태로 저장된다")
    void processEvent_maxRetryExceeded_marksAsFailed() {
        // given - retryCount 증가 후 MAX와 같아지는 경계값
        OutboxEvent event = pendingEvent("RESERVATION_CREATED", OutboxPolicy.MAX_RETRY_COUNT - 1);
        willThrow(new RuntimeException("Kafka error"))
                .given(eventPublisher).publishRaw(anyString(), anyString(), anyString());
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = outboxEventProcessor.processEvent(event);

        // then
        assertThat(result).isFalse();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 Kafka 발행 없이 실패로 처리된다")
    void processEvent_unknownEventType_failsGracefully() {
        // given
        OutboxEvent event = new OutboxEvent(2L, "RESERVATION", 100L, "UNKNOWN_TYPE",
                "{}", OutboxStatus.PENDING, OutboxPolicy.MAX_RETRY_COUNT - 1,
                LocalDateTime.now(), null);
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = outboxEventProcessor.processEvent(event);

        // then
        assertThat(result).isFalse();
        verify(outboxEventRepository).save(any());
    }
}
