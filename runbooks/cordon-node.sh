#!/usr/bin/env bash
# Runbook: cordon-node
# Purpose : Mark an unhealthy node as cordoned so it stops receiving new work.
# Usage   : cordon-node.sh <instance>   e.g. cordon-node.sh node2:9100
#
# Idempotent and safe to run repeatedly. In the Docker demo it records state to
# a file; on a real cluster it also best-effort cordons via kubectl if present.
set -euo pipefail

INSTANCE="${1:-unknown}"
NODE="${INSTANCE%%:*}"          # strip :port -> node hostname
TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
STATE_DIR="${CLUSTERPULSE_STATE:-/tmp/clusterpulse}"
mkdir -p "$STATE_DIR"

echo "{\"ts\":\"$TS\",\"runbook\":\"cordon-node\",\"node\":\"$NODE\",\"action\":\"cordon\"}"

# Record cordon state (idempotent)
echo "$TS cordoned" > "$STATE_DIR/${NODE}.cordoned"

# Real-cluster hook (non-fatal in demo)
if command -v kubectl >/dev/null 2>&1; then
  kubectl cordon "$NODE" >/dev/null 2>&1 || echo "note: kubectl cordon skipped ($NODE not a kube node)"
fi

echo "Node '$NODE' marked cordoned. State: $STATE_DIR/${NODE}.cordoned"
