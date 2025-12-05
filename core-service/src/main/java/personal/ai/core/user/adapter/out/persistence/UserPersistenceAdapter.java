package personal.ai.core.user.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.core.user.application.port.out.UserRepository;
import personal.ai.core.user.domain.model.User;

import java.util.Optional;

/**
 * User Persistence Adapter
 * JPA를 사용한 사용자 저장소 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    @Override
    public Optional<User> findById(Long userId) {
        log.debug("Finding user by id: {}", userId);
        return jpaUserRepository.findById(userId)
                .map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        return jpaUserRepository.findByEmail(email)
                .map(UserEntity::toDomain);
    }

    @Override
    public boolean existsById(Long userId) {
        log.debug("Checking if user exists: {}", userId);
        return jpaUserRepository.existsById(userId);
    }
}
