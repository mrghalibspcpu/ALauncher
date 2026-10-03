#!/usr/bin/env bash
# A Launcher — gradle-free build: aapt2 -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLCHAIN="${TOOLCHAIN:-$HOME/.toolchain}"
export JAVA_HOME="${JAVA_HOME:-$TOOLCHAIN/jdk-17.0.20.1+1}"
SDK="${ANDROID_SDK:-$TOOLCHAIN/sdk}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
PATH="$JAVA_HOME/bin:$PATH"

APP="$ROOT/app"
OUT="$ROOT/.build"
DIST="$ROOT/dist"
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$DIST"

echo "==> compiling resources"
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"

echo "==> linking resources"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 1 --version-name 1.0 \
  --auto-add-overlay \
  "$OUT/res.zip"

echo "==> compiling java"
find "$APP/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$PLATFORM:$BT/core-lambda-stubs.jar" -classpath "$PLATFORM" \
  -d "$OUT/classes" @"$OUT/sources.txt"

if [ -z "$(find "$OUT/classes" -name '*.class' -print -quit)" ]; then
  echo "javac produced no classes"; exit 1
fi

echo "==> dexing"
"$BT/d8" --release --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

echo "==> packaging"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes*.dex)

echo "==> aligning"
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS="$ROOT/.keystore/alauncher.jks"
if [ ! -f "$KS" ]; then
  echo "==> creating signing key"
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -v -keystore "$KS" -storepass alauncher -keypass alauncher \
    -alias alauncher -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=A Launcher, OU=A, O=A Launcher, L=., S=., C=PK" >/dev/null
fi

echo "==> signing"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:alauncher --key-pass pass:alauncher \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$DIST/ALauncher.apk" "$OUT/aligned.apk"

"$BT/apksigner" verify --print-certs "$DIST/ALauncher.apk" | head -3
ls -lh "$DIST/ALauncher.apk"
echo "==> done: dist/ALauncher.apk"
