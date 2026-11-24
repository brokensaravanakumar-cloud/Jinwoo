#!/data/data/com.termux/files/usr/bin/bash
# Run ONCE in Termux (from F-Droid / GitHub build of Termux). Installs Ubuntu inside Termux (proot-distro).
set -e
pkg update -y && pkg upgrade -y
pkg install -y proot-distro nano unzip
proot-distro install ubuntu || echo "(ubuntu already installed)"
echo
echo "Next: bash backend/termux/2_install_backend.sh"
