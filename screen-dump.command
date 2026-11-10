#!/bin/zsh
# Saves a text picture of whatever Yonder screen is open on the USB-connected phone to screen-dump.txt.
# Open the screen on the phone first (for example Tune -> advanced), then double-click this file in Finder.
cd "$(dirname "$0")"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
OUT="$PWD/screen-dump.txt"
if ! adb get-state >/dev/null 2>&1; then
  echo "No phone found. Plug it in, unlock it, accept the USB debugging prompt, then run this again."
  exit 1
fi
adb shell uiautomator dump /sdcard/yonder-screen.xml >/dev/null 2>&1
{
  echo "==== Yonder screen, saved $(date) ===="
  adb shell cat /sdcard/yonder-screen.xml | perl -pe 's/></>\n</g'
} > "$OUT" 2>&1
adb shell rm /sdcard/yonder-screen.xml >/dev/null 2>&1
echo "Saved screen-dump.txt in $PWD. You can close this window."
