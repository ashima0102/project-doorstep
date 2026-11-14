#!/usr/bin/env bash
# Generates synthetic applications and posts them to the running service.
# Usage: scripts/generate.sh [count=500] [baseUrl=http://localhost:8080]
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q compile exec:java \
  -Dexec.mainClass=com.azentio.doorstep.generator.GeneratorCli \
  -Dexec.args="${1:-500} ${2:-http://localhost:8080}"
