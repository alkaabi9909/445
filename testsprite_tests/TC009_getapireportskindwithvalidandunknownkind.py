import requests

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/auth/login"
REPORTS_URL = f"{BASE_URL}/api/reports"
USERNAME = "admin"
PASSWORD = "Qanoon@123"
TIMEOUT = 30

def test_getapireportskindwithvalidandunknownkind():
    session = requests.Session()
    try:
        # Login to obtain session cookie
        login_resp = session.post(
            LOGIN_URL,
            json={"username": USERNAME, "password": PASSWORD},
            timeout=TIMEOUT
        )
        assert login_resp.status_code == 200, f"Login failed: {login_resp.text}"
        assert 'JSESSIONID' in session.cookies.get_dict(), "Session cookie JSESSIONID not set"

        valid_kinds = ["financial", "cases", "consultants", "team"]
        for kind in valid_kinds:
            url = f"{REPORTS_URL}/{kind}"
            resp = session.get(url, timeout=TIMEOUT)
            assert resp.status_code == 200, f"Expected 200 for kind '{kind}', got {resp.status_code}"
            data = resp.json()
            # Validate presence of keys in response
            assert "title" in data, f"Missing 'title' in report kind '{kind}' response"
            assert "headers" in data, f"Missing 'headers' in report kind '{kind}' response"
            assert isinstance(data["headers"], list), f"'headers' should be a list for kind '{kind}'"
            assert "rows" in data, f"Missing 'rows' in report kind '{kind}' response"
            assert isinstance(data["rows"], list), f"'rows' should be a list for kind '{kind}'"
            # Check each row length matches headers count
            header_count = len(data["headers"])
            for row in data["rows"]:
                assert isinstance(row, list), f"Each row should be a list for kind '{kind}'"
                assert len(row) == header_count, f"Row length {len(row)} does not match headers count {header_count} for kind '{kind}'"
            # Optional keys
            assert "chart" in data, f"Missing 'chart' in report kind '{kind}' response"
            assert "totals" in data, f"Missing 'totals' in report kind '{kind}' response"

        # Test unknown kind
        unknown_kind = "unknownkind123"
        unknown_url = f"{REPORTS_URL}/{unknown_kind}"
        unknown_resp = session.get(unknown_url, timeout=TIMEOUT)
        assert unknown_resp.status_code == 400, f"Expected 400 for unknown kind, got {unknown_resp.status_code}"
        error_json = unknown_resp.json()
        assert "error" in error_json, "Error message missing for unknown kind request"
    finally:
        session.close()

test_getapireportskindwithvalidandunknownkind()