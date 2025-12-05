package personal.ai.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 정의
 * HTTP Status Code와 메시지를 함께 관리
 */
public enum ErrorCode {
    // Common (1xxx)
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C002", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C003", "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "C005", "리소스 충돌이 발생했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C006", "서버 내부 오류가 발생했습니다."),

    // User Domain (2xxx)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "U002", "이미 존재하는 사용자입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "U003", "인증 정보가 올바르지 않습니다."),

    // Booking Domain (3xxx)
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "좌석을 찾을 수 없습니다."),
    SEAT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "B002", "예약 불가능한 좌석입니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "B003", "이미 선택된 좌석입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "B004", "예약을 찾을 수 없습니다."),
    RESERVATION_EXPIRED(HttpStatus.BAD_REQUEST, "B005", "예약이 만료되었습니다."),
    CONCURRENT_RESERVATION(HttpStatus.CONFLICT, "B006", "동시 예약 충돌이 발생했습니다."),
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "B007", "콘서트를 찾을 수 없습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "B008", "콘서트 일정을 찾을 수 없습니다."),

    // Payment Domain (4xxx)
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "P001", "결제에 실패했습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "P002", "잔액이 부족합니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P003", "결제 정보를 찾을 수 없습니다."),

    // Queue Domain (5xxx)
    QUEUE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Q001", "대기열 토큰을 찾을 수 없습니다."),
    QUEUE_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Q002", "대기열 토큰이 만료되었습니다."),
    QUEUE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Q003", "유효하지 않은 대기열 토큰입니다."),
    QUEUE_FULL(HttpStatus.TOO_MANY_REQUESTS, "Q004", "대기열이 가득 찼습니다."),
    QUEUE_EXTENSION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Q005", "더 이상 연장할 수 없습니다."),

    // External Service (6xxx)
    EXTERNAL_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "E001", "외부 서비스 오류가 발생했습니다."),
    EXTERNAL_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "E002", "외부 서비스 응답 시간 초과입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
