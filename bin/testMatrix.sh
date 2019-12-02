#!/bin/bash

# exit when any command fails
set -e

run_tests() {
  echo "==================================================="
  echo "=== $1 "
  echo "==================================================="
  mvn clean test -e -Dtest.docker.image="$1" -Darango.license.key="$ARANGO_LICENSE_KEY"
}

for img in \
  docker.io/arangodb/arangodb:3.4.8 \
  docker.io/arangodb/enterprise:3.4.8 \
  docker.io/arangodb/arangodb:3.5.3 \
  docker.io/arangodb/enterprise:3.5.3 \
  docker.io/arangodb/arangodb-test:devel-nightly \
  docker.io/arangodb/enterprise-test:devel-nightly; do
  run_tests $img
done
