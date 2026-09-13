#!/usr/bin/env bash
# Replays the ops/check-full-stack.sh steps on a machine whose toolchain (JDK 26 / Node 22, no cgroup guard) the
# original script refuses. Evidence only; the guarded script remains the release gate. Usage: manual-full-stack.sh <new-artifact-dir>
# Manual replay of ops/check-full-stack.sh steps on this machine (JDK 26 / Node 22, no cgroup guard).
set -Eeuo pipefail
umask 077
backend_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)
frontend_root=$(cd -- "$backend_root/../shopupu-web" && pwd)
script_dir="$backend_root/ops"
run_nonce=$(python3 -c 'import secrets; print(secrets.token_hex(8))')
run_name="shopupu-accept-$(id -u)-$run_nonce"
artifact_dir="$1"; mkdir -p "$artifact_dir"; artifact_dir=$(cd "$artifact_dir" && pwd)
work_dir=$(mktemp -d "/tmp/shopupu-full-stack.XXXXXXXX")
backend_pid=; frontend_pid=; db_id=
cleanup() {
  status=$?; trap - EXIT INT TERM
  for pid in "$frontend_pid" "$backend_pid"; do
    [[ -n "$pid" ]] || continue
    kill -TERM -- "-$pid" 2>/dev/null || true
    for attempt in {1..40}; do kill -0 "$pid" 2>/dev/null || break; sleep 0.25; done
    kill -KILL -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true
  done
  [[ -z "$db_id" ]] || docker rm -f -v "$db_id" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
  echo "Manual acceptance exit=$status; artifacts: $artifact_dir"; exit "$status"
}
trap cleanup EXIT; trap 'exit 130' INT; trap 'exit 143' TERM
for port in 18080 3120 15432; do ! ss -ltn | grep -q ":$port " || { echo "port $port busy" >&2; exit 2; }; done

echo "[1/9] backend jar"
(cd "$backend_root" && MAVEN_OPTS='-Xmx1g' ./mvnw -o -q -B -DskipTests package) > "$artifact_dir/backend-build.log" 2>&1
jar=$(ls "$backend_root"/target/*.jar | head -1)

echo "[2/9] disposable frontend copy + npm ci + build"
frontend="$work_dir/frontend"; mkdir -p "$frontend"
(cd "$frontend_root" && git ls-files --cached --others --exclude-standard -z | grep -zvE '^(node_modules|\.next|\.env)' | xargs -0 -I{} sh -c 'mkdir -p "$0/$(dirname "{}")" && cp -p "{}" "$0/{}"' "$frontend")
git -C "$frontend_root" show HEAD:src/generated/api.d.ts > "$artifact_dir/api.committed.d.ts"
cp "$frontend_root/src/generated/api.d.ts" "$artifact_dir/api.working-tree.d.ts"
common_env=(env -i "PATH=$PATH" "HOME=$HOME" "LANG=C.UTF-8")
(cd "$frontend" && "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=640' npm ci --no-audit --no-fund) > "$artifact_dir/frontend-install.log" 2>&1

password="Isolated-demo-$run_nonce-only-acceptance"
db_password=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
jwt_secret=$(python3 -c 'import secrets; print(secrets.token_hex(48))')
mfa_key=$(python3 -c 'import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())')
callback_secret=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
admin_email="admin-$run_nonce@example.invalid"

echo "[3/9] database container"
db_id=$(docker run -d --name "$run_name-db" --label "com.shopupu.acceptance.run=$run_nonce" --memory=512m --memory-swap=512m --cpus=1 --pids-limit=128 --tmpfs /var/lib/postgresql:rw,size=256m -p 127.0.0.1:15432:5432 -e POSTGRES_USER=acceptance -e POSTGRES_DB=shopupu -e "POSTGRES_PASSWORD=$db_password" pgvector/pgvector:pg18 -c shared_buffers=32MB -c max_connections=20)
printf '%s\n' "$db_id" > "$artifact_dir/created-container-id.txt"
for attempt in {1..60}; do docker exec "$db_id" pg_isready -U acceptance -d shopupu >/dev/null 2>&1 && break; sleep 1; done

echo "[4/9] backend on 18080"
(cd "$backend_root" && exec setsid "${common_env[@]}" JAVA_TOOL_OPTIONS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2' SERVER_PORT=18080 DB_URL=jdbc:postgresql://127.0.0.1:15432/shopupu DB_USERNAME=acceptance "DB_PASSWORD=$db_password" DB_POOL_SIZE=6 "JWT_SECRET=$jwt_secret" "MFA_ENCRYPTION_KEY=$mfa_key" PAYMENTS_DEFAULT_PROVIDER=stub PAYMENT_CURRENCY=EUR SHIPPING_CURRENCY=EUR "PAYMENT_CALLBACK_SECRET=$callback_secret" AI_ENABLED=false AI_EMBEDDING_PROVIDER=stub AI_LLM_PROVIDER=stub NOTIFICATION_PROVIDER=disabled FRONTEND_BASE_URL=http://127.0.0.1:3120 CORS_ALLOWED_ORIGINS=http://127.0.0.1:3120 "UPLOADS_DIR=$work_dir/uploads" PUBLIC_UPLOADS_BASE_URL=http://127.0.0.1:18080/uploads BOOTSTRAP_ADMIN_ENABLED=true "BOOTSTRAP_ADMIN_EMAIL=$admin_email" "BOOTSTRAP_ADMIN_PASSWORD=$password" java -jar "$jar" --spring.config.import=optional:file:/dev/null --app.rate-limit.auth-capacity=120 --app.rate-limit.auth-refill-per-minute=120) > "$artifact_dir/backend.log" 2>&1 &
backend_pid=$!
for attempt in {1..90}; do kill -0 "$backend_pid" 2>/dev/null || { echo 'backend exited' >&2; exit 1; }; curl -fsS --max-time 2 http://127.0.0.1:18080/actuator/health >/dev/null 2>&1 && break; sleep 1; done
curl -fsS --max-time 3 http://127.0.0.1:18080/actuator/health > "$artifact_dir/health.json"

echo "[5/9] fixture"
docker exec -i "$db_id" psql -X -qAt -U acceptance -d shopupu -v ON_ERROR_STOP=1 -v "nonce=$run_nonce" -v "admin_email=$admin_email" < "$script_dir/full-stack-fixture.sql" > "$artifact_dir/fixture.json"
python3 - "$artifact_dir/fixture.json" <<'PY'
import json, sys, urllib.request
fixture = json.load(open(sys.argv[1]))
with urllib.request.urlopen('http://127.0.0.1:18080/api/v1/catalog/products/' + str(fixture['productId']), timeout=3) as response:
    assert json.load(response)['slug'] == fixture['productSlug'], 'Backend identity proof failed; refusing API writes.'
PY

echo "[6/9] frontend build + schema diff"
(cd "$frontend" && "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=1024' NEXT_TELEMETRY_DISABLED=1 NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18080 API_INTERNAL_BASE_URL=http://127.0.0.1:18080 npm run build) > "$artifact_dir/frontend-build.log" 2>&1
curl -fsS --max-time 15 http://127.0.0.1:18080/v3/api-docs > "$artifact_dir/openapi.json"
"${common_env[@]}" NODE_OPTIONS='--max-old-space-size=384' "$frontend/node_modules/.bin/openapi-typescript" "$artifact_dir/openapi.json" -o "$artifact_dir/api.actual.d.ts" > "$artifact_dir/schema-generation.log" 2>&1
schema_head=0; cmp -s "$artifact_dir/api.committed.d.ts" "$artifact_dir/api.actual.d.ts" || schema_head=1
schema_tree=0; cmp -s "$artifact_dir/api.working-tree.d.ts" "$artifact_dir/api.actual.d.ts" || schema_tree=1
diff -u "$artifact_dir/api.committed.d.ts" "$artifact_dir/api.actual.d.ts" > "$artifact_dir/schema.diff" || true

echo "[7/9] HTTP acceptance + bounded load; restore drill"
http_status=0; "${common_env[@]}" "FULLSTACK_PASSWORD=$password" python3 "$script_dir/full-stack-smoke.py" "$artifact_dir/fixture.json" "$artifact_dir/http-acceptance.json" || http_status=$?
restore_status=0; "${common_env[@]}" python3 "$script_dir/full-stack-restore.py" "$db_id" "$artifact_dir/fixture.json" "$work_dir/uploads" "$artifact_dir" || restore_status=$?

echo "[8/9] frontend on 3120 + Playwright EN/DE"
mkdir -p "$frontend/public/.well-known"; printf '%s' "$run_nonce" > "$frontend/public/.well-known/shopupu-verification.txt"
(cd "$frontend" && exec setsid "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=256' NEXT_TELEMETRY_DISABLED=1 NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18080 API_INTERNAL_BASE_URL=http://127.0.0.1:18080 node "$frontend/node_modules/next/dist/bin/next" start -H 127.0.0.1 -p 3120) > "$artifact_dir/frontend.log" 2>&1 &
frontend_pid=$!
for attempt in {1..60}; do kill -0 "$frontend_pid" 2>/dev/null || { echo 'frontend exited' >&2; exit 1; }; [[ $(curl -fsS --max-time 2 http://127.0.0.1:3120/.well-known/shopupu-verification.txt 2>/dev/null || true) == "$run_nonce" ]] && break; sleep 1; done
[[ $(curl -fsS --max-time 3 http://127.0.0.1:3120/.well-known/shopupu-verification.txt) == "$run_nonce" ]]
browser_status=0
(cd "$frontend" && "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=384' E2E_BASE_URL=http://127.0.0.1:3120 E2E_API_BASE_URL=http://127.0.0.1:18080 "E2E_FIXTURE_FILE=$artifact_dir/fixture.json" "E2E_PASSWORD=$password" E2E_REQUIRE_DE=1 "E2E_ARTIFACT_DIR=$artifact_dir/browser" npm exec -- playwright test --config=playwright.isolated.config.ts) > "$artifact_dir/browser.log" 2>&1 || browser_status=$?

echo "[9/9] legacy smoke.spec.ts (scratch config, no webServer)"
cat > "$frontend/playwright.scratch.config.ts" <<'CFG'
import { defineConfig } from "@playwright/test";
export default defineConfig({ testDir: "e2e", testMatch: ["smoke.spec.ts"], workers: 1, retries: 0, timeout: 60_000,
  use: { baseURL: "http://127.0.0.1:3120", headless: true, trace: "retain-on-failure" } });
CFG
legacy_status=0
(cd "$frontend" && "${common_env[@]}" NODE_OPTIONS='--max-old-space-size=384' E2E_BASE_URL=http://127.0.0.1:3120 E2E_CALLBACK_API_BASE_URL=http://127.0.0.1:18080 "E2E_PAYMENT_CALLBACK_SECRET=$callback_secret" npm exec -- playwright test --config=playwright.scratch.config.ts) > "$artifact_dir/legacy-smoke.log" 2>&1 || legacy_status=$?

python3 - "$artifact_dir" "$schema_head" "$schema_tree" "$http_status" "$browser_status" "$restore_status" "$legacy_status" <<'PY'
from pathlib import Path
import json, sys
r = {'schemaFreshnessVsCommittedHead': 'PASS' if sys.argv[2]=='0' else 'FAIL', 'schemaFreshnessVsWorkingTree': 'PASS' if sys.argv[3]=='0' else 'FAIL',
     'httpSmokeAndBoundedLoad': 'PASS' if sys.argv[4]=='0' else 'FAIL', 'browser': 'PASS' if sys.argv[5]=='0' else 'FAIL', 'germanIncluded': True,
     'restoreDrill': 'PASS' if sys.argv[6]=='0' else 'FAIL', 'legacySmokeSpec': 'PASS' if sys.argv[7]=='0' else 'FAIL',
     'externalServices': 'NOT_TESTED', 'productionCapacity': 'NOT_MEASURED', 'toolchain': 'JDK 26.0.2.1 / Node 22.23.2, no cgroup guard (manual replay of ops/check-full-stack.sh)'}
Path(sys.argv[1], 'summary.json').write_text(json.dumps(r, indent=2)); print(json.dumps(r, indent=2))
PY
((schema_tree == 0 && http_status == 0 && browser_status == 0 && restore_status == 0 && legacy_status == 0))
