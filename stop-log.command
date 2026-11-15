#!/bin/zsh
# Saves everything about an unexpected stop to stop-log.txt (and the whole phone log to stop-log-full.txt).
# Run it as soon as possible after the stop, before playing anything, with the phone plugged in.
cd "$(dirname "$0")"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
LOG="$PWD/stop-log.txt"
FULL="$PWD/stop-log-full.txt"
P=com.mattsapps.yonderpod
if ! adb get-state >/dev/null 2>&1; then
  echo "No phone found. Plug it in, accept the USB debugging prompt if it asks, then run this again."
  exit 1
fi
adb logcat -d -v time > "$FULL" 2>&1
{
  echo "==== Yonder stop log, saved $(date) ===="
  adb shell dumpsys package $P | grep -E "versionName|versionCode" | head -2
  echo
  echo "==== Why playback stopped (reason: 1 you/a button, 2 another app took the sound, 3 headphones/Bluetooth disconnected, 4 remote, 5 end of item, 6 suppressed) ===="
  grep -E "Audio focus: playWhenReady|BecomingNoisy|headsetDisconnected|MediaButton|Media3MediaButtonRecv|DuckingFocus|SleepTimer|ClockSleepTimer" "$FULL" | tail -60
  echo
  echo "==== Yonder's player, episode ends and live pause ===="
  grep -E "M3PlaybackService|EpisodeEnd|NextUpRule|LiveBuffer|PlaybackService|ExoPlayer|AndroidRuntime" "$FULL" | tail -300
  echo
  echo "==== Episode end log on the phone ===="
  adb exec-out run-as $P cat no_backup/episode-end-log.txt 2>&1 | tail -80
  echo
  echo "==== Did Android kill Yonder? (newest first) ===="
  adb shell dumpsys activity exit-info $P 2>&1 | head -80
  echo
  echo "==== Crashes kept by Android ===="
  adb logcat -d -b crash -v time 2>&1 | grep -A30 $P | tail -80
  echo
  echo "==== Process deaths and kills (events log) ===="
  adb logcat -d -b events -v time 2>&1 | grep -E "am_(proc_died|kill|anr|crash|low_memory).*yonderpod" | tail -30
  echo
  echo "==== Bluetooth and audio routing events ===="
  adb shell dumpsys audio 2>&1 | sed -n '/Audio event log/,/^$/p' | tail -80
  adb shell dumpsys audio 2>&1 | grep -iE "a2dp|bluetooth|becoming_noisy|setWiredDeviceConnectionState|setBluetoothA2dp|onSetA2dp|device.*(dis)?connect" | tail -80
  echo
  echo "==== Audio focus history ===="
  adb shell dumpsys audio 2>&1 | sed -n '/Audio Focus stack entries/,/^$/p'
  adb shell dumpsys audio 2>&1 | grep -iE "requestAudioFocus|abandonAudioFocus|focus loss|dispatching" | tail -60
  echo
  echo "==== Media sessions now ===="
  adb shell dumpsys media_session 2>&1 | head -120
  echo
  echo "==== Player service now ===="
  adb shell dumpsys activity services $P/de.danoeh.antennapod.playback.service.Media3PlaybackService 2>&1 | grep -E "ServiceRecord|isForeground|foregroundServiceType|startRequested|restartCount|createTime|lastActivity"
} > "$LOG" 2>&1
echo "Saved stop-log.txt and stop-log-full.txt. You can close this window."
