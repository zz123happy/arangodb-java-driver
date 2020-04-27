#!/bin/bash

# exit when any command fails
set -e

while :; do
  ./debug/bin/testApi.sh $1
done
