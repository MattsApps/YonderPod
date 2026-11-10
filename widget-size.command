#!/bin/zsh
# Saves the sizes the launcher reports for Yonder's home-screen widgets to widget-size.txt.
# Plug the phone in, unlock it, make sure the 4x2 (and 4x1, if you have one) is on the home screen, then double-click this file.
cd "$(dirname "$0")"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
OUT="$PWD/widget-size.txt"
if ! adb get-state >/dev/null 2>&1; then
  echo "No phone found. Plug it in, unlock it, accept the USB debugging prompt, then run this again."
  exit 1
fi
{
  echo "==== Yonder widget sizes, saved $(date) ===="
  echo "Phone: $(adb shell getprop ro.product.model) / Android $(adb shell getprop ro.build.version.release)"
  echo "Density: $(adb shell wm density)"
  echo "Screen: $(adb shell wm size)"
  echo "Launcher: $(adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1)"
  echo
  adb shell dumpsys appwidget | grep -iE -B2 -A20 "yonderpod"
} > "$OUT" 2>&1
echo "Saved widget-size.txt in $PWD. You can close this window."
