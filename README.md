# 티키타카 기억카드 Backend

Spring Boot Kotlin API 서버, Go 운영 보조 도구, PostgreSQL, Docker Compose, GitHub Actions CI/CD, AWS 단일 서버 배포 skeleton입니다.

현재 레포 루트가 BE 루트이므로 별도 `backend/` 폴더 없이 아래 구조를 사용합니다.

```text
api/
  Dockerfile
  docker-compose.local.yml
  docker-compose.prod.yml
  src/main/kotlin/com/tikitaka/memorycard/
  src/main/resources/db/migration/
tools/go/
infra/
.github/
```

## 로컬 실행

Java 21과 PostgreSQL이 필요합니다.

```bash
cd api
cp .env.example .env
./gradlew bootRun
```

기본 API:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/topics
```

질문 오디오 URL은 BE가 파일을 서빙하지 않고 `FRONTEND_BASE_URL/audio/questions/{audio_file_name}` 형태로 생성합니다.

## Docker Compose 실행

로컬 개발용 app + postgres:

```bash
cd api
docker compose -f docker-compose.local.yml up -d --build
```

종료:

```bash
docker compose -f docker-compose.local.yml down
```

prod compose는 서버의 `api/.env` 파일을 읽습니다. 실제 secret 값은 compose 파일에 하드코딩하지 않습니다.

## 검증

```bash
cd api
./gradlew clean test build
docker build -t tikitaka-memory-api:local .

cd ../tools/go
go test ./...
go run ./cmd/validate-question-manifest --file ./testdata/questions.seed.json
```

운영 smoke check:

```bash
cd tools/go
go run ./cmd/smokecheck --base-url https://api.example.com
```

## AWS 서버 준비

EC2 또는 Lightsail 같은 단일 서버에 Docker와 Docker Compose plugin이 설치되어 있어야 합니다.

```bash
sudo mkdir -p /opt/tikitaka-memory-api/api
sudo chown -R ubuntu:ubuntu /opt/tikitaka-memory-api
cd /opt/tikitaka-memory-api/api
nano .env
```

서버 `DEPLOY_PATH/api/.env` 예시:

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
DATABASE_URL=jdbc:postgresql://postgres:5432/tikitaka
DATABASE_USERNAME=tikitaka
DATABASE_PASSWORD=change-me
FRONTEND_BASE_URL=https://tikitaka.web.app
CORS_ALLOWED_ORIGINS=https://tikitaka.web.app,https://www.example.com
SHARE_TOKEN_SECRET=change-me-long-random
POSTGRES_DB=tikitaka
POSTGRES_USER=tikitaka
POSTGRES_PASSWORD=change-me
```

Nginx reverse proxy는 `infra/nginx/tikitaka-api.conf.example`를 참고하세요. 도메인 연결과 HTTPS 인증서는 Certbot 등으로 서버에서 별도 설정합니다. 이 레포는 인증서 자동 발급 스크립트를 포함하지 않습니다.

GitHub Actions 배포용 SSH key는 서버의 `~/.ssh/authorized_keys`에 공개키를 등록하고, 개인키는 GitHub Secret `AWS_SSH_PRIVATE_KEY`에 등록합니다.

## GitHub Secrets

필수 secret:

```text
AWS_HOST
AWS_SSH_PORT
AWS_USER
AWS_SSH_PRIVATE_KEY
DEPLOY_PATH
API_BASE_URL
```

등록 예시:

```bash
gh secret set AWS_HOST --body "your-ec2-public-ip-or-domain"
gh secret set AWS_SSH_PORT --body "22"
gh secret set AWS_USER --body "ubuntu"
gh secret set AWS_SSH_PRIVATE_KEY < ~/.ssh/your-deploy-key
gh secret set DEPLOY_PATH --body "/opt/tikitaka-memory-api"
gh secret set API_BASE_URL --body "https://api.example.com"
```

서버 DB 비밀번호, `SHARE_TOKEN_SECRET` 같은 민감값은 초기 skeleton에서는 서버의 `DEPLOY_PATH/api/.env`에 직접 둡니다. 민감값을 코드, README, workflow에 커밋하지 마세요.

## CI/CD 동작

`.github/workflows/be-ci.yml`:

- `main`, `develop` push와 pull request에서 실행
- Java 21 설정
- Gradle cache 설정
- Spring Boot Kotlin `clean test build`
- Go `go test ./...`
- API Docker image build 확인

`.github/workflows/be-deploy-aws.yml`:

- `main` push 또는 수동 실행
- API build/test와 Go test 실행
- SSH key로 AWS 서버 접속
- `api/`, `tools/`, `infra/`를 `DEPLOY_PATH`로 동기화
- 서버의 기존 `DEPLOY_PATH/api/.env`를 보존
- 서버에서 `docker compose -f docker-compose.prod.yml up -d --build`
- 배포 후 Go `smokecheck` 실행

## API Skeleton

- `GET /actuator/health`
- `GET /api/v1/topics`
- `GET /api/v1/topics/{topicId}/questions`
- `POST /api/v1/memory-sessions`
- `POST /api/v1/memory-sessions/{sessionId}/answers`
- `POST /api/v1/memory-sessions/{sessionId}/card-draft`
- `PATCH /api/v1/memory-cards/{cardId}`
- `POST /api/v1/memory-cards/{cardId}/publish`
- `GET /api/v1/shared-cards/{shareToken}`

## 아직 수동으로 해야 할 일

- AWS 서버 생성과 보안 그룹 설정
- Docker와 Docker Compose 설치
- 서버 `DEPLOY_PATH/api/.env` 작성
- 도메인 DNS 연결
- Nginx 설정 적용
- HTTPS 인증서 발급과 갱신 설정
- GitHub Secrets 등록
- Firebase Hosting에 질문 MP3 업로드
