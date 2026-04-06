# /security

보안 취약점을 검토합니다.

---

## Usage

```
/security                 # 전체 프로젝트 보안 검토
/security src/main/java   # 특정 디렉토리 검토
/security --diff          # 변경된 파일만 검토
```

---

## Process

### Step 1: 에이전트 로드

`.claude/agents/security-reviewer.md` 에이전트를 활성화합니다.

### Step 2: 자동 스캔

```bash
# 하드코딩된 시크릿 검색
grep -rn "password\s*=" --include="*.java" --include="*.yml"
grep -rn "secret\s*=" --include="*.java" --include="*.yml"
grep -rn "api.?key" -i --include="*.java"

# SQL Injection 취약점
grep -rn "Query.*+" --include="*.java"
grep -rn "createNativeQuery" --include="*.java"

# 인가 누락 엔드포인트
grep -rn "@GetMapping\|@PostMapping" --include="*.java" -A5
```

### Step 3: 검토 카테고리

**OWASP Top 10**
- A01: Broken Access Control
- A02: Cryptographic Failures
- A03: Injection
- A04: Insecure Design

**Redis Security**
- Race Condition (Check-Then-Act)
- Lua Script 원자성
- Key Expiration (TTL)

**Authentication**
- JWT 서명 검증
- Secret Management
- Token Storage

**Concurrency (티켓팅 특화)**
- Double Booking Prevention
- Queue Bypass Prevention

### Step 4: 결과 출력

```markdown
## Security Review Report

### Summary
- **Risk Level**: CRITICAL / HIGH / MEDIUM / LOW
- **Vulnerabilities Found**: N개

### Findings
[취약점 목록 및 해결책]

### Recommendations
[추가 보안 권장사항]
```

---

## Severity Levels

| Level | 설명 | 조치 |
|-------|------|------|
| **CRITICAL** | 즉시 악용 가능 | 즉시 수정 |
| **HIGH** | 악용 가능 | 빠른 수정 |
| **MEDIUM** | 제한적 영향 | 계획 수정 |
| **LOW** | 모범 사례 미준수 | 개선 권장 |
