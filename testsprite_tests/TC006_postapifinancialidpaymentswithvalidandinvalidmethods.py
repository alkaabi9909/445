import requests
from datetime import datetime
import traceback

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/auth/login"
FINANCIAL_URL = f"{BASE_URL}/api/financial"

SESSION = requests.Session()
SESSION.timeout = 30

USERNAME = "admin"
PASSWORD = "Qanoon@123"

def login():
    resp = SESSION.post(
        LOGIN_URL,
        json={"username": USERNAME, "password": PASSWORD},
        timeout=30
    )
    assert resp.status_code == 200, "Login failed"
    assert "JSESSIONID" in resp.cookies, "Session cookie missing"
    return resp.cookies.get("JSESSIONID")

def logout():
    try:
        resp = SESSION.post(f"{BASE_URL}/api/auth/logout", timeout=30)
        return resp.status_code == 200
    except Exception:
        return False

def create_financial_file():
    # Create a financial file by posting a consultation or case? 
    # PRD doesn't have a direct create financial endpoint.
    # However, we can create a new case, then use its id as financial id.
    # Or get existing financial list and pick first.

    # Trying GET /api/financial to get existing financial files
    resp = SESSION.get(FINANCIAL_URL, timeout=30)
    if resp.status_code != 200:
        raise RuntimeError("Failed to get financial files list")
    data = resp.json()
    items = data.get("items") or data.get("content")
    if items and len(items) > 0:
        return items[0]["id"]
    else:
        # No existing financial files found, create one by creating a party and case, then associate financially?
        # The PRD does not specify direct financial file creation via API.
        # We must rely on existing financial files for the test.
        raise RuntimeError("No financial files available for testing")

def delete_payment(financial_id, payment_id):
    # No endpoint for deleting a payment is described.
    # So skipping explicit cleanup of payment.
    pass

def test_post_api_financial_id_payments_with_valid_and_invalid_methods():
    jsessionid = login()
    SESSION.cookies.set("JSESSIONID", jsessionid)

    financial_id = None
    try:
        financial_id = create_financial_file()
    except Exception as e:
        print("Cannot find or create financial file for testing:", str(e))
        logout()
        assert False, "Setup failed: no financial resource"

    valid_methods = ["CASH", "CHEQUE", "CARD", "TRANSFER"]
    invalid_methods = ["cash", "cheque", "card", "transfer", "bitcoins", "paypal", "C@SH", ""]

    url = f"{FINANCIAL_URL}/{financial_id}/payments"

    # Test all valid methods expecting 200 with pending payment data (ok and data keys)
    for method in valid_methods:
        payload = {
            "amount": 1000,
            "paymentDate": datetime.now().strftime("%Y-%m-%d"),
            "method": method
        }
        resp = SESSION.post(url, json=payload, timeout=30)
        try:
            assert resp.status_code == 200, f"Expected 200 for method {method}, got {resp.status_code}"
            resp_json = resp.json()
            assert "ok" in resp_json and resp_json["ok"] is True, f"'ok' not True in response for {method}"
            assert "data" in resp_json, f"'data' missing in response for {method}"
            # Optionally check minimum data keys:
            assert isinstance(resp_json["data"], dict), "data is not a dict"
        except Exception:
            print(f"Failure for valid method: {method} response: {resp.text}")
            raise

    # Test all invalid methods expecting 400 with Arabic error JSON
    for method in invalid_methods:
        payload = {
            "amount": 1000,
            "paymentDate": datetime.now().strftime("%Y-%m-%d"),
            "method": method
        }
        resp = SESSION.post(url, json=payload, timeout=30)
        try:
            assert resp.status_code == 400, f"Expected 400 for invalid method '{method}', got {resp.status_code}"
            resp_json = resp.json()
            assert "error" in resp_json, f"'error' key missing in error response for method '{method}'"
            assert isinstance(resp_json["error"], str) and len(resp_json["error"]) > 0
            assert isinstance(resp_json.get("blockers", []), list)
        except Exception:
            print(f"Failure for invalid method: {method} response: {resp.text}")
            raise

    logout()

test_post_api_financial_id_payments_with_valid_and_invalid_methods()