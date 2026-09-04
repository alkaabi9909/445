import requests

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/auth/login"
CASES_URL = f"{BASE_URL}/api/cases"
TIMEOUT = 30

def test_get_api_cases_with_valid_and_invalid_status_filter():
    session = requests.Session()

    # Step 1: Login with valid credentials to get session cookie
    login_payload = {"username": "admin", "password": "Qanoon@123"}
    try:
        login_resp = session.post(LOGIN_URL, json=login_payload, timeout=TIMEOUT)
        assert login_resp.status_code == 200, f"Login failed with status {login_resp.status_code}"
        # Ensure session cookie is present
        cookies = session.cookies.get_dict()
        assert "JSESSIONID" in cookies, "Session cookie JSESSIONID not found after login"

        headers = {"Accept": "application/json"}

        # Step 2: GET /api/cases with valid status filter (e.g., status=open)
        params_valid = {"status": "open"}
        resp_valid = session.get(CASES_URL, params=params_valid, headers=headers, timeout=TIMEOUT)
        assert resp_valid.status_code == 200, f"Expected 200 for valid status filter, got {resp_valid.status_code}"
        json_valid = resp_valid.json()
        # Validate presence of keys for paging structure
        assert isinstance(json_valid, dict), "Response is not a JSON object"
        assert "items" in json_valid and "total" in json_valid and "page" in json_valid and "size" in json_valid, \
            "Paging keys missing from valid status response"
        assert isinstance(json_valid["items"], list), "Items should be a list"

        # Step 3: GET /api/cases with unknown status filter
        params_invalid = {"status": "unknown"}
        resp_invalid = session.get(CASES_URL, params=params_invalid, headers=headers, timeout=TIMEOUT)
        assert resp_invalid.status_code == 400, f"Expected 400 for unknown status filter, got {resp_invalid.status_code}"
        json_invalid = resp_invalid.json()
        assert "error" in json_invalid, "Error message missing in 400 response"
        assert isinstance(json_invalid["error"], str) and json_invalid["error"], "Arabic error message expected"

    finally:
        # Logout to invalidate session
        logout_url = f"{BASE_URL}/api/auth/logout"
        try:
            logout_resp = session.post(logout_url, timeout=TIMEOUT)
            # Logout may fail if session already invalid, ignore exceptions here
        except Exception:
            pass

test_get_api_cases_with_valid_and_invalid_status_filter()