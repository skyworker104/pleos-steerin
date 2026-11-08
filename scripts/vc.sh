#!/usr/bin/env bash
# Drive the Pleos Connect Virtual Console (emulator v2.1.0+) from the command line.
# The console only accepts writes while a browser session heartbeat is alive, so this script
# keeps one running for the duration of each command.
#
# Usage:
#   scripts/vc.sh forward                  # adb forward tcp:8088 (run once after the AVD boots)
#   scripts/vc.sh state                    # gear + motion snapshot
#   scripts/vc.sh gear R|P|N|D
#   scripts/vc.sh set steering=460 speed=3 brake=0   # steering(deg) speed(km/h, INTEGER only) brake(%) accelerator(%)
#   scripts/vc.sh trunk open|close
#   scripts/vc.sh model                    # scripted textbook reverse park (same keyframes as the in-app simulator)
#   scripts/vc.sh novice                   # scripted hesitant park with a retry and a harsh stop
set -euo pipefail
VC="${VC_URL:-http://127.0.0.1:8088}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

hb_start() {
  CID=$(uuidgen)
  ( while true; do curl -s -X POST "$VC/browser/session?client=$CID" >/dev/null || true; sleep 1; done ) &
  HB=$!
  trap 'kill $HB 2>/dev/null || true' EXIT
  sleep 1.5
}
put() { curl -s -X PUT "$VC$1" >/dev/null; }
gear() { case "$1" in P|p) g=Park;; R|r) g=Reverse;; N|n) g=Neutral;; D|d) g=Drive;; *) g=$1;; esac; put "/vehicle/gear?gear=$g"; }
ctl() { put "/vehicle/controls?$(IFS='&'; echo "$*")"; }

# frames: "sec steering speed brake gear"
play() {
  hb_start
  local last_gear=""
  while read -r t steer speed brake g; do
    [ -z "$t" ] && continue
    if [ "$g" != "$last_gear" ]; then gear "$g"; last_gear=$g; fi
    ctl "steering=$steer" "speed=$speed" "brake=$brake" "accelerator=0"
    sleep "$t"
  done
}

case "${1:-}" in
  forward) "$ADB" forward tcp:8088 tcp:8088; echo "open $VC/" ;;
  state) curl -s "$VC/vehicle/state" | python3 -c 'import sys,json;d=json.load(sys.stdin);print("gear:",d["gear"].get("actual"));print("controls:",{k:d["controls"][k] for k in ("speed_kph","brake_pct","steering_deg","emulator_active")});print("trunk:",d["body"]["closures"]["trunk"])' ;;
  gear) hb_start; gear "$2" ;;
  set) hb_start; shift; ctl "$@" ;;
  trunk) hb_start; v=false; [ "$2" = open ] && v=true; put "/vehicle/body?trunk=$v" ;;
  model) play <<'EOF'
1.0 0 0 100 D
2.0 0 0 100 R
0.7 150 0 100 R
0.7 300 0 100 R
0.6 460 0 100 R
1.0 460 1 0 R
1.0 460 2 0 R
4.1 460 3 0 R
0.7 380 3 0 R
0.7 300 3 0 R
0.7 220 3 0 R
0.7 140 2 0 R
0.7 60 2 0 R
0.5 0 2 0 R
5.0 0 2 0 R
1.0 0 1 20 R
1.0 0 0 100 R
1.0 0 0 100 P
EOF
  ;;
  novice) play <<'EOF'
1.0 0 0 100 D
1.0 0 0 100 R
1.0 200 6 0 R
2.0 450 8 0 R
2.0 150 7 0 R
2.0 420 6 0 R
0.5 420 0 100 R
1.5 -300 0 100 D
3.0 -300 4 0 D
1.0 -300 0 100 D
1.0 380 0 100 R
7.0 380 3 0 R
4.0 -80 3 0 R
4.0 40 2 0 R
10.0 0 2 0 R
1.0 0 0 100 R
1.0 0 0 100 P
EOF
  ;;
  *) sed -n '2,13p' "$0"; exit 1 ;;
esac
