#!/bin/bash

# Get the directory of this bash file
SCRIPT_DIR=$(dirname "$0")

# Append a path to a java jar to it
JAR_PATH="$SCRIPT_DIR/server/build/libs/server-all.jar"

mkdir $JAR_PATH $SCRIPT_DIR/Ubuild2/server/build/libs/
cp -R $JAR_PATH $SCRIPT_DIR/Ubuild2/server/build/libs/
cp $SCRIPT_DIR/ubuild.sh $SCRIPT_DIR/Ubuild2
