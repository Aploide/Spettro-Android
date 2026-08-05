#!/usr/bin/env bash
#
# Capture the Play Store screenshots, phone or tablet.
#
# Drives the debug-only ScreenshotActivity (app/src/debug/…/screenshots/) scene
# by scene and pulls a full-resolution PNG for each. Output lands in
# playstore/screenshots/ (phone) or playstore/screenshots-tablet/ (tablet) as
# <NN>-<scene>-<theme>.png.
#
# Phone captures must come from a 1080x1920 (9:16 exactly) device so the PNGs
# satisfy Play's "longest side at most twice the shortest" rule straight out
# of the emulator. Tablet captures come from the landscape Pixel Tablet AVD
# (2560x1600, 16:10 — also within the 2:1 rule). Boot either with
# ./tools/start.sh [phone|tablet].
#
#   ./tools/playstore-screenshots.sh                 # phone, emulator-5554
#   ./tools/playstore-screenshots.sh tablet          # tablet, emulator-5556
#   SERIAL=emulator-5558 ./tools/playstore-screenshots.sh tablet
#   SKIP_INSTALL=1 ./tools/playstore-screenshots.sh  # reuse the installed APK
#
set -euo pipefail

FORM="${1:-phone}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
case "$FORM" in
    phone)  SERIAL="${SERIAL:-emulator-5554}"; OUT="$ROOT/playstore/screenshots" ;;
    tablet) SERIAL="${SERIAL:-emulator-5556}"; OUT="$ROOT/playstore/screenshots-tablet" ;;
    *) echo "usage: $0 [phone|tablet]" >&2; exit 1 ;;
esac
PKG=to.eyed.spettro.mobile
ACT="$PKG/.screenshots.ScreenshotActivity"
SETTLE="${SETTLE:-3}"

adb() { command adb -s "$SERIAL" "$@"; }
demo() { adb shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }

# scene:theme pairs, in listing order — the first eight are the intended set.
SCENES=(
    chat:dark
    list:dark
    config:dark
    permission:dark
    chat_busy:dark
    question:dark
    pairing:dark
    settings:dark
    chat:light
    list:light
    config:light
    permission:light
    chat_busy:light
    question:light
    pairing:light
    settings:light
    projects:dark
    projects:light
)

mkdir -p "$OUT"

if [[ "${SKIP_INSTALL:-0}" != "1" ]]; then
    echo "==> building debug APK"
    (cd "$ROOT" && ./gradlew --quiet :app:assembleDebug)
    echo "==> installing on $SERIAL"
    adb install -r -t "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
fi

echo "==> device setup"
adb shell settings put global sysui_demo_allowed 1
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
# A clean, deliberate status bar: fixed clock, full battery, full signal, no
# notification icons — the same trick the Android team's own listings use.
demo -e command enter
demo -e command clock -e hhmm 0941
demo -e command battery -e level 100 -e plugged false
demo -e command network -e wifi show -e level 4
demo -e command network -e mobile show -e datatype none -e level 4
demo -e command notifications -e visible false
demo -e command status -e volume hide -e bluetooth hide -e alarm hide -e location hide

last_theme=""
i=0
for entry in "${SCENES[@]}"; do
    scene="${entry%%:*}"
    theme="${entry##*:}"
    i=$((i + 1))
    name="$(printf '%02d' "$i")-${scene}-${theme}.png"

    # System bar icon contrast follows the device's night mode, not the theme
    # we force inside the app — keep the two in step.
    if [[ "$theme" != "$last_theme" ]]; then
        adb shell cmd uimode night "$([[ "$theme" == dark ]] && echo yes || echo no)" >/dev/null
        last_theme="$theme"
        sleep 2
    fi

    adb shell am force-stop "$PKG"
    adb shell am start -n "$ACT" --es scene "$scene" --es theme "$theme" --es form "$FORM" >/dev/null
    sleep "$SETTLE"
    adb exec-out screencap -p > "$OUT/$name"
    echo "    $name  ($(identify -format '%wx%h' "$OUT/$name" 2>/dev/null || echo '?'))"
done

adb shell am force-stop "$PKG"
demo -e command exit
echo "==> $i screenshots in $OUT"
