#!/usr/bin/env bash
# Build 极米助手 / Z9X Manager without Gradle. Needs:
#   ANDROID_SDK_ROOT with platforms;android-28 and build-tools;34.0.0
#   JDK 11 javac (JDK 21 classfiles break older d8)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/workspace/android-sdk}}"
JDK11="${JDK11:-/workspace/jdk-11.0.25+9}"
BT="${SDK}/build-tools/34.0.0"
AJ="${SDK}/platforms/android-28/android.jar"
OUT="${ROOT}/build"
KS="${ROOT}/z9x-manager.keystore"
JAVAC="${JDK11}/bin/javac"
JAR="${JDK11}/bin/jar"
VERSION="1.0.0"
APK_NAME="Z9XManager-v${VERSION}.apk"

[[ -x "$JAVAC" ]] || { echo "Need JDK11 at $JDK11"; exit 1; }
[[ -f "$AJ" ]] || { echo "Need $AJ"; exit 1; }
[[ -x "$BT/d8" ]] || { echo "Need build-tools 34.0.0"; exit 1; }

if [[ ! -f "$KS" ]]; then
  keytool -genkeypair -keystore "$KS" -alias z9xmanager \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass z9xmanager -keypass z9xmanager \
    -dname "CN=Z9X Manager Local, O=Local, C=CN"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/dex" "$OUT/apk" "$OUT/classes"

"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/apk/base.apk" -I "$AJ" \
  --manifest "$ROOT/AndroidManifest.xml" --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 28 \
  "$OUT/res.zip" --auto-add-overlay

find "$ROOT/src" "$OUT/gen" -name '*.java' > "$OUT/sources.list"
"$JAVAC" -source 1.8 -target 1.8 -Xlint:-options \
  -bootclasspath "$AJ" -classpath "$AJ" \
  -d "$OUT/classes" @"$OUT/sources.list"

(cd "$OUT/classes" && "$JAR" cf "$OUT/classes.jar" .)
"$BT/d8" --lib "$AJ" --min-api 26 --output "$OUT/dex" "$OUT/classes.jar"

cp "$OUT/apk/base.apk" "$OUT/apk/withdex.apk"
(cd "$OUT/dex" && zip -q -u "$OUT/apk/withdex.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/apk/withdex.apk" "$OUT/apk/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:z9xmanager --key-pass pass:z9xmanager \
  --ks-key-alias z9xmanager --out "$OUT/apk/${APK_NAME}" "$OUT/apk/aligned.apk"
"$BT/apksigner" verify "$OUT/apk/${APK_NAME}"
sha256sum "$OUT/apk/${APK_NAME}"
echo "OK: $OUT/apk/${APK_NAME}"
