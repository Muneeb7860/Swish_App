import jwt
import datetime
import sys

secret = "my-secret-key-that-is-long-enough-to-be-secure-for-jwt-signature-verification-32bytes-long"

def generate_token(username, role):
    payload = {
        "sub": username,
        "role": role,
        "exp": datetime.datetime.utcnow() + datetime.timedelta(days=365)
    }
    return jwt.encode(payload, secret, algorithm="HS256")

if __name__ == "__main__":
    username = sys.argv[1] if len(sys.argv) > 1 else "swissuser"
    role = sys.argv[2] if len(sys.argv) > 2 else "customer"
    token = generate_token(username, role)
    print(token)
