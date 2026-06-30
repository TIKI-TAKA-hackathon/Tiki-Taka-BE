# Backend GitHub Secrets

## Required Secrets

```text
AWS_HOST
AWS_SSH_PORT
AWS_USER
AWS_SSH_PRIVATE_KEY
DEPLOY_PATH
API_BASE_URL
```

## 서버 .env에 둘 값

아래 값들은 GitHub Secret으로 둘 수도 있지만, 초기 skeleton에서는 서버의 `DEPLOY_PATH/api/.env`에 직접 둡니다. 실제 민감값은 README, 코드, workflow, commit에 넣지 마세요.

```text
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

## gh CLI 등록 예시

```bash
gh secret set AWS_HOST --body "your-ec2-public-ip-or-domain"
gh secret set AWS_SSH_PORT --body "22"
gh secret set AWS_USER --body "ubuntu"
gh secret set AWS_SSH_PRIVATE_KEY < ~/.ssh/your-deploy-key
gh secret set DEPLOY_PATH --body "/opt/tikitaka-memory-api"
gh secret set API_BASE_URL --body "https://api.example.com"
```

민감값을 README나 코드에 커밋하지 마세요. 서버 `.env`는 서버에서 직접 만들고 권한을 제한하세요.
