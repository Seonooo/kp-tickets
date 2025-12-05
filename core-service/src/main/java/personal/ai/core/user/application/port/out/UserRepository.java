package personal.ai.core.user.application.port.out;

import personal.ai.core.user.domain.model.User;

import java.util.Optional;

/**
 * User Repository (Output Port)
 * 사용자 저장소 인터페이스
 */
public interface UserRepository {

    /**
     * 사용자 ID로 조회
     * @param userId 사용자 ID
     * @return 사용자 정보 (없으면 Optional.empty())
     */
    Optional<User> findById(Long userId);

    /**
     * 이메일로 사용자 조회
     * @param email 이메일
     * @return 사용자 정보 (없으면 Optional.empty())
     */
    Optional<User> findByEmail(String email);

    /**
     * 사용자 존재 여부 확인
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    boolean existsById(Long userId);
}
