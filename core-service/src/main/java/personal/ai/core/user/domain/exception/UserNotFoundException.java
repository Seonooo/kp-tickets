package personal.ai.core.user.domain.exception;

/**
 * User Not Found Exception
 * 사용자를 찾을 수 없을 때 발생하는 예외
 */
public class UserNotFoundException extends RuntimeException {
    private final Long userId;

    public UserNotFoundException(Long userId) {
        super(String.format("User not found: userId=%d", userId));
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
