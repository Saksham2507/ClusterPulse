#!/usr/bin/env bash
# Fault injection: generate CPU load to trigger the HighCPU alert.
# Run this ON a monitored Linux host or KVM node (not inside node-exporter).
# Usage: ./stress-cpu.sh [duration_seconds]
set -euo pipefail

DURATION="${1:-120}"
CORES="$(nproc)"
echo "Generating CPU load on $CORES cores for ${DURATION}s (HighCPU fires after ~30s > 85%)..."

if command -v stress-ng >/dev/null 2>&1; then
  stress-ng --cpu "$CORES" --timeout "${DURATION}s"
else
  echo "stress-ng not found; using bash busy-loops instead"
  for _ in $(seq 1 "$CORES"); do
    timeout "${DURATION}s" bash -c 'while :; do :; done' &
  done
  wait
fi
echo "CPU stress finished."
