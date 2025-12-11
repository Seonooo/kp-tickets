# language: ko
기능: Queue Service API
  콘서트 티켓팅을 위한 대기열 관리 API
  클라이언트는 REST API를 통해 대기열 시스템과 상호작용한다

  배경:
  Given 대기열 시스템이 준비되어 있다

  # ==========================================
  # API 1: POST /api/v1/queue/enter
  # 대기열 진입 API
  # ==========================================

  시나리오: [진입 API] 사용자가 대기열 진입 API를 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 있다
  When 사용자가 대기열 진입 API를 호출한다
  Then 대기열 진입이 성공한다
  And 대기 순번을 받는다

  시나리오: [진입 API] 여러 사용자가 동시에 대기열 진입 API를 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  When 100명의 사용자가 동시에 진입 API를 호출한다
  Then 모든 사용자가 대기열에 추가된다
  And 각 사용자는 고유한 순번을 받는다

  # ==========================================
  # API 2: GET /api/v1/queue/status
  # 상태 조회 API
  # ==========================================

  시나리오: [상태 API] WAITING 상태의 사용자가 상태 조회 API를 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 대기 큐에 있다
  When 상태 조회 API를 호출한다
  Then 상태가 "WAITING"이다
  And 대기 순번이 표시된다

  시나리오: [상태 API] READY 상태의 사용자가 상태 조회 API를 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 활성 큐에 있다
  When 상태 조회 API를 호출한다
  Then 상태가 "READY"이다
  And 토큰이 반환된다
  And 만료 시간이 표시된다

  시나리오: [상태 API] ACTIVE 상태의 사용자가 상태 조회 API를 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 활성 상태이다
  When 상태 조회 API를 호출한다
  Then 상태가 "ACTIVE"이다
  And 토큰이 반환된다
  And 연장 횟수가 표시된다

  # ==========================================
  # API 3: POST /api/v1/queue/activate
  # 토큰 활성화 API
  # ==========================================

  시나리오: [활성화 API] READY 사용자가 토큰 활성화 API를 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 활성 큐에 있다
  When 토큰 활성화 API를 호출한다
  Then 토큰 활성화가 성공한다
  And 상태가 "ACTIVE"로 변경된다
  And 만료 시간이 10분으로 설정된다

  # ==========================================
  # API 4: POST /api/v1/queue/extend
  # 토큰 연장 API
  # ==========================================

  시나리오: [연장 API] ACTIVE 사용자가 토큰 연장 API를 1차 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 활성 상태이다
  When 토큰 연장 API를 호출한다
  Then 토큰 연장이 성공한다
  And 연장 횟수가 1이다
  And 만료 시간이 갱신된다

  시나리오: [연장 API] ACTIVE 사용자가 토큰 연장 API를 2차 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 활성 상태이다
  And 이미 1회 연장했다
  When 토큰 연장 API를 호출한다
  Then 토큰 연장이 성공한다
  And 연장 횟수가 2이다
  And 만료 시간이 갱신된다

  # ==========================================
  # API 5: POST /api/v1/queue/validate
  # 토큰 검증 API
  # ==========================================

  시나리오: [검증 API] 유효한 ACTIVE 토큰으로 검증 API를 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 활성 상태이다
  And 유효한 토큰을 가지고 있다
  When 토큰 검증 API를 호출한다
  Then 토큰 검증이 성공한다

  시나리오: [검증 API] 유효한 READY 토큰으로 검증 API를 호출한다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 활성 큐에 있다
  When 토큰 검증 API를 호출한다
  Then 토큰 검증이 성공한다

  # ==========================================
  # API 6: GET /api/v1/queue/subscribe
  # SSE 실시간 구독 API
  # ==========================================

  시나리오: [SSE API] 사용자가 SSE 구독 API를 호출하고 상태 변경 알림을 받는다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 대기 큐에 있다
  When SSE 구독 API를 호출한다
  Then SSE 연결이 성공한다
  When 사용자 상태가 "READY"로 변경된다
  Then SSE 이벤트를 수신한다
  And 이벤트 데이터의 상태는 "READY"이다
  And 이벤트 데이터에 토큰이 포함된다

  # ==========================================
  # 이벤트 기반: Kafka 결제 완료 이벤트 수신
  # ==========================================

  시나리오: [이벤트] 결제 완료 이벤트를 수신하면 대기열에서 제거된다
  Given 콘서트 "CONCERT-001"이 있다
  And 사용자 "USER-001"이 활성 상태이다
  When 결제 완료 이벤트가 발행된다
  Then 대기열에서 제거된다
  When 상태 조회 API를 호출한다
  Then 상태가 "NOT_FOUND"이다
