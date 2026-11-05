"""Local image fixture for manual loading profiles. Requires Python 3 and Pillow.

Run on the host, then `adb reverse tcp:18766 tcp:18766`. Pass
`--es artworkBase http://127.0.0.1:18766/run-name --ez stress true` to the
preview activity. A new run-name bypasses image caches; reuse it for disk-cache
comparisons. GET /stats returns aggregate request counts and transferred bytes.
"""

import io
import json
import threading
import time
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from PIL import Image, ImageDraw


def serve():
    payloads = {}
    for width, height in [(320, 480), (320, 180), (640, 360), (1920, 1080)]:
        noise = Image.effect_noise((width, height), 45).convert("RGB")
        for variant in range(8):
            image = Image.blend(noise, Image.new("RGB", noise.size, (35 + variant * 8, 65, 95)), .65)
            ImageDraw.Draw(image).text((20, 30), f"JOYFIN LOCAL IMAGE {variant}", fill="white", stroke_width=1)
            buffer = io.BytesIO()
            image.save(buffer, "JPEG", quality=85)
            payloads[width, height, variant] = buffer.getvalue()
    lock = threading.Lock()
    counts = Counter()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            url = urlparse(self.path)
            if url.path == "/stats":
                with lock:
                    data = json.dumps(dict(counts)).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(data)
                return
            try:
                requested = int(parse_qs(url.query).get("MaxWidth", ["320"])[0])
            except ValueError:
                self.send_error(400)
                return
            width = min((320, 640, 1920), key=lambda value: abs(value - requested))
            height = (180 if "/Backdrop/" in url.path else 480) if width == 320 else width * 9 // 16
            data = payloads[width, height, sum(url.path.encode()) % 8]
            time.sleep(.08)
            with lock:
                counts["requests"] += 1
                counts["bytes"] += len(data)
                counts[str(width)] += 1
                max_height = parse_qs(url.query).get("MaxHeight", [None])[0]
                if max_height is not None:
                    counts[f"maxHeight:{max_height}"] += 1
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "public, max-age=86400")
            self.end_headers()
            try:
                self.wfile.write(data)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def log_message(self, *args):
            pass

    ThreadingHTTPServer(("127.0.0.1", 18766), Handler).serve_forever()


if __name__ == "__main__":
    serve()
