package personal.ai.core.booking.domain.model;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;
import personal.ai.core.booking.domain.exception.InvalidReservationStateException;
import personal.ai.core.booking.domain.exception.ReservationAccessDeniedException;
import personal.ai.core.booking.domain.exception.ReservationAlreadyConfirmedException;

import java.time.LocalDateTime;

/**
 * Reservation Domain Model
 * 예약 도메인 모델 (불변)
 */
public record Reservation(
        Long id,
        Long userId,
        Long seatId,
        Long scheduleId,
        ReservationStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {
    public Reservation {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "User ID cannot be null");
        }
        if (seatId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Seat ID cannot be null");
        }
        if (scheduleId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Schedule ID cannot be null");
        }
        if (status == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Reservation status cannot be null");
        }
        if (expiresAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Expiration time cannot be null");
        }
        if (createdAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Creation time cannot be null");
        }
    }

    /**
     * 예약 생성 (정적 팩토리 메서드)
     *
     * @param userId     사용자 ID
     * @param seatId     좌석 ID
     * @param scheduleId 일정 ID
     * @param ttlMinutes 만료 시간 (분)
     * @return 새로운 예약 (PENDING 상태)
     */
    public static Reservation create(Long userId, Long seatId, Long scheduleId, int ttlMinutes) {
        return new Reservation(
                null,
                userId,
                seatId,
                scheduleId,
                ReservationStatus.PENDING,
                LocalDateTime.now().plusMinutes(ttlMinutes),
                LocalDateTime.now());
    }

    /**
     * 예약 확정 (PENDING -> CONFIRMED)
     * 결제 완료 시 호출
     */
    public Reservation confirm() {
        if (status != ReservationStatus.PENDING) {
            throw new InvalidReservationStateException(status);
        }
        return new Reservation(id, userId, seatId, scheduleId,
                ReservationStatus.CONFIRMED, expiresAt, createdAt);
    }

    /**
     * 예약 취소 (PENDING -> CANCELLED)
     * 사용자 취소 시 호출
     */
    public Reservation cancel() {
        if (status != ReservationStatus.PENDING) {
            throw new InvalidReservationStateException(status);
        }
        return new Reservation(id, userId, seatId, scheduleId,
                ReservationStatus.CANCELLED, expiresAt, createdAt);
    }

    /**
     * 예약 만료 (PENDING -> EXPIRED)
     * 시간 초과 시 호출
     */
    public Reservation expire() {
        if (status != ReservationStatus.PENDING) {
            throw new InvalidReservationStateException(status);
        }
        return new Reservation(id, userId, seatId, scheduleId,
                ReservationStatus.EXPIRED, expiresAt, createdAt);
    }

    /**
     * 예약 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 예약 대기 상태 여부 확인
     */
    public boolean isPending() {
        return status == ReservationStatus.PENDING;
    }

    /**
     * 예약 확정 상태 여부 확인
     */
    public boolean isConfirmed() {
        return status == ReservationStatus.CONFIRMED;
    }

    /**
     * 예약 취소 가능 여부 확인
     * (PENDING 상태이고 만료되지 않은 경우)
     */
    public boolean isCancellable() {
        return isPending() && !isExpired();
    }

    // ========== Domain Validation Methods (Tell, Don't Ask) ==========

    /**
     * 소유권 검증
     * 요청한 사용자가 예약의 소유자인지 확인
     *
     * @param requestUserId 요청 사용자 ID
     * @throws BusinessException requestUserId가 null인 경우
     * @throws ReservationAccessDeniedException 소유권 불일치 시
     */
    public void ensureOwnership(Long requestUserId) {
        if (requestUserId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Request user ID cannot be null");
        }

        if (!this.userId.equals(requestUserId)) {
            throw new ReservationAccessDeniedException();
        }
    }

    /**
     * PENDING 상태 검증
     *
     * @throws ReservationAlreadyConfirmedException 이미 확정된 예약일 때 (중복 결제 시도)
     * @throws InvalidReservationStateException 기타 유효하지 않은 상태일 때
     */
    public void ensurePending() {
        // 이미 확정된 예약: 중복 결제 시도 (409 CONFLICT)
        if (status == ReservationStatus.CONFIRMED) {
            throw new ReservationAlreadyConfirmedException(id);
        }

        // 기타 유효하지 않은 상태 (400 BAD_REQUEST)
        if (!isPending()) {
            throw new InvalidReservationStateException(status);
        }
    }
}
