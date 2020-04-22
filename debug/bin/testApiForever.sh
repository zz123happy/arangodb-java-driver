#!/bin/bash

# keeps the tests running forever
# every failing test will generate a logfile named like out-$(date +%s%N).txt

while :; do
  ./debug/bin/testApi.sh $1
done
