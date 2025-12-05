package personal.ai.core.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for User
 */
public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * 이메일로 사용자 조회
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * 사용자 ID 존재 여부 확인
     */
    boolean existsById(Long userId);
}
