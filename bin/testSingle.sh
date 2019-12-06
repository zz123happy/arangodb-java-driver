#!/bin/bash

# exit when any command fails
set -e

echo "==================================================="
echo "=== $1 "
echo "==================================================="

docker pull "$1"
logfile=out-$(date +%s%N).txt
mvn clean test -e -Dtest.docker.image="$1" -Darango.license.key="$ARANGO_LICENSE_KEY" >"$logfile" 2>&1
rm "$logfile"
