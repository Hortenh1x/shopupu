# Identity and notification remediation — T3

Status: implementation and regression tests authored; runtime UNVERIFIED in this agent run. Maven, Docker, npm, browsers and external email/Google calls were not run because of the memory incident. Root owns the controlled verification run. Static `git diff --check` passed for both repositories.

## Authentication contract

Registration and refresh preserve `{accessToken, refreshToken}`. Local and Google login return either `{status: AUTHENTICATED, accessToken, refreshToken}` or `{status: MFA_REQUIRED | MFA_ENROLLMENT_REQUIRED, challengeToken, expiresAt}`. Customers use the existing first factor. Current ADMIN/MANAGER roles require MFA, including Google-backed accounts.

Public POST endpoints (exact whitelist entries):

- `/api/v1/auth/mfa/enrollment/start`: `{challengeToken}` -> `{secret, otpauthUri, expiresAt}`.
- `/api/v1/auth/mfa/enrollment/confirm`: `{challengeToken, code}` -> authenticated result plus ten `recoveryCodes`, once.
- `/api/v1/auth/mfa/verify`: `{challengeToken, code}` or `{challengeToken, recoveryCode}` -> authenticated result or replacement enrollment challenge.

No client user ID is accepted. Challenges are opaque, hashed, valid for five minutes, consumed once, tied to account authVersion, and allow five failed guesses. Failed-attempt updates commit before a 401. TOTP is SHA-1, 20 random secret bytes, six digits, 30 seconds, adjacent-step tolerance, with last-accepted-step replay prevention under the user lock. Seeds use AES-GCM with random IV and user-bound AAD; recovery codes are 128 random bits each and stored only as hashes.

Recovery requires a completed first factor (local password or verified Google identity) and one recovery code. It authorizes replacement enrollment only; the old factor stays active until confirmation. Confirmation rotates the factor, all recovery codes and authVersion, and revokes old refresh/access sessions. Password reset does not remove MFA.

## Session boundaries

All auth mutations acquire the user write lock before refresh, one-time token or challenge rows. Scalar user-ID lookups precede locking; state is reloaded under the lock. Refresh consumes one guarded row and mints its successor in the same transaction. Reuse commits revocation of every refresh token and increments authVersion before throwing 401 outside the transaction; concurrent replay therefore cannot leave two usable branches. There is no nested REQUIRES_NEW transaction waiting on the same user lock.

JWT validation reads the current user's enabled state, roles and authVersion. Password reset/change and refresh reuse invalidate existing access JWTs immediately. A previous customer JWT cannot inherit newly granted ADMIN/MANAGER privileges without MFA. Privileged refresh preserves the original MFA assurance timestamp, bounded to 12 hours; refresh never renews that assurance. Access lifetime is capped at 15 minutes. Ordinary logout revokes the presented refresh token only for its authenticated owner; an already-issued access JWT can remain valid for its remaining lifetime, at most 15 minutes.

New credentials require 15 Unicode code points and at most 72 UTF-8 bytes, with no composition rules. All user-chosen credential writes and bootstrap use PasswordPolicy. Password change also invalidates outstanding password-reset links. Existing passwords are accepted for login. The common-password check is a small project-authored full-password list, with no imported corpus or external license; it must not be described as comprehensive breach screening.

Account throttles use normalized account hashes and separate operation budgets: login 10 per minute; reset/resend five per 15 minutes. Windows do not extend on additional attacks and the Caffeine cache is bounded. This is a single-instance demo policy, not a distributed limiter.

Success audit rows now join the business transaction (`REQUIRED`), so rollback removes the corresponding audit success and no second connection is needed while the user lock is held. Local login prohibits an enclosing transaction, rolls back failed authentication first, then commits its failure audit before returning 401. Failed-login actors use `AuditService.accountActor`: `account-sha256:` plus the lowercase SHA-256 hex digest of the trimmed, Locale.ROOT-lowercased UTF-8 email. This is a stable pseudonym, not anonymous data; local export/erasure also matches it. Late failures cannot recreate the erased raw email. Audit persistence failures deliberately fail closed and propagate: success operations roll back, and an unavailable failed-attempt audit can return a server error instead of 401. Audit failures are not swallowed in a transaction already marked rollback-only.

## Mail and demo policy

`notifications.provider` is explicitly `disabled` (default), `smtp`, or `resend`. `NotificationService.isAvailable()` means a real provider is configured; it does not prove inbox deliverability. Registration remains usable with mail disabled and mints no verification token; emailVerified stays false. Fictional demo checkout does not require ownership of an email channel. Forgot-password and resend return `503 EMAIL_DELIVERY_UNAVAILABLE` when delivery is disabled; forgot-password does so before looking up any account.

Account and order notifications enqueue only AFTER_COMMIT. Provider adapters are synchronous inside the dedicated notification worker, propagate failures, and never log raw links/tokens/provider error bodies. Retry is bounded to three attempts (short backoff); queue rejection and final failure have logs and `shopupu.notification.delivery` counters tagged `kind` and `outcome` (`accepted`, `retry`, `failed`, `unavailable`). A successful provider acceptance does not establish delivery. The queue and retry state are in memory and can be lost on restart; this is not a durable outbox. SMTP/Resend hookup, delivery acceptance and durable delivery requirements remain part of the separately authorized external integration step.

Required deployment settings: `MFA_ENCRYPTION_KEY` is Base64 for 32 random bytes with no public default. Empty leaves customer login usable but privileged login fails closed with `MFA_CONFIGURATION_REQUIRED`; malformed keys fail startup validation. Configure issuer through `MFA_ISSUER`. The all-zero key exists only in the test profile fixture. SMTP needs explicit sender/host and bounded connection/read/write timeouts; Resend needs explicit key/from and a 1..15 second timeout. No external credentials were added by this agent.

The SMTP adapter rejects a blank `spring.mail.host` and an explicitly configured port outside 1..65535 at startup. Spring Boot otherwise creates a mail bean for a present-but-empty host; that bean alone does not indicate notification availability. Disabled/Resend selection remains independent of any such Boot mail bean.

## Client behavior

AuthForm holds challenges, seeds and recovery codes only in component state. It requests no profile before full MFA. Enrollment results do not publish a session until the user confirms saving recovery codes, preserving the once-only screen across QueryProvider remounts. AuthProvider binds asynchronous results to their initiating attempt and checks render generation, unmount state, session snapshot and attempt both around awaits and inside the shared session write lock. Fresh profile validation uses the returned access token explicitly.

Forgot-password success occurs only after an accepted request; HTTP 503 is shown as failure. Welcome/verify/forgot pages read the public email capability and explain disabled demo email truthfully. New-password forms enforce matching length/byte bounds; login accepts older short passwords.

## Regression evidence to run

Backend: `AuthSessionIntegrityIT`, `AccountNotificationCommitIT`, `AuthServiceTest`, `RefreshTokenServiceTest`, `OneTimeTokenServiceTest`, `PasswordPolicyTest`, `MfaCryptoTest`, `AccountAuthThrottleTest`, `NotificationDeliveryTest`, `JwtTokenProviderTest`, existing `GoogleAuthFlowIT`, `SecurityAccessIT`, `UserServiceTest`, then root's required verify gate. These cover concurrency, rollback, reuse/expiry/purpose, immediate revocation, role escalation, MFA enrollment/replay/recovery/attempt state, disabled mail, queue failure and transient retry.

Follow-up regression tests (also runtime UNVERIFIED): `AuthAuditTransactionIT` uses a one-connection pool to cover successful/failed login, business rollback, and fail-closed audit persistence; `AuthServiceTest` verifies rollback precedes failed-attempt auditing; `AuditServiceTest` checks the pseudonym contract and propagated failures; `NotificationProviderConfigurationTest` checks missing/blank SMTP host, port bounds and exclusive provider selection without any live send.

Frontend: `AuthProvider.test.tsx`, `passwordPolicy.test.ts`, plus existing auth/session and full root-controlled client checks. MFA tests prove no early profile/session and recovery-code save gating. Static source review is not PASS for these tests.

Schema change: additive migration `V22__identity_session_and_mfa_guards.sql`; applied migrations remain untouched.
