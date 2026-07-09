#!/usr/bin/env bash
set -e

export DISPLAY=:99

# Virtual X display
Xvfb :99 -screen 0 "${GEOMETRY:-1280x800x24}" -nolisten tcp &
for i in $(seq 1 30); do
  if xdpyinfo -display :99 >/dev/null 2>&1; then break; fi
  sleep 0.3
done

# Lightweight window manager
fluxbox >/dev/null 2>&1 &

# VNC server on the virtual display.
# Hardening: if VNC_PASSWORD is set, require it; otherwise (dev) allow no auth.
if [ -n "${VNC_PASSWORD:-}" ]; then
  x11vnc -storepasswd "${VNC_PASSWORD}" /tmp/.vncpass >/dev/null 2>&1
  x11vnc -display :99 -forever -shared -rfbauth /tmp/.vncpass -rfbport 5900 -bg -quiet
  echo "x11vnc: password auth ENABLED"
else
  x11vnc -display :99 -forever -shared -nopw -rfbport 5900 -bg -quiet
  echo "x11vnc: NO auth (dev mode)"
fi

# noVNC web bridge on :6080. If CERT_FILE is set, serve over TLS (wss).
WS_ARGS=(--web=/usr/share/novnc 6080 localhost:5900)
if [ -n "${CERT_FILE:-}" ]; then
  WS_ARGS=(--cert="${CERT_FILE}" --ssl-only "${WS_ARGS[@]}")
  echo "websockify: TLS ENABLED"
fi
websockify "${WS_ARGS[@]}" &

echo "noVNC available at http://<host>:6080/vnc.html"

mkdir -p "${STATE_DIR:-/app/state}"
exec java -jar /app/sd-runner-app.jar
