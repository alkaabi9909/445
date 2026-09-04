import requests

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/auth/login"
TIMEOUT = 30

def test_post_api_auth_login_with_valid_and_invalid_credentials():
    # Valid credentials (seeded user per PRD)
    valid_credentials = {
        "username": "admin",
        "password": "Qanoon@123"
    }

    # Invalid credentials - new throwaway user to avoid account lockout
    invalid_credentials = {
        "username": "invaliduser",
        "password": "WrongPassword123!"
    }

    # Test valid credentials
    try:
        valid_resp = requests.post(
            LOGIN_URL,
            json=valid_credentials,
            timeout=TIMEOUT
        )
    except requests.RequestException as e:
        assert False, f"Valid login request failed: {e}"

    # Assert status code 200
    assert valid_resp.status_code == 200, f"Expected 200, got {valid_resp.status_code}"

    # Assert response content contains expected keys and user data structure
    try:
        valid_json = valid_resp.json()
    except ValueError:
        assert False, "Valid login response is not valid JSON"

    # Expected keys: user, permissions[], settings
    assert "user" in valid_json, "'user' key not in valid login response JSON"
    assert "permissions" in valid_json and isinstance(valid_json["permissions"], list), "'permissions' key missing or not a list"
    assert "settings" in valid_json, "'settings' key not in valid login response JSON"

    # Assert Set-Cookie header includes JSESSIONID cookie
    cookies = valid_resp.cookies
    assert "JSESSIONID" in cookies, "Session cookie 'JSESSIONID' not found in valid login response"

    # Test invalid credentials
    try:
        invalid_resp = requests.post(
            LOGIN_URL,
            json=invalid_credentials,
            timeout=TIMEOUT,
            allow_redirects=False
        )
    except requests.RequestException as e:
        assert False, f"Invalid login request failed: {e}"

    # Assert status code 401
    assert invalid_resp.status_code == 401, f"Expected 401 for invalid credentials, got {invalid_resp.status_code}"

    # Assert Content-Type is JSON
    content_type = invalid_resp.headers.get("Content-Type", "")
    assert "application/json" in content_type, f"Invalid login response does not have JSON Content-Type, got {content_type}"

    # Assert response JSON structure: error (Arabic string), blockers (empty list)
    try:
        invalid_json = invalid_resp.json()
    except ValueError:
        assert False, "Invalid login response is not valid JSON"

    assert "error" in invalid_json and isinstance(invalid_json["error"], str) and invalid_json["error"], "'error' key missing or empty in invalid login response"
    assert "blockers" in invalid_json and isinstance(invalid_json["blockers"], list), "'blockers' key missing or not a list in invalid login response"

    # Assert no redirect (response history should be empty)
    assert len(invalid_resp.history) == 0, "Invalid login response should not redirect"

test_post_api_auth_login_with_valid_and_invalid_credentials()