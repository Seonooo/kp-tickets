// Docker 환경용 공통 설정 및 유틸리티 함수

export const BASE_URL_CORE = 'http://host.docker.internal:8080';
export const BASE_URL_QUEUE = 'http://host.docker.internal:8081';

export const CONCERT_ID = 1;
export const SCHEDULE_ID = 1;

// ApiResponse 검증 (프로젝트 DTO 기준: result, message, data)
export function validateApiResponse(response, expectedStatus = 200) {
  return {
    'status is correct': (r) => r.status === expectedStatus,
    'result is success': (r) => r.json('result') === 'success',
    'has message field': (r) => r.json('message') !== null,
    'has data field': (r) => r.json('data') !== undefined,
  };
}

// ErrorResponse 검증
export function validateErrorResponse(response, expectedStatus, expectedCode) {
  return {
    'status is correct': (r) => r.status === expectedStatus,
    'has code field': (r) => r.json('code') !== null,
    'has message field': (r) => r.json('message') !== null,
    'has timestamp field': (r) => r.json('timestamp') !== null,
    'error code matches': (r) => r.json('code') === expectedCode,
  };
}

// 랜덤 userId 생성 (Queue Service: String, Core Service: Long 호환)
export function randomUserId() {
  // Generate numeric user ID compatible with both services
  // Range: 1000000000 ~ 9999999999 (10-digit numeric ID)
  return String(Math.floor(Math.random() * 9000000000) + 1000000000);
}

// 응답 시간 체크
export function checkResponseTime(response, maxMs) {
  return {
    [`response time < ${maxMs}ms`]: (r) => r.timings.duration < maxMs,
  };
}
