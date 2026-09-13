# ADR-0004: Browser sessions, account changes and interrupted operations

Status: implementation selected for the fictional-store remediation, 12 September 2026. The original request explicitly authorizes the fixes. New regression tests are authored; runtime verification remains pending and must be recorded in the assessment before accepting the change.

## Context and decision

The frontend and API are separate deployments. This change retains manually attached Bearer authentication rather than introducing a new BFF/cookie deployment. Access tokens and the loaded user live in browser memory. The refresh token remains in same-origin localStorage; the guest cart token is an opaque localStorage value. A script running in this origin can read these values. Session generation checks address stale-account work, not arbitrary same-origin script execution. They must not be described as making localStorage equivalent to an HttpOnly cookie.

No authentication cookie is automatically attached to API mutations. Browser origin policy, explicit CORS allowlisting, server authentication and object/role authorization remain independent boundaries. The planned language preference cookie contains only `en` or `de` and grants no access. A future BFF/auth-cookie migration must revisit CSRF, cookie scope, cross-origin deployment and session revocation before claiming equivalent protection.

## Publication and cross-tab behavior

A browser Web Lock named `shopupu-session` serializes refresh rotation, login publication, logout and guarded guest-token writes. It requires a supporting browser on HTTPS or localhost. If a secure lock or persistent storage is unavailable, sign-in fails with an actionable message; no uncoordinated refresh fallback is used.

Each account publication creates a marker. The refresh value is a JSON record containing its token and that marker. Readers accept it only when it matches the committed session marker. The new identity is not published to in-memory tokens/user/cache until storage succeeds. Failed publication attempts restore the previous values where possible and block the failed tab if persistence cannot be trusted. A lock owner detecting an incomplete record fails closed. Outside-lock readers may see a transient staged record and do not mistake it for a committed identity. The former plain-token format is read for migration until a successful rotation replaces it; release acceptance must use the current client version in every tested tab.

The browser compares the marker directly as well as receiving storage events, because event delivery can be delayed. A generation change clears the old query client and remounts account-specific observers and local form/chat state. Private query and mutation wrappers capture the initiating generation, guard requests/retries and discard late callbacks. Multi-step mutations assert the same generation after each await before another request, download or navigation. Server object authorization is still mandatory even when the client discards a response.

## Login, MFA and guest carts

An authentication attempt captures the session and guest cart that initiated it. Password/Google first factor does not publish a privileged session while MFA is pending. Challenge steps and final publication remain bound to the same attempt; superseded/unmounted attempts cannot install their tokens. Recovery codes are acknowledged before the recovered session is published. If another tab replaced the guest cart during login/MFA, only the originally merged cart token can be removed.

## Expiry and revocation

The backend serializes refresh consumption/rotation with the account and token state. Reuse revokes the chain and advances auth version; password change/reset also revokes prior access sessions through that version. Privileged tokens require the MFA assurance for their current role. Ordinary logout revokes its refresh chain, clears the browser session and propagates the marker; an already issued access token can remain valid for at most the configured 15-minute lifetime. The UI states this actual policy.

## Interrupted checkout/payment requests

A checkout/payment operation stores its UUID key and request body in per-user sessionStorage before HTTP. A lost response, conflict or unknown provider result preserves the key for retry and recovery. It is not safe to generate a fresh key merely because a network request failed. A confirmed result or definitive rejection completes that stored operation. Account changes cannot redirect the new user using the old operation's late response.

## Acceptance evidence

Required regression coverage includes parallel refresh; A→B with late success/401/500 and delayed retry; mutation callbacks after API resolution; logout and profile refresh from old closures; queued multi-step mutations; localStorage/marker/rollback failures; unavailable Web Locks; two-tab guest replacement during MFA; and lost checkout/payment responses. Source implementation and authored tests are not a substitute for the Vitest, two-tab browser and API concurrency results on the final artifacts.
