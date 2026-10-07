#!/usr/bin/env python3
"""
Comprehensive API Test Suite for Subscriptions Module
Follows /skill:api-tester standards:
- Functional Validation (6 endpoints, positive & negative flows, business state transitions)
- Security Validation (Authentication, Authorization, JWT tampering, SQL injection resilience)
- Performance & SLA Validation (Latency SLA < 200ms, concurrency benchmarks, p50/p90/p95/p99)
"""

import sys
import time
import json
import concurrent.futures
import requests

BASE_URL = "http://localhost:8080"

# Test credentials
USER_502 = {
    "usernameOrEmail": "tester123@example.com",
    "password": "admin"
}
USER_702 = {
    "usernameOrEmail": "testuser01@example.com",
    "password": "admin"
}

DEVICE_INFO = {
    "deviceType": "Desktop",
    "deviceName": "ApiTester-Runner",
    "osName": "Linux",
    "osVersion": "7.0",
    "browserName": "Chrome",
    "browserVersion": "120.0",
    "screenWidth": 1920,
    "screenHeight": 1080,
    "userAgent": "api-tester-agent",
    "language": "vi-VN",
    "timeZone": "Asia/Ho_Chi_Minh"
}

results = {
    "functional": [],
    "security": [],
    "performance": {}
}

def log_test(category, name, passed, details=""):
    status_str = "PASS" if passed else "FAIL"
    print(f"[{status_str}] [{category.upper()}] {name}: {details}")
    results[category].append({
        "name": name,
        "passed": passed,
        "details": details
    })

def login(creds):
    url = f"{BASE_URL}/api/auth/login"
    payload = {
        "usernameOrEmail": creds["usernameOrEmail"],
        "password": creds["password"],
        "deviceInfo": DEVICE_INFO
    }
    resp = requests.post(url, json=payload, timeout=10)
    if resp.status_code == 200:
        data = resp.json()
        return data.get("data", {}).get("accessToken")
    raise RuntimeError(f"Login failed for {creds['usernameOrEmail']}: {resp.status_code} {resp.text}")

def clean_user_subscriptions(user_id):
    """Clean subscriptions for test isolation via sqlplus command"""
    import subprocess
    cmd = f"""docker exec -i oracle-db sqlplus -s "/ as sysdba" << 'EOF'
ALTER SESSION SET CONTAINER = XEPDB1;
DELETE FROM SPRING_APP.subscription_audit_logs WHERE user_id = {user_id};
DELETE FROM SPRING_APP.user_subscriptions WHERE user_id = {user_id};
DELETE FROM SPRING_APP.customer_payment_profiles WHERE user_id = {user_id};
UPDATE SPRING_APP.users SET user_rank = 'MEMBER' WHERE id = {user_id};
COMMIT;
EXIT;
EOF
"""
    subprocess.run(cmd, shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def run_functional_tests(token_502, token_702):
    print("\n================== 1. FUNCTIONAL TESTS ==================")
    headers_502 = {"Authorization": f"Bearer {token_502}", "Content-Type": "application/json"}
    headers_702 = {"Authorization": f"Bearer {token_702}", "Content-Type": "application/json"}

    # 1.1 GET /api/v1/subscriptions/plans (Public)
    try:
        r = requests.get(f"{BASE_URL}/api/v1/subscriptions/plans", timeout=5)
        passed = (r.status_code == 200 and 
                  isinstance(r.json().get("data"), list) and 
                  len(r.json()["data"]) >= 2)
        plans = r.json().get("data", [])
        plan_codes = [p.get("planCode") for p in plans]
        log_test("functional", "GET /api/v1/subscriptions/plans (Public access)", passed, 
                 f"Status={r.status_code}, Found plans={plan_codes}")
    except Exception as e:
        log_test("functional", "GET /api/v1/subscriptions/plans (Public access)", False, str(e))

    # 1.2 GET /api/v1/subscriptions/my (User 502 with no subscription initially)
    try:
        r = requests.get(f"{BASE_URL}/api/v1/subscriptions/my", headers=headers_502, timeout=5)
        passed = (r.status_code == 200 and r.json().get("data") is None)
        log_test("functional", "GET /api/v1/subscriptions/my (User without sub)", passed,
                 f"Status={r.status_code}, data={r.json().get('data')}")
    except Exception as e:
        log_test("functional", "GET /api/v1/subscriptions/my (User without sub)", False, str(e))

    # 1.3 POST /api/v1/subscriptions/google-pay/register (Register Free Trial for ANH_KIM)
    sub_id_502 = None
    try:
        payload = {
            "planCode": "ANH_KIM",
            "paymentToken": "tok_gpay_test_vault_4242",
            "cardNetwork": "VISA",
            "lastFour": "4242"
        }
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/google-pay/register", 
                          headers=headers_502, json=payload, timeout=10)
        data = r.json().get("data", {})
        sub_id_502 = data.get("id")
        passed = (r.status_code == 200 and 
                  data.get("subscriptionState") == "TRIALING" and 
                  data.get("isTrial") is True and 
                  data.get("targetRank") == "SILVER" and
                  data.get("autoRenewing") is True)
        log_test("functional", "POST /api/v1/subscriptions/google-pay/register (ANH_KIM 7-day Trial)", passed,
                 f"Status={r.status_code}, subId={sub_id_502}, state={data.get('subscriptionState')}, isTrial={data.get('isTrial')}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/google-pay/register (ANH_KIM 7-day Trial)", False, str(e))

    # 1.4 POST /api/v1/subscriptions/google-pay/register (Duplicate trial registration check)
    try:
        payload = {
            "planCode": "ANH_KIM",
            "paymentToken": "tok_gpay_duplicate",
            "cardNetwork": "MASTERCARD",
            "lastFour": "5555"
        }
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/google-pay/register", 
                          headers=headers_502, json=payload, timeout=10)
        status_val = r.json().get("status") if isinstance(r.json().get("status"), dict) else {}
        err_msg = status_val.get("message") or r.json().get("detail") or r.text[:80]
        passed = (r.status_code in (400, 422, 500) and "đã có gói" in r.text)
        log_test("functional", "POST /api/v1/subscriptions/google-pay/register (Reject duplicate active sub)", passed,
                 f"Status={r.status_code}, msg={err_msg}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/google-pay/register (Reject duplicate active sub)", False, str(e))

    # 1.5 GET /api/v1/subscriptions/my (User 502 with active trialing subscription)
    try:
        r = requests.get(f"{BASE_URL}/api/v1/subscriptions/my", headers=headers_502, timeout=5)
        data = r.json().get("data", {})
        passed = (r.status_code == 200 and 
                  data.get("id") == sub_id_502 and 
                  data.get("subscriptionState") == "TRIALING" and
                  data.get("planCode") == "ANH_KIM")
        log_test("functional", "GET /api/v1/subscriptions/my (Verify trialing sub details)", passed,
                 f"Status={r.status_code}, subId={data.get('id')}, planCode={data.get('planCode')}")
    except Exception as e:
        log_test("functional", "GET /api/v1/subscriptions/my (Verify trialing sub details)", False, str(e))

    # 1.6 POST /api/v1/subscriptions/{id}/renew (MIT recurring charge & Fineract accounting)
    if sub_id_502:
        try:
            r = requests.post(f"{BASE_URL}/api/v1/subscriptions/{sub_id_502}/renew", 
                              headers=headers_502, timeout=10)
            data = r.json()
            passed = (r.status_code == 200 and data.get("data") is True)
            log_test("functional", f"POST /api/v1/subscriptions/{sub_id_502}/renew (MIT Recurring Renewal)", passed,
                     f"Status={r.status_code}, renewed={data.get('data')}, message={data.get('status', {}).get('message')}")
        except Exception as e:
            log_test("functional", f"POST /api/v1/subscriptions/{sub_id_502}/renew (MIT Recurring Renewal)", False, str(e))

        # Verify state updated to ACTIVE and isTrial=False
        try:
            r = requests.get(f"{BASE_URL}/api/v1/subscriptions/my", headers=headers_502, timeout=5)
            data = r.json().get("data", {})
            passed = (data.get("subscriptionState") == "ACTIVE" and data.get("isTrial") is False)
            log_test("functional", "GET /api/v1/subscriptions/my (Post-renew ACTIVE state check)", passed,
                     f"state={data.get('subscriptionState')}, isTrial={data.get('isTrial')}")
        except Exception as e:
            log_test("functional", "GET /api/v1/subscriptions/my (Post-renew ACTIVE state check)", False, str(e))

    # 1.7 POST /api/v1/subscriptions/{id}/cancel (FTC Click-to-Cancel)
    if sub_id_502:
        try:
            payload = {"reason": "Không còn nhu cầu sử dụng dịch vụ trong kỳ tới"}
            r = requests.post(f"{BASE_URL}/api/v1/subscriptions/{sub_id_502}/cancel", 
                              headers=headers_502, json=payload, timeout=10)
            data = r.json().get("data", {})
            passed = (r.status_code == 200 and 
                      data.get("subscriptionState") == "CANCELLED" and 
                      data.get("autoRenewing") is False)
            log_test("functional", f"POST /api/v1/subscriptions/{sub_id_502}/cancel (Click-to-Cancel)", passed,
                     f"Status={r.status_code}, state={data.get('subscriptionState')}, autoRenewing={data.get('autoRenewing')}")
        except Exception as e:
            log_test("functional", f"POST /api/v1/subscriptions/{sub_id_502}/cancel (Click-to-Cancel)", False, str(e))

    # 1.8 POST /api/v1/subscriptions/purchase-onetime (User 702 - VNPay Payment)
    onetime_sub_id = None
    initial_expiry = None
    try:
        payload = {
            "planCode": "ANH_KIM",
            "paymentMethod": "VNPAY",
            "transactionReference": "VNPAY_TXN_TEST_2026_01",
            "note": "Thanh toan mua 1 lan goi Anh Kim qua VNPay QR"
        }
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/purchase-onetime", 
                          headers=headers_702, json=payload, timeout=10)
        data = r.json().get("data", {})
        onetime_sub_id = data.get("id")
        initial_expiry = data.get("expiryTime")
        passed = (r.status_code == 200 and 
                  data.get("subscriptionState") == "ACTIVE" and 
                  data.get("autoRenewing") is False and
                  data.get("isTrial") is False and
                  data.get("targetRank") == "SILVER")
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (VNPay Gateway)", passed,
                 f"Status={r.status_code}, subId={onetime_sub_id}, state={data.get('subscriptionState')}, expiry={initial_expiry}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (VNPay Gateway)", False, str(e))

    # 1.9 POST /api/v1/subscriptions/purchase-onetime (User 702 - BANK_TRANSFER Cumulative Extension)
    try:
        payload = {
            "planCode": "ANH_KIM",
            "paymentMethod": "BANK_TRANSFER",
            "transactionReference": "BANK_TXN_987654321",
            "note": "Chuyen khoan Ngan hang Vietcombank gia han them 1 thang"
        }
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/purchase-onetime", 
                          headers=headers_702, json=payload, timeout=10)
        data = r.json().get("data", {})
        new_expiry = data.get("expiryTime")
        # Ensure new_expiry > initial_expiry
        passed = (r.status_code == 200 and 
                  data.get("id") == onetime_sub_id and
                  new_expiry > initial_expiry)
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (BANK_TRANSFER Cumulative Extension)", passed,
                 f"Old expiry={initial_expiry} -> New expiry={new_expiry}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (BANK_TRANSFER Cumulative Extension)", False, str(e))

    # 1.10 POST /api/v1/subscriptions/purchase-onetime (User 702 - CREDIT_CARD upgrade to MA_THUAT DIAMOND)
    try:
        payload = {
            "planCode": "MA_THUAT",
            "paymentMethod": "CREDIT_CARD",
            "transactionReference": "CC_TXN_VISA_9999",
            "note": "Nang cap len goi Ma Thuat Diamond qua the VISA quoc te"
        }
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/purchase-onetime", 
                          headers=headers_702, json=payload, timeout=10)
        data = r.json().get("data", {})
        passed = (r.status_code == 200 and 
                  data.get("planCode") == "MA_THUAT" and
                  data.get("targetRank") == "DIAMOND")
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (CREDIT_CARD Upgrade MA_THUAT)", passed,
                 f"Plan={data.get('planCode')}, targetRank={data.get('targetRank')}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (CREDIT_CARD Upgrade MA_THUAT)", False, str(e))

    # 1.11 Edge Cases & Input Validation
    # Missing planCode in purchase-onetime
    try:
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/purchase-onetime", 
                          headers=headers_702, json={"paymentMethod": "VNPAY"}, timeout=5)
        passed = (r.status_code == 400)
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (Validation: Missing planCode)", passed,
                 f"Status={r.status_code}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (Validation: Missing planCode)", False, str(e))

    # Missing paymentMethod in purchase-onetime
    try:
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/purchase-onetime", 
                          headers=headers_702, json={"planCode": "ANH_KIM"}, timeout=5)
        passed = (r.status_code == 400)
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (Validation: Missing paymentMethod)", passed,
                 f"Status={r.status_code}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (Validation: Missing paymentMethod)", False, str(e))

    # Non-existent planCode
    try:
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/purchase-onetime", 
                          headers=headers_702, json={"planCode": "NON_EXISTENT_PLAN", "paymentMethod": "VNPAY"}, timeout=5)
        passed = (r.status_code in (400, 404, 500) and "Không tìm thấy" in r.text)
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (Validation: Non-existent planCode)", passed,
                 f"Status={r.status_code}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/purchase-onetime (Validation: Non-existent planCode)", False, str(e))

    # Non-existent subscription ID for renew
    try:
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/999999/renew", 
                          headers=headers_702, timeout=5)
        passed = (r.status_code in (400, 404, 500) and "Không tìm thấy" in r.text)
        log_test("functional", "POST /api/v1/subscriptions/999999/renew (Non-existent sub ID)", passed,
                 f"Status={r.status_code}")
    except Exception as e:
        log_test("functional", "POST /api/v1/subscriptions/999999/renew (Non-existent sub ID)", False, str(e))


def run_security_tests(token):
    print("\n================== 2. SECURITY TESTS ==================")

    # 2.1 Unauthenticated requests to protected endpoints
    protected_endpoints = [
        ("GET", "/api/v1/subscriptions/my", None),
        ("POST", "/api/v1/subscriptions/google-pay/register", {"planCode": "ANH_KIM", "paymentToken": "tok"}),
        ("POST", "/api/v1/subscriptions/purchase-onetime", {"planCode": "ANH_KIM", "paymentMethod": "VNPAY"}),
        ("POST", "/api/v1/subscriptions/1/renew", None),
        ("POST", "/api/v1/subscriptions/1/cancel", {"reason": "test"})
    ]

    for method, ep, body in protected_endpoints:
        try:
            if method == "GET":
                r = requests.get(f"{BASE_URL}{ep}", timeout=5)
            else:
                r = requests.post(f"{BASE_URL}{ep}", json=body, timeout=5)
            # Expect 401 Unauthorized or 403 Forbidden
            passed = (r.status_code in (401, 403))
            log_test("security", f"Reject unauthenticated {method} {ep}", passed, f"Status={r.status_code}")
        except Exception as e:
            log_test("security", f"Reject unauthenticated {method} {ep}", False, str(e))

    # 2.2 Tampered / Malformed JWT Token
    try:
        tampered_token = token[:-5] + "XXXXX"
        r = requests.get(f"{BASE_URL}/api/v1/subscriptions/my", 
                         headers={"Authorization": f"Bearer {tampered_token}"}, timeout=5)
        passed = (r.status_code in (401, 403))
        log_test("security", "Reject tampered JWT token", passed, f"Status={r.status_code}")
    except Exception as e:
        log_test("security", "Reject tampered JWT token", False, str(e))

    # 2.3 SQL Injection resilience
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    sqli_payloads = [
        ("'; DROP TABLE user_subscriptions; --", "SQLi in cancel reason"),
        ("' OR '1'='1", "SQLi in planCode")
    ]

    try:
        # SQLi in cancel reason
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/1/cancel", 
                          headers=headers, json={"reason": "'; DROP TABLE user_subscriptions; --"}, timeout=5)
        # Should not crash database or return internal SQL syntax error
        passed = (r.status_code in (200, 400, 404, 500) and "ORA-" not in r.text and "SQLSyntax" not in r.text)
        log_test("security", "SQL Injection resilience in cancel reason", passed, f"Status={r.status_code}, safe=True")
    except Exception as e:
        log_test("security", "SQL Injection resilience in cancel reason", False, str(e))

    try:
        # SQLi in purchase planCode
        r = requests.post(f"{BASE_URL}/api/v1/subscriptions/purchase-onetime", 
                          headers=headers, json={"planCode": "' OR '1'='1", "paymentMethod": "VNPAY"}, timeout=5)
        passed = (r.status_code in (400, 404, 500) and "ORA-" not in r.text and "SQLSyntax" not in r.text)
        log_test("security", "SQL Injection resilience in planCode parameter", passed, f"Status={r.status_code}, safe=True")
    except Exception as e:
        log_test("security", "SQL Injection resilience in planCode parameter", False, str(e))


def run_performance_tests(token):
    print("\n================== 3. PERFORMANCE & CONCURRENCY TESTS ==================")
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    # Benchmark: GET /api/v1/subscriptions/plans (Public)
    latencies_plans = []
    for _ in range(50):
        t0 = time.perf_counter()
        r = requests.get(f"{BASE_URL}/api/v1/subscriptions/plans", timeout=5)
        latencies_plans.append((time.perf_counter() - t0) * 1000)

    latencies_plans.sort()
    p50_plans = latencies_plans[int(len(latencies_plans) * 0.50)]
    p90_plans = latencies_plans[int(len(latencies_plans) * 0.90)]
    p95_plans = latencies_plans[int(len(latencies_plans) * 0.95)]
    p99_plans = latencies_plans[int(len(latencies_plans) * 0.99)]

    sla_plans_passed = p95_plans < 200.0
    print(f"-> Benchmark GET /plans (50 reqs): p50={p50_plans:.2f}ms, p90={p90_plans:.2f}ms, p95={p95_plans:.2f}ms, p99={p99_plans:.2f}ms | SLA (<200ms): {'PASS' if sla_plans_passed else 'FAIL'}")

    # Benchmark: GET /api/v1/subscriptions/my (Authenticated)
    latencies_my = []
    for _ in range(50):
        t0 = time.perf_counter()
        r = requests.get(f"{BASE_URL}/api/v1/subscriptions/my", headers=headers, timeout=5)
        latencies_my.append((time.perf_counter() - t0) * 1000)

    latencies_my.sort()
    p50_my = latencies_my[int(len(latencies_my) * 0.50)]
    p90_my = latencies_my[int(len(latencies_my) * 0.90)]
    p95_my = latencies_my[int(len(latencies_my) * 0.95)]
    p99_my = latencies_my[int(len(latencies_my) * 0.99)]

    sla_my_passed = p95_my < 200.0
    print(f"-> Benchmark GET /my (50 reqs): p50={p50_my:.2f}ms, p90={p90_my:.2f}ms, p95={p95_my:.2f}ms, p99={p99_my:.2f}ms | SLA (<200ms): {'PASS' if sla_my_passed else 'FAIL'}")

    # Concurrency test: 30 parallel requests
    print("-> Executing 30 concurrent requests to GET /plans...")
    def fetch_plans():
        t0 = time.perf_counter()
        resp = requests.get(f"{BASE_URL}/api/v1/subscriptions/plans", timeout=5)
        return resp.status_code, (time.perf_counter() - t0) * 1000

    concurrent_samples = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=15) as executor:
        futures = [executor.submit(fetch_plans) for _ in range(30)]
        for f in concurrent.futures.as_completed(futures):
            status_code, dur = f.result()
            concurrent_samples.append((status_code, dur))

    all_200 = all(s[0] == 200 for s in concurrent_samples)
    avg_dur = sum(s[1] for s in concurrent_samples) / len(concurrent_samples)
    print(f"-> Concurrency results: 30/30 status 200: {all_200}, average duration: {avg_dur:.2f}ms")

    results["performance"] = {
        "plans": {"p50": p50_plans, "p90": p90_plans, "p95": p95_plans, "p99": p99_plans, "sla_passed": sla_plans_passed},
        "my": {"p50": p50_my, "p90": p90_my, "p95": p95_my, "p99": p99_my, "sla_passed": sla_my_passed},
        "concurrency": {"total": 30, "all_success": all_200, "avg_ms": avg_dur}
    }


def main():
    print("=== STARTING COMPREHENSIVE SUBSCRIPTIONS API TEST SUITE ===")
    
    # 0. Clean test users
    print("-> Cleaning test users (ID 502, 702) for isolated testing...")
    clean_user_subscriptions(502)
    clean_user_subscriptions(702)

    # 1. Login
    print("-> Authenticating test users...")
    token_502 = login(USER_502)
    token_702 = login(USER_702)
    print("-> Tokens acquired successfully.")

    # 2. Run Test Phases
    run_functional_tests(token_502, token_702)
    run_security_tests(token_502)
    run_performance_tests(token_502)

    # 3. Summary
    print("\n================== SUMMARY ==================")
    total_func = len(results["functional"])
    passed_func = sum(1 for t in results["functional"] if t["passed"])
    total_sec = len(results["security"])
    passed_sec = sum(1 for t in results["security"] if t["passed"])

    print(f"Functional Tests: {passed_func}/{total_func} PASSED ({passed_func/total_func*100:.1f}%)")
    print(f"Security Tests:   {passed_sec}/{total_sec} PASSED ({passed_sec/total_sec*100:.1f}%)")
    print(f"Performance SLA:  Plans p95={results['performance']['plans']['p95']:.2f}ms, My p95={results['performance']['my']['p95']:.2f}ms")
    
    if passed_func == total_func and passed_sec == total_sec and results['performance']['plans']['sla_passed'] and results['performance']['my']['sla_passed']:
        print("\n>>> ALL TESTS PASSED SUCCESSFULLY! QUALITY STATUS: PASS <<<")
        return 0
    else:
        print("\n>>> SOME CHECKS FAILED! <<<")
        return 1

if __name__ == "__main__":
    sys.exit(main())
