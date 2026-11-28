#!/bin/bash
# usage: grab.sh <id> — moves the newest NEW Adam clip (newer than .marker) from Downloads to voice/<id>.mp3
H="$(cd "$(dirname "$0")" && pwd)"; D=/c/Users/admin/Downloads; V="$H/voice"; M="$H/.marker"; [ -f "$M" ] || touch "$M"
for i in $(seq 1 40); do
  f=$(find "$D" -maxdepth 1 -name 'ElevenLabs_*Adam*.mp3' -newer "$M" -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-)
  if [ -n "$f" ]; then mv "$f" "$V/$1.mp3"; touch "$M"; printf "%s %ss\n" "$1" "$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$V/$1.mp3")"; exit 0; fi
  sleep 1
done
echo "no new clip for $1"; exit 1
