#!/usr/bin/env bash
# Records the README GIFs (docs/media/*.gif) from a connected phone over adb.
#
# Usage: scripts/record-gifs.sh [reading|paused|context|light ...]   (default: all)
#
# Each scene starts from a fresh install state: app data is cleared, the book is opened through
# the system picker, and the scene is scripted with adb input. The phone's dark mode and Do Not
# Disturb are restored afterwards. Tuned on a Pixel 8 (1080x2400, gesture navigation).
#
# Needs: adb (from $ANDROID_HOME or PATH), ffmpeg, curl. Turn off Bedtime mode / greyscale first.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
PACKAGE=io.github.nanopenguin.lesto
# Standard Ebooks edition: its front matter gives the page some headings to scroll past.
BOOK_URL="https://standardebooks.org/ebooks/lewis-carroll/alices-adventures-in-wonderland/john-tenniel/downloads/lewis-carroll_alices-adventures-in-wonderland_john-tenniel.epub?source=download"
BOOK_CHAPTER="I: Down the Rabbit-Hole"
BOOK_NAME=lesto-demo-alice.epub
OUT_DIR=$ROOT/docs/media
WORK_DIR=${TMPDIR:-/tmp}/lesto-gifs
GIF_WIDTH=320
GIF_FPS=20

ADB=${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb
[[ -x $ADB ]] || ADB=adb
command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }
mkdir -p "$WORK_DIR"

adb() { "$ADB" "$@"; }
shell() { adb shell "$@"; }

# --- Device setup -------------------------------------------------------------------------------

check_device() {
    adb get-state >/dev/null 2>&1 || { echo "No device connected" >&2; exit 1; }
    # Bedtime mode's greyscale shows up as an active global saturation transform.
    if shell dumpsys color_display | grep -A1 "Global saturation" | grep -q "Activated: true"; then
        echo "The screen is in greyscale (Bedtime mode?). Turn it off and retry." >&2
        exit 1
    fi
}

ORIGINAL_NIGHT_MODE=""
ORIGINAL_ZEN_MODE=""

save_device_state() {
    ORIGINAL_NIGHT_MODE=$(shell cmd uimode night | awk '{print $3}')
    ORIGINAL_ZEN_MODE=$(shell settings get global zen_mode)
    trap restore_device_state EXIT
    shell cmd notification set_dnd priority >/dev/null # no heads-up notifications mid-take
    shell svc power stayon usb
}

restore_device_state() {
    shell cmd uimode night "$ORIGINAL_NIGHT_MODE" >/dev/null || true
    [[ $ORIGINAL_ZEN_MODE == 0 ]] && shell cmd notification set_dnd off >/dev/null || true
    shell svc power stayon false || true
    shell rm -f "/sdcard/Download/$BOOK_NAME" /sdcard/lesto-ui.xml || true
}

install_app() {
    (cd "$ROOT" && ./gradlew -q assembleDebug)
    adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
}

push_book() {
    [[ -f $WORK_DIR/alice.epub ]] || curl -sSLf -o "$WORK_DIR/alice.epub" "$BOOK_URL"
    adb push "$WORK_DIR/alice.epub" "/sdcard/Download/$BOOK_NAME" >/dev/null
    shell content call --method scan_volume --uri content://media --arg external_primary >/dev/null
}

# --- UI helpers ---------------------------------------------------------------------------------

# Prints the center "x y" of the first on-screen node whose text is exactly $1.
find_node() {
    shell uiautomator dump /sdcard/lesto-ui.xml >/dev/null 2>&1 || return 1
    shell cat /sdcard/lesto-ui.xml |
        tr '>' '\n' |
        grep -F "text=\"$1\"" |
        head -1 |
        sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
        awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Prints the center of the node labelled $1, waiting up to ten seconds for it to appear.
wait_for_text() {
    local point
    for _ in $(seq 20); do
        point=$(find_node "$1")
        if [[ -n $point ]]; then
            echo "$point"
            return
        fi
        sleep 0.5
    done
    echo "Could not find \"$1\" on screen" >&2
    exit 1
}

tap_text() {
    local point
    point=$(wait_for_text "$1")
    shell input tap $point
}

# Taps the middle of the page, which plays or pauses.
tap_page() { shell input tap 540 1000; }

# Drags from ($1,$2) to ($3,$4) over $5 milliseconds.
drag() { shell input swipe "$1" "$2" "$3" "$4" "$5"; }

open_book() {
    shell pm clear "$PACKAGE" >/dev/null
    shell cmd uimode night "$1" >/dev/null
    shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    tap_text "Open book"
    tap_text "$BOOK_NAME"
    tap_text "Titlepage" # the chapter picker
    tap_text "$BOOK_CHAPTER"
    wait_for_text "$BOOK_CHAPTER" >/dev/null
    sleep 1.5 # the sheet closes and the page settles
}

# --- Recording ----------------------------------------------------------------------------------

RECORD_PID=""

start_recording() {
    shell rm -f /sdcard/lesto-take.mp4
    adb shell screenrecord --bit-rate 20000000 /sdcard/lesto-take.mp4 &
    RECORD_PID=$!
    sleep 1
}

# Stops the recording and converts it to docs/media/$1.gif.
finish_recording() {
    sleep 0.5
    shell pkill -INT screenrecord || true
    wait "$RECORD_PID" || true
    sleep 1
    adb pull /sdcard/lesto-take.mp4 "$WORK_DIR/$1.mp4" >/dev/null
    shell rm -f /sdcard/lesto-take.mp4

    # Crop the status bar, trim the recorder's start-up, and use one shared palette. 64 colours keep
    # the GIFs small; fewer can drop the accent colour, which has few pixels, so the GIF looks grey.
    local status_bar_height
    status_bar_height=$(shell dumpsys window | grep -m1 -oE 'type=statusBars frame=\[0,0\]\[[0-9]+,[0-9]+\]' | grep -oE '[0-9]+\]$' | tr -d ']')
    local filters="crop=iw:ih-$status_bar_height:0:$status_bar_height,fps=$GIF_FPS,scale=$GIF_WIDTH:-2:flags=lanczos"
    ffmpeg -v error -y -ss 0.8 -i "$WORK_DIR/$1.mp4" \
        -vf "$filters,split[a][b];[a]palettegen=max_colors=64:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
        "$OUT_DIR/$1.gif"
    echo "Wrote docs/media/$1.gif ($(du -h "$OUT_DIR/$1.gif" | cut -f1))"
}

# --- Scenes -------------------------------------------------------------------------------------

# Paused page, then playing word by word.
scene_reading() {
    open_book yes
    start_recording
    sleep 1.5
    tap_page
    sleep 8
    finish_recording reading
}

# Dragging the paused page around: along the line, down the page, back up to the headings, and home.
scene_paused() {
    open_book yes
    start_recording
    sleep 1
    drag 540 1400 540 1250 500    # onto the first line of text
    sleep 0.8
    drag 850 1400 300 1400 1200   # along the line
    sleep 0.8
    drag 540 1600 540 1150 1000   # down to later lines
    sleep 0.8
    drag 300 1400 850 1400 1000   # back along the line
    sleep 0.8
    drag 540 700 540 1500 1200    # up past the headings
    sleep 0.8
    drag 540 1500 540 1050 1000   # back to the first line
    sleep 1.5
    finish_recording paused
}

# Turning on context words, then playing.
scene_context() {
    open_book yes
    tap_page # read past the heading first
    sleep 8
    tap_page
    sleep 1
    start_recording
    sleep 1
    tap_text "Context"
    sleep 1
    tap_page
    sleep 7
    finish_recording context
}

# The same in light mode: play, pause, drag.
scene_light() {
    open_book no
    start_recording
    sleep 1
    tap_page
    sleep 6
    tap_page
    sleep 1
    drag 850 1400 300 1400 1200   # along the line
    sleep 0.8
    drag 540 1250 540 1450 800    # back a line
    sleep 1.5
    finish_recording light
}

# --- Main ---------------------------------------------------------------------------------------

[[ ${BASH_SOURCE[0]} == "$0" ]] || return 0 # sourced, for trying out single steps

scenes=("$@")
[[ ${#scenes[@]} -gt 0 ]] || scenes=(reading paused context light)
for scene in "${scenes[@]}"; do
    declare -F "scene_$scene" >/dev/null || { echo "Unknown scene: $scene" >&2; exit 1; }
done

check_device
save_device_state
install_app
push_book
for scene in "${scenes[@]}"; do
    echo "Recording $scene..."
    "scene_$scene"
done
shell am force-stop "$PACKAGE"
