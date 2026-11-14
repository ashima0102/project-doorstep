#!/usr/bin/env bash
# Replays 1,000 applications + 50 redeliveries + 10 conflicts + 10 malformed messages through Kafka
# and prints the service's counters. Start from a clean state first:
#   docker compose down -v && docker compose up -d   # then start the service (see README)
# Usage: scripts/replay.sh [bootstrap=localhost:9092] [baseUrl=http://localhost:8080]
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q compile exec:java \
  -Dexec.mainClass=com.azentio.doorstep.generator.ReplayCli \
  -Dexec.args="${1:-localhost:9092} ${2:-http://localhost:8080}"
