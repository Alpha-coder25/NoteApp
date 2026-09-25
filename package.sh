#!/usr/bin/env bash
# =====================================================================
# NoteApp - Windows packaging via jpackage (uses the portable toolchain
# in tools/ when no system JDK is available).
#
# Result: target/dist/NoteApp/NoteApp.exe - a self-contained app image
# with a bundled Java runtime; users do NOT need Java installed.
# =====================================================================
set -e
cd "$(dirname "$0")"

# Prefer system JDK; fall back to the portable one in tools/
if [ -z "$JAVA_HOME" ]; then
  export JAVA_HOME="$(pwd)/tools/jdk-21.0.12.1+1"
fi

echo "Building fat jar..."
./build.sh clean package -DskipTests

echo "Creating Windows application image..."
"$JAVA_HOME/bin/jpackage" \
  --type app-image \
  --name NoteApp \
  --app-version 1.0.0 \
  --vendor "NoteApp Project" \
  --description "Offline note-taking desktop application" \
  --input target \
  --main-jar NoteApp.jar \
  --dest target/dist

echo
echo "Done. Executable: target/dist/NoteApp/NoteApp.exe"
