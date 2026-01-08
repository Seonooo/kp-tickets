# Queue Service Scale Out 테스트 가이드

## 📋 목차
1. [테스트 목적](#테스트-목적)
2. [테스트 환경](#테스트-환경)
3. [사전 준비](#사전-준비)
4. [테스트 실행](#테스트-실행)
5. [결과 분석](#결과-분석)
6. [문제 해결](#문제-해결)

---

## 🎯 테스트 목적

**핵심 질문**: Queue Service 인스턴스를 1개 → 2개 → 4개로 늘리면 성능이 개선되는가?

**측정 지표**:
- ✅ **응답 시간** (P95, P99): 인스턴스 증가 시 감소 예상
- ✅ **처리량** (TPS): 100으로 고정 (비교를 위해)
- ✅ **에러율**: 5% 미만 유지
- ✅ **CPU/메모리 사용률**: 인스턴스당 부하 감소 예상
- ✅ **인스턴스별 요청 분산**: 균등 분산 확인

---

## 🖥️ 테스트 환경

### 아키텍처
```
[K6 Load Tester]
    ↓ TPS 100
[Docker DNS] → Round Robin 분산
    ├─→ [Queue Service 인스턴스 #1] ← Prometheus
    ├─→ [Queue Service 인스턴스 #2] ← Prometheus
    ├─→ [Queue Service 인스턴스 #3] ← Prometheus
    └─→ [Queue Service 인스턴스 #4] ← Prometheus
          ↓
     [Redis 공유]
```

### 주요 컴포넌트
- **Queue Service**: 대기열 처리 (스케일 아웃 대상)
- **Redis**: 대기열 데이터 저장 (단일 인스턴스)
- **Kafka**: 메시지 브로커 (단일 인스턴스)
- **Prometheus**: 메트릭 수집
- **Grafana**: 대시보드
- **K6**: 부하 테스트

---

## 📦 사전 준비

### 1. 환경 변수 확인

`.env` 파일에 다음 변수가 설정되어 있는지 확인:

```bash
# MySQL
MYSQL_ROOT_PASSWORD=rootpassword
MYSQL_DATABASE=concert
MYSQL_USER=concert_user
MYSQL_PASSWORD=concert_password

# Redis
REDIS_PASSWORD=redis_password

# Spring Profile
SPRING_PROFILES_ACTIVE=prod

# Redis Pool
REDIS_POOL_MAX_ACTIVE=20
```

### 2. Docker 이미지 빌드

**중요**: 첫 실행 전에 반드시 이미지를 빌드하세요.

```bash
# 프로젝트 루트 디렉토리에서
docker-compose -f docker-compose.simple-scale.yml build
```

빌드 완료 확인:
```bash
docker images | grep ai
```

출력 예시:
```
ai-queue-service    latest    ...
ai-core-service     latest    ...
```

### 3. 테스트 데이터 준비

MySQL에 테스트 데이터 삽입:

```bash
# 서비스 시작
docker-compose -f docker-compose.simple-scale.yml up -d db

# 잠시 대기 (DB 초기화)
sleep 30

# 테스트 데이터 삽입
docker exec -i concert-db mysql -u concert_user -pconcert_password concert < k6-tests/test-data.sql
```

---

## 🚀 테스트 실행

### 테스트 시나리오

**부하**: TPS 100 (초당 100개 요청)
**지속 시간**: 5분
**API**: `POST /api/v1/queue/enter` (대기열 진입)

### 단계별 실행

#### ⭐ 1단계: 인스턴스 1개 테스트

```bash
# 1. 전체 서비스 시작 (Queue Service 1개)
docker-compose -f docker-compose.simple-scale.yml up -d

# 2. 서비스 상태 확인
docker-compose -f docker-compose.simple-scale.yml ps

# 3. Queue Service 로그 확인 (별도 터미널)
docker-compose -f docker-compose.simple-scale.yml logs -f queue-service

# 4. Prometheus 타겟 확인
# 브라우저: http://localhost:9090/targets
# → queue-service:8081 타겟이 UP 상태인지 확인

# 5. K6 테스트 실행
docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js

# 6. 결과 기록
# - K6 Summary 출력 복사
# - Grafana 스크린샷 저장 (http://localhost:3000)
```

**예상 결과** (1개 인스턴스):
- 응답 시간 P95: 50-100ms
- 응답 시간 P99: 100-200ms
- CPU 사용률: 30-40%
- 메모리 사용률: 40-50%
- 에러율: < 1%

---

#### ⭐ 2단계: 인스턴스 2개 테스트

```bash
# 1. Queue Service만 중지
docker-compose -f docker-compose.simple-scale.yml stop queue-service
docker-compose -f docker-compose.simple-scale.yml rm -f queue-service

# 2. Queue Service 2개로 스케일 업
docker-compose -f docker-compose.simple-scale.yml up -d --scale queue-service=2

# 3. 인스턴스 확인
docker-compose -f docker-compose.simple-scale.yml ps queue-service

# 출력 예시:
# ai-queue-service-1    Up
# ai-queue-service-2    Up

# 4. 로그 확인 (2개 인스턴스 모두)
docker-compose -f docker-compose.simple-scale.yml logs -f queue-service

# 5. Prometheus 타겟 확인
# 브라우저: http://localhost:9090/targets
# → queue-service-1:8081, queue-service-2:8081 모두 UP 확인

# 6. K6 테스트 실행
docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js

# 7. 결과 기록
```

**예상 결과** (2개 인스턴스):
- 응답 시간 P95: 30-60ms (↓ 개선)
- 응답 시간 P99: 60-120ms (↓ 개선)
- CPU 사용률: 15-20% (각 인스턴스)
- 메모리 사용률: 30-40% (각 인스턴스)
- 에러율: < 1%
- **요청 분산**: 각 인스턴스가 50%씩 처리

---

#### ⭐ 3단계: 인스턴스 4개 테스트

```bash
# 1. Queue Service만 중지
docker-compose -f docker-compose.simple-scale.yml stop queue-service
docker-compose -f docker-compose.simple-scale.yml rm -f queue-service

# 2. Queue Service 4개로 스케일 업
docker-compose -f docker-compose.simple-scale.yml up -d --scale queue-service=4

# 3. 인스턴스 확인
docker-compose -f docker-compose.simple-scale.yml ps queue-service

# 출력 예시:
# ai-queue-service-1    Up
# ai-queue-service-2    Up
# ai-queue-service-3    Up
# ai-queue-service-4    Up

# 4. Prometheus 타겟 확인
# 브라우저: http://localhost:9090/targets
# → 4개 모두 UP 확인

# 5. K6 테스트 실행
docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js

# 6. 결과 기록
```

**예상 결과** (4개 인스턴스):
- 응답 시간 P95: 20-40ms (↓ 추가 개선)
- 응답 시간 P99: 40-80ms (↓ 추가 개선)
- CPU 사용률: 8-15% (각 인스턴스)
- 메모리 사용률: 25-35% (각 인스턴스)
- 에러율: < 1%
- **요청 분산**: 각 인스턴스가 25%씩 처리

---

## 📊 결과 분석

### 1. K6 Summary 비교

K6 테스트 완료 후 출력되는 Summary를 비교:

```
======= 인스턴스 1개 =======
http_req_duration..........: avg=85ms  p(95)=95ms  p(99)=150ms
http_req_failed............: 0.50%
queue_entry_success_rate...: 99.50%

======= 인스턴스 2개 =======
http_req_duration..........: avg=45ms  p(95)=55ms  p(99)=90ms  ← 개선!
http_req_failed............: 0.30%                            ← 개선!
queue_entry_success_rate...: 99.70%                           ← 개선!

======= 인스턴스 4개 =======
http_req_duration..........: avg=25ms  p(95)=35ms  p(99)=60ms  ← 추가 개선!
http_req_failed............: 0.10%                            ← 추가 개선!
queue_entry_success_rate...: 99.90%                           ← 추가 개선!
```

### 2. Grafana 대시보드 확인

**URL**: http://localhost:3000 (admin/admin)

#### 주요 확인 사항:

**패널 1: HTTP Request Duration (P95/P99)**
- 인스턴스 증가 → 응답 시간 감소 확인

**패널 2: JVM Heap Memory Usage**
- 각 인스턴스의 메모리 사용률 확인
- 인스턴스 증가 → 개별 메모리 사용률 감소

**패널 3: CPU Usage**
- 각 인스턴스의 CPU 사용률 확인
- 인스턴스 증가 → 개별 CPU 사용률 감소

**패널 4: HTTP Request Throughput (TPS)**
- 총 TPS가 100으로 일정한지 확인
- 각 인스턴스가 균등하게 처리하는지 확인

**패널 5: HTTP Error Rate (5xx)**
- 에러율이 5% 미만인지 확인

**패널 6: Redis Connected Clients**
- 인스턴스 증가에 따라 Redis 연결 수 증가 확인

### 3. Prometheus 쿼리

**URL**: http://localhost:9090

유용한 쿼리:

```promql
# 인스턴스별 평균 응답 시간
rate(http_server_requests_seconds_sum{job="queue-service"}[1m])
/
rate(http_server_requests_seconds_count{job="queue-service"}[1m])

# 인스턴스별 요청 수
rate(http_server_requests_seconds_count{job="queue-service"}[1m])

# 인스턴스별 CPU 사용률
process_cpu_usage{job="queue-service"}

# 인스턴스별 Heap 사용률
jvm_memory_used_bytes{job="queue-service", area="heap"}
/
jvm_memory_max_bytes{job="queue-service", area="heap"}
* 100
```

### 4. 성능 개선 계산

**공식**:
```
성능 개선율 = (1개 인스턴스 P95 - N개 인스턴스 P95) / 1개 인스턴스 P95 * 100
```

**예시**:
```
1개: P95 = 95ms
2개: P95 = 55ms
개선율 = (95 - 55) / 95 * 100 = 42% 개선

2개: P95 = 55ms
4개: P95 = 35ms
개선율 = (55 - 35) / 55 * 100 = 36% 추가 개선
```

---

## 🔧 문제 해결

### 문제 1: Prometheus가 Queue Service 인스턴스를 발견하지 못함

**증상**:
- http://localhost:9090/targets에서 queue-service 타겟이 0개 또는 DOWN

**원인**:
- DNS 서비스 디스커버리 설정 문제

**해결**:
```bash
# 1. Prometheus 로그 확인
docker-compose -f docker-compose.simple-scale.yml logs prometheus

# 2. Queue Service DNS 확인
docker exec concert-prometheus nslookup queue-service

# 3. 정적 타겟으로 변경 (monitoring/prometheus-scale.yml)
# dns_sd_configs 주석 처리하고 static_configs 주석 해제
```

---

### 문제 2: K6 테스트가 Queue Service에 연결 실패

**증상**:
```
ERRO[0001] Queue Service is not healthy!
```

**원인**:
- Queue Service가 시작되지 않았거나 Health Check 실패

**해결**:
```bash
# 1. Queue Service 상태 확인
docker-compose -f docker-compose.simple-scale.yml ps queue-service

# 2. Queue Service 로그 확인
docker-compose -f docker-compose.simple-scale.yml logs queue-service

# 3. Health Check 직접 호출
docker exec -it ai-queue-service-1 wget -O- http://localhost:8081/actuator/health
```

---

### 문제 3: 인스턴스가 균등하게 요청을 받지 못함

**증상**:
- Grafana에서 일부 인스턴스만 트래픽을 받음

**원인**:
- Docker DNS Round Robin이 제대로 작동하지 않음

**해결**:
```bash
# 1. K6 컨테이너에서 DNS 확인
docker exec concert-k6 nslookup queue-service

# 2. 여러 번 호출하여 다른 IP 반환 확인
for i in {1..10}; do
  docker exec concert-k6 nslookup queue-service | grep Address
  sleep 1
done

# 3. 수동으로 각 인스턴스 Health Check
docker exec concert-k6 wget -O- http://ai-queue-service-1:8081/actuator/health
docker exec concert-k6 wget -O- http://ai-queue-service-2:8081/actuator/health
```

---

### 문제 4: Redis 메모리 부족

**증상**:
```
OOM command not allowed when used memory > 'maxmemory'
```

**원인**:
- Redis maxmemory 512MB 초과

**해결**:
```bash
# 1. Redis 메모리 사용량 확인
docker exec concert-cache redis-cli -a redis_password INFO memory

# 2. maxmemory 증가 (docker-compose.simple-scale.yml)
# --maxmemory 512mb → --maxmemory 1gb

# 3. Redis 재시작
docker-compose -f docker-compose.simple-scale.yml restart cache
```

---

### 문제 5: 디스크 공간 부족

**증상**:
```
No space left on device
```

**원인**:
- Prometheus 데이터, 로그 파일 누적

**해결**:
```bash
# 1. Docker 시스템 정리
docker system prune -a --volumes

# 2. Prometheus 데이터 삭제
docker volume rm ai_prometheus_data

# 3. 로그 파일 정리
docker-compose -f docker-compose.simple-scale.yml logs --tail=0 -f
```

---

## 📈 추가 테스트 아이디어

### 1. TPS 증가 테스트

TPS를 100 → 200 → 500으로 증가시켜 한계점 찾기:

```javascript
// k6-tests/queue-entry-scale-test.js 수정
export const options = {
  scenarios: {
    queue_entry_constant_load: {
      rate: 200,  // TPS 200으로 변경
      // ...
    },
  },
};
```

### 2. 장기 안정성 테스트

5분 → 30분 → 1시간으로 테스트 시간 증가:

```javascript
// k6-tests/queue-entry-scale-test.js 수정
export const options = {
  scenarios: {
    queue_entry_constant_load: {
      duration: '30m',  // 30분으로 변경
      // ...
    },
  },
};
```

### 3. 점진적 부하 증가 (Ramp-up)

TPS를 0에서 200까지 점진적으로 증가:

```javascript
// k6-tests/queue-entry-ramp-test.js 생성
export const options = {
  scenarios: {
    queue_entry_ramping_load: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      stages: [
        { duration: '1m', target: 50 },   // 1분간 TPS 50까지
        { duration: '2m', target: 100 },  // 2분간 TPS 100까지
        { duration: '2m', target: 200 },  // 2분간 TPS 200까지
        { duration: '1m', target: 0 },    // 1분간 0까지 감소
      ],
      preAllocatedVUs: 100,
      maxVUs: 300,
    },
  },
};
```

### 4. 부하 급증 테스트 (Spike)

갑작스러운 트래픽 급증 시뮬레이션:

```javascript
// k6-tests/queue-entry-spike-test.js 생성
export const options = {
  scenarios: {
    queue_entry_spike_load: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      stages: [
        { duration: '2m', target: 100 },   // 안정 상태
        { duration: '10s', target: 500 },  // 급증!
        { duration: '2m', target: 500 },   // 유지
        { duration: '10s', target: 100 },  // 복구
        { duration: '2m', target: 100 },   // 안정 상태
      ],
      preAllocatedVUs: 200,
      maxVUs: 600,
    },
  },
};
```

---

## 🎓 결론 및 다음 단계

### 예상 결론

이 테스트를 통해 다음을 확인할 수 있습니다:

1. ✅ **수평 확장 효과**: 인스턴스 증가 → 응답 시간 감소
2. ✅ **선형 확장성**: 2배 증가 → 약 2배 성능 개선
3. ✅ **부하 분산**: Docker DNS Round Robin이 효과적으로 작동
4. ✅ **병목 지점**: Redis/Kafka가 병목이 되는 시점 파악

### 다음 단계

1. **Nginx 로드 밸런서 추가**
   - `docker-compose.nginx-scale.yml` 생성
   - Nginx vs Docker DNS 성능 비교

2. **프로덕션 환경 준비**
   - Kubernetes로 마이그레이션
   - HPA (Horizontal Pod Autoscaler) 설정

3. **추가 최적화**
   - Redis Cluster 구성 (단일 인스턴스 병목 해소)
   - Kafka 파티션 증가
   - 데이터베이스 Read Replica 추가

---

## 📞 지원

문제가 발생하면:
1. 로그 확인: `docker-compose -f docker-compose.simple-scale.yml logs`
2. 문제 해결 섹션 참고
3. GitHub Issues 등록

**Happy Testing! 🚀**
