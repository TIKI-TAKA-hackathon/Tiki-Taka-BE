# Codex 리뷰 루프 (Claude 구현 → Codex 리뷰)

Claude Code가 구현하고 Codex가 read-only 리뷰어로 diff를 검수해서, 리뷰어가
`<<<PASS>>>`를 낼 때까지 구현 → 리뷰 → 수정을 반복하는 최소 셋업입니다.
gate(test/lint/detekt)는 Claude 구현 단계에서 통과시키고, Codex는 정합성과
요구사항만 봅니다.

## 구성

```
codex-review-loop/
├── loop.sh                 # 드라이버 (대상 repo 안에서 실행)
├── Dockerfile.agents       # claude + codex 한 이미지
├── prompts/
│   ├── implementer.md      # Claude(구현자) 프롬프트
│   └── reviewer.md         # Codex(리뷰어) 프롬프트 — 얇은 래퍼
├── backend/AGENTS.md       # BE 리뷰 체크리스트 (Kotlin/Spring)
├── frontend/AGENTS.md      # FE 리뷰 체크리스트 (TS SPA)
└── specs/example-task.md   # 스펙 양식 예시
```

모델이 읽는 파일(prompts, AGENTS.md)은 에이전트 지시문 표준이자 CLAUDE.md의
"코드에 한국어 금지" 규칙에 맞춰 영어로 작성했습니다. 체크리스트를 한국어로 바꿔도
Codex는 동작합니다.

## 배치

- `loop.sh`, `prompts/`, `Dockerfile.agents` → 이 폴더째로 아무 데나 둬도 됩니다.
  `loop.sh`는 프롬프트를 자기 위치 기준으로, 스펙/diff는 git repo 기준으로 찾습니다.
  **대상 repo 안에서 실행**만 하면 됩니다.
- `backend/AGENTS.md`, `frontend/AGENTS.md` → 대상 repo의 해당 패키지 디렉토리에
  넣으세요. 이미 AGENTS.md가 있으면 "Review checklist" 섹션만 병합.
  - 모노레포: `<repo>/backend/AGENTS.md`, `<repo>/frontend/AGENTS.md`
  - 별도 repo: 각 repo 루트에 `AGENTS.md` 하나씩. 실행 시 package 인자를 `.`로.
- Codex는 AGENTS.md를, Claude는 CLAUDE.md를 읽습니다. 둘을 맞추려면
  `ln -s CLAUDE.md AGENTS.md`도 가능합니다.
- `.review/`에는 리뷰 산출물이 쌓이니 `.gitignore`에 추가하세요.

## 준비 (Docker)

```bash
cd codex-review-loop
docker build -t agents -f Dockerfile.agents .

cd /path/to/your/repo          # 반드시 repo 안에서 (상위 디렉토리 마운트 금지)
docker run --rm -it \
  -v "$PWD":/work \
  -v claude-auth:/root/.claude \
  -v codex-auth:/root/.codex \
  agents bash
# 컨테이너 안 최초 1회: `claude` 로그인, `codex login`
```

호스트에 claude/codex가 이미 있으면 Docker 없이 `loop.sh`를 그대로 돌려도 됩니다
(권한 우회는 Docker 안에서만 권장).

## 사용법

```bash
./loop.sh <spec-file> [package-dir] [max-iters]
```

- `spec-file` — repo 루트 기준 경로(또는 절대경로). 양식은 `specs/example-task.md`.
- `package-dir` — 리뷰 체크리스트(AGENTS.md)가 있는 디렉토리. 단일 repo면 `.`,
  모노레포면 `backend` / `frontend`. 기본 `.`.
- `max-iters` — 구현 → 리뷰 반복 상한. 기본 3.

예:

```bash
# 모노레포 BE 작업
/tools/codex-review-loop/loop.sh specs/add-order-endpoint.md backend

# 모노레포 FE 작업
/tools/codex-review-loop/loop.sh specs/order-detail-page.md frontend

# 단일 repo
/tools/codex-review-loop/loop.sh specs/task.md
```

FE·BE가 함께 필요한 기능은 **BE 먼저 루프 → 커밋 → FE 루프** 순으로 하세요.
그래야 FE 리뷰어가 실제 API 계약을 보고 검수합니다.

## 동작 방식

각 반복에서:

1. Claude가 repo 루트에서 SPEC(+ 직전 리뷰 피드백)대로 구현하고, 패키지 gate를
   green으로 만듭니다.
2. Codex를 패키지 디렉토리 안에서 read-only로 실행해 SPEC + (그 패키지로 스코프한)
   diff만 보고 리뷰합니다. 구현자의 자기평가는 넘기지 않습니다(redaction).
3. 응답 마지막 줄의 `<<<PASS>>>` / `<<<FAIL>>>`로 종료를 결정합니다. FAIL이면
   피드백을 다음 구현에 주입해 반복합니다.

리뷰 결과는 `.review/<package>-feedback.md`에 남습니다.

## 설정 / 권한

리뷰어 실행값은 `loop.sh`가 플래그로 겁니다(config보다 우선):
`--sandbox read-only -a never -c model_reasoning_effort=high -c web_search=disabled`.

- **read-only인 이유**: gate는 Claude가 이미 돌렸고, 리뷰어가 코드를 "고쳐주는" 것도
  막습니다.
- **Docker 안 bwrap 경고가 뜨면**: read-only는 user namespace가 필요합니다.
  `CODEX_SANDBOX=danger-full-access ./loop.sh ...`로 재실행하면 컨테이너를 경계로
  삼습니다.
- **리뷰어가 gate를 직접 재실행하게 하려면**: `CODEX_SANDBOX=workspace-write`.

체크리스트를 바꾸려면 `AGENTS.md`를, gate 명령을 바꾸려면 `AGENTS.md`의
"Verification gates"와 실제 프로젝트 스크립트를 맞추세요.

## 주의

- 권한 우회는 **신뢰하는 repo에서만**. 컨테이너 안이라도 네트워크는 열려 있어 유출
  경로가 존재합니다. `~/.ssh`·클라우드 크레덴셜은 마운트하지 마세요.
- PASS 후에는 커밋하고 다음 태스크를 시작하세요(안 그러면 다음 diff에 이전 변경이
  섞입니다).
- `max-iters`로 비용/usage 폭주를 막습니다. 계속 FAIL이면 마지막 피드백을 사람이
  판단하세요.
