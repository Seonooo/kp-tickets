package personal.ai.core.booking.application.port.in;

import personal.ai.core.booking.domain.model.Reservation;

/**
 * Reserve Seat UseCase (Input Port)
 * 좌석 예약 유스케이스
 */
public interface ReserveSeatUseCase {

    /**
     * 좌석 예약
     * Redis SETNX로 선점 후 DB에 PENDING 상태로 저장
     *
     * @param command 예약 커맨드 (userId, seatId, scheduleId, queueToken)
     * @return 생성된 예약 정보
     * @throws personal.ai.core.booking.domain.exception.SeatAlreadyReservedException Redis 선점 실패 시 (409 Conflict)
     * @throws personal.ai.core.booking.domain.exception.SeatNotFoundException 좌석을 찾을 수 없을 때
     * @throws personal.ai.core.booking.domain.exception.SeatNotAvailableException 좌석이 예약 불가능한 상태일 때
     */
    Reservation reserveSeat(ReserveSeatCommand command);
}
