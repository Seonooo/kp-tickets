package personal.ai.core.booking.application.port.out;

import personal.ai.core.booking.domain.model.Reservation;

import java.util.Optional;

/**
 * Reservation Repository (Output Port)
 * 예약 저장소 인터페이스 (MVP)
 */
public interface ReservationRepository {

    /**
     * 예약 저장
     *
     * @param reservation 예약 정보
     * @return 저장된 예약 정보 (ID 포함)
     */
    Reservation save(Reservation reservation);

    /**
     * 예약 ID로 조회
     *
     * @param reservationId 예약 ID
     * @return 예약 정보
     */
    Optional<Reservation> findById(Long reservationId);
}
