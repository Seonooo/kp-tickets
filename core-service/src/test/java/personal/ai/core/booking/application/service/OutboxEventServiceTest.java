package personal.ai.core.booking.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personal.ai.core.booking.application.port.out.OutboxEventRepository;
import personal.ai.core.booking.domain.model.OutboxEvent;
import personal.ai.core.booking.domain.model.OutboxEvent.OutboxStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventService 단위 테스트")
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventProcessor outboxEventProcessor;

    @InjectMocks
    private OutboxEventService outboxEventService;

    private OutboxEvent pendingEvent(Long id) {
        return new OutboxEvent(id, "RESERVATION", 100L, "RESERVATION_CREATED",
                "{\"reservationId\":100}", OutboxStatus.PENDING, 0,
                LocalDateTime.now(), null);
    }

    @Test
    @DisplayName("대기 중인 이벤트가 없으면 Processor를 호출하지 않고 0을 반환한다")
    void publishPendingEvents_noEvents_returnsZero() {
        // given
        given(outboxEventRepository.findPendingEvents()).willReturn(List.of());

        // when
        int count = outboxEventService.publishPendingEvents();

        // then
        assertThat(count).isZero();
        verify(outboxEventProcessor, never()).processEvent(any());
    }

    @Test
    @DisplayName("모든 이벤트가 성공하면 발행 수를 정확히 집계한다")
    void publishPendingEvents_allSuccess_returnsCount() {
        // given
        List<OutboxEvent> events = List.of(pendingEvent(1L), pendingEvent(2L), pendingEvent(3L));
        given(outboxEventRepository.findPendingEvents()).willReturn(events);
        given(outboxEventProcessor.processEvent(any())).willReturn(true);

        // when
        int count = outboxEventService.publishPendingEvents();

        // then
        assertThat(count).isEqualTo(3);
        verify(outboxEventProcessor, org.mockito.Mockito.times(3)).processEvent(any());
    }

    @Test
    @DisplayName("일부 이벤트가 실패해도 나머지 처리가 이어지고 성공 건수만 집계된다")
    void publishPendingEvents_partialFailure_continuesAndCountsSuccessOnly() {
        // given
        OutboxEvent e1 = pendingEvent(1L);
        OutboxEvent e2 = pendingEvent(2L);
        OutboxEvent e3 = pendingEvent(3L);
        given(outboxEventRepository.findPendingEvents()).willReturn(List.of(e1, e2, e3));
        given(outboxEventProcessor.processEvent(e1)).willReturn(true);
        given(outboxEventProcessor.processEvent(e2)).willReturn(false);
        given(outboxEventProcessor.processEvent(e3)).willReturn(true);

        // when
        int count = outboxEventService.publishPendingEvents();

        // then - 2건 성공, Processor는 3건 모두 호출됨
        assertThat(count).isEqualTo(2);
        verify(outboxEventProcessor).processEvent(e1);
        verify(outboxEventProcessor).processEvent(e2);
        verify(outboxEventProcessor).processEvent(e3);
    }
}
