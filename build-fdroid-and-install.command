#!/bin/zsh
# Builds the F-Droid flavour (debug), checks it has no Google billing, and installs it on the USB-connected phone.
# The F-Droid and Play builds share the app ID, so this REPLACES the Play debug build on the phone
# (Yonder's data is kept). BeyondPod is a different app and isn't touched.
# To go back to the Play build afterwards, run build-and-install.command.
# Double-click this file in Finder, or run it from Terminal.
cd "$(dirname "$0")"
./build-fdroid.command
sleep 1
if ! grep -q "OK: no billing" fdroid-log.txt; then
  echo "STOP: not installing (see fdroid-log.txt)."; exit 1
fi
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
if ! adb get-state >/dev/null 2>&1; then
  echo "STOP: no phone found. Plug it in, unlock it, accept the USB debugging prompt, then run this again."; exit 1
fi
echo "== Installing the F-Droid build =="
adb install -r app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
RESULT=$?
if [ $RESULT -eq 0 ]; then
  adb shell monkey -p com.mattsapps.yonderpod 1 >/dev/null 2>&1
  echo "== Podcast apps on phone =="; adb shell pm list packages | grep -i -E "beyondpod|antennapod|yonderpod"
fi
echo "== DONE (exit $RESULT) =="
