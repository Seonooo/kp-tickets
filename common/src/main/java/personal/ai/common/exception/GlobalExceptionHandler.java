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

        @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
                        org.springframework.web.bind.MethodArgumentNotValidException e) {
                log.warn("Validation failed: {}", e.getMessage());
                // 방어적 코딩: 에러 목록이 비어있을 수 있음 (이론적으로)
                String message = "입력값이 유효하지 않습니다.";
                if (!e.getBindingResult().getAllErrors().isEmpty()) {
                        message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
                }
                ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT, message);
                return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus()).body(response);
        }

        @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
        public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
                        org.springframework.web.method.annotation.HandlerMethodValidationException e) {
                log.warn("Parameter validation failed: {}", e.getMessage());
                String message = "입력값이 유효하지 않습니다.";
                if (!e.getAllErrors().isEmpty()) {
                        message = e.getAllErrors().get(0).getDefaultMessage();
                }
                ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT, message);
                return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus()).body(response);
        }

        @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
        public ResponseEntity<ErrorResponse> handleConstraintViolationException(
                        jakarta.validation.ConstraintViolationException e) {
                log.warn("Constraint violation: {}", e.getMessage());
                ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT, e.getMessage());
                return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus()).body(response);
        }

        @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
        public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
                        org.springframework.web.bind.MissingServletRequestParameterException e) {
                log.warn("Missing parameter: {}", e.getParameterName());
                ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT,
                                "필수 파라미터가 누락되었습니다: " + e.getParameterName());
                return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus()).body(response);
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
