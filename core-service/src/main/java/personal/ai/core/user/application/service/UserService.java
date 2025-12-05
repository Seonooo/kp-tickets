package personal.ai.core.user.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.user.application.port.in.GetUserUseCase;
import personal.ai.core.user.application.port.in.ValidateUserUseCase;
import personal.ai.core.user.application.port.out.UserRepository;
import personal.ai.core.user.domain.exception.UserNotFoundException;
import personal.ai.core.user.domain.model.User;

/**
 * User Application Service
 * 사용자 관련 모든 UseCase를 구현하는 Application Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements ValidateUserUseCase, GetUserUseCase {

    private final UserRepository userRepository;

    @Override
    public User validateUser(Long userId) {
        log.debug("Validating user: userId={}", userId);

        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Override
    public boolean exists(Long userId) {
        log.debug("Checking user existence: userId={}", userId);

        return userRepository.existsById(userId);
    }

    @Override
    public User getUser(Long userId) {
        log.debug("Getting user: userId={}", userId);

        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Override
    public User getUserByEmail(String email) {
        log.debug("Getting user by email: email={}", email);

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(null));
    }
}
