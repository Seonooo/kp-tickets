Feature: Health Check API
  애플리케이션의 헬스 체크 기능을 검증합니다.
  인프라(MySQL, Redis, Kafka) 연결 상태를 확인하고, API 응답 포맷을 검증합니다.

  Background:
    Given 애플리케이션이 실행 중입니다

  Scenario: 헬스 체크 API 호출 시 정상 응답을 반환한다
    When "/api/v1/health" 엔드포인트에 GET 요청을 보낸다
    Then 응답 상태 코드는 200이다
    And 응답 본문의 "result" 필드는 "success"이다
    And 응답 본문의 "message" 필드는 "Application is healthy"이다

  Scenario: 헬스 체크 상세 정보를 확인한다
    When "/api/v1/health" 엔드포인트에 GET 요청을 보낸다
    Then 응답 본문의 "data.database" 필드는 "UP"이다
    And 응답 본문의 "data.redis" 필드는 "UP"이다
    And 응답 본문의 "data.kafka" 필드는 "UP"이다

  Scenario: Actuator 헬스 체크 엔드포인트를 확인한다
    When "/actuator/health" 엔드포인트에 GET 요청을 보낸다
    Then 응답 상태 코드는 200이다
    And 응답 본문의 "status" 필드는 "UP"이다
