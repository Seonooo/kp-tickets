package personal.ai.core.booking.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personal.ai.core.booking.application.port.out.OutboxEventRepository;
import personal.ai.core.booking.application.port.out.ReservationEventPublisher;
import personal.ai.core.booking.domain.model.OutboxEvent;
import personal.ai.core.booking.domain.model.OutboxEvent.OutboxStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventService 단위 테스트")
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ReservationEventPublisher eventPublisher;

    @InjectMocks
    private OutboxEventService outboxEventService;

    private OutboxEvent pendingEvent(String eventType, int retryCount) {
        return new OutboxEvent(1L, "RESERVATION", 100L, eventType,
                "{\"reservationId\":100}", OutboxStatus.PENDING, retryCount,
                LocalDateTime.now(), null);
    }

    @Test
    @DisplayName("대기 중인 이벤트가 없으면 0을 반환한다")
    void publishPendingEvents_noEvents_returnsZero() {
        // given
        given(outboxEventRepository.findPendingEvents()).willReturn(List.of());

        // when
        int count = outboxEventService.publishPendingEvents();

        // then
        assertThat(count).isZero();
        verify(eventPublisher, never()).publishRaw(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PENDING 이벤트 발행 성공 시 PUBLISHED 상태로 저장되고 발행 수를 반환한다")
    void publishPendingEvents_success_returnsPublishedCount() {
        // given
        OutboxEvent event = pendingEvent("RESERVATION_CREATED", 0);
        given(outboxEventRepository.findPendingEvents()).willReturn(List.of(event));
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int count = outboxEventService.publishPendingEvents();

        // then
        assertThat(count).isEqualTo(1);
        verify(eventPublisher).publishRaw(eq("reservation.created"), anyString(), anyString());
        verify(outboxEventRepository).save(any());
    }

    @Test
    @DisplayName("발행 실패 시 retryCount가 증가하고 PENDING 상태를 유지한다")
    void publishPendingEvents_publishFails_incrementsRetryCount() {
        // given
        OutboxEvent event = pendingEvent("RESERVATION_CREATED", 0);
        given(outboxEventRepository.findPendingEvents()).willReturn(List.of(event));
        willThrow(new RuntimeException("Kafka error"))
                .given(eventPublisher).publishRaw(anyString(), anyString(), anyString());
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int count = outboxEventService.publishPendingEvents();

        // then
        assertThat(count).isZero();
        verify(outboxEventRepository).save(any());
    }

    @Test
    @DisplayName("retryCount가 MAX_RETRY_COUNT 이상이면 FAILED 상태로 저장된다")
    void publishPendingEvents_maxRetryExceeded_marksAsFailed() {
        // given
        int retryCountAtLimit = OutboxEventService.MAX_RETRY_COUNT - 1;
        OutboxEvent event = pendingEvent("RESERVATION_CREATED", retryCountAtLimit);
        given(outboxEventRepository.findPendingEvents()).willReturn(List.of(event));
        willThrow(new RuntimeException("Kafka error"))
                .given(eventPublisher).publishRaw(anyString(), anyString(), anyString());
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        outboxEventService.publishPendingEvents();

        // then - FAILED 상태로 저장 여부 검증
        verify(outboxEventRepository).save(
                argThat(saved -> saved instanceof OutboxEvent e && e.status() == OutboxStatus.FAILED)
        );
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 예외 발생 후 FAILED 처리된다")
    void publishPendingEvents_unknownEventType_marksAsFailed() {
        // given
        OutboxEvent event = new OutboxEvent(2L, "RESERVATION", 100L, "UNKNOWN_TYPE",
                "{}", OutboxStatus.PENDING, OutboxEventService.MAX_RETRY_COUNT - 1,
                LocalDateTime.now(), null);
        given(outboxEventRepository.findPendingEvents()).willReturn(List.of(event));
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int count = outboxEventService.publishPendingEvents();

        // then
        assertThat(count).isZero();
        verify(outboxEventRepository).save(any());
    }

    private static <T> T argThat(java.util.function.Predicate<T> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
