#!/usr/bin/env bash
set -euo pipefail
: "${RESTORE_DATABASE:?Select an existing empty isolated target database}"
: "${PGHOST:?Set PGHOST}"
: "${PGUSER:?Set the restore PGUSER}"
if [[ $# != 1 || ! -f "$1" ]]; then
  echo 'Usage: restore-empty-db.sh ARCHIVE_PATH' >&2
  exit 2
fi
if [[ ! "$RESTORE_DATABASE" =~ ^shopupu_restore_[A-Za-z0-9_]+$ || "$RESTORE_DATABASE" == "${PGDATABASE:-}" ]]; then
  echo 'Restore target must have a shopupu_restore_ prefix and differ from the source database.' >&2
  exit 2
fi
count=$(psql --dbname="$RESTORE_DATABASE" -X -A -t -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')")
if [[ "$count" != 0 ]]; then
  echo 'Target is not empty; refusing to replace existing data.' >&2
  exit 2
fi
pg_restore --exit-on-error --no-owner --no-acl --single-transaction --dbname="$RESTORE_DATABASE" -- "$1"
printf 'Restore completed; run the verification SQL and application checks against this isolated target.\n'
