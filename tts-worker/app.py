from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json


def render_stub(voice: str, text: str) -> bytes:
    del voice, text
    return b"\xff\xf3\x18\xc4" + (b"\x00" * 512)


class Handler(BaseHTTPRequestHandler):
    def do_POST(self) -> None:
        if self.path != "/render":
            self.send_error(404)
            return

        content_length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(content_length) or b"{}")
        audio = render_stub(
            voice=str(payload.get("voice", "ko_default")),
            text=str(payload.get("text", "")),
        )

        self.send_response(200)
        self.send_header("Content-Type", "audio/mpeg")
        self.send_header("Content-Length", str(len(audio)))
        self.send_header("X-Duration-Ms", "250")
        self.end_headers()
        self.wfile.write(audio)

    def log_message(self, format: str, *args: object) -> None:
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
