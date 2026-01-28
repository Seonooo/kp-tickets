# /plan

기능 구현 계획을 수립합니다.

---

## Usage

```
/plan                         # 아키텍처 설계 모드 시작
/plan "대기열 시스템 개선"      # 특정 기능 계획
/plan --adr                   # ADR 문서 생성
```

---

## Process

### Step 1: 에이전트 로드

`.claude/agents/architect.md` 에이전트를 활성화합니다.

### Step 2: 요구사항 분석

```markdown
## Feature Request Analysis

### Business Requirements
- 무엇을 해결하려는가?
- 사용자는 누구인가?
- 성공 기준은 무엇인가?

### Non-Functional Requirements
- 예상 트래픽
- 응답시간 목표
- 데이터 정합성 수준
- 가용성 목표
```

### Step 3: 기존 코드베이스 분석

```bash
# 프로젝트 구조 확인
tree -d -L 3

# 관련 도메인 확인
ls -la core-service/src/main/java/personal/ai/core/

# 기존 패턴 확인
grep -rn "UseCase\|Repository\|Adapter" --include="*.java"
```

### Step 4: 아키텍처 설계

```markdown
## Architecture Design

### Component Diagram
[다이어그램]

### Data Flow
1. 요청 흐름
2. 데이터 흐름
3. 이벤트 흐름

### API Contract
- Endpoint
- Request/Response

### Database Schema
- Entity 설계
- 인덱스 전략
```

### Step 5: 트레이드오프 분석

```markdown
## Trade-off Analysis

### Option A vs Option B
| 항목 | Option A | Option B |
|------|----------|----------|
| 장점 | ... | ... |
| 단점 | ... | ... |
| 복잡도 | ... | ... |
| 성능 | ... | ... |

### Decision
선택: Option X
이유: ...
```

### Step 6: 구현 계획

```markdown
## Implementation Plan

### Phase 1: 기반 작업
- [ ] 도메인 모델 정의
- [ ] Port 인터페이스 정의
- [ ] 테스트 작성 (TDD)

### Phase 2: 핵심 구현
- [ ] Application Service
- [ ] Repository Adapter
- [ ] Controller

### Phase 3: 통합 및 검증
- [ ] 통합 테스트
- [ ] 성능 테스트
- [ ] 문서화
```

---

## Output Templates

### Architecture Design Document
```markdown
# [기능명] Architecture Design

## 1. Overview
## 2. Requirements
## 3. Component Design
## 4. Data Flow
## 5. API Contract
## 6. Database Schema
## 7. Trade-offs
## 8. Risks & Mitigations
## 9. Implementation Plan
```

### ADR (Architecture Decision Record)
```markdown
# ADR-XXX: [제목]

## Status
Proposed / Accepted / Deprecated

## Context
[배경 설명]

## Decision
[결정 내용]

## Consequences
### Positive
### Negative
### Risks
```

---

## Reference Documents

계획 수립 시 참조할 문서:

| 문서 | 경로 |
|------|------|
| 아키텍처 | `/docs/architecture.md` |
| 비즈니스 로직 | `/docs/biz-logic.md` |
| ERD | `/docs/erd.md` |
| 기술 스택 | `/docs/tech-stack.md` |
| 컨벤션 | `/docs/convention.md` |
