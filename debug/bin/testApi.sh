#!/bin/bash

# runs the tests against the docker image provided as argument
# logs are redirected to a generate logfile named like out-$(date +%s%N).txt
# if tests are successfull the logfile will be removed

# USAGE:
#   export ARANGO_LICENSE_KEY=<arangodb-enterprise-license>
#   ./testApi.sh <dockerImage>

# EXAMPLE:
#   ./testApi.sh docker.io/arangodb/arangodb:3.6.2

# exit when any command fails
set -e

echo "==================================================="
echo "=== $1 "
echo "==================================================="

logfile=out-$(date +%s%N).txt
mvn clean test -e -DfailIfNoTests=false -Dtest="com.arangodb.next.api.**" -Dtestcontainers.reuse.enable=true -Dtest.docker.image="$1" -Darango.license.key="$ARANGO_LICENSE_KEY" >"$logfile" 2>&1

# remove logfile if the test completed successfully
rm "$logfile"
