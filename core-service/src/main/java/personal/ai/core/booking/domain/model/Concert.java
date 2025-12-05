package personal.ai.core.booking.domain.model;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Concert Domain Model
 * 콘서트 도메인 모델 (불변)
 */
public record Concert(
        Long id,
        String name,
        String description
) {
    public Concert {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Concert ID cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Concert name cannot be null or blank");
        }
    }
}
