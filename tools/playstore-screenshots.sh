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
# ScreenshotActivity serves more scenes than the listing uses, for eyeballing
# a surface after changing it:
#
#   chat  chat_busy  list  split  config  projects  pairing  settings
#                            (the listing's own; split is the tablet shell)
#   error                    (a turn that ended on an error, with Try again)
#   mention                  (the @-mention menu over a project's files)
#   tools                    (tool rows: reads folded, a two-hunk diff, exit status, Needs approval)
#   tools_diff               (the folded reads and the edit open, as a unified diff)
#   todo                     (a seven-task list open above the composer while a turn runs)
#   permission | prompt_dock (two approvals docked above the composer, "1 of 2")
#   permission_edit          (an approval with a diff, docked above the composer)
#   question                 (two questions in the dock, one at a time)
#   permission_sheet  question_sheet   (the sheet a prompt naming no chat gets)
#   workflow  workflow_done  workflow_paused  workflow_continued
#   workflow_stopped  workflow_failed
#   orchestration            (two runs mixed into a conversation)
#   activation               (the "ultracode" and "+500k" highlight, and the budget line)
#   activation_askfirst      (the same under Ask first: muted, with the way out)
#   thinking  thinking_meteor  thinking_smoulder  thinking_paused  thinking_off
#   thinking_low  thinking_unset  thinking_inert  thinking_inert_off  thinking_live
#                            (the slider open over the chat; a meteor's
#                             instant via --es progress 0.35 [--es from 5],
#                             0 for Off, the smoulder's via --es idle 1.5)
#   thinking_chip_off        (the chat's chip reading "Thinking off")
#   new_session              (what "+" opens: greeting, "Working in", starter prompts)
#   new_chat                 (an empty chat: the greeting over starter prompts)
#   reconnecting | disconnected_reconnecting
#                            (a dropped link ridden out in place: the pill, the
#                             composer's reconnecting placeholder)
#   disconnected_<reason>[_repeated]
#                            (searching hostasleep sharingoff hostquit
#                             nolocalnetwork turnedaway versionmismatch revoked
#                             transport disconnected; _repeated after a run of
#                             refusals)
#   pairing_link  pairing_link_bad
#                            (a spettro-pair:// link opened while paired elsewhere,
#                             and one that isn't a pairing link)
#   providers  archived
#
# Every scene takes the phone's accent too: add `--es accent mono` for
# Monochrome (Lilac is the default).
#
# Drive one directly rather than through this script:
#
#   adb shell am start -n \
#       to.eyed.spettro.mobile.dev/to.eyed.spettro.mobile.screenshots.ScreenshotActivity \
#       --es scene workflow_paused --es theme dark --es form phone
#
set -euo pipefail

FORM="${1:-phone}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
case "$FORM" in
    phone)  SERIAL="${SERIAL:-emulator-5554}"; OUT="$ROOT/playstore/screenshots" ;;
    tablet) SERIAL="${SERIAL:-emulator-5556}"; OUT="$ROOT/playstore/screenshots-tablet" ;;
    *) echo "usage: $0 [phone|tablet]" >&2; exit 1 ;;
esac
# The debug build installs under its own application id so it can sit beside a
# Play install (see the debug block in app/build.gradle.kts). The class itself
# keeps the module namespace, so the component has to be spelled out in full —
# the ".screenshots.X" shorthand would resolve against the *suffixed* id and
# fail to start.
PKG=to.eyed.spettro.mobile.dev
ACT="$PKG/to.eyed.spettro.mobile.screenshots.ScreenshotActivity"
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
