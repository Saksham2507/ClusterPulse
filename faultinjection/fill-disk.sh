#!/usr/bin/env bash
# Fault injection: allocate a large file to trigger the DiskPressure alert.
# Run this ON a monitored Linux host or KVM node.
# Usage: ./fill-disk.sh [target_path] [size_mb]
set -euo pipefail

TARGET="${1:-/tmp/clusterpulse-ballast.bin}"
SIZE_MB="${2:-500}"

echo "Allocating ${SIZE_MB}MB at '$TARGET' to simulate disk pressure..."
dd if=/dev/zero of="$TARGET" bs=1M count="$SIZE_MB" status=progress
echo
echo "Done. DiskPressure fires when usage crosses 85%."
echo "Clean up with:   rm -f $TARGET   (or let disk-cleanup.sh handle it)"
