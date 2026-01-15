# GC 최적화: G1 GC → ZGC 전환

**문제 해결 과정**: TPS 44 → 182.7 (315% 개선), GC Pause 사용자 체감 불가 수준으로 감소 (~0ms)

---

## 📌 비즈니스 요구사항

### 배경
콘서트 티켓팅 시스템에서 **초당 5,000건 이상의 요청 처리**가 필요했습니다. 티켓 오픈 시점에 수십만 명이 동시 접속하며, 이때 **응답 지연**이 발생하면:
- 사용자 이탈 증가 (3초 이상 대기 시 50% 이탈)
- 불공정한 선착순 (GC로 인한 랜덤 지연)
- 예매 성공률 저하 (타임아웃으로 인한 실패)

### 목표
- **초당 5,000건 이상** 요청 처리
- **P95 응답시간 < 500ms**
- **GC Pause < 10ms** (사용자가 체감하지 못하는 수준)

---

## 🔍 문제 발견

### 초기 상태 (Baseline)
```yaml
# docker-compose.yml - core-service
environment:
  JAVA_TOOL_OPTIONS: "-Xms1g -Xmx2g"  # Default: G1 GC
```

### 성능 테스트 결과
```bash
k6 run k6-tests/queue-entry-scale-test.js
```

**결과**
```
TPS: 44 req/s
P95: 419ms
P99: 651ms
GC Pause: 45ms (평균)
GC Count: 146회
Total GC Time: 6.563초 (3분 테스트)
```

### Grafana 메트릭 분석

**1. JVM Heap Memory**
```
Used Heap: 1.2GB ~ 1.8GB 사이 진동
GC 후: 약 800MB까지 감소
→ 메모리 부족은 아님
```

**2. GC Metrics**
```
GC Pause Time (avg): 45ms
GC Pause Time (max): 120ms
GC Count: 146회 (3분간)
→ 평균 1.2초마다 GC 발생
```

**3. Request Latency**
```
P95: 419ms
P99: 651ms
→ GC Pause와 상관관계 발견
```

### 병목 지점 식별

**문제 1: Stop-The-World**
- G1 GC의 Young GC는 Stop-The-World로 동작
- 평균 45ms 동안 모든 애플리케이션 스레드 정지
- 초당 평균 0.8회 발생 → 누적 지연 시간 발생

**문제 2: GC Overhead**
- 3분간 총 6.563초 GC 소요
- 전체 시간의 3.6% GC에 사용
- CPU 자원 낭비

**문제 3: 처리량 저하**
- GC로 인한 스레드 정지
- 요청 큐잉 발생
- 타임아웃 증가 → TPS 44로 제한

---

## 💡 해결 과정

### 1단계: GC 알고리즘 조사

**후보 GC 비교**

| GC | Pause Time | Throughput | Heap Size | 선택 |
|-----|-----------|-----------|-----------|------|
| **G1 GC** | ~45ms | 높음 | <32GB | ❌ Pause 큼 |
| **ZGC** | <1ms | 약간 낮음 | 모든 크기 | ✅ **선택** |
| **Shenandoah** | <10ms | 약간 낮음 | 모든 크기 | △ ZGC 우선 |

**ZGC 선택 이유**
1. **Concurrent GC**: Stop-The-World 최소화
2. **Scalable**: 2GB ~ 16TB 힙 크기에서도 일정한 Pause
3. **Java 21 지원**: Generational ZGC로 성능 향상

### 2단계: ZGC 적용

**변경 사항**
```yaml
# docker-compose.yml - core-service
environment:
  JAVA_TOOL_OPTIONS: >
    -Xms1g
    -Xmx2g
    -XX:+UseZGC
    -XX:+ZGenerational
    -XX:SoftMaxHeapSize=1536m
```

**설정 설명**
- `-XX:+UseZGC`: ZGC 활성화
- `-XX:+ZGenerational`: Generational ZGC 활성화 ([JEP 439](https://openjdk.org/jeps/439), Java 21+)
  - Young/Old Generation 분리로 GC 효율 향상
- `-XX:SoftMaxHeapSize=1536m`: Soft GC 임계값 설정 ([JDK-8222145](https://bugs.openjdk.org/browse/JDK-8222145))
  - 이 값 이하에서 GC 유지 시도, 초과 시에만 Max Heap(2GB)까지 사용
  - Over-Commit 방지 및 메모리 사용 최적화

### 3단계: 재배포 및 테스트

```bash
# 빌드
./gradlew :core-service:clean :core-service:build -x test

# Docker 이미지 재빌드
docker-compose build core-service

# 재배포
docker-compose up -d core-service

# 성능 테스트
k6 run k6-tests/queue-entry-scale-test.js
```

---

## 📊 결과 분석

### Before vs After 비교

| 지표 | Before (G1 GC) | After (ZGC) | 개선율 |
|------|----------------|-------------|--------|
| **TPS** | 44 req/s | **182.7 req/s** | **+315%** |
| **P95** | 419ms | 292ms | -30.3% |
| **P99** | 651ms | 577ms | -11.4% |
| **GC Pause** | 45ms (avg) | **~0ms (< 1ms)** | **사용자 체감 불가** |
| **GC Count** | 146회 | 250회 | +71% |
| **Total GC Time** | 6.563초 | **0.018초** | **-99.7%** |

### 실험 목적 달성 여부

**실험의 목적**
- ❌ TPS 5,000 달성 (X) - 전체 시스템 목표
- ✅ **GC가 병목인지 검증하고 제거** (O) - 실험의 실제 목적

**결과**
- GC로 인한 Stop-The-World 지연 사실상 제거 (45ms → < 1ms)
- GC Overhead 99.7% 감소로 CPU 자원을 요청 처리에 집중
- TPS 315% 개선으로 **GC가 주요 병목이었음을 데이터로 입증**

**다음 단계**
- TPS 182.7로 개선되었으나 목표(5,000)에는 미달
- Grafana 분석 결과 **DB Connection Pool 100% 사용률** 발견
- → GC 병목 제거 완료, 다음 병목(DB Pool)으로 이동

### Grafana 메트릭 (ZGC 적용 후)

**1. GC Pause Time**
```
Max Pause: 0.8ms
Avg Pause: 0.07ms
P99 Pause: 0.5ms
→ 사용자가 체감할 수 없는 수준
```

**2. GC Overhead**
```
Total GC Time: 0.018초 (3분간)
GC Overhead: 0.01%
→ CPU 자원 거의 낭비 없음
```

**3. Throughput**
```
TPS: 182.7 req/s
→ 목표(5,000) 대비 88% 미달이지만, G1 대비 315% 개선
→ 다음 병목: DB Connection Pool (다음 문서 참조)
```

### 처리량 3배 증가 원인 분석

**1. GC Pause 제거**
- G1 GC: 평균 1.2초마다 45ms 정지
- ZGC: Concurrent 처리로 정지 없음
- → **스레드가 중단 없이 지속적으로 요청 처리**

**2. GC Overhead 감소**
- G1 GC: 3.6% 시간을 GC에 사용
- ZGC: 0.01% 시간만 사용
- → **CPU 자원을 요청 처리에 집중**

**3. 큐잉 현상 제거**
- G1 GC: Pause 중 요청이 큐에 쌓임 → 타임아웃
- ZGC: 요청 즉시 처리 가능
- → **타임아웃 감소, 처리량 증가**

---

## 🎓 배운 점

### 1. GC는 "보이지 않는 병목"이다
- 애플리케이션 코드는 완벽해도 GC로 인해 성능 저하 가능
- **모니터링 필수**: Grafana로 GC 메트릭 실시간 확인

### 2. 트레이드오프 이해
**ZGC의 단점**
- 메모리 사용량 약간 증가 (Concurrent Marking 오버헤드)
- GC Count 증가 (146회 → 250회)
- 이론적 GC Throughput 약간 감소 가능

**그럼에도 선택한 이유**
- **Pause Time이 핵심**: 티켓팅 시스템에서 응답 지연은 치명적
- **유효 처리량은 오히려 증가**: ZGC의 이론적 GC Throughput이 낮지만 이 프로젝트에서는 **GC Pause로 인한 요청 정체가 주요 병목**이었기 때문에 실제 E2E TPS는 315% 증가
- **확장성**: 향후 Heap 증가 시에도 일정한 Pause 보장

**Throughput 역설**
```
이론적 GC Throughput: G1 > ZGC
→ GC 자체는 G1이 더 효율적

실제 E2E TPS: ZGC > G1 (315% 개선)
→ GC Pause 제거로 요청 큐잉/타임아웃 감소

결론: 도메인 특성상 Latency > Throughput
```

### 3. "Premature Optimization"이 아닌 이유
- **데이터 기반**: Grafana로 병목 지점 명확히 식별
- **비즈니스 요구사항**: 예매 성공률 95% 달성을 위한 필수 조치
- **실험**: G1 → ZGC 전환 후 측정 → 315% 개선 입증

### 4. 다음 병목 예측
ZGC 적용 후 TPS 182.7로 개선되었지만, 목표(5,000)에는 미달.
Grafana 분석 결과 **DB Connection Pool 100% 사용률** 발견.

→ **다음 도전**: [DB Connection Pool 병목 문제](02-db-pool-tuning.md)

---

## 🧠 CS 이론과 깊이

### GC 알고리즘 이론: 왜 ZGC가 빠른가?

#### 1. Generational Hypothesis의 한계

**G1 GC의 전제**
```
대부분의 객체는 짧게 산다 (Weak Generational Hypothesis)
→ Young Generation을 자주 GC
→ Old Generation은 가끔 GC

문제:
- Young GC도 Stop-The-World
- 평균 45ms 정지
```

**ZGC의 접근**
```
모든 Generation을 Concurrent하게 처리
→ Stop-The-World 최소화 (< 1ms)
→ Generational ZGC (Java 21+)로 성능 향상
```

#### 2. ZGC 내부 동작: Colored Pointers

**핵심 아이디어**: 64비트 포인터의 상위 비트를 메타데이터로 사용

```
64비트 포인터 구조 (ZGC):
[63-48]: Metadata (16비트)
  - Marked0, Marked1, Remapped, Finalizable
[47-0]: 실제 주소 (48비트)

예시:
0x0001_FFFF_1234_5678
  ↑ Marked0 비트 설정
  ↑ 실제 주소: 0xFFFF_1234_5678
```

**장점**
- 별도 메타데이터 구조 불필요 (메모리 절약)
- 포인터 하나로 객체 상태 확인 가능
- Load Barrier에서 빠른 검사

#### 3. Load Barrier: Concurrent Compaction의 핵심

**G1 GC의 문제**
```java
// Compaction 중 객체 이동
Object obj = heap[oldAddress];
// → 이 시점에 GC가 객체를 이동시키면?
// → Dangling Pointer 발생!
// → 해결: Stop-The-World로 모든 스레드 정지
```

**ZGC의 해결**
```java
// Load Barrier 삽입 (컴파일러 자동)
Object obj = loadBarrier(heap[address]);

// loadBarrier 내부:
if (pointer.isMarked0() && currentPhase.isMarked1()) {
    // 객체가 이동됨 → Forwarding Pointer 확인
    pointer = forwardingTable.get(pointer);
}
return pointer;
```

**결과**
- GC가 객체를 이동시켜도 애플리케이션 스레드 정지 불필요
- Load Barrier 오버헤드 < 1% (하드웨어 최적화)

#### 4. Memory Barrier와 Cache Coherence

**왜 ZGC는 메모리를 더 쓰는가?**

**G1 GC**
```
Stop-The-World
→ 모든 스레드 정지
→ Cache 일관성 문제 없음
→ Memory Barrier 최소
```

**ZGC**
```
Concurrent GC
→ 애플리케이션 스레드와 GC 스레드 동시 실행
→ Cache 일관성 유지 필요
→ Memory Barrier 필요 (MFENCE, SFENCE)

메모리 오버헤드:
- Forwarding Table
- Colored Pointers 메타데이터
- Load Barrier 코드
→ 약 10-15% 메모리 증가
```

**트레이드오프**
- 메모리 10-15% 증가 vs GC Pause 완전 제거
- 티켓팅 시스템에서는 **Latency > Memory**
- → ZGC 선택

---

## 🔀 고려한 다른 GC 알고리즘

### 1. Shenandoah GC

**장점**
- ZGC와 유사한 Concurrent GC
- Pause Time < 10ms
- OpenJDK 기반 배포판에 포함

**단점**
- ZGC보다 Pause Time이 약간 김 (< 10ms vs < 1ms)
- Generational 지원 없음 (Java 21 기준)
- ZGC 대비 프로덕션 검증 사례 적음

**선택하지 않은 이유**
- **Pause Time**: 더 짧은 Pause Time을 가지는 것이 좋다고 생각함
- **Generational 지원**: ZGC는 Java 21+ Generational ZGC를 지원하여 GC 효율 향상 ([JEP 439](https://openjdk.org/jeps/439))

### 2. Parallel GC

**장점**
- 최고의 Throughput
- Young/Old Generation 병렬 처리

**단점**
- Stop-The-World (Young GC 평균 30ms)
- Pause Time이 G1보다 더 김

**선택하지 않은 이유**
- Latency가 Throughput보다 중요
- Pause Time으로 인한 사용자 경험 저하

### 3. Serial GC

**장점**
- 가장 간단
- 메모리 사용량 최소

**단점**
- Single-threaded GC
- Pause Time 매우 김 (> 100ms)

**선택하지 않은 이유**
- 프로덕션 환경 부적합
- 대용량 트래픽 처리 불가

---

## 📂 관련 문서

- **[02. DB Pool 튜닝](02-db-pool-tuning.md)**: ZGC 적용 후 발견한 다음 병목
- **[Performance Test Summary](../PERFORMANCE_TEST_SUMMARY.md)**: 전체 성능 테스트 과정
- **[Grafana Metrics Guide](../grafana-metrics-guide.md)**: GC 메트릭 확인 방법

---

## 🔧 재현 방법

### 1. G1 GC로 테스트 (Before)
```yaml
# docker-compose.yml
environment:
  JAVA_TOOL_OPTIONS: "-Xms1g -Xmx2g"
```

```bash
docker-compose up -d core-service
k6 run k6-tests/queue-entry-scale-test.js
```

### 2. ZGC로 테스트 (After)
```yaml
# docker-compose.yml
environment:
  JAVA_TOOL_OPTIONS: "-Xms1g -Xmx2g -XX:+UseZGC -XX:+ZGenerational"
```

```bash
docker-compose up -d core-service
k6 run k6-tests/queue-entry-scale-test.js
```

### 3. Grafana 확인
```
http://localhost:3000
→ JVM Dashboard
→ GC Pause Time, GC Count, Total GC Time 비교
```

---

**작성자**: Yoon Seon-ho
**작성일**: 2025-12-26
**태그**: `GC`, `ZGC`, `Performance`, `JVM`
