package personal.ai.core.booking.application.port.in;

/**
 * Expire Reservation UseCase (Input Port)
 * Redis TTL 만료 이벤트로 예약을 만료 처리하는 유스케이스
 */
public interface ExpireReservationUseCase {

    /**
     * 예약 만료 처리
     *
     * @param reservationId 만료할 예약 ID
     */
    void expireReservation(Long reservationId);
}
