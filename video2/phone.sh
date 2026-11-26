#!/bin/bash
# Records one Android take while driving the real Conuco app over adb.  usage: phone.sh <take>
export MSYS_NO_PATHCONV=1
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"; D="-s R5CWC3TLZEF"
OUT="$(dirname "$0")/raw"; mkdir -p "$OUT"
bnd() { "$A" $D shell uiautomator dump /sdcard/u.xml >/dev/null; "$A" $D shell cat /sdcard/u.xml | tr '>' '\n' | grep "$1" | grep -o 'bounds="[^"]*"' | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2)" "int(($2+$4)/2)}'; }
tap() { local p; p=$(bnd "$1" | ${2:-head} -1); [ -n "$p" ] && "$A" $D shell input tap $p; }
swipe() { "$A" $D shell input swipe 720 $1 720 $2 ${3:-900}; }
open() { "$A" $D shell am start -a android.intent.action.VIEW -d "conuco://tab/$1" -n com.petiht.conuco/.MainActivity >/dev/null 2>&1; }
rec_start() { "$A" $D shell "screenrecord --bit-rate 20000000 --time-limit 170 /sdcard/take.mp4" & REC=$!; sleep 1.5; }
rec_stop() { "$A" $D shell pkill -INT screenrecord; wait $REC 2>/dev/null; sleep 1.5; "$A" $D pull /sdcard/take.mp4 "$OUT/$1.mp4" >/dev/null && echo "saved $1 $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/$1.mp4")s"; }

case "$1" in
  today)
    open today; sleep 3; swipe 900 2200 600; sleep 1
    rec_start; sleep 3.5; swipe 2200 1300 2500; sleep 2.5; swipe 2000 1000 2500; sleep 2.5; swipe 1100 2400 2500; sleep 2; rec_stop today ;;
  cloud)
    open doctor; sleep 3; swipe 900 2200 500; sleep 1
    rec_start; sleep 2
    tap 'text="Banana"' tail; sleep 2.5
    for i in 1 2 3 4 5; do dg=$(bnd 'text="Diagnose"' | tail -1); dy=$(echo "$dg" | awk '{print $2}'); [ -n "$dy" ] && [ "$dy" -lt 2500 ] && break; swipe 2200 1500 1200; sleep 0.8; done
    sleep 1; "$A" $D shell input tap $dg; sleep 3.5
    tap 'text="Yes, send it"'; sleep 9
    for i in 1 2 3 4 5 6; do y=$(bnd 'text="Cloud · with consent' | awk '{print $2}'); [ -n "$y" ] && break; swipe 1400 2100 400; sleep 0.6; done
    swipe 1600 $((1600 - y + 420)) 1400; sleep 3.5
    swipe 2200 1500 1600; sleep 1.5
    tap 'text="Prune\|text="Cut\|text="Remove' ; sleep 2.5
    for i in 1 2 3 4 5; do s=$(bnd 'text="Share"'); [ -n "$s" ] && break; swipe 2200 1500 1200; sleep 0.8; done
    swipe 2000 1600 1200; sleep 1.5
    tap 'text="Share"'; sleep 3.5; "$A" $D shell input keyevent 4; sleep 1.5
    tap 'text="Warn neighbours"'; sleep 3
    rec_stop cloud ;;
  act)
    # result actions: diagnose first (not recorded), then record the step tap, share sheet and the neighbour warning
    open doctor; sleep 3; swipe 900 2200 500; sleep 1
    tap 'text="Banana"' tail; sleep 2
    for i in 1 2 3 4 5; do dg=$(bnd 'text="Diagnose"' | tail -1); dy=$(echo "$dg" | awk '{print $2}'); [ -n "$dy" ] && [ "$dy" -lt 2500 ] && break; swipe 2200 1500 1200; sleep 0.8; done
    "$A" $D shell input tap $dg; sleep 3; tap 'text="Yes, send it"'; sleep 10
    for i in 1 2 3 4 5 6 7; do st=$(bnd 'text="DO NOW"' | head -1); sy=$(echo "$st" | awk '{print $2}'); [ -n "$sy" ] && [ "$sy" -lt 1900 ] && break; swipe 2100 1500 900; sleep 0.8; done
    swipe 1500 $((1500 - sy + 500)) 1200; sleep 1.5
    rec_start; sleep 2.5
    p=$(bnd 'text="DO NOW"' | head -1); py=$(echo "$p" | awk '{print $2}'); "$A" $D shell input tap 720 $((py + 190)); sleep 2.2
    "$A" $D shell input tap 720 $((py + 470)); sleep 2.5
    for i in 1 2 3 4 5 6; do s=$(bnd 'text="Share"' | head -1); sy2=$(echo "$s" | awk '{print $2}'); [ -n "$sy2" ] && [ "$sy2" -lt 2300 ] && break; swipe 2100 1400 1300; sleep 1; done
    sleep 1.5; "$A" $D shell input tap $s; sleep 4
    "$A" $D shell input keyevent 4; sleep 2
    w=$(bnd 'text="Warn neighbours"' | head -1); [ -n "$w" ] && "$A" $D shell input tap $w; sleep 3.5
    rec_stop act ;;
  map)
    open map; sleep 6
    rec_start; sleep 2; "$A" $D shell input tap 720 1680; "$A" $D shell input tap 720 1680; sleep 4; swipe 2400 1700 2500; sleep 3; rec_stop map ;;
  farm)
    open farm; sleep 3; swipe 900 2200 500; sleep 1
    rec_start; sleep 3; swipe 2200 1300 2500; sleep 3; rec_stop farm ;;
  market)
    open market; sleep 3; swipe 900 2200 500; sleep 1
    rec_start; sleep 1.5; tap 'text="Banana"'; sleep 3; swipe 2000 1400 2500; sleep 3; rec_stop market ;;
  camera)
    open today; sleep 2
    rec_start; sleep 2; open camera; sleep 7; "$A" $D shell input keyevent 4; sleep 1.5; rec_stop camera ;;
esac
