#!/bin/bash

for img in \
  docker.io/arangodb/arangodb:3.4.8 \
  docker.io/arangodb/enterprise:3.4.8 \
  docker.io/arangodb/arangodb:3.5.3 \
  docker.io/arangodb/enterprise:3.5.3 \
  docker.io/arangodb/arangodb-test:devel-nightly \
  docker.io/arangodb/enterprise-test:devel-nightly; do
  ./bin/testSingle.sh $img
done

echo "***************"
echo "*** SUCCESS ***"
echo "***************"
