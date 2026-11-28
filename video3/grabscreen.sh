#!/bin/bash
# usage: grabscreen.sh <name> <seconds> — records the Chrome page area of the user's screen
H="$(cd "$(dirname "$0")" && pwd)"
powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$H/front.ps1")" >/dev/null; sleep 0.8
ffmpeg -hide_banner -loglevel error -y -f gdigrab -draw_mouse 0 -framerate 30 -offset_x 0 -offset_y 226 -video_size 1880x800 -i desktop -t "$2" \
  -c:v libx264 -preset ultrafast -crf 16 -pix_fmt yuv420p "$H/raw/$1.mp4" && echo "recorded $1"
