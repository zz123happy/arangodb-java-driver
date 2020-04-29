#!/bin/bash

# execute the tests for every docker image in the matrix
# every failing test will generate a logfile named like out-$(date +%s%N).txt

#  docker.io/arangodb/arangodb:3.5.4 \
#  docker.io/arangodb/enterprise:3.5.4 \
#  docker.io/arangodb/arangodb:3.6.3 \
#  docker.io/arangodb/enterprise:3.6.3 \
#  docker.io/arangodb/arangodb-preview:3.7.0-alpha.2 \
#  docker.io/arangodb/enterprise-preview:3.7.0-alpha.2 \

for img in \
  docker.io/arangodb/enterprise:3.7.0 \
  docker.io/arangodb/enterprise:3.7.0.1; do
  ./bin/testSingle.sh $img
done

echo "***************"
echo "*** SUCCESS ***"
echo "***************"
