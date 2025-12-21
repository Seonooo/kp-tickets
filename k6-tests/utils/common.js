// 공통 설정 및 유틸리티 함수

// Docker Compose 환경: 서비스명으로 접근 (k6도 같은 네트워크)
export const BASE_URL_CORE = 'http://core-service:8080';
export const BASE_URL_QUEUE = 'http://queue-service:8081';

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

// 랜덤 userId 생성
export function randomUserId() {
  return `USER-${Math.floor(Math.random() * 1000000)}`;
}

// 응답 시간 체크
export function checkResponseTime(response, maxMs) {
  return {
    [`response time < ${maxMs}ms`]: (r) => r.timings.duration < maxMs,
  };
}
