#!/bin/zsh
# Saves what Yonder is running in the background (foreground services, alarms, jobs, recordings)
# from the USB-connected phone into fgs-log.txt. Double-click this file in Finder with the phone
# plugged in and unlocked. Private feed links are blanked out.
cd "$(dirname "$0")"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
PKG=com.mattsapps.yonderpod
LOG="$PWD/fgs-log.txt"
TMP="$(mktemp -d)"
if ! adb get-state >/dev/null 2>&1; then
  echo "No phone found. Plug it in, unlock it, accept the USB debugging prompt, then run this again."
  exit 1
fi
private() {
  sed -E 's#(patreon\.com|token=|auth=|sig=|key=)[^ "<]*#\1[private]#g'
}
copy_db() {
  for part in "" "-wal" "-shm"; do
    adb exec-out run-as $PKG cat "databases/$1$part" > "$TMP/$1$part" 2>/dev/null
    [ -s "$TMP/$1$part" ] || rm -f "$TMP/$1$part"
  done
}
echo "Collecting... this takes about half a minute."
{
  echo "==== Yonder background check, saved $(date) ===="
  echo "Phone: $(adb shell getprop ro.product.model) / Android $(adb shell getprop ro.build.version.release)"
  echo "Yonder: $(adb shell dumpsys package $PKG | grep -m1 versionName)"
  echo
  echo "==== Foreground services running now ===="
  adb shell dumpsys activity services $PKG
  echo
  echo "==== Foreground service time used (dataSync, specialUse) ===="
  adb shell dumpsys activity -a 2>/dev/null | grep -i -E "fgs|foreground" | grep -i -E "$PKG|dataSync|specialUse|time limit" | head -80
  echo
  echo "==== Yonder's alarms ===="
  adb shell dumpsys alarm | grep -B1 -A4 "$PKG" | head -200
  echo
  echo "==== Yonder's jobs (WorkManager) ===="
  adb shell dumpsys jobscheduler $PKG | grep -E "JOB #|Required constraints|Unsatisfied|Satisfied|Running|Pending|enqueue|tag|worker|Worker" | head -150
  echo
  echo "==== Recording schedules (recordings.db) ===="
  copy_db recordings.db
  if [ -f "$TMP/recordings.db" ]; then
    sqlite3 -header -column "$TMP/recordings.db" "SELECT id, station, name, zone, weekdays, start_minute, end_minute, feed, method, status, last_saved, last_missed FROM shows;"
    echo "-- waiting (held-back) recordings --"
    sqlite3 -header -column "$TMP/recordings.db" "SELECT id, show, file, airing_start, airing_end FROM pending;"
  else
    echo "Could not read recordings.db (not a debug build, or no recordings yet)."
  fi
  echo
  echo "==== Recorded shows in the podcast database (state 0 = subscribed, 1 = not subscribed, 2 = archived) ===="
  copy_db Antennapod.db
  if [ -f "$TMP/Antennapod.db" ]; then
    sqlite3 -header -column "$TMP/Antennapod.db" "SELECT f.id, f.title, f.custom_title, f.state, (SELECT COUNT(*) FROM FeedItems i WHERE i.feed = f.id) AS episodes FROM Feeds f WHERE f.download_url LIKE '%yonderpod-recordings/%';"
    echo "-- radio stations --"
    sqlite3 -header -column "$TMP/Antennapod.db" "SELECT i.id, i.title FROM FeedItems i JOIN Feeds f ON i.feed = f.id WHERE f.download_url LIKE '%yonderpod-radio%';"
  else
    echo "Could not read the podcast database."
  fi
  echo
  echo "==== Recording settings (preferences) ===="
  adb exec-out run-as $PKG cat shared_prefs/YonderRecordings.xml 2>/dev/null
  echo
  adb exec-out run-as $PKG ls shared_prefs 2>/dev/null
  echo
  echo "==== Recent log: recordings, downloads, updates and foreground services ===="
  adb logcat -d -v time | grep -E "Recordings|RecordingS|YonderAutoDownload|UpdateScheduler|FeedUpdateWorker|EpisodeDownloadWorker|SystemFg|WM-|ForegroundService|startForeground|$PKG" | grep -v "Kumiho" | tail -500
  echo
  echo "==== Crashes since the phone last restarted ===="
  adb logcat -d -b crash -v time
} 2>&1 | private > "$LOG"
rm -rf "$TMP"
echo "Saved fgs-log.txt in $PWD. You can close this window."
