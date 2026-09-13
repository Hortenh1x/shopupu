#!/usr/bin/env bash
set -euo pipefail
umask 077
: "${PGDATABASE:?Select the source PGDATABASE explicitly}"
: "${PGHOST:?Set PGHOST}"
: "${PGUSER:?Set the backup PGUSER}"
if [[ $# != 1 || -e "$1" ]]; then
  echo 'Usage: backup-db.sh NEW_ARCHIVE_PATH (destination must not exist)' >&2
  exit 2
fi
pg_dump --format=custom --no-owner --no-acl --file="$1"
pg_restore --list -- "$1" >/dev/null
sha256sum "$1" > "$1.sha256"
printf 'Archive created. It requires an isolated restore verification before it counts as a usable backup.\n'
