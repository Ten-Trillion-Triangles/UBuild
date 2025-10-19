#!/bin/bash

# Get the directory of this bash file
SCRIPT_DIR=$(dirname "$0")

# Append a path to a java jar to it
JAR_PATH="$SCRIPT_DIR/server/build/libs/server-all.jar"

# Run the java jar with support for passing arguments
java -jar "$JAR_PATH" "$@"
