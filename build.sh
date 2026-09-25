#!/usr/bin/env bash
# Convenience wrapper: builds and tests the project with the portable toolchain in tools/
# Usage: ./build.sh [any maven args, e.g. "clean test" or "package -DskipTests"]
set -e
cd "$(dirname "$0")"
export JAVA_HOME="$(pwd)/tools/jdk-21.0.12.1+1"
exec ./tools/apache-maven-3.9.9/bin/mvn.cmd "$@"
