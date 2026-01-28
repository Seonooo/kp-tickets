# /build

프로젝트를 빌드합니다.

---

## Usage

```
/build                    # 전체 빌드
/build core               # core-service 빌드
/build queue              # queue-service 빌드
/build --skip-tests       # 테스트 스킵
/build --clean            # 클린 빌드
```

---

## Commands

### 전체 빌드
```bash
./gradlew build
```

### 클린 빌드
```bash
./gradlew clean build
```

### 테스트 스킵
```bash
./gradlew build -x test
```

### 모듈별 빌드
```bash
# Core Service
./gradlew :core-service:build

# Queue Service
./gradlew :queue-service:build
```

### Docker 이미지 빌드
```bash
# Docker Compose로 빌드
docker-compose build

# 특정 서비스
docker-compose build core-service
docker-compose build queue-service
```

---

## Build Verification

### 빌드 성공 확인
```bash
# JAR 파일 확인
ls -la core-service/build/libs/
ls -la queue-service/build/libs/
```

### 의존성 확인
```bash
./gradlew dependencies
```

### 컴파일 에러 확인
```bash
./gradlew compileJava
```

---

## Troubleshooting

### 빌드 실패 시
1. 클린 빌드: `./gradlew clean build`
2. 캐시 삭제: `rm -rf ~/.gradle/caches/`
3. 상세 로그: `./gradlew build --info`

### 메모리 부족
```bash
# gradle.properties
org.gradle.jvmargs=-Xmx2g -XX:+HeapDumpOnOutOfMemoryError
```

### 의존성 충돌
```bash
./gradlew dependencyInsight --dependency [library-name]
```
