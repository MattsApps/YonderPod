#!/bin/zsh
# Records what happens when the home-screen widget's play button is pressed, and saves it to widget-log.txt.
# Plug the phone in and unlock it, double-click this file, then follow the steps it shows.
cd "$(dirname "$0")"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
LOG="$PWD/widget-log.txt"
if ! adb get-state >/dev/null 2>&1; then
  echo "No phone found. Plug it in, unlock it, accept the USB debugging prompt, then run this again."
  exit 1
fi
adb logcat -c
echo
echo "Recording now. On the phone:"
echo "  1. Do whatever makes the widget's play button stop working (for example play a station, pause it, start a podcast, pause it)."
echo "  2. Press the widget's play button 3 times, a few seconds apart, right on the play mark."
echo "  3. Then press it 3 times on the top half of the circle, above the play mark."
echo
echo "When you've done that, come back here and press Return."
read
{
  echo "==== Yonder widget log, saved $(date) ===="
  echo
  echo "==== Widgets on the home screen (sizes in dp) ===="
  adb shell dumpsys appwidget | grep -iE -A12 "yonderpod" | head -80
  echo
  echo "==== Media sessions ===="
  adb shell dumpsys media_session | head -150
  echo
  echo "==== Yonder's services ===="
  adb shell dumpsys activity services com.mattsapps.yonderpod | head -120
  echo
  echo "==== Log: Yonder, media buttons, services, crashes ===="
  adb logcat -d -v time | grep -iE "yonderpod|M3PlaybackService|MSessionService|MediaSession|MediaButton|media_button|ForegroundService|AndroidRuntime|PlaybackPendingIntent|WidgetUpdater|ActivityManager.*yonder" | tail -800
} > "$LOG" 2>&1
echo "Saved widget-log.txt in $PWD. You can close this window."
