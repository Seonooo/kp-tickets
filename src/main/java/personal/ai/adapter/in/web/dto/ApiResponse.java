package personal.ai.adapter.in.web.dto;

/**
 * 모든 API 응답의 통일된 포맷
 * agent.md의 API Design Guidelines에 따라 정의됨
 *
 * @param result  응답 결과 ("success" 또는 "error")
 * @param message 응답 메시지
 * @param data    응답 데이터 (제네릭 타입)
 * @param <T>     데이터 타입
 */
public record ApiResponse<T>(
        String result,
        String message,
        T data
) {
    /**
     * 성공 응답 생성
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, data);
    }

    /**
     * 성공 응답 생성 (데이터 없음)
     */
    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>("success", message, null);
    }

    /**
     * 실패 응답 생성
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>("error", message, data);
    }

    /**
     * 실패 응답 생성 (데이터 없음)
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>("error", message, null);
    }
}
