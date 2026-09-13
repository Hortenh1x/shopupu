#!/usr/bin/env python3
"""Bounded, synthetic HTTP acceptance against the harness's proven local fixture only."""
import base64
import concurrent.futures
import hashlib
import hmac
import json
import math
import os
from pathlib import Path
import struct
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:18080"
FIXTURE = json.loads(Path(sys.argv[1]).read_text())
REPORT = Path(sys.argv[2])
PASSWORD = os.environ["FULLSTACK_PASSWORD"]
CHECKS = []


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def call(method, path, data=None, token=None, headers=None, expected=(200,)):
    assert path.startswith("/") and not path.startswith("//")
    outgoing = {"Content-Type": "application/json", **(headers or {})}
    if token:
        outgoing["Authorization"] = "Bearer " + token
    encoded = data if isinstance(data, bytes) else None if data is None else json.dumps(data).encode()
    request = urllib.request.Request(BASE + path, data=encoded, headers=outgoing, method=method)
    started = time.monotonic()
    try:
        response = urllib.request.build_opener(NoRedirect()).open(request, timeout=3)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read(1024 * 1024 + 1)
    assert len(raw) <= 1024 * 1024, "Unexpected response size"
    status = response.status
    assert status in expected, f"{method} {path}: expected {expected}, received {status}"
    result = json.loads(raw) if raw and "json" in response.headers.get("Content-Type", "") else raw
    if status >= 400:
        assert isinstance(result, dict) and result.get("code") and result.get("requestId"), "Problem Details contract missing"
        assert response.headers.get("X-Request-Id") == result["requestId"]
        assert "accessToken" not in result and "externalPaymentId" not in result
    return result, (time.monotonic() - started) * 1000


def login(email, privileged=False):
    response, _ = call("POST", "/api/v1/auth/login", {"email": email, "password": PASSWORD})
    if not privileged:
        assert response["status"] == "AUTHENTICATED"
        return response
    assert response["status"] == "MFA_ENROLLMENT_REQUIRED" and "accessToken" not in response
    enrollment, _ = call("POST", "/api/v1/auth/mfa/enrollment/start", {"challengeToken": response["challengeToken"]})
    secret = enrollment["secret"]
    secret_bytes = base64.b32decode(secret + "=" * ((-len(secret)) % 8))
    digest = hmac.new(secret_bytes, struct.pack(">Q", int(time.time()) // 30), hashlib.sha1).digest()
    offset = digest[-1] & 15
    code = str((struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7fffffff) % 1_000_000).zfill(6)
    authenticated, _ = call("POST", "/api/v1/auth/mfa/enrollment/confirm", {"challengeToken": response["challengeToken"], "code": code})
    assert authenticated["status"] == "AUTHENTICATED" and len(authenticated["recoveryCodes"]) == 10
    CHECKS.append("privileged login requires and completes local MFA")
    return authenticated


def smoke():
    product, _ = call("GET", f"/api/v1/catalog/products/{FIXTURE['productId']}")
    assert product["slug"] == FIXTURE["productSlug"], "Wrong backend/fixture; refusing writes"
    config, _ = call("GET", "/api/v1/storefront/config")
    assert config["demoMode"] and config["fictionalProducts"]
    assert config["payments"]["mode"] == "LOCAL_SIMULATION" and config["payments"]["testMode"]
    assert not config["email"]["available"] and not config["ai"]["available"]
    schema, _ = call("GET", "/v3/api-docs")
    for path in ("/api/v1/auth/mfa/enrollment/confirm", "/api/v1/admin/payments/{id}/refund/retry", "/api/v1/users/me/export"):
        assert path in schema["paths"], f"Missing current API contract: {path}"
    review_schema = schema["components"]["schemas"]["ReviewResponse"]
    assert "source" in review_schema["properties"]
    CHECKS.append("current schema includes MFA/refund/privacy/provenance contracts")
    call("GET", "/api/v1/catalog/products/2147483000", expected=(404,))
    call("GET", "/api/v1/admin/orders", expected=(401,))
    call("POST", "/api/v1/payments/callback", {"externalEventId": "unsigned", "externalPaymentId": "untrusted", "status": "SUCCEEDED"}, expected=(403,))
    alice = login(FIXTURE["buyerAEmail"])
    bob = login(FIXTURE["buyerBEmail"])
    admin = login(FIXTURE["adminEmail"], privileged=True)
    manager = login(FIXTURE["managerEmail"], privileged=True)
    a, b = alice["accessToken"], bob["accessToken"]
    call("GET", "/api/v1/admin/catalog/products", token=a, expected=(403,))
    call("GET", "/api/v1/admin/catalog/products", token=manager["accessToken"])
    call("GET", "/api/v1/admin/orders", token=manager["accessToken"], expected=(403,))
    call("POST", "/api/v1/cart/items", {"variantId": FIXTURE["variantId"], "quantity": 1}, token=a)
    order_key = str(uuid.uuid4())
    order, _ = call("POST", "/api/v1/orders/checkout", {}, token=a, headers={"Idempotency-Key": order_key}, expected=(201,))
    replay, _ = call("POST", "/api/v1/orders/checkout", {}, token=a, headers={"Idempotency-Key": order_key}, expected=(201,))
    assert replay["id"] == order["id"]
    call("POST", "/api/v1/orders/checkout", {"promoCode": "different"}, token=a, headers={"Idempotency-Key": order_key}, expected=(409,))
    call("POST", "/api/v1/shipping/method", {"orderId": order["id"], "method": "LOCAL_PICKUP"}, token=a)
    payment_key = str(uuid.uuid4())
    payment, _ = call("POST", "/api/v1/payments", {"orderId": order["id"]}, token=a, headers={"Idempotency-Key": payment_key}, expected=(201,))
    call("POST", "/api/v1/payments", {"orderId": order["id"]}, token=b, headers={"Idempotency-Key": payment_key}, expected=(403,))
    call("GET", f"/api/v1/payments/{payment['id']}", token=b, expected=(403,))
    call("POST", f"/api/v1/payments/{payment['id']}/simulate-success", token=b, expected=(403,))
    for _ in range(2):
        paid, _ = call("POST", f"/api/v1/payments/{payment['id']}/simulate-success", token=a)
        assert paid["status"] == "SUCCEEDED"
    call("POST", f"/api/v1/admin/payments/{payment['id']}/refund", token=manager["accessToken"], expected=(403,))
    for _ in range(2):
        refund, _ = call("POST", f"/api/v1/admin/payments/{payment['id']}/refund", token=admin["accessToken"])
        assert refund["status"] == "REFUNDED" and refund["refundStatus"] == "SUCCEEDED"
    CHECKS.append("owner/key binding, repeat checkout/payment transitions and admin-only full local refund")
    # This generates one owned upload for the subsequent DB + uploads restore drill.
    png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0mEAAAAASUVORK5CYII=")
    boundary = "acceptance-" + uuid.uuid4().hex
    multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="fixture.png"\r\nContent-Type: image/png\r\n\r\n'.encode() + png + f'\r\n--{boundary}--\r\n'.encode())
    uploaded, _ = call("POST", f"/api/v1/admin/catalog/products/{FIXTURE['productId']}/images", multipart,
                       token=admin["accessToken"], headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}, expected=(201, 200))
    assert uploaded["url"].startswith(BASE + "/uploads/")
    contents, _ = call("GET", uploaded["url"][len(BASE):])
    assert hashlib.sha256(contents).digest() == hashlib.sha256(png).digest()
    CHECKS.append("owned synthetic PNG upload is retrievable without changing bytes")
    exported, _ = call("GET", "/api/v1/users/me/export", token=a)
    assert exported["records"]["payments"] and exported["scope"]
    assert PASSWORD not in json.dumps(exported) and alice["refreshToken"] not in json.dumps(exported)


def bounded_load():
    paths = ["/api/v1/catalog/products", f"/api/v1/catalog/products/{FIXTURE['productId']}", "/api/v1/storefront/config"]
    samples = []
    errors = []
    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        pending = set()
        for index in range(120):
            if time.monotonic() - started > 45:
                raise AssertionError("Bounded load exceeded its 45-second deadline")
            time.sleep(max(0, started + index / 4 - time.monotonic()))
            if len(pending) == 4:
                done, pending = concurrent.futures.wait(pending, return_when=concurrent.futures.FIRST_COMPLETED)
                for future in done:
                    try: samples.append(future.result()[1])
                    except Exception as error: errors.append(type(error).__name__)
            pending.add(pool.submit(call, "GET", paths[index % len(paths)]))
        for future in concurrent.futures.as_completed(pending):
            try: samples.append(future.result()[1])
            except Exception as error: errors.append(type(error).__name__)
    ordered = sorted(samples)
    p95 = ordered[max(0, math.ceil(len(ordered) * .95) - 1)] if ordered else None
    result = {"requests": 120, "maximumConcurrency": 4, "targetRequestsPerSecond": 4,
              "errors": len(errors), "p95Milliseconds": p95, "elapsedSeconds": time.monotonic() - started,
              "proposedP95BudgetMilliseconds": 750, "productionCapacityClaim": False}
    assert not errors and p95 is not None and p95 <= 750, f"Proposed isolated acceptance budget exceeded: {result}"
    return result


try:
    smoke()
    result = {"status": "PASS", "checks": CHECKS, "boundedPublicLoad": bounded_load(), "externalProviders": "NOT_TESTED"}
except Exception as error:
    result = {"status": "FAIL", "checksCompleted": CHECKS, "errorType": type(error).__name__, "detail": str(error), "externalProviders": "NOT_TESTED"}
    REPORT.write_text(json.dumps(result, indent=2))
    raise SystemExit(1)
REPORT.write_text(json.dumps(result, indent=2))
