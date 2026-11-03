#!/bin/zsh
# Builds the signed Google Play bundle (.aab) for Yonder.
# The upload key and its password never go in this folder: the key lives in ~/Keys/yonder
# and the password is typed in each time.
cd "$(dirname "$0")"
LOG="$PWD/release-log.txt"
exec > >(tee "$LOG") 2>&1

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export JAVA_OPTS="--enable-native-access=ALL-UNNAMED"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

KEYSTORE="${YONDER_UPLOAD_KEYSTORE:-$HOME/Keys/yonder/yonder-upload.jks}"
if [ ! -f "$KEYSTORE" ]; then
  echo "STOP: no upload key at $KEYSTORE"
  echo "Make it first (see 'Handover: Beta-ready' in the plan)."
  exit 1
fi
case "$(cd "$(dirname "$KEYSTORE")" && pwd)/" in
  "$PWD"/*)
    echo "STOP: the upload key is inside the project folder. Move it to ~/Keys/yonder first."
    exit 1
    ;;
esac

printf "Upload key password (not shown): "
read -s UPLOAD_PASSWORD
echo
export YONDER_UPLOAD_KEYSTORE="$KEYSTORE"
export YONDER_UPLOAD_ALIAS="upload"
export YONDER_UPLOAD_PASSWORD="$UPLOAD_PASSWORD"
unset UPLOAD_PASSWORD

./gradlew --console=plain clean :app:bundlePlayRelease
RESULT=$?
unset YONDER_UPLOAD_PASSWORD

AAB="app/build/outputs/bundle/playRelease/app-play-release.aab"
if [ $RESULT -eq 0 ] && [ -f "$AAB" ]; then
  VERSION=$(grep -m1 'versionName' app/build.gradle | sed 's/.*"\(.*\)".*/\1/')
  OUT="$HOME/Desktop/Yonder releases"
  mkdir -p "$OUT"
  cp "$AAB" "$OUT/yonder-$VERSION.aab"
  echo "Saved: $OUT/yonder-$VERSION.aab"
  echo "Upload that file in Play Console (Testing > Internal testing > Create new release)."
else
  echo "STOP: the build failed. Send release-log.txt to the coding chat."
fi
echo "== DONE (exit $RESULT) =="
