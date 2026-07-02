# TTS worker scaffold

This service is a thin HTTP boundary for Korean TTS rendering.

The default `app.py` is a stub: it accepts `POST /render` with `{ "voice", "text" }`
and returns deterministic placeholder `audio/mpeg` bytes plus `X-Duration-Ms`.
It exists so backend development and CI do not depend on a local model download.

## Real Kokoro setup

Replace `render_stub()` in `app.py` with a Kokoro-backed renderer and keep the same
HTTP contract:

```http
POST /render
Content-Type: application/json

{ "voice": "ko_default", "text": "오후 7시 30분에 저녁약 복용 시간입니다." }
```

Response:

```http
200 OK
Content-Type: audio/mpeg
X-Duration-Ms: 1200
```

Kokoro is Apache-2.0 licensed. Keep model files out of the git repository and load
them through the container image build, a mounted volume, or an external artifact store.
