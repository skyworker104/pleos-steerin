#!/usr/bin/env bash
# Pleos Connect emulator helper (macOS, Apple Silicon).
# Mirrors the official guide:
#   https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
#
# Usage:
#   scripts/pleos_avd.sh install   # register update site, install system image (4.7 GB), create AVD
#   scripts/pleos_avd.sh start     # cold-boot the AVD (4 GB RAM, hardware GPU)
#   scripts/pleos_avd.sh deploy    # build debug APK, install, grant vehicle permissions, launch
#   scripts/pleos_avd.sh logs      # follow app + Pleos SDK logs
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME="$SDK"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"

AVD_NAME="Pleos_Connect_v2.1"
PKG="system-images;android-34;pleos_car_emulator;arm64-v8a"
SITE_URL="https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml"
APP_ID="com.steerin.parkingmaster.debug"
ACTIVITY="com.steerin.parkingmaster.presentation.MainActivity"
PERMS=(
  pleos.car.permission.READ_CAR_STEERING pleos.car.permission.CAR_DRIVE pleos.car.permission.CAR_DOORS
  pleos.car.permission.CAR_SAFETY pleos.car.permission.CAR_SPEED pleos.car.permission.CAR_INFO
  android.car.permission.CAR_SPEED android.car.permission.CAR_POWERTRAIN
)

cmd_install() {
  mkdir -p "$HOME/.android"
  cat > "$HOME/.android/repositories.cfg" <<EOF
### User Sources for Android SDK Manager
count=1
disp00=Pleos Connect System Image
src00=${SITE_URL//:/\\:}
enabled00=true
EOF
  echo ">> installing $PKG (accepting 42dot SDK license)"
  yes | "$SDKMANAGER" "$PKG"
  echo ">> creating AVD $AVD_NAME"
  echo no | "$AVDMANAGER" create avd --force --name "$AVD_NAME" --package "$PKG" --device "automotive_1024p_landscape" --abi arm64-v8a || \
  echo no | "$AVDMANAGER" create avd --force --name "$AVD_NAME" --package "$PKG" --abi arm64-v8a
  local ini="$HOME/.android/avd/$AVD_NAME.avd/config.ini"
  # Guide: RAM 4 GB, hardware graphics, cold boot
  sed -i '' '/^hw.ramSize=/d;/^hw.gpu.enabled=/d;/^hw.gpu.mode=/d;/^fastboot.forceColdBoot=/d;/^fastboot.forceFastBoot=/d' "$ini"
  printf 'hw.ramSize=4096\nhw.gpu.enabled=yes\nhw.gpu.mode=host\nfastboot.forceColdBoot=yes\nfastboot.forceFastBoot=no\n' >> "$ini"
  echo ">> done. AVD config: $ini"
}

cmd_start() {
  "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-load -no-boot-anim "$@" &
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
  echo ">> $AVD_NAME booted"
  "$ADB" forward tcp:8088 tcp:8088 && echo ">> Virtual Console: http://127.0.0.1:8088/"
}

cmd_deploy() {
  ( cd "$(dirname "$0")/.." && ./gradlew :app:assembleDebug -q )
  "$ADB" install -r "$(dirname "$0")/../app/build/outputs/apk/debug/app-debug.apk"
  for p in "${PERMS[@]}"; do "$ADB" shell pm grant "$APP_ID" "$p" >/dev/null 2>&1 || true; done
  "$ADB" shell am start -n "$APP_ID/$ACTIVITY"
}

cmd_logs() {
  "$ADB" logcat -v time SteerinApp:I PleosSignalSource:D AaosCarSource:D UxRestrictions:D VoiceCoach:D AndroidRuntime:E '*:S'
}

case "${1:-}" in
  install) cmd_install ;;
  start) shift; cmd_start "$@" ;;
  deploy) cmd_deploy ;;
  logs) cmd_logs ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
