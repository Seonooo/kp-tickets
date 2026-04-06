# /code-review

최근 변경된 코드를 리뷰합니다.

---

## Usage

```
/code-review              # HEAD~1 기준 변경사항 리뷰
/code-review HEAD~3       # 최근 3개 커밋 리뷰
/code-review main         # main 브랜치 대비 리뷰
/code-review path/to/file # 특정 파일 리뷰
```

---

## Process

### Step 1: 변경사항 수집

```bash
# 변경된 파일 목록
git diff --name-only ${target:-HEAD~1}

# 상세 변경 내용
git diff ${target:-HEAD~1}
```

### Step 2: 에이전트 로드

`.claude/agents/code-reviewer.md` 에이전트의 체크리스트를 기반으로 리뷰를 수행합니다.

### Step 3: 검토 항목

**[CRITICAL] 필수 수정**
- 보안 취약점 (하드코딩된 비밀번호, SQL Injection)
- 데이터 정합성 (트랜잭션 누락, Race Condition)
- 아키텍처 위반 (Entity 외부 노출, Port/Adapter 원칙)

**[HIGH] 권장 수정**
- N+1 문제
- 트랜잭션 범위 과다
- 예외 처리 부재
- 테스트 누락

**[MEDIUM] 개선 권장**
- 코드 스타일 (메서드 길이, 중첩 깊이)
- Java 21 기능 미활용
- 네이밍 규칙

### Step 4: 결과 출력

```markdown
## Code Review Summary

### Overview
- 변경 파일: N개
- 검토 결과: ✅ APPROVED / ⚠️ CHANGES REQUESTED / ❌ BLOCKED

### Issues Found
[이슈 목록]

### Positive Findings
[잘 작성된 부분]

### Recommendations
[추가 개선 제안]
```

---

## Options

| 옵션 | 설명 |
|------|------|
| `--staged` | staged 파일만 리뷰 |
| `--security` | 보안 검토 포함 |
| `--verbose` | 상세 출력 |
