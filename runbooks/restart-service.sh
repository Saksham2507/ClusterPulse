#!/usr/bin/env bash
# Runbook: restart-service
# Purpose : Restart a service on the affected node to recover it.
# Usage   : restart-service.sh <instance>   e.g. restart-service.sh node2:9100
#
# Detects the available service manager. Uses systemd on real nodes, falls back
# to docker in the demo, and logs intent if neither is available.
set -euo pipefail

INSTANCE="${1:-unknown}"
NODE="${INSTANCE%%:*}"
SERVICE="${CLUSTERPULSE_SERVICE:-prometheus-node-exporter}"
TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "{\"ts\":\"$TS\",\"runbook\":\"restart-service\",\"node\":\"$NODE\",\"service\":\"$SERVICE\",\"action\":\"restart\"}"

if command -v systemctl >/dev/null 2>&1; then
  if systemctl restart "$SERVICE" 2>/dev/null; then
    echo "Restarted '$SERVICE' via systemd on $NODE"
  else
    echo "systemctl restart '$SERVICE' failed (service missing?)"
  fi
elif command -v docker >/dev/null 2>&1; then
  docker restart "$NODE" >/dev/null 2>&1 && echo "Restarted container '$NODE'" || echo "docker restart skipped"
else
  echo "No service manager available -- logged remediation intent only (demo mode)"
fi
