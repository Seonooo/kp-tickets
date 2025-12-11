package personal.ai.core.user.domain.model;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * User Domain Model
 * 사용자 도메인의 불변 모델
 */
public record User(
        Long id,
        String name,
        String email
) {
    public User {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "User ID cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "User name cannot be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "User email cannot be null or blank");
        }
    }

    /**
     * 사용자 존재 여부 확인 (항상 true, 조회 성공 시점에서는 존재)
     */
    public boolean exists() {
        return true;
    }
}
