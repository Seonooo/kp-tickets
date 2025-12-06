package personal.ai.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러
 * agent.md의 API Design Guidelines - HTTP Status Code 규칙 준수
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
                ErrorCode errorCode = e.getErrorCode();
                log.warn("Business exception occurred: code={}, message={}, detail={}",
                                errorCode.getCode(), errorCode.getMessage(), e.getMessage());

                ErrorResponse response = ErrorResponse.of(errorCode, errorCode.getMessage());
                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(response);
        }

        @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
                        org.springframework.web.servlet.resource.NoResourceFoundException e) {
                log.warn("Resource not found: {}", e.getResourcePath());

                ErrorResponse response = ErrorResponse.of(
                                ErrorCode.NOT_FOUND,
                                "요청한 URL을 찾을 수 없습니다: " + e.getResourcePath());
                return ResponseEntity
                                .status(ErrorCode.NOT_FOUND.getHttpStatus())
                                .body(response);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleException(Exception e) {
                log.error("Unexpected exception occurred", e);

                ErrorResponse response = ErrorResponse.of(
                                ErrorCode.INTERNAL_SERVER_ERROR,
                                ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
                return ResponseEntity
                                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                                .body(response);
        }
}
