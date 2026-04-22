---
tags: [pitfalls, history]
related:
  - "[[convention]]"
  - "[[architecture]]"
---

# Pitfalls (오류 패턴 이력)

하네스에서 실제로 감지된 위반 패턴을 기록합니다.
`doc-updater` agent가 자동으로 업데이트합니다.

---

<!-- 오류 발생 시 아래 형식으로 추가됩니다 -->

## [2026-04-06] CodeRabbit 오탐: Agent 실패 시 fail-open 처리

### CodeRabbit 지적
`check-architecture.js`, `check-java-code.js`에서 Agent 호출 실패 시 `APPROVED`를 반환하면
검사가 우회(fail-open)될 수 있다는 지적.

### 무시 근거
의도적 설계 결정. Claude CLI 미응답, 네트워크 불안정 등 환경 문제로 인해
개발 흐름이 완전히 차단되는 상황을 방지하기 위해 fail-open으로 설계.
Stage 1 regex 검사는 항상 실행되므로 명백한 위반은 잡을 수 있음.

### 관련 설계 결정
하네스는 개발 생산성과 품질 보증의 균형을 위해 Agent 레이어를 선택적 보강으로 운용.
Agent 불가 시에도 Stage 1 regex + 코드 리뷰 agent 등 다중 방어선이 존재.

---

## [2026-04-06] CodeRabbit 오탐: git commit PostToolUse 사후 검사

### CodeRabbit 지적
`check-commit-message.js`가 `git log -1`로 이미 생성된 커밋을 사후 검사하므로
잘못된 커밋을 사전에 막지 못한다는 지적.

### 무시 근거
의도적 PostToolUse 설계. 커밋 후 exit(1)이 발생하면 Claude가 자동으로 `git commit --amend`로
메시지를 수정하는 흐름. PreToolUse로 변경 시 git commit 명령 인자에서 메시지를 파싱해야 하는
복잡성이 발생하며 (인터랙티브 커밋, heredoc 등 다양한 형식 대응 필요), 현재 방식으로도
잘못된 커밋이 원격에 push되기 전에 수정 가능함.

### 관련 설계 결정
PostToolUse → exit(1) → Claude amend 순환 흐름이 이 프로젝트의 커밋 검증 전략.

## [2026-04-22] Jackson `NON_FINAL` + PTV allowlist 범위 불일치

### 발생 상황
`RedisCacheConfig`에서 `GenericJackson2JsonRedisSerializer` + `RecordSupportingTypeResolver(NON_FINAL)` 조합을 쓰면서
PTV(PolymorphicTypeValidator) allowlist는 `personal.ai` + `java.util`만 허용.

### 감지된 패턴
```java
BasicPolymorphicTypeValidator.builder()
    .allowIfSubType("personal.ai")
    .allowIfSubType("java.util")
    .build();
// NON_FINAL typing은 Long, BigDecimal, enum 같은 "final이지만 Jackson이 타입 힌트를 붙이는" 값에도 @class 기입
// → 역직렬화 시 PTV가 java.lang.Long 을 거부 → InvalidTypeIdException
```

### 원인
1. `DefaultTyping.NON_FINAL` + record/enum/wrapper 조합은 기대보다 많은 필드에 `@class`를 찍음.
2. Spring Cache의 기본 에러 처리는 **silent failure** — `CustomCacheErrorHandler`를 붙여도 로그만 남고 요청은 DB 폴백으로 흘러가 **캐시가 동작하지 않는다는 사실이 장애로 드러나지 않음**.

### 올바른 패턴
```java
BasicPolymorphicTypeValidator.builder()
    .allowIfSubType("personal.ai")
    .allowIfSubType("java.util")
    .allowIfSubType("java.lang")   // Long, Integer, Boolean 등
    .allowIfSubType("java.math")   // BigDecimal
    .allowIfSubType("java.time")   // LocalDateTime 등
    .build();
```

그리고 **반드시 라운드트립 테스트**를 둔다. Serializer 팩토리를 `static` 메서드로 추출해 단위 테스트에서 그대로 호출하면 Testcontainers 없이도 빠르게 검증 가능.

### 관련 파일
- `core-service/src/main/java/personal/ai/core/booking/adapter/out/cache/RedisCacheConfig.java`
- `core-service/src/test/java/personal/ai/core/booking/adapter/out/cache/RedisCacheConfigSerializationTest.java`

---

<!--
## [날짜] [위반 유형]

### 발생 상황
[어떤 코드에서 발생했는지]

### 감지된 패턴
```java
// 위반 코드 예시
```

### 원인
[왜 문제인지]

### 올바른 패턴
```java
// 수정된 코드 예시
```

### 관련 파일
- `파일 경로:라인번호`
-->
