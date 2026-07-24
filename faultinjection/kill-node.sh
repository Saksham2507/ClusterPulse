#!/usr/bin/env bash
# Fault injection: take a node offline to trigger the NodeDown alert.
# This is the primary, always-works demo (no tools needed inside the node).
# Usage: ./kill-node.sh [node2]
set -euo pipefail

NODE="${1:-node2}"
cd "$(dirname "$0")/.."

echo "Stopping '$NODE' to simulate a node failure..."
docker compose stop "$NODE"
echo
echo "Now watch:"
echo "  1. Prometheus -> Alerts:   http://localhost:9090/alerts   (NodeDown fires in ~15-25s)"
echo "  2. Daemon logs:            docker compose logs -f remediation-daemon"
echo "  3. Grafana dashboard:      http://localhost:3000"
echo
echo "Restore the node with:   docker compose start $NODE"
