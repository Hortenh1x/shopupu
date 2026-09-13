#!/usr/bin/env bash
# Isolated acceptance only. This script never connects to the normal development services.
set -Eeuo pipefail
umask 077
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
backend_root=$(cd -- "$script_dir/.." && pwd)
frontend_root=$(cd -- "$backend_root/../shopupu-web" 2>/dev/null && pwd || true)
allow_dirty=0
require_de=0
artifact_dir=
while (($#)); do
  case "$1" in
    --frontend) frontend_root=$2; shift 2 ;;
    --artifacts) artifact_dir=$2; shift 2 ;;
    --allow-dirty) allow_dirty=1; shift ;;
    --require-de) require_de=1; shift ;;
    *) echo "Usage: $0 [--frontend PATH] [--artifacts NEW_PATH] [--allow-dirty] [--require-de]" >&2; exit 2 ;;
  esac
done

# Use the shared desktop guard; a CI owner must supply an equivalent guarded runner.
# The marker alone is insufficient: actual cgroup limits are checked again inside it.
if [[ ${SHOPUPU_FULLSTACK_GUARDED:-0} != 1 ]]; then
  guard=${SHOPUPU_RESOURCE_GUARD:-/tmp/shopupu-limited-run.sh}
  [[ -x "$guard" ]] || { echo 'A resource guard is required; see docs/full-stack-verification.md.' >&2; exit 75; }
  args=(--frontend "$frontend_root")
  [[ -z "$artifact_dir" ]] || args+=(--artifacts "$artifact_dir")
  ((allow_dirty == 0)) || args+=(--allow-dirty)
  ((require_de == 0)) || args+=(--require-de)
  exec "$guard" env SHOPUPU_FULLSTACK_GUARDED=1 "$script_dir/check-full-stack.sh" "${args[@]}"
fi
python3 - <<'PY'
from pathlib import Path
import socket, sys
if sys.version_info < (3, 12):
    raise SystemExit('Python 3.12 or newer is required.')
memory = int(next(line.split()[1] for line in Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemAvailable:')))
if memory < 6 * 1024 * 1024:
    raise SystemExit(75)
relative = next(line.split(':', 2)[2] for line in Path('/proc/self/cgroup').read_text().splitlines() if line.startswith('0::'))
cgroup = Path('/sys/fs/cgroup') / relative.lstrip('/')
try:
    limits = [cgroup, *list(cgroup.parents)[:-2]]
    memory_limits = [int((p / 'memory.max').read_text()) for p in limits if (p / 'memory.max').exists() and (p / 'memory.max').read_text().strip() != 'max']
    no_swap = any((p / 'memory.swap.max').exists() and (p / 'memory.swap.max').read_text().strip() == '0' for p in limits)
    cpu_limits = [(p / 'cpu.max').read_text().split() for p in limits if (p / 'cpu.max').exists()]
    two_cpus = any(quota != 'max' and int(quota) <= 2 * int(period) for quota, period in cpu_limits)
    assert memory_limits and min(memory_limits) <= 2 * 1024**3 and no_swap and two_cpus
except (OSError, AssertionError):
    raise SystemExit('Refusing to start without a real <=2GiB / zero-swap / <=2CPU cgroup.')
for port in (18080, 3120, 15432):
    with socket.socket() as listener:
        try:
            listener.bind(('127.0.0.1', port))
        except OSError:
            raise SystemExit(f'Port {port} is occupied; no existing service will be reused.')
PY
for command in git docker java npm node curl python3 setsid rg; do
  command -v "$command" >/dev/null || { echo "Missing prerequisite: $command" >&2; exit 2; }
done
docker_endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}' "$(docker context show)")
[[ "$docker_endpoint" == unix://* && -z ${DOCKER_HOST:-} ]] || { echo 'A local Unix-socket Docker context is required; remote Docker is refused.' >&2; exit 2; }
[[ $(node --version) == v24.* ]] || { echo 'Node 24 is required.' >&2; exit 2; }
java -version 2>&1 | head -1 | rg '"25([.]|")' >/dev/null || { echo 'JDK 25 is required.' >&2; exit 2; }
[[ -f "$frontend_root/package-lock.json" ]] || { echo 'Provide the matching frontend checkout with --frontend.' >&2; exit 2; }
if ((allow_dirty == 0)); then
  for repository in "$backend_root" "$frontend_root"; do
    [[ -z $(git -C "$repository" status --porcelain) ]] || { echo 'Dirty checkout: commit the reviewed revisions or explicitly use --allow-dirty for local evidence.' >&2; exit 2; }
  done
fi
run_nonce=$(python3 -c 'import secrets; print(secrets.token_hex(8))')
run_name="shopupu-accept-$(id -u)-$run_nonce"
artifact_dir=${artifact_dir:-"$backend_root/target/full-stack-$run_nonce"}
[[ ! -e "$artifact_dir" ]] || { echo 'Artifact destination must be new.' >&2; exit 2; }
mkdir -p -- "$artifact_dir"
artifact_dir=$(cd -- "$artifact_dir" && pwd)
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/shopupu-full-stack.XXXXXXXX")
backend_pid=
frontend_pid=
db_id=
cleanup() {
  status=$?
  trap - EXIT INT TERM
  for pid in "$frontend_pid" "$backend_pid"; do
    [[ -n "$pid" ]] || continue
    kill -TERM -- "-$pid" 2>/dev/null || true
    for attempt in {1..40}; do kill -0 "$pid" 2>/dev/null || break; sleep 0.25; done
    kill -KILL -- "-$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done
  if [[ -n "$db_id" ]]; then docker rm -f -v "$db_id" >/dev/null 2>&1 || true; fi
  rm -rf -- "$work_dir"
  echo "Acceptance exit=$status; artifacts: $artifact_dir"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Snapshot source into disposable directories. Existing .next/target/.env files are never read or changed.
python3 - "$backend_root" "$frontend_root" "$work_dir" "$artifact_dir" <<'PY'
from pathlib import Path
import hashlib, json, shutil, subprocess, sys
manifest = {}
for source, name in zip(sys.argv[1:3], ('backend', 'frontend')):
    source = Path(source).resolve(); destination = Path(sys.argv[3]) / name
    entries = subprocess.check_output(['git', '-C', str(source), 'ls-files', '--cached', '--others', '--exclude-standard', '-z']).decode().split('\0')
    hashes = {}
    for entry in sorted(set(filter(None, entries))):
        relative = Path(entry)
        if any(part in ('.git', 'node_modules', '.next', 'target', 'uploads') or part.startswith('.env') for part in relative.parts):
            continue
        original = source / relative
        if not original.is_file(): continue
        original.resolve().relative_to(source)
        copied = destination / relative; copied.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(original, copied)
        hashes[entry] = hashlib.sha256(copied.read_bytes()).hexdigest()
    manifest[name] = {'revision': subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD']).decode().strip(), 'files': hashes}
Path(sys.argv[4], 'source-manifest.json').write_text(json.dumps(manifest, indent=2))
PY
backend="$work_dir/backend"
frontend="$work_dir/frontend"
[[ -x "$backend/mvnw" ]] || { echo 'Backend snapshot is incomplete.' >&2; exit 2; }
# HEAD is deliberate: a working-tree schema edit does not masquerade as the committed contract.
git -C "$frontend_root" show HEAD:src/generated/api.d.ts > "$artifact_dir/api.committed.d.ts"
common_env=(env -i "PATH=$PATH" "HOME=$HOME" "LANG=C.UTF-8")
echo 'Building fresh backend sources (the separate backend verify gate owns unit/IT/coverage).'
(cd -- "$backend" && "${common_env[@]}" JAVA_TOOL_OPTIONS='-Xmx640m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=2' MAVEN_OPTS='-Xmx256m -XX:MaxMetaspaceSize=192m' ./mvnw -B -DskipTests package) > "$artifact_dir/backend-build.log" 2>&1
shopt -s nullglob
jars=("$backend"/target/*.jar)
((${#jars[@]} == 1)) || { echo 'Expected exactly one backend application jar.' >&2; exit 2; }
echo 'Installing dependencies in the disposable frontend.'
(cd -- "$frontend" && "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=640' npm ci --no-audit --no-fund) > "$artifact_dir/frontend-install.log" 2>&1

# Browser provisioning is explicit and separate; this harness never installs system packages.
(cd -- "$frontend" && "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=128' node -e 'const fs = require("node:fs"); const path = require("@playwright/test").chromium.executablePath(); if (!fs.existsSync(path)) { throw new Error("Matching Playwright Chromium is missing. Provision it under the resource guard before running acceptance."); }') > "$artifact_dir/browser-prerequisite.log" 2>&1

password="Isolated-demo-$run_nonce-only-acceptance"
db_password=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
jwt_secret=$(python3 -c 'import secrets; print(secrets.token_hex(48))')
mfa_key=$(python3 -c 'import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())')
admin_email="admin-$run_nonce@example.invalid"
db_id=$(docker run -d --name "$run_name-db" --label "com.shopupu.acceptance.run=$run_nonce" --memory=512m --memory-swap=512m --cpus=1 --pids-limit=128 --tmpfs /var/lib/postgresql:rw,size=256m -p 127.0.0.1:15432:5432 -e POSTGRES_USER=acceptance -e POSTGRES_DB=shopupu -e "POSTGRES_PASSWORD=$db_password" pgvector/pgvector:pg18 -c shared_buffers=32MB -c max_connections=20)
printf '%s\n' "$db_id" > "$artifact_dir/created-container-id.txt"
for attempt in {1..60}; do
  if docker exec "$db_id" pg_isready -U acceptance -d shopupu >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$db_id" pg_isready -U acceptance -d shopupu >/dev/null
(cd -- "$backend" && exec setsid "${common_env[@]}" JAVA_TOOL_OPTIONS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2' SERVER_PORT=18080 DB_URL=jdbc:postgresql://127.0.0.1:15432/shopupu DB_USERNAME=acceptance "DB_PASSWORD=$db_password" DB_POOL_SIZE=6 "JWT_SECRET=$jwt_secret" "MFA_ENCRYPTION_KEY=$mfa_key" PAYMENTS_DEFAULT_PROVIDER=stub PAYMENT_CURRENCY=EUR SHIPPING_CURRENCY=EUR AI_ENABLED=false AI_EMBEDDING_PROVIDER=stub AI_LLM_PROVIDER=stub NOTIFICATION_PROVIDER=disabled FRONTEND_BASE_URL=http://127.0.0.1:3120 CORS_ALLOWED_ORIGINS=http://127.0.0.1:3120 "UPLOADS_DIR=$work_dir/uploads" PUBLIC_UPLOADS_BASE_URL=http://127.0.0.1:18080/uploads BOOTSTRAP_ADMIN_ENABLED=true "BOOTSTRAP_ADMIN_EMAIL=$admin_email" "BOOTSTRAP_ADMIN_PASSWORD=$password" java -jar "${jars[0]}" --spring.config.import=optional:file:/dev/null --app.rate-limit.auth-capacity=120 --app.rate-limit.auth-refill-per-minute=120) > "$artifact_dir/backend.log" 2>&1 &
backend_pid=$!
for attempt in {1..90}; do
  kill -0 "$backend_pid" 2>/dev/null || { echo 'Owned backend exited during startup.' >&2; exit 1; }
  if curl -fsS --max-time 2 http://127.0.0.1:18080/actuator/health > /dev/null 2>&1; then break; fi
  sleep 1
done
curl -fsS --max-time 3 http://127.0.0.1:18080/actuator/health > "$artifact_dir/health.json"
# All fixture writes use the exact container ID created above, never a configured user database.
docker exec -i "$db_id" psql -X -qAt -U acceptance -d shopupu -v ON_ERROR_STOP=1 -v "nonce=$run_nonce" -v "admin_email=$admin_email" < "$script_dir/full-stack-fixture.sql" > "$artifact_dir/fixture.json"
python3 - "$artifact_dir/fixture.json" <<'PY'
import json, sys, urllib.request
fixture = json.load(open(sys.argv[1]))
with urllib.request.urlopen('http://127.0.0.1:18080/api/v1/catalog/products/' + str(fixture['productId']), timeout=3) as response:
    assert json.load(response)['slug'] == fixture['productSlug'], 'Backend identity proof failed; refusing API writes.'
PY
echo 'Building the disposable frontend against the owned backend.'
(cd -- "$frontend" && "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=640' NEXT_TELEMETRY_DISABLED=1 NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18080 API_INTERNAL_BASE_URL=http://127.0.0.1:18080 npm run build) > "$artifact_dir/frontend-build.log" 2>&1
curl -fsS --max-time 15 http://127.0.0.1:18080/v3/api-docs > "$artifact_dir/openapi.json"
"${common_env[@]}" NODE_OPTIONS='--max-old-space-size=384' "$frontend/node_modules/.bin/openapi-typescript" "$artifact_dir/openapi.json" -o "$artifact_dir/api.actual.d.ts" > "$artifact_dir/schema-generation.log" 2>&1
schema_status=0
cmp -s "$artifact_dir/api.committed.d.ts" "$artifact_dir/api.actual.d.ts" || schema_status=1
diff -u "$artifact_dir/api.committed.d.ts" "$artifact_dir/api.actual.d.ts" > "$artifact_dir/schema.diff" || true
http_status=0
"${common_env[@]}" "FULLSTACK_PASSWORD=$password" python3 "$script_dir/full-stack-smoke.py" "$artifact_dir/fixture.json" "$artifact_dir/http-acceptance.json" || http_status=$?

restore_status=0
"${common_env[@]}" python3 "$script_dir/full-stack-restore.py" "$db_id" "$artifact_dir/fixture.json" "$work_dir/uploads" "$artifact_dir" || restore_status=$?

# This marker proves the frontend is our disposable copy, even if another process raced for the port.
mkdir -p "$frontend/public/.well-known"
printf '%s' "$run_nonce" > "$frontend/public/.well-known/shopupu-verification.txt"
(cd -- "$frontend" && exec setsid "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=256' NEXT_TELEMETRY_DISABLED=1 NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18080 API_INTERNAL_BASE_URL=http://127.0.0.1:18080 node "$frontend/node_modules/next/dist/bin/next" start -H 127.0.0.1 -p 3120) > "$artifact_dir/frontend.log" 2>&1 &
frontend_pid=$!
for attempt in {1..60}; do
  kill -0 "$frontend_pid" 2>/dev/null || { echo 'Owned frontend exited during startup.' >&2; exit 1; }
  if [[ $(curl -fsS --max-time 2 http://127.0.0.1:3120/.well-known/shopupu-verification.txt 2>/dev/null || true) == "$run_nonce" ]]; then break; fi
  sleep 1
done
[[ $(curl -fsS --max-time 3 http://127.0.0.1:3120/.well-known/shopupu-verification.txt) == "$run_nonce" ]]
browser_status=0
(cd -- "$frontend" && "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=384' E2E_BASE_URL=http://127.0.0.1:3120 E2E_API_BASE_URL=http://127.0.0.1:18080 "E2E_FIXTURE_FILE=$artifact_dir/fixture.json" "E2E_PASSWORD=$password" "E2E_REQUIRE_DE=$require_de" "E2E_ARTIFACT_DIR=$artifact_dir/browser" npm exec -- playwright test --config=playwright.isolated.config.ts) > "$artifact_dir/browser.log" 2>&1 || browser_status=$?
python3 - "$artifact_dir" "$schema_status" "$http_status" "$browser_status" "$require_de" "$restore_status" <<'PY'
from pathlib import Path
import json, sys
report = {'schemaFreshness': 'PASS' if sys.argv[2]=='0' else 'FAIL', 'httpSmokeAndBoundedLoad': 'PASS' if sys.argv[3]=='0' else 'FAIL', 'browser': 'PASS' if sys.argv[4]=='0' else 'FAIL', 'germanIncluded': sys.argv[5]=='1', 'restoreDrill': 'PASS' if sys.argv[6]=='0' else 'FAIL', 'externalServices': 'NOT_TESTED', 'productionCapacity': 'NOT_MEASURED'}
Path(sys.argv[1], 'summary.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
PY
((schema_status == 0 && http_status == 0 && browser_status == 0 && restore_status == 0))
