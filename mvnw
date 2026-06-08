#!/bin/sh
# Maven Wrapper startup script for POSIX (Linux / macOS / Git Bash on Windows)

set -e

# Locate the project base dir (the directory containing this script)
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-"$(dirname "$(realpath "$0")")"}"

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER="org.apache.maven.wrapper.MavenWrapperMain"
DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

# Download the wrapper jar if it is not present yet
if [ ! -f "$WRAPPER_JAR" ]; then
    if command -v curl > /dev/null 2>&1; then
        curl -fsSL -o "$WRAPPER_JAR" "$DOWNLOAD_URL"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "$WRAPPER_JAR" "$DOWNLOAD_URL"
    else
        echo "Neither curl nor wget is available. Cannot download Maven Wrapper." >&2
        exit 1
    fi
fi

# Resolve the java executable
if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
else
    JAVA_EXE="java"
fi

exec "$JAVA_EXE" \
    -classpath "$WRAPPER_JAR" \
    "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
    "$WRAPPER_LAUNCHER" \
    "$@"
