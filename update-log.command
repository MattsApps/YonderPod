#!/bin/zsh
# Records one Yonder update run from the USB-connected phone into update-log.txt.
# Double-click this file in Finder with the phone plugged in and unlocked, then follow the steps it shows.
cd "$(dirname "$0")"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
LOG="$PWD/update-log.txt"
if ! adb get-state >/dev/null 2>&1; then
  echo "No phone found. Plug it in, unlock it, accept the USB debugging prompt, then run this again."
  exit 1
fi
adb logcat -c
echo
echo "Recording. On the phone: open Yonder, swipe up to Podcasts Tend and tap \"Updated ... ↻\"."
echo "Wait until it has finished updating and the downloads have started (a minute or two),"
echo "then come back to this window and press Return."
read -r
{
  echo "==== Yonder update log, saved $(date) ===="
  echo "Charging: $(adb shell dumpsys battery | grep -E 'AC powered|USB powered|status')"
  echo "Network: $(adb shell dumpsys connectivity | grep -m1 -E 'Active default network|NetworkAgentInfo' )"
  echo
  echo "==== Yonder's download decisions ===="
  adb logcat -d -v time YonderAutoDownload:V UpdateScheduler:V FeedUpdateWorker:V EpisodeDownloadWorker:V \
    DownloadServiceInterface:V MediaDownloadedHandler:V M3PlaybackService:D '*:S'
  echo
  echo "==== Download jobs waiting in Android's job list ===="
  adb shell dumpsys jobscheduler com.mattsapps.yonderpod 2>/dev/null | grep -E "JOB #|Required constraints|Unsatisfied|EpisodeDownloadWorker" | head -60
  echo
  echo "==== Full log, last 600 lines ===="
  adb logcat -d -v time | tail -600
} > "$LOG" 2>&1
echo "Saved update-log.txt in $PWD. You can close this window."
