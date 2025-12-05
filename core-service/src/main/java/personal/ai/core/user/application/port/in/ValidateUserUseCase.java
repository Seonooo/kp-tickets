package personal.ai.core.user.application.port.in;

import personal.ai.core.user.domain.model.User;

/**
 * Validate User UseCase (Input Port)
 * 사용자 검증 유스케이스
 */
public interface ValidateUserUseCase {

    /**
     * 사용자 ID로 검증
     * @param userId 사용자 ID
     * @return 사용자 정보
     * @throws personal.ai.core.user.domain.exception.UserNotFoundException 사용자가 존재하지 않을 때
     */
    User validateUser(Long userId);

    /**
     * 사용자 존재 여부 확인
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    boolean exists(Long userId);
}
