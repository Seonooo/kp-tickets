# /db-review

데이터베이스 성능을 검토합니다.

---

## Usage

```
/db-review                    # Repository 전체 검토
/db-review SeatRepository     # 특정 Repository 검토
/db-review --slow-query       # Slow Query 분석
/db-review --index            # 인덱스 최적화 제안
```

---

## Process

### Step 1: 에이전트 로드

`.claude/agents/database-reviewer.md` 에이전트를 활성화합니다.

### Step 2: Repository 스캔

```bash
# Repository 파일 목록
find . -name "*Repository.java" -type f

# 쿼리 패턴 검색
grep -rn "@Query" --include="*Repository.java"
grep -rn "findBy\|findAll" --include="*Repository.java"
```

### Step 3: 검토 항목

**N+1 Problem**
```java
// [BAD]
@Query("SELECT o FROM Order o")
List<Order> findAll();

// [GOOD]
@Query("SELECT o FROM Order o JOIN FETCH o.items")
List<Order> findAllWithItems();
```

**Index Optimization**
```sql
-- 실행 계획 확인
EXPLAIN SELECT * FROM seat WHERE schedule_id = 1;

-- 인덱스 추가
CREATE INDEX idx_seat_schedule ON seat(schedule_id);
```

**Query Optimization**
```java
// [BAD] SELECT *
findByScheduleId(id);

// [GOOD] Projection
@Query("SELECT new SeatDto(s.id, s.status) FROM Seat s WHERE s.scheduleId = :id")
```

**Connection Pool**
```yaml
hikari:
  maximum-pool-size: 150
  minimum-idle: 10
  connection-timeout: 30000
```

**Transaction Scope**
```java
// [BAD] 넓은 트랜잭션
@Transactional
public void process() {
    query();
    externalApi();  // 느림!
    save();
}

// [GOOD] 최소 범위
public void process() {
    data = query();
    result = externalApi();
    saveInTransaction(data, result);
}
```

### Step 4: 성능 분석

```sql
-- MySQL 상태 확인
SHOW STATUS LIKE 'Threads_connected';
SHOW STATUS LIKE 'Slow_queries';

-- 실행 계획
EXPLAIN ANALYZE SELECT ...;
```

### Step 5: 결과 출력

```markdown
## Database Review Report

### Summary
- **Repositories Reviewed**: N개
- **Issues Found**: N개
- **Performance Impact**: HIGH / MEDIUM / LOW

### Issues

#### [CRITICAL] N+1 in OrderRepository
- **File**: `OrderRepository.java:45`
- **Impact**: 100 orders → 101 queries
- **Solution**: Fetch Join 추가

#### [HIGH] Missing Index
- **Table**: `seat`
- **Column**: `schedule_id`
- **Solution**: `CREATE INDEX ...`

### Query Performance
| Query | Before | After | Improvement |
|-------|--------|-------|-------------|
| findSeats | 5.2s | 0.8s | 84% |

### Recommendations
1. 인덱스 추가
2. Pool Size 조정
3. 쿼리 최적화
```

---

## Common Optimizations

### 1. N+1 해결
```java
// @EntityGraph
@EntityGraph(attributePaths = {"items"})
List<Order> findAll();

// @BatchSize
@BatchSize(size = 100)
private List<OrderItem> items;
```

### 2. Pagination
```java
Page<Seat> findByScheduleId(Long id, Pageable pageable);
```

### 3. Projection
```java
interface SeatProjection {
    Long getId();
    String getStatus();
}
List<SeatProjection> findByScheduleId(Long id);
```

### 4. Native Query (복잡한 경우)
```java
@Query(value = "SELECT ... FROM ... WHERE ...", nativeQuery = true)
List<Object[]> findComplex();
```
