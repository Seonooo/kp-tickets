# AWS EC2 배포 가이드

이 문서는 Docker Hub를 통해 AWS EC2에 자동 배포하는 CD 파이프라인 설정 가이드입니다.

## 📋 목차

1. [사전 준비](#사전-준비)
2. [GitHub Secrets 설정](#github-secrets-설정)
3. [EC2 인스턴스 초기 설정](#ec2-인스턴스-초기-설정)
4. [배포 프로세스](#배포-프로세스)
5. [트러블슈팅](#트러블슈팅)

---

## 🔧 사전 준비

### 1. Docker Hub 계정
- Docker Hub 계정 생성: https://hub.docker.com
- Access Token 생성:
  1. Docker Hub 로그인
  2. Account Settings → Security → New Access Token
  3. Token 이름: `github-actions-cd`
  4. 생성된 토큰 저장 (다시 볼 수 없음)

### 2. AWS EC2 인스턴스
- **권장 사양**:
  - Instance Type: `t3.medium` 이상 (2 vCPU, 4GB RAM)
  - OS: Ubuntu 22.04 LTS
  - Storage: 30GB 이상
  - Security Group:
    - SSH (22)
    - HTTP (80)
    - HTTPS (443)
    - Application (8080, 8081)

### 3. SSH 키 페어
- EC2 인스턴스 생성 시 키 페어 생성 또는 기존 키 사용
- `.pem` 파일 안전하게 보관

### 4. AWS IAM 사용자 (보안 그룹 동적 IP 관리)
- **목적**: GitHub Actions가 실행될 때만 보안 그룹에 IP를 추가하고, 완료 후 제거하여 보안 강화
- **IAM 정책** (보안 그룹 특정):
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": [
          "ec2:AuthorizeSecurityGroupIngress",
          "ec2:RevokeSecurityGroupIngress"
        ],
        "Resource": "arn:aws:ec2:REGION:ACCOUNT_ID:security-group/SECURITY_GROUP_ID"
      }
    ]
  }
  ```

  **예시** (실제 값으로 교체 필요):
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": [
          "ec2:AuthorizeSecurityGroupIngress",
          "ec2:RevokeSecurityGroupIngress"
        ],
        "Resource": "arn:aws:ec2:ap-northeast-2:123456789012:security-group/sg-0123456789abcdef"
      }
    ]
  }
  ```

  **값 확인 방법:**
  - `REGION`: AWS 리전 (예: `ap-northeast-2`, `us-east-1`)
  - `ACCOUNT_ID`: AWS 계정 ID 확인:
    ```bash
    aws sts get-caller-identity --query Account --output text
    ```
  - `SECURITY_GROUP_ID`: EC2 보안 그룹 ID (예: `sg-0123456789abcdef`)

  > **⚠️ 보안 권장사항**:
  > - 리소스 ARN에 와일드카드(`*`)를 사용하지 마세요
  > - 특정 보안 그룹만 명시하여 최소 권한 원칙 적용
  > - 리전은 `AWS_REGION` Secret과 일치해야 함

- **IAM 사용자 생성**:
  1. AWS Console → IAM → Users → Create user
  2. User name: `github-actions-cd`
  3. Attach policies: 위 JSON 정책 생성 후 연결
  4. Security credentials → Create access key
  5. Access key ID와 Secret access key 저장

---

## 🔐 GitHub Secrets 설정

GitHub Repository → Settings → Secrets and variables → Actions → New repository secret

### 필수 Secrets

#### 1. 인프라 관련 (9개)

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `DOCKERHUB_USERNAME` | Docker Hub 사용자 이름 | `myusername` |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token (Read & Write 권한) | `dckr_pat_xxxxx...` |
| `EC2_HOST` | EC2 인스턴스 Public IP 또는 도메인 | `13.125.123.456` |
| `EC2_USERNAME` | EC2 SSH 사용자 이름 | `ubuntu` (Ubuntu AMI 기본값) |
| `EC2_SSH_KEY` | EC2 SSH Private Key (.pem 파일 내용 전체) | `-----BEGIN RSA PRIVATE KEY-----...` |
| `AWS_ACCESS_KEY_ID` | AWS IAM Access Key ID (보안 그룹 관리용) | `AKIA...` |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM Secret Access Key | `wJa...` |
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` |
| `EC2_SECURITY_GROUP_ID` | EC2 보안 그룹 ID | `sg-0123456789abcdef` |

#### 2. 애플리케이션 환경 변수 (5개)

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL Root 비밀번호 | `secure_root_pass_123!` |
| `MYSQL_DATABASE` | MySQL 데이터베이스 이름 | `concert_db` |
| `MYSQL_USER` | MySQL 사용자 이름 | `concert_user` |
| `MYSQL_PASSWORD` | MySQL 사용자 비밀번호 | `secure_user_pass_123!` |
| `REDIS_PASSWORD` | Redis 비밀번호 | `secure_redis_pass_123!` |

> **⚠️ 중요**: 모든 비밀번호는 **16자 이상, 특수문자 포함** 권장

### SSH Key 등록 방법

```bash
# .pem 파일 내용 전체 복사
cat your-key.pem

# 출력된 내용 전체를 EC2_SSH_KEY에 등록
# -----BEGIN RSA PRIVATE KEY----- 부터
# -----END RSA PRIVATE KEY----- 까지
```

### 보안 그룹 ID 확인 방법

```bash
# AWS Console에서 확인:
# EC2 → Instances → 인스턴스 선택 → Security 탭 → Security groups 클릭
# 또는
# EC2 → Security Groups → 해당 보안 그룹 선택 → Details에서 Security group ID 확인

# AWS CLI로 확인:
aws ec2 describe-security-groups --filters "Name=group-name,Values=your-sg-name" --query 'SecurityGroups[0].GroupId' --output text
```

> **💡 참고**: 보안 그룹 동적 IP 관리를 사용하면 EC2 보안 그룹에서 SSH 포트를 0.0.0.0/0으로 열어둘 필요가 없습니다. GitHub Actions 실행 시에만 자동으로 IP를 추가하고 완료 후 제거합니다.

---

## 🚀 EC2 인스턴스 초기 설정

### 1. EC2에 SSH 접속

```bash
# 로컬에서 실행
ssh -i your-key.pem ubuntu@<EC2_PUBLIC_IP>
```

### 2. 초기 설정 스크립트 실행

```bash
# 스크립트 다운로드
wget https://raw.githubusercontent.com/<YOUR_REPO>/main/scripts/ec2-setup.sh

# 실행 권한 부여
chmod +x ec2-setup.sh

# 스크립트 실행
./ec2-setup.sh

# Docker 그룹 적용 (재로그인 없이)
newgrp docker
```

### 3. 애플리케이션 디렉토리 생성

```bash
# 배포 디렉토리 생성
mkdir -p ~/concert-app
cd ~/concert-app
```

> **💡 참고**: `.env` 파일과 `docker-compose.prod.yml`은 GitHub Actions가 자동으로 생성/전송합니다.

### 4. Docker Hub 로그인

```bash
docker login
# Username: <DOCKERHUB_USERNAME>
# Password: <DOCKERHUB_TOKEN> (Access Token 사용)
```

### 5. 첫 배포 준비 완료!

이제 GitHub에 코드를 푸시하면 자동으로 배포됩니다:

```bash
# 로컬에서 실행
git push origin main
```

GitHub Actions가 자동으로:
- Docker 이미지 빌드
- Docker Hub에 푸시
- `.env` 파일 생성 (GitHub Secrets에서)
- `docker-compose.prod.yml` 전송
- 컨테이너 배포

---

## 🔄 배포 프로세스

### 자동 배포 (CD Pipeline)

1. **코드 푸시**:
   ```bash
   git push origin main
   ```

2. **GitHub Actions 자동 실행**:
   - 현재 GitHub Actions 러너의 공인 IP 가져오기
   - EC2 보안 그룹에 SSH 규칙 추가 (임시)
   - Docker 이미지 빌드 (core-service, queue-service)
   - Docker Hub에 푸시
   - EC2에 SSH 접속
   - 최신 이미지 Pull
   - 컨테이너 재시작
   - EC2 보안 그룹에서 SSH 규칙 제거 (자동 cleanup)

3. **배포 확인**:
   ```bash
   # GitHub Actions 로그 확인
   # Repository → Actions 탭

   # EC2에서 확인
   ssh -i your-key.pem ubuntu@<EC2_PUBLIC_IP>
   cd ~/concert-app
   docker compose ps
   docker compose logs -f core-service
   ```

### 수동 배포 (긴급 상황)

```bash
# EC2 접속
ssh -i your-key.pem ubuntu@<EC2_PUBLIC_IP>
cd ~/concert-app

# 최신 이미지 Pull
docker compose pull

# 서비스 재시작
docker compose up -d --force-recreate

# 확인
docker compose ps
```

---

## 🔍 트러블슈팅

### 1. Docker Hub 푸시 실패

**증상**: GitHub Actions에서 Docker push 실패

**해결책**:
- `DOCKERHUB_USERNAME`과 `DOCKERHUB_TOKEN` 확인
- Docker Hub에서 repository가 생성되어 있는지 확인
- Token 권한 확인 (Read, Write, Delete)

### 2. EC2 SSH 접속 실패

**증상**: GitHub Actions에서 "Permission denied" 또는 "Connection timeout" 에러

**해결책**:
- `EC2_SSH_KEY` 전체 내용 확인 (개행 포함)
- EC2 인스턴스가 실행 중인지 확인
- AWS IAM 권한 확인:
  ```bash
  # IAM 사용자에 ec2:AuthorizeSecurityGroupIngress 권한이 있는지 확인
  ```
- 보안 그룹 규칙 확인:
  ```bash
  # EC2 보안 그룹에서 GitHub Actions IP가 추가되었는지 확인
  aws ec2 describe-security-groups --group-ids <SECURITY_GROUP_ID>
  ```

### 2-1. 보안 그룹 IP 추가 실패

**증상**: "An error occurred (InvalidPermission.Duplicate)" 에러

**해결책**:
- 이미 규칙이 존재하는 경우입니다 (정상)
- 워크플로우에 `|| echo "Rule may already exist"` 처리되어 있어 무시됨

**증상**: "UnauthorizedOperation" 에러

**해결책**:
- AWS IAM 사용자 권한 확인
- `AWS_ACCESS_KEY_ID`와 `AWS_SECRET_ACCESS_KEY` 확인
- IAM 정책에 `ec2:AuthorizeSecurityGroupIngress`, `ec2:RevokeSecurityGroupIngress` 포함 여부 확인

### 3. 컨테이너 시작 실패

**증상**: `docker compose up` 후 컨테이너가 즉시 종료

**해결책**:
```bash
# 로그 확인
docker compose logs core-service
docker compose logs queue-service

# 환경 변수 확인
cat .env

# 네트워크 확인
docker network ls
docker network inspect concert-network
```

### 4. Health Check 실패

**증상**: 컨테이너가 unhealthy 상태

**해결책**:
```bash
# Health check 로그 확인
docker inspect concert-core-service | grep -A 10 Health

# Actuator endpoint 확인
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

### 5. 메모리 부족

**증상**: OOMKilled 에러

**해결책**:
- EC2 인스턴스 타입 업그레이드 (t3.medium → t3.large)
- docker-compose.yml의 메모리 제한 조정
- JVM 옵션 조정 (`JAVA_TOOL_OPTIONS`)

### 6. 이미지 Pull 실패

**증상**: "manifest unknown" 에러

**해결책**:
```bash
# Docker Hub 로그인 확인
docker login

# 이미지 존재 확인
docker pull <DOCKERHUB_USERNAME>/concert-core-service:latest

# 수동으로 이미지 Pull
docker compose pull --ignore-pull-failures
```

---

## 📊 모니터링

### 로그 확인

```bash
# 전체 로그
docker compose logs -f

# 특정 서비스 로그
docker compose logs -f core-service
docker compose logs -f queue-service

# 최근 100줄
docker compose logs --tail=100 core-service
```

### 리소스 사용량

```bash
# 컨테이너 리소스 확인
docker stats

# 디스크 사용량
df -h
docker system df
```

### 컨테이너 상태

```bash
# 실행 중인 컨테이너
docker compose ps

# 상세 정보
docker compose ps -a
docker inspect concert-core-service
```

---

## 🔄 롤백

### 특정 버전으로 롤백

```bash
# 이전 버전의 SHA 확인 (GitHub Commits)
# 예: abc1234567890...

# docker-compose.yml 수정
nano docker-compose.yml

# image 태그를 특정 SHA로 변경
# image: ${DOCKERHUB_USERNAME}/concert-core-service:abc1234567890

# 재배포
docker compose pull
docker compose up -d --force-recreate
```

### 이전 이미지로 빠른 롤백

```bash
# 사용 가능한 이미지 확인
docker images | grep concert

# 특정 이미지로 태그 변경
docker tag <OLD_IMAGE_ID> <DOCKERHUB_USERNAME>/concert-core-service:latest

# 재시작
docker compose up -d --force-recreate core-service
```

---

## 🔒 보안 권장사항

1. **환경 변수 보호**:
   - `.env` 파일 권한: `chmod 600 .env`
   - Git에 커밋하지 않기 (`.gitignore`에 추가)

2. **SSH 키 보호**:
   - `.pem` 파일 권한: `chmod 400 your-key.pem`
   - 정기적으로 키 교체

3. **동적 IP 관리 (보안 강화)**:
   - GitHub Actions 실행 시에만 보안 그룹에 IP 추가
   - 완료 후 자동으로 IP 제거
   - EC2 보안 그룹에서 SSH 포트를 0.0.0.0/0으로 열 필요 없음
   - 최소 권한 원칙 적용

4. **AWS IAM 최소 권한**:
   - IAM 사용자에 필요한 권한만 부여
   - 정기적으로 Access Key 교체
   - CloudTrail로 API 호출 모니터링

5. **방화벽 설정**:
   ```bash
   sudo ufw enable
   sudo ufw status
   ```

6. **Docker Hub Token**:
   - Read & Write 권한만 부여 (Delete 불필요)
   - 정기적으로 토큰 교체

7. **데이터베이스 비밀번호**:
   - 강력한 비밀번호 사용 (16자 이상, 특수문자 포함)
   - 정기적으로 비밀번호 변경

---

## 📞 지원

- GitHub Issues: 문제 발생 시 이슈 등록
- GitHub Actions 로그: 배포 실패 원인 확인
- EC2 로그: `docker compose logs -f`

---

## 📝 체크리스트

배포 전 확인사항:

- [ ] Docker Hub 계정 및 토큰 준비 (Read & Write 권한)
- [ ] AWS IAM 사용자 생성 및 정책 설정
- [ ] EC2 인스턴스 생성 및 Security Group 설정
- [ ] SSH 키 페어 생성 및 저장
- [ ] GitHub Secrets 등록 (14개: 인프라 9개 + 환경변수 5개)
  - Docker Hub: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`
  - EC2: `EC2_HOST`, `EC2_USERNAME`, `EC2_SSH_KEY`
  - AWS: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `EC2_SECURITY_GROUP_ID`
  - DB: `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `REDIS_PASSWORD`
- [ ] EC2 초기 설정 스크립트 실행
- [ ] Docker Hub 로그인 (EC2에서)
- [ ] 첫 배포 (GitHub Actions 자동 실행)
- [ ] 배포 확인 및 테스트

---

**작성일**: 2025-12-16
**버전**: 1.0.0
