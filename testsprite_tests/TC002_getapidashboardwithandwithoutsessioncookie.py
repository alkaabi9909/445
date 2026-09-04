import requests

BASE_URL = "http://localhost:8080"
LOGIN_ENDPOINT = f"{BASE_URL}/api/auth/login"
DASHBOARD_ENDPOINT = f"{BASE_URL}/api/dashboard"

def test_get_api_dashboard_with_and_without_session_cookie():
    session = requests.Session()
    login_payload = {
        "username": "admin",
        "password": "Qanoon@123"
    }
    try:
        # Login to retrieve session cookie (JSESSIONID)
        login_resp = session.post(LOGIN_ENDPOINT, json=login_payload, timeout=30)
        assert login_resp.status_code == 200
        # Confirm JSESSIONID cookie set in session
        assert any(cookie.name == "JSESSIONID" for cookie in session.cookies), "JSESSIONID cookie missing after login"

        # Test GET /api/dashboard with valid session cookie
        dashboard_resp = session.get(DASHBOARD_ENDPOINT, timeout=30)
        assert dashboard_resp.status_code == 200
        json_data = dashboard_resp.json()
        assert isinstance(json_data, dict)
        # Validate response has 'workload' array and 'financials' key
        assert "workload" in json_data and isinstance(json_data["workload"], list)
        assert "financials" in json_data

        # Test GET /api/dashboard without session cookie
        # Use a fresh requests session without cookies
        no_cookie_resp = requests.get(DASHBOARD_ENDPOINT, timeout=30)
        assert no_cookie_resp.status_code == 401
        error_json = no_cookie_resp.json()
        assert isinstance(error_json, dict)
        # Expected error structure: {'error': '<arabic_message>', 'blockers': []}
        assert "error" in error_json
        assert "blockers" in error_json
        assert isinstance(error_json["error"], str)
        assert isinstance(error_json["blockers"], list)
    except requests.exceptions.RequestException as e:
        assert False, f"Request failed: {e}"

test_get_api_dashboard_with_and_without_session_cookie()