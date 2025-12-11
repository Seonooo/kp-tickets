package personal.ai.core.booking.application.port.out;

import personal.ai.core.booking.domain.model.Reservation;

/**
 * Reservation Event Port
 * 예약 이벤트 발행 책임 (Outbox 패턴)
 */
public interface ReservationEventPort {

    /**
     * 예약 상태 변경 이벤트 발행
     *
     * @param reservation 예약
     */
    void publishReservationEvent(Reservation reservation);
}
