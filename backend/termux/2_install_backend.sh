#!/data/data/com.termux/files/usr/bin/bash
# Run ONCE in Termux from the JinWooAssistant folder. Installs Python + backend dependencies inside Ubuntu.
set -e
BACKEND="$(cd "$(dirname "$0")/.." && pwd)"
proot-distro login ubuntu --bind "$BACKEND:/root/backend" -- bash -lc '
  set -e
  apt-get update
  apt-get install -y python3 python3-venv python3-pip
  python3 -m venv /root/venv
  . /root/venv/bin/activate
  pip install --upgrade pip
  pip install -r /root/backend/requirements.txt
  echo "Backend dependencies installed."
'
if [ ! -f "$BACKEND/.env" ]; then
  cp "$BACKEND/.env.example" "$BACKEND/.env"
  echo ".env created. Edit it now:  nano $BACKEND/.env   (add GEMINI_API_KEY / OPENAI_API_KEY and APP_ACCESS_TOKEN)"
fi
echo "Next: bash backend/termux/3_start_backend.sh"
