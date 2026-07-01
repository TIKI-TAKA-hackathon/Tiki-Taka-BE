# Deploy & Operate — 고찌봄 (Tiki-Taka) Backend

## Architecture

Push to `main` → GitHub Actions verifies (ktlint/detekt/tests), builds a Docker image, and
pushes it to GHCR → the workflow SSHes into EC2, pulls the image, and restarts the stack.

On the server, `docker compose` runs four services: `postgres`, `backend` (the prebuilt GHCR
image), `nginx` (TLS termination for `api.stdiodh.xyz`, proxies `/api` to the backend), and
`certbot` (Let's Encrypt issuance + auto-renew).

```
push main ──▶ verify-and-build (GHCR: ghcr.io/stdiodh/tiki-taka-be:latest + :<sha>)
          └─▶ deploy (SSH) ──▶ git pull ──▶ compose pull backend ──▶ compose up -d
```

## Prerequisites (one-time, done by a human)

- EC2 instance with Docker + the Docker Compose plugin installed.
- DNS `A` record `api.stdiodh.xyz` → `15.164.189.72`, propagated. Use an Elastic IP so the
  record does not break on instance restart.
- EC2 security group inbound open on **80** and **443**.

## GitHub secrets

Two groups. Set under **Settings → Secrets and variables → Actions**.

**1) CI → EC2 deploy (required):**

| Secret | Value |
| --- | --- |
| `EC2_HOST` | EC2 public IP or hostname (e.g. `15.164.189.72` or `api.stdiodh.xyz`) |
| `EC2_USER` | SSH user; defaults to `ubuntu` if unset |
| `EC2_SSH_KEY` | Private SSH key whose public key is in the server's `~/.ssh/authorized_keys` |

```bash
gh secret set EC2_HOST    --body "15.164.189.72"
gh secret set EC2_USER    --body "ubuntu"
gh secret set EC2_SSH_KEY < ~/.ssh/your-deploy-key
```

**2) GHCR image access:**

- Pushing the image uses the built-in `GITHUB_TOKEN` (the workflow already grants
  `packages: write`) — no secret to add.
- For the server's `docker compose pull` to work, either make the GHCR package **public**
  (GHCR package → Package settings → Change visibility → Public), **or** run
  `docker login ghcr.io` on the server with a PAT that has `read:packages`.

## First-time EC2 setup (manual)

```bash
# 1) Clone the repo to the path the deploy workflow expects
cd ~ && git clone https://github.com/stdiodh/Tiki-Taka-BE.git
cd ~/Tiki-Taka-BE

# 2) Create the server env file (never committed)
cp .env.example .env
#    edit .env: POSTGRES_PASSWORD (strong), APP_CORS_ALLOWED_ORIGINS (real Firebase domains)

# 3) Set the Let's Encrypt contact email
#    edit init-letsencrypt.sh: set EMAIL=... (keep STAGING=1 for the first run)

# 4) Build images and bring up DB + backend to confirm boot + Flyway migration
docker compose build
docker compose up -d postgres backend
docker compose logs -f backend   # Ctrl-C once healthy

# 5) Issue the certificate (dummy cert → nginx up → real cert → reload)
./init-letsencrypt.sh
#    verify staging succeeded, then flip to trusted certs (see below)

# 6) Bring up the full stack
docker compose up -d
```

If the GHCR package is public you can instead run the prod stack straight from the image on
subsequent boots:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Flipping `STAGING=0` for trusted certificates

`init-letsencrypt.sh` defaults to `STAGING=1`, which uses the Let's Encrypt **staging**
environment (untrusted certs, but no rate limits — safe for testing). Once a staging run
succeeds:

1. Edit `init-letsencrypt.sh` and set `STAGING=0`.
2. Re-run `./init-letsencrypt.sh` to obtain a real, browser-trusted certificate.

The `certbot` container then renews every 12h automatically.

## Verify

```bash
curl https://api.stdiodh.xyz/api/health
# expected: {"status":"UP","service":"gojjibom-api","time":"..."}
```

## Running the review loop

`loop.sh` drives implement → gates → adversarial review until the reviewer emits `<<<PASS>>>`
(or the max iterations is hit). It requires the `claude` and `codex` CLIs on `PATH`.

```bash
./loop.sh specs/<task>.md [max_iters]   # max_iters defaults to 5
```

Each iteration: Claude implements the spec, the gates run
(`./gradlew ktlintCheck detekt test build`), and — if gates pass — Codex reviews the diff
read-only against `prompts/reviewer.md` + the checklist in `AGENTS.md`. Write new tasks as
`specs/NNN-*.md` from `specs/_template.md`.
