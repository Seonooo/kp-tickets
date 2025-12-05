package personal.ai.core.user.application.port.in;

import personal.ai.core.user.domain.model.User;

/**
 * Get User UseCase (Input Port)
 * 사용자 조회 유스케이스
 */
public interface GetUserUseCase {

    /**
     * 사용자 ID로 조회
     * @param userId 사용자 ID
     * @return 사용자 정보
     * @throws personal.ai.core.user.domain.exception.UserNotFoundException 사용자가 존재하지 않을 때
     */
    User getUser(Long userId);

    /**
     * 이메일로 사용자 조회
     * @param email 이메일
     * @return 사용자 정보
     * @throws personal.ai.core.user.domain.exception.UserNotFoundException 사용자가 존재하지 않을 때
     */
    User getUserByEmail(String email);
}
