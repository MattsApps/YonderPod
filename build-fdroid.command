#!/bin/zsh
# Builds the F-Droid flavour (debug) and checks it has no Google billing in it.
# It doesn't install anything: the F-Droid build has the same app ID as the Play build.
# Double-click this file in Finder, or run it from Terminal.
cd "$(dirname "$0")"
LOG="$PWD/fdroid-log.txt"
exec > >(tee "$LOG") 2>&1

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export JAVA_OPTS="--enable-native-access=ALL-UNNAMED"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "== Building the F-Droid flavour =="
./gradlew --console=plain :app:assembleFdroidDebug
RESULT=$?
APK="app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk"
if [ $RESULT -ne 0 ] || [ ! -f "$APK" ]; then
  echo "STOP: the build failed. Send fdroid-log.txt to the coding chat."
  echo "== DONE (exit $RESULT) =="; exit 1
fi

echo "== Checking for Google billing =="
AAPT=$(ls -d "$ANDROID_HOME"/build-tools/*/aapt 2>/dev/null | tail -1)
FOUND=0
if [ -n "$AAPT" ] && "$AAPT" dump permissions "$APK" | grep -q "com.android.vending.BILLING"; then
  echo "PROBLEM: the F-Droid build asks for the billing permission."
  FOUND=1
fi
if unzip -p "$APK" 'classes*.dex' | LC_ALL=C grep -a -q "com/android/billingclient"; then
  echo "PROBLEM: the F-Droid build contains Google billing code."
  FOUND=1
fi
if [ $FOUND -eq 0 ]; then
  echo "OK: no billing permission and no billing code in the F-Droid build."
fi
echo "== DONE (exit $FOUND) =="
