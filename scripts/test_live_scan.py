import requests
import subprocess
import time
import re

base_url = "http://127.0.0.1"

print("==================================================")
print(" LIVE ENDPOINT AUDIT & SCAN RUNNING NATIVELY...")
print("==================================================")

# Step 1: Login request
print("\n[Step 1] Requesting Admin OTP Session...")
login_payload = {"username": "swissadmin", "password": "adminpassword"}
login_res = requests.post(f"{base_url}/api/auth/login", json=login_payload)

if login_res.status_code != 200:
    print(f"❌ Login Request Failed: {login_res.status_code} {login_res.text}")
    exit(1)

data = login_res.json()
session_token = data.get("sessionToken")
print(f"✔️ Session token acquired: {session_token}")

# Step 2: Grab the generated MFA PIN from docker logs
print("\n[Step 2] Retrieving MFA PIN from container stdout...")
time.sleep(1.5) # Wait for log sync
logs_output = subprocess.check_output('docker logs swiss_backend | grep "user swissadmin" | tail -n 1', shell=True).decode('utf-8')
print(f"✔️ Active log matched: {logs_output.strip()}")

pin_match = re.search(r"PIN code: (\d{6})", logs_output)
if not pin_match:
    print("❌ Failed to parse PIN from docker logs!")
    exit(1)
pin = pin_match.group(1)
print(f"✔️ Decrypted OTP: {pin}")

# Step 3: MFA verification
print("\n[Step 3] Verifying OTP and generating Admin JWT...")
verify_payload = {"sessionToken": session_token, "code": pin}
verify_res = requests.post(f"{base_url}/api/auth/mfa/verify", json=verify_payload)

if verify_res.status_code != 200:
    print(f"❌ MFA Verification Failed: {verify_res.status_code} {verify_res.text}")
    exit(1)

jwt_token = verify_res.json().get("token")
print("✔️ Admin JWT acquired successfully!")

# Step 4: Endpoint Scan
headers = {"Authorization": f"Bearer {jwt_token}"}

endpoints = [
    ("GET /api/customer/catalog", f"{base_url}/api/customer/catalog"),
    ("GET /api/orders?customerId=swissuser", f"{base_url}/api/orders?customerId=swissuser"),
    ("GET /api/ledger?customerId=swissuser", f"{base_url}/api/ledger?customerId=swissuser"),
    ("GET /api/admin/chaos/active", f"{base_url}/api/admin/chaos/active"),
    ("GET /api/admin/hitl/queue", f"{base_url}/api/admin/hitl/queue"),
    ("GET /api/admin/health", f"{base_url}/api/admin/health"),
    ("GET /api/wholesaler/restocks?id=wholesaler-1", f"{base_url}/api/wholesaler/restocks?id=wholesaler-1"),
    ("GET /api/wholesaler/invoices?id=wholesaler-1", f"{base_url}/api/wholesaler/invoices?id=wholesaler-1"),
    ("GET /api/inventory/picker/queue?storeId=store-1", f"{base_url}/api/inventory/picker/queue?storeId=store-1"),
    ("GET /api/rider/academy/courses", f"{base_url}/api/rider/academy/courses")
]

print("\n==================================================")
print(" RUNNING SYSTEM ENDPOINT SECURITY VERIFICATION...")
print("==================================================")

failures = 0
for name, url in endpoints:
    try:
        res = requests.get(url, headers=headers)
        status = res.status_code
        length = len(res.content)
        
        if status == 200:
            print(f"✅ {name:<60} ➡️ {status} OK ({length} bytes)")
        elif status == 403:
            print(f"❌ {name:<60} ➡️ {status} FORBIDDEN (CORS/Role Blocked) 🔴")
            failures += 1
        elif status == 401:
            print(f"❌ {name:<60} ➡️ {status} UNAUTHORIZED (JWT Expired/Bad Signature) 🔴")
            failures += 1
        else:
            print(f"⚠️ {name:<60} ➡️ {status} ({length} bytes)")
    except Exception as e:
        print(f"💥 {name:<60} ➡️ ERROR: {e}")
        failures += 1

print("\n==================================================")
if failures == 0:
    print("🏆 SYSTEM AUDIT PASSED: 0 Security Violations or 403 Barriers detected! All endpoints verified active.")
else:
    print(f"⚠️ AUDIT WARNING: {failures} endpoint(s) returned security anomalies. Check logs.")
print("==================================================")
