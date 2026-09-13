# Owner integration checklist

This application is permanently a **fictional store with test payments**. `demo.enabled=false`, live Stripe keys and legacy live payment providers are rejected. Preparation does not mean a service is connected or verified. No keys were activated, emails sent, public deployment changed or real payment attempted during remediation.

## Local mode

With `PAYMENTS_DEFAULT_PROVIDER=stub`, `NOTIFICATION_PROVIDER=disabled` and `AI_ENABLED=false`, browsing, accounts, fictional orders and owner-authorized local payment simulation can run without external providers. The UI labels the simulation honestly and exposes the provider availability from `/api/v1/storefront/config`. Email verification/reset remain unavailable with an explicit 503; registration does not claim a message was sent. Use synthetic account/address data.

## Privileged account enrollment

Before first ADMIN/MANAGER sign-in, generate a 32-byte Base64 `MFA_ENCRYPTION_KEY` (`openssl rand -base64 32`) and store it in the selected secret manager. Provision the administrator through the one-off bootstrap settings with a strong password, then disable bootstrap. Sign in, enroll the displayed TOTP secret in your authenticator and confirm a code. Save the one-time recovery codes in a password manager before continuing. Recovery requires the account's password and an unused recovery code, then a new factor; it does not bypass MFA.

The key encrypts persisted factors. Back it up separately from the database with restricted access. Replacing it blindly makes existing factors unreadable; plan key rotation/migration or deliberate privileged re-enrollment. No production key is supplied by the repository. Password changes/resets and refresh-token reuse revoke sessions via auth version; ordinary logout revokes its refresh chain while an already issued access token has at most 15 minutes left. See `docs/identity-remediation-2026-09-12.md` for the exact policy.

## Stripe sandbox / test mode

1. Select a Stripe sandbox/test environment owned by you. Obtain a **test secret key** (`sk_test_...`). No publishable key or embedded Elements is needed: checkout is hosted on Stripe. Do not use a real card.
2. Set `PAYMENTS_DEFAULT_PROVIDER=stripe`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `FRONTEND_BASE_URL` and `PAYMENT_CURRENCY=EUR`. The secret key and webhook signing secret must belong to the same selected test environment. Keep both private. Configure the final HTTPS frontend return origin; localhost HTTP is allowed for local work.
3. Register `POST /api/v1/payments/stripe/webhook` with API version **2025-06-30.basil**, matching the adapter's pinned version. Subscribe to `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`, `checkout.session.expired`, `refund.created`, `refund.updated`, `refund.failed`. Only card checkout is offered. Local Stripe CLI forwarding has its own signing secret: use that secret for the local server, not the dashboard endpoint's secret.
4. Make a disposable demo order, choose delivery/pickup, open hosted test checkout and use a documented Stripe test card. Verify the signed event changes the payment/order and consumes the reservation once. Repeat event delivery; reload and retry after a deliberately lost response. Verify wrong signatures, missing/live flags and mismatched amount/currency/reference do not change local state. The browser return URL itself never marks an order paid.
5. From ADMIN test a full refund and its pending/failure/success responses. Local inventory and order state change only after confirmed success. An unknown result must be reconciled before another refund. A confirmed failed/canceled refund can be explicitly retried with its previous `refundOperationKey`; duplicate clicks replay the same replacement operation.

The server freezes the exact create body and idempotency key before HTTP. It can safely retry that original create for up to 23 hours, then stops automatic resubmission. It preserves reservations for unknown Stripe outcomes. For a later unresolved create, find the original Checkout Session in **that same test environment** by `shopupu_payment_id`/`shopupu_order_id` metadata. An authenticated ADMIN can call `POST /api/v1/admin/payments/{id}/recover-stripe-session` with `{ "sessionId": "cs_test_..." }`. This reads and validates the session and its exact local binding before applying its state/checkout URL; it creates no new Stripe operation. Never release stock merely because the local timeout elapsed.

Refund recovery looks up the persisted refund ID or lists refunds for the bound PaymentIntent and matches the original operation metadata. Do not perform manual dashboard/partial refunds as a substitute for this full-refund demo flow: dashboard refunds without the application operation metadata require an operator reconciliation procedure and are not silently applied. Close unknown operations before changing the selected Stripe account/provider. Provider-side records and funds are only test objects; no real goods or money are involved.

Record test environment identifier (no secrets), configured API version, selected backend/frontend image digests, test payment/refund IDs, webhook deliveries, recovery results and timestamps. Mock tests do not replace this owner-run integration acceptance. [Stripe testing](https://docs.stripe.com/testing), [webhook signatures](https://docs.stripe.com/webhooks/signature), [idempotent requests](https://docs.stripe.com/api/idempotent_requests).

## Email: choose and verify one sender

Choose SMTP or Resend, verify an owned sender/domain and authorize a control inbox for acceptance testing. Configure SMTP host/port/TLS/auth/from or Resend API key/from, then explicitly set `NOTIFICATION_PROVIDER=smtp` or `resend`. Credentials alone do not enable sending. Set `FRONTEND_BASE_URL` to the actual HTTPS application origin. Verify SPF/DKIM and delivery policy with the selected service.

Test registration verification, one-use/expired/reset tokens, password change, order updates, provider rejection and bounded retry using only the approved control inbox. Messages are queued **after database commit**; UI success means the request was accepted, not proof it reached the inbox. Monitor `shopupu_notification_delivery_total` by `kind`/`outcome` (`accepted`, `retry`, `failed`, `unavailable`). Confirm links, sender identity, language and browser landing pages. Never log one-time links/tokens or paste keys into reports.

The current delivery queue is bounded and in memory: a process crash can lose queued mail. Users can explicitly request a fresh reset/verification message when delivery is uncertain; order notifications are best effort. If guaranteed delivery across restarts is a release requirement, a persistent outbox and provider idempotency are required before enabling that promise. This limitation must remain visible in the operator acceptance and must not be described as guaranteed delivery.

## Google and AI

Google: register the frontend's exact authorized origins with an owned OAuth web client. Use the **same** client ID in backend `GOOGLE_CLIENT_ID` and frontend build-time `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, rebuild the frontend and test success, cancellation and wrong-audience rejection. Privileged Google login still requires the local TOTP challenge. Provider consent and external account configuration are owner tasks.

AI: keep disabled until provider choice and spending limits are approved. Use the existing DeepSeek `deepseek-v4-flash` with thinking disabled; configure the selected embedding provider/model/dimensions. Quotas and input/output/concurrency limits are in `.env.example`. They are per process, reset on restart and do not replace provider spending limits. Run the fixed small EN/DE evaluation described in `docs/ai-guardrails-2026-09-12.md`; no unrestricted live corpus or automatic key spending is part of setup. Explain external processing of stylist text/review text in the deployment notice.

## Deployment, operations and privacy

Provision restricted DB roles, rotate historical credentials, perform an isolated backup/restore and rehearse collation repair before touching the existing database (`docs/database-recovery.md`). Keep secrets out of images, build arguments, client bundles and source. Pin and record deployment image digests/revisions. The current running user containers have not been replaced.

Select a monitoring system, its owner and an alert destination. `/actuator/health` is public; diagnostics/Prometheus require ADMIN access. A scraper needs a controlled access arrangement with token refresh or a private authenticated proxy; do not publish an unauthenticated metrics endpoint or embed a long-lived administrator token. Install and verify `ops/prometheus-rules.yml` in the selected system, adjust the job label and test one failure notification to the approved recipient. Until that test is recorded, external alert delivery is unverified.

Configure HTTPS/DNS/edge routes, isolate the origin and strip incoming forwarding headers before enabling `SERVER_FORWARD_HEADERS_STRATEGY=framework`. Verify forged client IP headers cannot mint new auth buckets and callback signatures survive forwarding. Confirm both `/api/v1/*` and image routes, product HTTP 404s, auth/MFA, EN/DE pages and checkout on the exact images.

Supply the operator identity/contact details and deployment-specific privacy/data-processing notice before public release. Select retention/backups/provider agreements for that deployment; the technical demo notice is not a claim of legal compliance. All fictional product/review/test-payment notices must remain visible.
