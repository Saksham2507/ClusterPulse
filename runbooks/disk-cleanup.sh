#!/usr/bin/env bash
# Runbook: disk-cleanup
# Purpose : Reclaim disk space on a node under disk pressure.
# Usage   : disk-cleanup.sh <instance>
#
# SAFE BY DEFAULT: truncation is a dry-run (prints what it *would* remove).
# Set CLUSTERPULSE_APPLY=1 to actually truncate old large log files.
set -euo pipefail

INSTANCE="${1:-unknown}"
NODE="${INSTANCE%%:*}"
TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
APPLY="${CLUSTERPULSE_APPLY:-0}"

echo "{\"ts\":\"$TS\",\"runbook\":\"disk-cleanup\",\"node\":\"$NODE\",\"action\":\"reclaim-disk\",\"apply\":$APPLY}"

# Only touch well-known, safe locations. Never a blanket rm.
for DIR in /var/log /tmp; do
  [ -d "$DIR" ] || continue
  while IFS= read -r f; do
    if [ "$APPLY" = "1" ]; then
      : > "$f" && echo "truncated $f"
    else
      echo "would-truncate $f"
    fi
  done < <(find "$DIR" -type f -name "*.log" -size +50M -mtime +1 2>/dev/null || true)
done

# Vacuum systemd journal if available
if command -v journalctl >/dev/null 2>&1; then
  journalctl --vacuum-time=2d >/dev/null 2>&1 || true
  echo "journal vacuumed to 2d"
fi

echo "disk-cleanup complete on $NODE (apply=$APPLY)"
