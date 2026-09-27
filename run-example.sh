#!/usr/bin/env sh
set -eu
BUILD_DIR="${TMPDIR:-/tmp}/release-report-mailer-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/reportdelivery/*.java
java -cp "$BUILD_DIR" reportdelivery.ReleaseReportApplication "$@"
