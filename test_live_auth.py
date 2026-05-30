import requests
import subprocess
import time
import re

base_url = "http://127.0.0.1"

print("--- Step 1: Requesting Login OTP ---")
payload = {"username": "swissuser", "password": "password123"}
response = requests.post(f"{base_url}/api/auth/login", json=payload)
print(f"Status Code: {response.status_code}")
print(f"Response: {response.text}")

if response.status_code != 200:
    print("Login request failed!")
    exit(1)

data = response.json()
session_token = data.get("sessionToken")
print(f"Session Token: {session_token}")

print("\n--- Step 2: Fetching OTP from Docker Logs ---")
time.sleep(1) # wait for log broadcast
logs_output = subprocess.check_output('docker logs swiss_backend | grep "MFA GATEWAY" | tail -n 1', shell=True).decode('utf-8')
print(f"Latest Log: {logs_output.strip()}")

pin_match = re.search(r"PIN code: (\d{6})", logs_output)
if not pin_match:
    print("Failed to parse PIN code from logs!")
    exit(1)
pin = pin_match.group(1)
print(f"Parsed PIN: {pin}")

print("\n--- Step 3: Verifying MFA Passcode ---")
verify_payload = {"sessionToken": session_token, "code": pin}
verify_response = requests.post(f"{base_url}/api/auth/mfa/verify", json=verify_payload)
print(f"Status Code: {verify_response.status_code}")
print(f"Response: {verify_response.text}")

if verify_response.status_code == 200:
    print("\n✅ SUCCESS: Programmatic Live Authentication succeeded! JWT issued successfully!")
else:
    print("\n❌ FAILURE: Live Authentication failed!")
