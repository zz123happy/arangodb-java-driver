#!/bin/bash

for img in \
  docker.io/arangodb/arangodb:3.4.8 \
  docker.io/arangodb/enterprise:3.4.8 \
  docker.io/arangodb/arangodb:3.5.3 \
  docker.io/arangodb/enterprise:3.5.3 \
  docker.io/arangodb/arangodb-preview:3.6.0-rc.1 \
  docker.io/arangodb/enterprise-preview:3.6.0-rc.1 \
  docker.io/arangodb/enterprise-preview:3.6.0-rc.1-ubi; do
  ./bin/testSingle.sh $img
done

echo "***************"
echo "*** SUCCESS ***"
echo "***************"
