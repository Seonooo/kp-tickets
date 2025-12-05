package personal.ai.core.booking.application.port.in;

import personal.ai.core.booking.domain.model.Reservation;

/**
 * Get Reservation UseCase (Input Port)
 * 예약 조회 유스케이스 (결제 전 확인용)
 */
public interface GetReservationUseCase {

    /**
     * 예약 ID로 예약 조회
     * 결제 페이지에서 예약 정보 확인용
     *
     * @param reservationId 예약 ID
     * @param userId 사용자 ID (소유권 검증용)
     * @return 예약 정보
     * @throws personal.ai.core.booking.domain.exception.ReservationNotFoundException 예약을 찾을 수 없을 때
     * @throws personal.ai.common.exception.BusinessException 예약 소유자가 아닐 때 (FORBIDDEN)
     */
    Reservation getReservation(Long reservationId, Long userId);
}
