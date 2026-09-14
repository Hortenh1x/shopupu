#!/usr/bin/env bash
# CI counterpart of the desktop guard (/tmp/shopupu-limited-run.sh) with the same contract:
# a shared flock, at least 6 GiB MemAvailable to launch, one transient scope capped at
# 2 GiB RAM / zero swap / two CPUs, and the scope is stopped when MemAvailable falls
# under 4 GiB. Hosted runners have no user systemd manager, so the scope is created by the
# system manager through sudo and the command drops straight back to the invoking user.
set -u
exec 9>/tmp/shopupu-heavy-check.lock
flock 9
if ! awk '/MemAvailable:/ { available=$2 } END { exit (available < 6291456) }' /proc/meminfo; then
  echo 'Check postponed: less than 6 GiB of memory available.' >&2
  exit 75
fi
unit="shopupu-check-$$-$(date +%s)"
sudo systemd-run --scope --quiet --unit="$unit" -p MemoryMax=2G -p MemorySwapMax=0 -p CPUQuota=200% \
  sudo -u "$(id -un)" -H env "PATH=$PATH" "HOME=$HOME" "LANG=${LANG:-C.UTF-8}" "JAVA_HOME=${JAVA_HOME:-}" "$@" &
runner=$!
cleanup() { sudo systemctl stop "$unit.scope" >/dev/null 2>&1 || true; }
trap cleanup INT TERM
while kill -0 "$runner" 2>/dev/null; do
  if ! awk '/MemAvailable:/ { available=$2 } END { exit (available < 4194304) }' /proc/meminfo; then
    echo 'Check stopped: less than 4 GiB available on the runner.' >&2
    cleanup
    wait "$runner" || true
    exit 75
  fi
  sleep 2
done
wait "$runner"
