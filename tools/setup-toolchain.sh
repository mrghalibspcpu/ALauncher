#!/usr/bin/env bash
# Fetches JDK 17 + Android build-tools 34 + platform 34 into ~/.toolchain
# (mirror usable from restricted networks where dl.google.com is blocked).
set -euo pipefail
T="${TOOLCHAIN:-$HOME/.toolchain}"
[ -x "$T/sdk/build-tools/34.0.0/aapt2" ] && { echo "toolchain already present"; exit 0; }
TMP="$(mktemp -d)"
git clone --depth 1 https://github.com/whynusn/android-sdk-bundle.git "$TMP/b"
mkdir -p "$T/sdk/build-tools" "$T/sdk/platforms"
cat "$TMP/b"/OpenJDK17.tar.gz.part-0* > "$TMP/jdk.tgz"
tar xzf "$TMP/jdk.tgz" -C "$T"
unzip -q "$TMP/b/build-tools_r34-linux.zip" -d "$TMP/bt"
mv "$TMP/bt/android-14" "$T/sdk/build-tools/34.0.0"
unzip -q "$TMP/b/platform-34_r03.zip" -d "$T/sdk/platforms"
rm -rf "$TMP"
echo "toolchain ready at $T"
