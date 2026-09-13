#!/usr/bin/env python3
"""Restore drill restricted to the container and files created by check-full-stack.sh."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import time

container_id, fixture_path, uploads_path, artifacts_path = sys.argv[1:]
assert re.fullmatch(r"[a-f0-9]{64}", container_id), "An exact created container ID is required"
fixture = json.loads(Path(fixture_path).read_text())
artifacts = Path(artifacts_path)
uploads = Path(uploads_path)
label = subprocess.check_output(["docker", "inspect", "--format", '{{ index .Config.Labels "com.shopupu.acceptance.run" }}', container_id], text=True).strip()
assert label == fixture["runNonce"], "Refusing a container not owned by this acceptance run"
started = time.monotonic()


def run_in_db(database, sql):
    return subprocess.check_output(["docker", "exec", "-i", container_id, "psql", "-X", "-qAt", "-U", "acceptance", "-d", database, "-v", "ON_ERROR_STOP=1"], input=sql, text=True, timeout=30)


# Canonical row digests + counts, across every public table, including orders/payments/audit/stock.
# MD5 here detects accidental restore differences; it is not an adversarial authenticity claim.
fingerprint_sql = r"""
select format('select %L, count(*), md5(coalesce(string_agg(to_jsonb(t)::text, chr(10) order by to_jsonb(t)::text), %L)) from public.%I t', tablename, '', tablename)
from pg_tables where schemaname = 'public' order by tablename
\gexec
"""
try:
    before = run_in_db("shopupu", fingerprint_sql)
    invariant = run_in_db("shopupu", "select count(*) from inventory where stock < 0 or reserved < 0 or reserved > stock;").strip()
    assert invariant == "0", "Source inventory is inconsistent"
    stock = run_in_db("shopupu", f"select stock || ':' || reserved from inventory where variant_id = {int(fixture['variantId'])};").strip()
    assert stock == "100:0", "Repeated full refund did not restore the fixture stock exactly once"
    with (artifacts / "database.dump").open("wb") as output:
        subprocess.run(["docker", "exec", container_id, "pg_dump", "-U", "acceptance", "-d", "shopupu", "--format=custom", "--no-owner", "--no-acl"], stdout=output, check=True, timeout=30)
    subprocess.run(["docker", "exec", container_id, "createdb", "-U", "acceptance", "shopupu_restore"], check=True, timeout=10)
    with (artifacts / "database.dump").open("rb") as source:
        subprocess.run(["docker", "exec", "-i", container_id, "pg_restore", "-U", "acceptance", "-d", "shopupu_restore", "--exit-on-error", "--single-transaction", "--no-owner", "--no-acl"], stdin=source, check=True, timeout=30)
    after = run_in_db("shopupu_restore", fingerprint_sql)
    assert before == after, "Restored table counts/content digests differ"
    assert run_in_db("shopupu_restore", "select count(*) from inventory where stock < 0 or reserved < 0 or reserved > stock;").strip() == "0"
    assert uploads.is_dir() and any(uploads.iterdir()), "Owned upload fixture is missing"
    with tarfile.open(artifacts / "uploads.tar", "w") as archive:
        archive.add(uploads, arcname="uploads")
    restored = uploads.parent / "restored-uploads"
    restored.mkdir()
    with tarfile.open(artifacts / "uploads.tar") as archive:
        archive.extractall(restored, filter="data")
    def hashes(root):
        return {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in root.rglob("*") if path.is_file()}
    files_before, files_after = hashes(uploads), hashes(restored / "uploads")
    assert files_before == files_after, "Restored upload filenames/content differ"
    elapsed = time.monotonic() - started
    result = {"status": "PASS", "isolatedDatabase": "shopupu_restore", "tableCountsAndDigestsEqual": True,
              "tables": before.splitlines(), "uploadSha256": files_after, "elapsedSeconds": elapsed,
              "proposedFixtureRecoveryBudgetSeconds": 60, "productionRtoClaim": False}
    assert elapsed <= 60, "Proposed fixture recovery budget exceeded"
except Exception as error:
    result = {"status": "FAIL", "errorType": type(error).__name__, "detail": str(error), "productionRtoClaim": False}
    (artifacts / "restore-drill.json").write_text(json.dumps(result, indent=2))
    raise SystemExit(1)
(artifacts / "restore-drill.json").write_text(json.dumps(result, indent=2))
