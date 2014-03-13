#!/bin/sh
. ../../env.sh
export JARS="${JARS}:squares.jar"

export JVM_OPTS="${JVM_OPTS} -Dcom.amd.aparapi.useAgent=true"
export JVM_OPTS="${JVM_OPTS} -Dcom.amd.aparapi.executionMode=${1}"
export JVM_OPTS="${JVM_OPTS} -Dcom.amd.aparapi.enableVerboseJNI=false"

echo java ${JVM_OPTS} -classpath ${JARS} com.amd.aparapi.sample.squares.HSASquares