#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.
set -e
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MVN_VERSION=3.9.11
MVN_HOME="$BASE_DIR/.mvn/apache-maven-$MVN_VERSION"
ARCHIVE="$BASE_DIR/.mvn/apache-maven-$MVN_VERSION-bin.zip"
if [ ! -x "$MVN_HOME/bin/mvn" ]; then
  mkdir -p "$BASE_DIR/.mvn"
  command -v curl >/dev/null 2>&1 || { echo "curl é necessário para instalar o Maven Wrapper" >&2; exit 1; }
  [ -f "$ARCHIVE" ] || curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MVN_VERSION/apache-maven-$MVN_VERSION-bin.zip" -o "$ARCHIVE"
  (cd "$BASE_DIR/.mvn" && unzip -q -o "$ARCHIVE")
fi
exec "$MVN_HOME/bin/mvn" -Dmaven.repo.local="$BASE_DIR/.mvn/repository" "$@"
