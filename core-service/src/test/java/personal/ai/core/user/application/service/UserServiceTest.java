package personal.ai.core.user.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personal.ai.core.user.application.port.out.UserRepository;
import personal.ai.core.user.domain.exception.UserNotFoundException;
import personal.ai.core.user.domain.model.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_EMAIL = "test@example.com";
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(TEST_USER_ID, "Test User", TEST_EMAIL);
    }

    @Test
    @DisplayName("사용자 검증 성공 - 사용자 존재")
    void validateUser_Success() {
        // given
        given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(testUser));

        // when
        User result = userService.validateUser(TEST_USER_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(TEST_USER_ID);
        assertThat(result.name()).isEqualTo("Test User");
        assertThat(result.email()).isEqualTo(TEST_EMAIL);
        verify(userRepository).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("사용자 검증 실패 - 사용자 없음")
    void validateUser_UserNotFound() {
        // given
        given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.validateUser(TEST_USER_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found")
                .hasMessageContaining(String.valueOf(TEST_USER_ID));
        verify(userRepository).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("사용자 존재 여부 확인 - 존재")
    void exists_True() {
        // given
        given(userRepository.existsById(TEST_USER_ID)).willReturn(true);

        // when
        boolean result = userService.exists(TEST_USER_ID);

        // then
        assertThat(result).isTrue();
        verify(userRepository).existsById(TEST_USER_ID);
    }

    @Test
    @DisplayName("사용자 존재 여부 확인 - 없음")
    void exists_False() {
        // given
        given(userRepository.existsById(TEST_USER_ID)).willReturn(false);

        // when
        boolean result = userService.exists(TEST_USER_ID);

        // then
        assertThat(result).isFalse();
        verify(userRepository).existsById(TEST_USER_ID);
    }

    @Test
    @DisplayName("사용자 조회 성공")
    void getUser_Success() {
        // given
        given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(testUser));

        // when
        User result = userService.getUser(TEST_USER_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(TEST_USER_ID);
        verify(userRepository).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("이메일로 사용자 조회 성공")
    void getUserByEmail_Success() {
        // given
        given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(testUser));

        // when
        User result = userService.getUserByEmail(TEST_EMAIL);

        // then
        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo(TEST_EMAIL);
        verify(userRepository).findByEmail(TEST_EMAIL);
    }

    @Test
    @DisplayName("이메일로 사용자 조회 실패")
    void getUserByEmail_UserNotFound() {
        // given
        given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserByEmail(TEST_EMAIL))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository).findByEmail(TEST_EMAIL);
    }
}
