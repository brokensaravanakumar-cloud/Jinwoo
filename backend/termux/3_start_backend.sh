#!/data/data/com.termux/files/usr/bin/bash
# Starts the backend on http://127.0.0.1:8000 (reachable by the JIN WOO AI app on the same phone).
# Set HOST=0.0.0.0 to allow other devices on your Wi-Fi (then keep APP_ACCESS_TOKEN set!).
BACKEND="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${HOST:-127.0.0.1}"
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock   # keep the CPU awake while serving
echo "Starting JIN WOO backend on $HOST:8000  (Ctrl+C to stop)"
exec proot-distro login ubuntu --bind "$BACKEND:/root/backend" -- bash -lc \
  ". /root/venv/bin/activate && cd /root/backend && uvicorn app.main:app --host $HOST --port 8000"
