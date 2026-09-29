"""Desktop launcher: starts the embedded FastAPI server and opens a native window.

Used directly (`python desktop.py`) or bundled into a standalone .exe with PyInstaller.
"""

import sys
import threading
import time

import webview

PORT = 8090


def run_server() -> None:
    import uvicorn

    uvicorn.run("app.main:app", host="127.0.0.1", port=PORT, log_level="warning")


def _wait_until_ready(timeout: float = 10.0) -> bool:
    import socket

    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", PORT), timeout=0.5):
                return True
        except OSError:
            time.sleep(0.2)
    return False


def main() -> None:
    thread = threading.Thread(target=run_server, daemon=True)
    thread.start()
    ready = _wait_until_ready()
    if not ready:
        print("Server failed to start.")
        sys.exit(1)
    webview.create_window(
        "Flashcard & Quiz Generator",
        f"http://127.0.0.1:{PORT}/app?platform=desktop",
        width=1280,
        height=840,
        min_size=(920, 620),
    )
    webview.start()


if __name__ == "__main__":
    main()
