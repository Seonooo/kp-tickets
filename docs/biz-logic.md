# Business Logic & Rules

이 문서는 **콘서트 티켓팅 서비스**의 핵심 비즈니스 규칙과 정책을 정의한다.
구현 시 아래의 수치와 정책을 하드코딩하지 말고, 설정 파일(`application.yml`)이나 상수로 관리하여 변경에 유연하게 대응해야 한다.

---

## 1. Queue Domain Policy

### 1.1 Queue Lifecycle Parameters
- **Wait Queue:** 인원 제한 없음 (Unlimited).
- **Active Queue Capacity:** 동시 접속 최대 **N명** (ex. 50,000명).
- **TTL Strategy (Two-Phase):**
    1. **진입 대기 시간 (Ready):** 스케줄러에 의해 Active로 전환된 직후, 유저가 폴링으로 토큰을 수령하고 페이지에 접속하기까지 **5분 (300초)**의 시간을 부여한다. (No-Show 방지)
    2. **활동 보장 시간 (Active):** 유저가 Redirect URL을 통해 예매 페이지에 최초 접속하는 시점에 토큰 만료 시간을 **현재 시간 + 10분**으로 초기화한다.

### 1.2 Extension Policy (시간 연장)
유저가 예매 진행 중 시간이 부족할 경우, **'시간 연장' 버튼을 클릭**하여 만료 시간을 초기화할 수 있다.
- **Trigger:** 유저의 명시적인 연장 요청 (API 호출).
- **Policy:**
    - 요청 시점 기준으로 만료 시간을 **현재 시간 + 10분 (600초)**으로 재설정한다. (기존 남은 시간에 더하는 것이 아님)
    - **Max Extension Count:** 최대 **2회**까지만 연장 가능.
- **Implementation Note:**
    - `POST /api/v1/queue/token/extension` 엔드포인트 호출.
    - Redis Hash의 `extend_count`를 체크하고 증가(`HINCRBY`)시킨다.
    - `extend_count >= 2`인 경우, `400 Bad Request` ("더 이상 연장할 수 없습니다")를 반환한다.

### 1.3 Cleanup Policy
- **Scheduler Interval:** **1초**.
- **Target:** `queue:active` (ZSet)에서 Score(만료시간)가 현재 시간보다 과거인 유저.
- **Action:** 즉시 삭제 및 해당 Slot 반환.

---

## 2. Booking Domain Policy

### 2.1 Seat Reservation (좌석 선점)
- **Pre-occupation TTL:** Redis `SETNX`로 선점된 좌석의 유효 시간은 **5분**이다.
- **Constraint:** 유저는 한 번에 **하나의 좌석**만 선점 및 결제할 수 있다. (동시 다중 선점 불가)
- **Fail-Fast:** 이미 선점된 좌석(`seat:{id}` 존재)을 요청하면, DB 조회를 생략하고 즉시 에러(`409 Conflict`)를 반환한다.

### 2.2 Payment (Mocking)
실제 PG사 연동 대신 `PaymentMockService`를 통해 결제를 시뮬레이션한다.
- **Simulation Logic:**
    - 성공률은 100%를 반환하도록 한다.
    - 네트워크 지연(Latency)을 시뮬레이션하기 위해 **500ms ~ 1s의 딜레이**를 임의로 부여한다. (추후 변경 가능)
- **Post-Payment Action:**
    - **Success:**
        1. DB 주문 상태 `PENDING` -> `SUCCESS`.
        2. Kafka 이벤트 발행 (`booking.payment.completed`).
        3. Active Token 만료 여부와 상관없이 결제 성공 처리.
    - **Fail:**
        1. DB 주문 상태 `PENDING` -> `CANCEL`.
        2. Redis 좌석 선점 Key (`seat:{id}`) 즉시 삭제.

---

## 3. User Domain Policy

### 3.1 Authentication (Mock)
- **Method:** `X-User-Id` Header.
- **Role:** 별도의 로그인 과정 없이 헤더에 포함된 ID를 신뢰하여 인증 처리한다.
- **User Profile:** 결제 시 유저 식별자(`userId`) 외에 별도의 포인트 잔액 확인이나 등급 조회 로직은 포함하지 않는다. (순수 인증/프로필 역할)

---

## 4. Error Handling Policy

### 4.1 Global Exception Cases
| 상황 | HTTP Status | Error Code | 메시지 예시 |
|:---|:---:|:---:|:---|
| 대기열 토큰 없음/만료 | `401` | `QUEUE_EXPIRED` | "대기열 순번이 만료되었습니다. 다시 줄을 서주세요." |
| 좌석 이미 선점됨 | `409` | `SEAT_ALREADY_RESERVED` | "이미 선택된 좌석입니다." |
| 결제 실패 (Mock) | `400` | `PAYMENT_FAILED` | "결제가 거절되었습니다. (잔액 부족 등)" |
| 유효하지 않은 요청 | `400` | `BAD_REQUEST` | "잘못된 요청입니다." |
| 서버 내부 오류 | `500` | `INTERNAL_SERVER_ERROR` | "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요." |