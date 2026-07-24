#!/usr/bin/env bash
# Provision a 3-node KVM/QEMU cluster via libvirt + cloud-init.
#
# This is the "real" virtualized path behind the resume line. The Docker
# Compose demo simulates the loop; this actually spins up VMs on a Linux host.
#
# Requirements (Linux host):
#   sudo apt install qemu-kvm libvirt-daemon-system virtinst cloud-image-utils
#   An Ubuntu cloud image at $BASE_IMG (download from cloud-images.ubuntu.com).
#
# Usage: sudo ./provision-cluster.sh
set -euo pipefail

BASE_IMG="${BASE_IMG:-/var/lib/libvirt/images/ubuntu-22.04-server-cloudimg-amd64.img}"
POOL_DIR="/var/lib/libvirt/images"
NODES=(node1 node2 node3)
RAM=2048
VCPUS=2
DISK=10G

if [ ! -f "$BASE_IMG" ]; then
  echo "Base cloud image not found at $BASE_IMG"
  echo "Download one, e.g.:"
  echo "  wget https://cloud-images.ubuntu.com/jammy/current/jammy-server-cloudimg-amd64.img -O $BASE_IMG"
  exit 1
fi

for n in "${NODES[@]}"; do
  echo "==> Creating VM: $n"

  # Backing-file overlay disk so we don't duplicate the whole base image.
  qemu-img create -f qcow2 -F qcow2 -b "$BASE_IMG" "$POOL_DIR/$n.qcow2" "$DISK"

  # cloud-init seed ISO (installs node_exporter on first boot).
  cloud-localds "$POOL_DIR/$n-seed.iso" cloud-init/user-data.yml \
    <(printf 'instance-id: %s\nlocal-hostname: %s\n' "$n" "$n")

  virt-install \
    --name "$n" \
    --ram "$RAM" \
    --vcpus "$VCPUS" \
    --disk path="$POOL_DIR/$n.qcow2",format=qcow2 \
    --disk path="$POOL_DIR/$n-seed.iso",device=cdrom \
    --os-variant ubuntu22.04 \
    --virt-type kvm \
    --graphics none \
    --network network=default \
    --import \
    --noautoconsole
done

echo
echo "Cluster provisioned. Find node IPs with:"
echo "  virsh net-dhcp-leases default"
echo
echo "Then add 'IP:9100' targets to prometheus/prometheus.yml (job: node) and"
echo "point Prometheus/Alertmanager/Grafana/daemon at those nodes."
