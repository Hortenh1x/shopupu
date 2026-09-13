# Client session remediation — T2 follow-up

Status: source changes and regression tests authored; runtime UNVERIFIED. No Maven, npm, Docker, browser, build or test process was launched by this agent. Root owns verification through the controlled resource wrappers. Static source review and `git diff --check` are not runtime PASS.

## Session publication

The writer lock `shopupu-session` serializes refresh rotation, login/logout publication and guest cart-token writes across cooperative tabs. New refresh tokens are stored as one JSON record `{token, marker}` before memory publication. The shared marker is written last. Conditional rollback restores the preceding stored record if publication fails; memory/user/cache generation is invalidated on a failed write. A refresh record whose embedded marker differs from the shared marker cannot be used, even if rollback also failed. An outside-lock reader can temporarily observe staging and returns no token; the lock owner rejects a persistently incomplete publication. Legacy plain tokens can be read until their next rotation writes the bound record.

Storage errors or unavailable Web Locks disable persistent authentication in the affected tab, drop its memory identity and show an actionable storage/HTTPS/reload message. Public catalog browsing remains possible. This fallback does not claim that all modern browsers lack the APIs. Failed `clearSession` operations settle their pending promise; AuthProvider stops retrying restoration on a permanent storage error, avoiding repeated generation/remount loops.

AuthProvider binds login, Google and MFA awaits to an attempt and render generation. Retained logout/reload functions cannot touch a later account. Enrollment/start uses the same pending-attempt guard. The Google button ignores callbacks after unmount. Recovery codes remain local until explicitly saved.

The first authentication factor captures its guest-cart token and sends that exact header. Successful publication removes only that token while holding the writer lock. A newer guest cart created during MFA is preserved. Cart responses remember a guest token only under the same lock and only for their original session and cart token; stale responses fail with SESSION_CHANGED or CART_CHANGED.

## Private queries and mutations

`useSessionMutation` captures identity at `mutate`/`mutateAsync` entry, before TanStack awaits mutation-cache hooks. The function and hook/per-call success, error and settled callbacks retain their initiating definition and session. Old executions reject before HTTP; old completion callbacks are suppressed. Mutation context exposes `assertCurrent` for a second request or external side effect after an await. Shipping checks before setting its method, GDPR export before downloading, and review deletion between invalidations.

Adoption covers 41 mutation sites across admin, cart/catalog actions, orders, payments, profile, reviews, shipping and the stylist. Auth first-factor, reset and token-verification flows retain their intentional auth behavior; the authenticated resend action on the verification page also uses the guard.

`useSessionQuery` binds 24 private query observers across 20 files to their render generation and passive session snapshot. Every request/retry/refetch checks before execution and after awaiting a result/error. It preserves enabled and configured retry behavior but stops stale retries. This closes A's 500 response followed by B's login during retry delay, even before the storage event reaches A's observer. Private cart queries include the SiteHeader. Public catalog queries and the public review infinite query retain existing hooks; no private useQueries/infinite query was present.

QueryProvider still creates a fresh QueryClient and remounts the subtree on generation changes. This matters because clearing MutationCache alone does not stop pending callbacks, and existing observers retain their original client. Protected layouts gate orders/checkout/admin mounting; admin orders/users additionally require ADMIN.

Profile verification resend displays explicit mail-unavailable capability text and backend 503 errors. New passwords use 15 Unicode code points and at most 72 UTF-8 bytes; login accepts older short passwords. The server remains authoritative for its small common-password denylist. German copy and localized server/client error mapping are the next user-requested step, not a completed result here.

## Authored regressions awaiting execution

- `src/lib/auth/session.persistence.test.ts`: token-write failure; marker rollback; failed rollback read by another tab; unavailable storage/Web Locks; settled sign-out.
- `src/lib/auth/AuthProvider.persistence.test.tsx`: failed persistent sign-out reaches a ready public state with one error, without recursive restoration.
- `src/lib/auth/AuthProvider.test.tsx`: original account/cache and MFA gates, stale retained login/logout callbacks, preservation of a newer guest cart during MFA.
- `src/lib/auth/useSessionMutation.test.tsx`: queued-before-B execution, delayed success/error/settled and per-call callbacks, async second-step guard, current success.
- `src/lib/auth/useSessionQuery.test.tsx`: A 500 then B during retry delay; late private result; enabled false/current refetch.
- `src/lib/api/cartToken.test.ts`: exact first-factor header; conditional guest forget; late cart result after guest replacement/session publication.
- `src/features/orders/CheckoutPage.test.tsx`: existing idempotency/promo coverage plus delayed checkout callback cannot redirect B.
- `src/features/profile/SecurityPanel.test.tsx`: delayed erasure callback cannot log B out.
- `src/features/shipping/ShippingPage.test.tsx`: address completion under A cannot start method update as B.
- `src/features/profile/ProfileForm.test.tsx`: disabled capability and visible 503 without false success.

Remaining verification: TypeScript inference against the installed TanStack/React versions, full existing client regression suite, and a controlled real-browser two-tab exercise for Web Locks/storage failure behavior. Unit tests model the shared lock; they are not evidence of an executed real-browser scenario. No source-level session defect remains intentionally open from the confirmed review findings. Persistent storage unavailable means signing in is disabled until storage is allowed and the page reloaded; interrupted checkout/payment keys keep the existing safe recovery behavior.
