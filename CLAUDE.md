# Project Navigation Map

이 파일은 규칙이 아닌 **지도**입니다.
작업 유형에 따라 어떤 파일을 먼저 읽어야 하는지 안내합니다.

---

## 작업 유형별 참조 파일

| 작업 유형 | 읽어야 할 파일 |
|----------|--------------|
| **계획 수립** | `docs/biz-logic.md`, `docs/architecture.md`, `.claude/agents/planner.md` |
| **구현** | `docs/convention.md`, `docs/architecture.md`, `docs/tech-stack.md`, `.claude/agents/implementer.md` |
| **TDD** | `docs/convention.md`, `.claude/agents/tdd-guide.md` |
| **아키텍처 설계/검토** | `docs/architecture.md`, `docs/erd.md`, `.claude/agents/architect.md` |
| **코드 리뷰** | `docs/convention.md`, `.claude/agents/code-reviewer.md` |
| **보안 검토** | `.claude/agents/security-reviewer.md` |
| **DB/쿼리 작업** | `docs/erd.md`, `docs/architecture.md`, `.claude/agents/database-reviewer.md` |
| **문서 업데이트** | `docs/`, `.claude/agents/doc-updater.md` |
| **PR 생성 + 보고** | `.claude/agents/pr-reporter.md` |
| **CodeRabbit 리뷰 처리** | `docs/convention.md`, `docs/architecture.md`, `.claude/agents/coderabbit-reviewer.md` |
| **과거 오류 확인** | `docs/pitfalls.md` |

---

## Agent Flow

```
planner        문서 파악 → 사용자 이해 확인 → Unit 분해 → 승인 대기
  └─ [Unit별 반복]
       ├─ tdd-guide   → 테스트 먼저 (Red)
       └─ implementer → 구현 (Green → Refactor)
            └─ hooks  → 자동 검증 (코드 품질 / 아키텍처)
                          BLOCKED 시 → doc-updater가 pitfalls 기록
  └─ doc-updater      → docs/ 문서 자동 업데이트
  └─ /ship 실행
       ├─ PR 자동 생성
       ├─ code-reviewer  (내부 수행)
       ├─ security-reviewer (내부 수행)
       ├─ coderabbit-reviewer → 수정/오탐 판단 → pitfalls 기록
       └─ 사용자에게 요약 보고서만 전달
```

### /ship 보고서 형식

```
## 작업 완료 보고

### 수행한 작업     → Unit별 완료 내용 (파일명 수준)
### 검토 결과       → ✅/⚠️/❌ 항목별 결과 (상세 코드 없음)
### 생성된 PR       → PR 제목 + URL
### 문서 업데이트   → 변경된 파일 목록
### 통계            → 변경 파일 수, 라인 수, 커밋 수
```

---

## 새 문서 추가 시

이 파일에 작업 유형과 파일 경로만 한 줄 추가하세요.
규칙과 내용은 해당 파일 안에 작성합니다.
