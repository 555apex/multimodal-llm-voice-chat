#!/usr/bin/env bash
set -euo pipefail
umask 077
out=${1:?Usage: bash 01_collect_target.sh OUTPUT_DIRECTORY}
mkdir -p -- "$out"
report="$out/target-inventory.txt"
{
 date -u '+captured_utc=%FT%TZ'
 for cmd in 'uname -a' 'cat /etc/os-release' 'lscpu' 'free -h' \
   'lsblk -o NAME,TYPE,SIZE,FSTYPE,MOUNTPOINTS,MODEL,SERIAL' 'findmnt' \
   'df -hT' 'ip -br address' 'ip route' 'timedatectl status' \
   'docker version' 'docker compose version' 'docker info' \
   'npu-smi info' 'npu-smi info -l'; do
   printf '\n$ %s\n' "$cmd"
   bash -c "$cmd" 2>&1 || true
 done
 for f in /usr/local/Ascend/driver/version.info /etc/ascend_install.info; do
   if [ -r "$f" ]; then printf '\n%s\n' "$f"; cat "$f"; fi
 done
 find /usr/local/Ascend -maxdepth 4 -name version.cfg -print 2>/dev/null || true
} > "$report"
printf 'Saved %s; review OS, architecture, 8 NPUs, RAID/NVMe and software compatibility.\n' "$report"
