# Isolated full-stack acceptance

Status on 2026-09-14: the guarded script has now **RUN on the pinned toolchain** — GitHub Actions run [34853727575](https://github.com/Hortenh1x/shopupu/actions/runs/34853727575) (`ubuntu-24.04`, JDK 25 / Node 24, `ops/ci/limited-run.sh` as the guard) passed every stage for backend `5f79669` + frontend `2d5cbfc`: schema freshness, HTTP acceptance + bounded load, restore drill, Playwright EN/DE + accessibility 13/13 — evidence E-024 in the audit index, artifacts under `docs/audit-2026-09-12/full-stack-ci-2026-09-14/`. The development desktop still refuses the script (JDK 26 / Node 22); its steps were replayed one-to-one by [`manual-full-stack.sh`](audit-2026-09-12/full-stack-replay-2026-09-14/manual-full-stack.sh) (E-021, E-024). A passing run is not a capacity, external-provider or production result. The separate repository CI gates still own backend `verify`, frontend unit tests, and container image builds.

## Scope and prerequisites

`ops/check-full-stack.sh` snapshots both source trees, builds a fresh backend and frontend, starts a disposable local stack and records evidence. It never reuses a running backend, database or frontend. Required: Linux with cgroup v2 and a working user systemd manager, local Unix-socket Docker, JDK 25, Node 24, Python 3.12+, Git, curl, ripgrep, setsid, and the matching frontend checkout with its lockfile. Chromium and its OS libraries must already be provisioned for that checkout's Playwright version. Browser provisioning is a separate owner action under the same resource guard; the harness does not install OS packages or launch desktop applications.

The default guard is `/tmp/shopupu-limited-run.sh`. `SHOPUPU_RESOURCE_GUARD` may select an equivalent executable. Its contract is a shared flock, minimum 6 GiB `MemAvailable` before launch, an aggregate process cgroup capped at 2 GiB RAM, zero swap and two CPUs, and stopping the owned scope if available memory falls below 4 GiB. The harness independently checks the actual cgroup limits. Exit 75 means **DEFERRED**, never PASS. Do not bypass this guard or nest the harness inside the same flock wrapper.

From the backend checkout, after the owner permits resource-intensive verification:

```bash
./ops/check-full-stack.sh --frontend '../shopupu-web' --require-de
```

Both checkouts must normally be clean and checked out at reviewed immutable commits. During remediation, `--allow-dirty` explicitly permits local source snapshots, including nonignored untracked source files. It does not turn those snapshots into committed release evidence. `--artifacts /absolute/new/directory` selects a new artifact destination; existing directories are rejected. The default is `target/full-stack-<nonce>`.

If browser provisioning is missing, prepare the matching frontend dependencies and run its `npm exec -- playwright install chromium` through the resource guard in a disposable checkout. OS library installation belongs to the machine/CI owner. No browser or dependency installation has been performed as part of authoring this harness.

## Isolation and cleanup

| Resource | Owned acceptance location | Bound |
| --- | --- | --- |
| Backend | `127.0.0.1:18080` | Java heap 384 MiB; shared process cgroup |
| Frontend | `127.0.0.1:3120` | Node heap 256 MiB; shared process cgroup |
| Database | `127.0.0.1:15432` | One `pgvector/pgvector:pg18` container, 512 MiB memory and total memory+swap, one CPU, 128 PIDs |
| Sources, uploads, restored uploads | New `/tmp/shopupu-full-stack.*` directory | Disposable; no original `.next`, `target`, uploads or `.env` files reused |
| Restore database | New `shopupu_restore` inside the owned container | Same container budget; created empty, never dropped/replaced |

Ports 3001, 8080 and 5432 on the host are never used. Port occupancy aborts before startup. A nonce-bearing product from the newly created database proves backend identity before authenticated API writes, and a nonce-bearing public file proves frontend identity. All database commands use the exact newly created container ID. Outbound browser origins are restricted to the two owned application origins; payments use the local stub, notifications and AI are disabled, and no inherited provider secrets or `.env` files enter the child application environment.

The container has a unique `shopupu-accept-<uid>-<nonce>-db` name and `com.shopupu.acceptance.run=<nonce>` label. EXIT/INT/TERM cleanup terminates only the process groups it started and removes only its exact container ID and temporary directory. Docker daemon work is outside the Java/Node cgroup: this script does **not** build Docker images and separately limits its only database container. The system memory guard also covers pressure from that container.

A machine crash or cgroup SIGKILL can prevent shell traps. In that case inspect `created-container-id.txt`, compare the exact container's acceptance label with `fixture.json`, and remove only that ID. Never use `docker rm $(docker ps ...)`, broad `pkill`, or commands against existing user services. No automatic cleanup claim covers an uncatchable kill.

## Recorded acceptance

1. `source-manifest.json` records both Git HEAD revisions and SHA-256 hashes of every copied source file. Backend packaging uses `-DskipTests`; this is not a replacement for `./mvnw verify`.
2. A fresh backend generates `openapi.json` (`springdoc.writer-with-order-by-keys=true` keeps it deterministic: without it Page schema properties came out in reflection order and flipped between JVM runs). The matching frontend's locked `openapi-typescript` generates `api.actual.d.ts`, which is compared byte for byte with **frontend HEAD's committed** `src/generated/api.d.ts`. `schema.diff` records drift, including auth/MFA, refunds, privacy and review provenance. Even `--allow-dirty` compares the committed contract so an uncommitted schema edit cannot produce a false freshness pass. Review the diff and regenerate/commit the frontend contract intentionally; the harness never rewrites either checkout.
3. `http-acceptance.json` covers public configuration and semantic 404, RFC Problem Details/request IDs, unsigned callbacks, mandatory local MFA for privileged users, customer/manager/admin scopes, checkout replay and conflicting promo code, cross-owner payment denial, repeat local success/refund, privacy export and one synthetic PNG upload.
4. The bounded HTTP fixture issues 120 public reads at a target four requests/second, at most four concurrent requests, three-second request timeouts and a 45-second submission deadline. Its **proposed, unmeasured** acceptance budget is zero errors and p95 <=750 ms. This small local fixture is not a capacity test or an accepted production SLO.
5. `restore-drill.json` records a custom `pg_dump`, restore to a new empty database, every public table's row counts/canonical content digests (including orders, payments, refund history and inventory), inventory guards, and exactly-once restoration of the refunded fixture's stock. It also archives and restores the owned upload and compares filenames/SHA-256 bytes. The **proposed, unmeasured** fixture recovery budget is <=60 seconds. This does not establish production RPO/RTO, backup retention, point-in-time recovery or recovery from a lost host. See [database recovery](database-recovery.md) and [runbook](runbook.md) for operational scope. The fixture is quiescent before backup; coordinated production DB/file snapshots require the separate documented procedure.
6. `playwright.isolated.config.ts` runs only the new `.acceptance.ts` files, one headless Chromium worker with no retries or reused web server. English coverage includes demo/synthetic-review disclosure, literal HTML injection text without execution, semantic product 404/noindex, real guest-cart merge, owner-only local checkout simulation, role denial, and a delayed A identity response after a real cross-tab switch to B. Screenshots/traces are retained only on failure.
7. `--require-de` additionally runs desktop/mobile language-selector persistence, `html lang`, German demo and login semantics, switching back to English, a complete German checkout (fictional-product label, cart, order, delivery form, local payment simulation, order list) with the API values (`STANDARD_POST`, `stub`, `PENDING`) unchanged, and the German back office: TOTP enrollment through the browser for the fixture's second, not-yet-enrolled ADMIN (`adminDeEmail`; the HTTP smoke already enrolled the bootstrap admin and manager through the API), navigation, order details/status history, the test-refund panel end to end and product-form validation. The agreed selector is `Language`/`Sprache` with values `en`/`de`. Without this flag, `germanIncluded=false` explicitly means German was **not checked**.

`summary.json` reports each completed phase and exits nonzero if schema, HTTP, restore or browser acceptance fails. Startup/build/prerequisite failure can stop before a summary exists; the exit code and stage logs are then the evidence. External Stripe TEST flows, Google, email delivery and live AI remain `NOT_TESTED`; production capacity remains `NOT_MEASURED`. Assertions against local stubs must never be presented as external-provider verification.

Artifacts use `umask 077`, but database dumps and browser traces still contain generated credentials/session material for these disposable accounts. Keep artifacts access-controlled and time-limited; never publish them as public CI attachments. The generated password is not written into a manifest or report.

## Cross-repository CI integration

`.github/workflows/full-stack.yml` runs this harness on GitHub-hosted `ubuntu-24.04` runners — manually (`workflow_dispatch`, with the frontend ref and the German requirement as inputs) and weekly. It is evidence for a release record, not a per-commit gate; the repository CI workflows still own backend `verify`, frontend unit tests and image builds.

The job performs the owner-side provisioning the harness deliberately does not do itself, all outside the guard: it checks out both repositories side by side, installs JDK 25 / Node 24 / ripgrep, warms the Maven repository, provisions the frontend's matching Chromium with `npx playwright install --with-deps chromium`, and only then invokes `./ops/check-full-stack.sh --frontend <checkout> [--require-de]` with `SHOPUPU_RESOURCE_GUARD=ops/ci/limited-run.sh`. That guard is the CI counterpart of the desktop one with the same contract — shared flock, at least 6 GiB `MemAvailable`, a transient systemd scope capped at 2 GiB RAM / zero swap / two CPUs, stopped below 4 GiB available. Hosted runners have no user systemd manager, so the scope is created by the system manager through passwordless `sudo` and the command drops back to the runner user; the harness still verifies the actual cgroup limits itself. Exit 75 is surfaced as a **DEFERRED** warning and fails the job; it is never a PASS. The artifact directory (`summary.json`, HTTP/restore/browser evidence, logs) is uploaded for 30 days and `summary.json` is echoed into the job summary.

Pin the two source SHAs in the release record. A branch name alone is insufficient to reproduce a cross-repository schema result. Image builds and full test suites must remain separate serialized guarded jobs; Docker image-build memory is not controlled by this script's process cgroup.
