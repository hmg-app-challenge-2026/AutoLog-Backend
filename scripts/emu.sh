#!/usr/bin/env bash
# Pleos Connect 에뮬레이터에서 AutoLog를 손으로 시험하는 도우미.
# 가상 콘솔(에뮬레이터 안 8088)은 "브라우저 세션"이 살아 있을 때만 값을 받으므로, 명령마다 세션 신호를 함께 보낸다.
#
#   scripts/emu.sh setup            포트 연결 + 권한(위치·알림·차속) 부여 + 앱 실행
#   scripts/emu.sh gear Drive       기어: Park | Reverse | Neutral | Drive
#   scripts/emu.sh speed 40         차속(km/h). 움직임 판단에만 쓰인다
#   scripts/emu.sh odo 23.4         계기판 누적거리(km). 거리는 이 값의 차이로만 계산된다(줄일 수 없음)
#   scripts/emu.sh state            콘솔의 현재 기어·차속·계기판
#   scripts/emu.sh log              AutoLog 로그 보기 (Ctrl+C로 끝)
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG="kr.autolog.vehicle"
CONSOLE="http://localhost:8088"

heartbeat() {
  "$ADB" forward tcp:8088 tcp:8088 >/dev/null
  curl -s -m 2 -X POST "$CONSOLE/browser/session?client=autolog-emu-sh" >/dev/null
}

put() {
  heartbeat
  curl -s -m 3 -X PUT "$CONSOLE$1" >/dev/null && echo "ok: $1"
}

case "${1:-}" in
  setup)
    "$ADB" forward tcp:8088 tcp:8088 >/dev/null
    # Pleos(AAOS)는 앱을 사용자 10으로 실행한다
    for p in pleos.car.permission.FUSED_LOCATION android.permission.POST_NOTIFICATIONS android.car.permission.CAR_SPEED; do
      "$ADB" shell pm grant --user 10 "$PKG" "$p" && echo "granted $p"
    done
    "$ADB" shell am start --user 10 -n "$PKG/.MainActivity" >/dev/null && echo "app started"
    ;;
  gear) put "/vehicle/gear?gear=${2:?Park|Reverse|Neutral|Drive}" ;;
  speed) put "/vehicle/controls?speed_kph=${2:?km/h}" ;;
  odo) put "/vehicle/odometer?traveled_distance_km=${2:?km}" ;;
  state)
    heartbeat
    curl -s -m 3 "$CONSOLE/vehicle/state" | python3 -c '
import json, sys
d = json.load(sys.stdin)
print("gear  :", (d["gear"].get("actual") or {}).get("value"))
print("speed :", d["controls"].get("speed_kph"), "km/h")
print("odo   :", d["odometer"].get("traveled_distance_km"), "km")'
    ;;
  log) "$ADB" logcat -s AutoLog/core AutoLog/session AutoLog/vehicle ;;
  *) sed -n '2,11p' "$0"; exit 1 ;;
esac
