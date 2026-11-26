#!/bin/bash
# usage: grab.sh <id>  — waits for the newest new Adam clip from today in Downloads and moves it to voice/<id>.mp3
D=/c/Users/admin/Downloads; V="$(dirname "$0")/voice"; mkdir -p "$V"
for i in $(seq 1 30); do
  f=$(ls -t "$D"/ElevenLabs_2026-11-26T*Adam*.mp3 2>/dev/null | head -1)
  if [ -n "$f" ]; then mv "$f" "$V/$1.mp3"; printf "%s %ss\n" "$1" "$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$V/$1.mp3")"; exit 0; fi
  sleep 1
done
echo "no new clip for $1"; exit 1
