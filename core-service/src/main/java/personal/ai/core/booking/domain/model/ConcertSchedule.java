package personal.ai.core.booking.domain.model;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * Concert Schedule Domain Model
 * 콘서트 일정 도메인 모델 (불변)
 */
public record ConcertSchedule(
        Long id,
        Long concertId,
        LocalDateTime performanceDate,
        String venue
) {
    public ConcertSchedule {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Schedule ID cannot be null");
        }
        if (concertId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Concert ID cannot be null");
        }
        if (performanceDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Performance date cannot be null");
        }
        if (venue == null || venue.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Venue cannot be null or blank");
        }
    }

    /**
     * 공연 시작 여부 확인
     */
    public boolean hasStarted() {
        return LocalDateTime.now().isAfter(performanceDate);
    }

    /**
     * 예매 가능 여부 확인 (공연 시작 전)
     */
    public boolean isBookable() {
        return !hasStarted();
    }
}
