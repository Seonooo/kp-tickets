# Claude Code Hooks

이 프로젝트에 설정된 자동화 Hooks 목록입니다.

---

## 활성화된 Hooks

### 1. Java 코드 검사 (PostToolUse: Edit/Write)

**트리거**: Java 파일 편집/생성 시

**검사 항목**:

| 검사 | 타입 | 설명 |
|------|------|------|
| 디버그 코드 | WARNING | `System.out.println`, `printStackTrace()` |
| Entity 노출 | ERROR | Controller에서 Entity 직접 반환 |
| 트랜잭션 범위 | ERROR/WARNING | 트랜잭션 내 Kafka/외부 API |
| 하드코딩 시크릿 | ERROR | password, secret, api_key |
| Redis 원자성 | WARNING | GET 후 SET 패턴 |
| N+1 패턴 | WARNING | JOIN FETCH 없는 연관 조회 |
| Lombok 오용 | ERROR/WARNING | record에 @Data, Entity에 @Setter |

**출력 예시**:
```
============================================================
📋 Code Check: ReservationService.java
============================================================

❌ ERRORS (수정 필요):

  [Line 45] TX_EXTERNAL_IO
  └─ 트랜잭션 내 Kafka 발행 - AFTER_COMMIT 사용 권장
     kafkaTemplate.send("orders", event);

⚠️  WARNINGS (검토 권장):

  [Line 23] DEBUG_CODE
  └─ System.out.println 발견 - Logger 사용 권장
     System.out.println("debug: " + value);

============================================================
총 1개 에러, 1개 경고
============================================================
```

---

### 2. 아키텍처 검증 (PostToolUse: Edit/Write)

**트리거**: Java 파일 편집/생성 시

**검사 항목**:

| 레이어 | 금지된 의존성 |
|--------|--------------|
| Domain | Adapter, Application Service, JPA, Spring |
| Application | Inbound Adapter, Outbound Adapter (Port 제외) |
| Adapter.In | Outbound Adapter |

**출력 예시**:
```
============================================================
🏗️  Architecture Check: Order.java
📦 Package: personal.ai.core.booking.domain.model
📍 Layer: DOMAIN
============================================================

❌ ARCHITECTURE VIOLATIONS:

  [Line 3]
  └─ Domain에서 JPA 의존 금지 (순수 POJO 유지)
     import jakarta.persistence.Entity;

  [Line 15]
  └─ Domain에서 @Entity 사용 금지 - 순수 POJO 유지
     @Entity

------------------------------------------------------------
📚 Reference: /docs/architecture.md
============================================================
```

---

### 3. 테스트 존재 검사 (PostToolUse: Edit/Write)

**트리거**: Service, UseCase, Adapter, Repository 파일 편집 시

**검사 항목**:
- 대응하는 테스트 파일 존재 여부

**출력 예시**:
```
============================================================
🧪 Test Coverage Check: ReservationService
============================================================

⚠️  WARNING: 테스트 파일이 없습니다!

  📁 대상 파일: .../ReservationService.java
  📝 예상 테스트: ReservationServiceTest.java

  💡 TDD 권장: /tdd 명령어로 테스트 먼저 작성
============================================================
```

---

### 4. 커밋 메시지 검사 (PostToolUse: git commit)

**트리거**: git commit 실행 후

**검사 항목**:
- Conventional Commits 형식 준수

**허용되는 type**:
| Type | 설명 |
|------|------|
| feat | 새로운 기능 |
| fix | 버그 수정 |
| docs | 문서 변경 |
| style | 코드 스타일 |
| refactor | 리팩토링 |
| test | 테스트 |
| chore | 빌드/설정 |
| perf | 성능 개선 |

**출력 예시**:
```
============================================================
📝 Commit Message Check
============================================================

⚠️  WARNING: 커밋 메시지 형식이 올바르지 않습니다.

  현재: "update code"

  📋 올바른 형식:
     type(scope): description

  📝 예시:
     feat(queue): 대기열 진입 API 추가
     fix(booking): 좌석 중복 예약 버그 수정
============================================================
```

---

## 스크립트 위치

```
.claude/scripts/
├── check-java-code.js      # Java 코드 검사
├── check-architecture.js   # 아키텍처 검증
├── check-test-coverage.js  # 테스트 존재 검사
└── check-commit-message.js # 커밋 메시지 검사
```

---

## 비활성화 방법

특정 Hook을 비활성화하려면 `.claude/settings.local.json`에서 해당 항목을 제거하세요.

```json
{
  "hooks": {
    "PostToolUse": [
      // 원하지 않는 hook 제거
    ]
  }
}
```

---

## 커스터마이징

스크립트를 수정하여 검사 규칙을 변경할 수 있습니다.

**예: 특정 패턴 허용**
```javascript
// check-java-code.js
// 테스트 파일에서는 System.out.println 허용
if (filePath.includes('/test/')) {
    // 디버그 코드 검사 스킵
}
```
