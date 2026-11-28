#!/bin/bash
# Records one Android take of Rosa's day while driving the real Conuco app over adb.
# Every tap is logged to raw/<take>.taps as "<seconds since recording start> <x> <y>" (full-res 1440x3120 coords)
# so the edit can draw a tap ripple and play a click sound at the right moment.   usage: phone.sh <take>
export MSYS_NO_PATHCONV=1
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"; D="-s R5CWC3TLZEF"
H="$(cd "$(dirname "$0")" && pwd)"; OUT="$H/raw"; mkdir -p "$OUT"
now() { date +%s.%N; }
bnd() { "$A" $D shell uiautomator dump /sdcard/u.xml >/dev/null; "$A" $D shell cat /sdcard/u.xml | tr '>' '\n' | grep "$1" | grep -o 'bounds="[^"]*"' | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2)" "int(($2+$4)/2)}'; }
taplog() { [ -n "$T0" ] && echo "$(awk -v a="$(now)" -v b="$T0" 'BEGIN{printf "%.2f", a-b}') $1 $2" >> "$OUT/$TAKE.taps"; }
tapxy() { taplog "$1" "$2"; "$A" $D shell input tap "$1" "$2"; }
tap() { local p; p=$(bnd "$1" | ${2:-head} -1); [ -n "$p" ] && tapxy $p; }
swipe() { "$A" $D shell input swipe 720 $1 720 $2 ${3:-900}; }
open() { "$A" $D shell cmd statusbar collapse >/dev/null 2>&1; "$A" $D shell am start -a android.intent.action.VIEW -d "conuco://tab/$1" -n com.petiht.conuco/.MainActivity >/dev/null 2>&1; }
rec_start() { TAKE=$1; rm -f "$OUT/$TAKE.taps"; "$A" $D shell "screenrecord --bit-rate 24000000 --time-limit 170 /sdcard/take.mp4" & REC=$!; sleep 1.2; T0=$(now); }
rec_stop() { sleep 0.5; "$A" $D shell pkill -INT screenrecord; wait $REC 2>/dev/null; sleep 1.5; "$A" $D pull /sdcard/take.mp4 "$(cygpath -w "$OUT/$TAKE.mp4")" >/dev/null && echo "saved $TAKE $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/$TAKE.mp4")s, $(wc -l < "$OUT/$TAKE.taps" 2>/dev/null || echo 0) taps"; T0=; }
wait_for() { for i in $(seq 1 ${2:-20}); do p=$(bnd "$1" | head -1); [ -n "$p" ] && return 0; sleep 1; done; return 1; }
scroll_until() { for i in $(seq 1 ${2:-6}); do p=$(bnd "$1" | head -1); y=$(echo "$p" | awk '{print $2}'); [ -n "$y" ] && [ "$y" -lt ${3:-2400} ] && return 0; swipe 2100 1400 700; sleep 0.8; done; return 1; }

case "$1" in
  alert)   # the three Conuco alerts in the notification shade, over the app; tap the outbreak alert
    open today; sleep 3; swipe 900 2300 500; sleep 1
    rec_start alert; sleep 2
    "$A" $D shell cmd statusbar expand-notifications; sleep 5
    n=$(bnd 'text="Outbreak near you"' | head -1); [ -n "$n" ] && tapxy $n; sleep 4
    rec_stop ;;
  today)
    open today; sleep 3; swipe 900 2300 500; sleep 1
    rec_start today; sleep 2.5; swipe 2100 1400 1800; sleep 2; swipe 2100 1500 1800; sleep 2; swipe 1300 2300 1500; sleep 1.5; rec_stop ;;
  doctor)
    open today; sleep 3
    rec_start doctor; sleep 1.5
    tapxy 720 2694; sleep 2
    tap 'text="Banana"' tail; sleep 2
    scroll_until 'text="Diagnose"' 6 2450; sleep 0.6; tap 'text="Diagnose"' tail; sleep 2.5
    tap 'text="Yes, send it"'; sleep 8
    for i in 1 2 3 4 5 6; do y=$(bnd 'text="Cloud · with consent' | awk '{print $2}'); [ -n "$y" ] && break; swipe 2100 1400 500; sleep 0.6; done
    [ -n "$y" ] && swipe 1600 $((1600 - y + 380)) 1400; sleep 3.5
    rec_stop ;;
  act)
    # needs a fresh result on screen: diagnose first (not recorded)
    open doctor; sleep 3; swipe 900 2200 500; sleep 1
    tap 'text="Banana"' tail; sleep 2
    scroll_until 'text="Diagnose"' 6 2450; tap 'text="Diagnose"' tail; sleep 2.5; tap 'text="Yes, send it"'; sleep 10
    for i in 1 2 3 4 5 6 7; do st=$(bnd 'text="DO NOW"' | head -1); sy=$(echo "$st" | awk '{print $2}'); [ -n "$sy" ] && [ "$sy" -lt 1900 ] && break; swipe 2100 1500 900; sleep 0.8; done
    swipe 1500 $((1500 - sy + 500)) 1200; sleep 1.5
    rec_start act; sleep 1.5
    p=$(bnd 'text="DO NOW"' | head -1); py=$(echo "$p" | awk '{print $2}'); tapxy 720 $((py + 190)); sleep 2.2
    scroll_until 'text="Warn neighbours"' 6 2300; sleep 1
    tap 'text="Warn neighbours"'; sleep 2.5
    tapxy 1006 2694; sleep 3.5
    "$A" $D shell input tap 720 1700; "$A" $D shell input tap 720 1700; sleep 3
    rec_stop ;;
  market)
    open market; sleep 3; swipe 900 2200 500; sleep 1
    rec_start market; sleep 1.2; tap 'text="Banana"'; sleep 2.5; swipe 2000 1500 1600; sleep 2.5; rec_stop ;;
  ask)
    open ask; sleep 3
    rec_start ask; sleep 1.5
    tap 'text="My plantain leaves have black streaks' ; sleep 1
    for i in $(seq 1 40); do c=$(bnd 'text="Yes, send it"' | head -1); [ -n "$c" ] && { sleep 1; tapxy $c; break; }; s=$(bnd 'text="Listen"\|content-desc="Listen"' | head -1); [ -n "$s" ] && break; sleep 1; done
    for i in $(seq 1 40); do s=$(bnd 'Listen' | head -1); [ -n "$s" ] && break; sleep 1; done
    sleep 1.5; swipe 2000 1500 1500; sleep 2.5
    rec_stop ;;
  farm)
    open farm; sleep 3; swipe 900 2200 500; sleep 1
    rec_start farm; sleep 1.5
    tap 'text="Dictate an entry"'; sleep 2.6
    powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$H/say.ps1")" "Sold three bunches of plantain for six hundred pesos." ; sleep 4
    scroll_until 'text="Sold"' 8 2300; sleep 0.8; tap 'text="Sold"'; sleep 1.5; scroll_until 'text="Save"' 5 2450; sleep 0.8; tap 'text="Save"' tail; sleep 2.5
    swipe 1200 2300 1200; sleep 2
    rec_stop ;;
  farm2)
    open farm; sleep 3; swipe 900 2200 500; sleep 1
    rec_start farm2; sleep 1.5; swipe 2100 1300 1800; sleep 2; swipe 2100 1400 1600; sleep 2; rec_stop ;;
  settings)
    open today; sleep 3
    rec_start settings; sleep 1; tapxy 1305 249; sleep 2; swipe 2300 700 1500; sleep 0.6; swipe 2300 900 1500; sleep 2.5; swipe 2200 1300 1500; sleep 2.5; rec_stop ;;
esac
