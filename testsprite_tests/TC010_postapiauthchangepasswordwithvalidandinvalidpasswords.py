import requests

BASE_URL = "http://localhost:8080"
LOGIN_PATH = "/api/auth/login"
CHANGE_PASSWORD_PATH = "/api/auth/change-password"
LOGOUT_PATH = "/api/auth/logout"
TIMEOUT = 30

def test_postapiauthchangepasswordwithvalidandinvalidpasswords():
    session = requests.Session()
    login_url = BASE_URL + LOGIN_PATH
    change_pw_url = BASE_URL + CHANGE_PASSWORD_PATH
    logout_url = BASE_URL + LOGOUT_PATH

    # Seeded user credentials
    username = "admin"
    initial_password = "Qanoon@123"

    # Login to get session cookie
    login_payload = {"username": username, "password": initial_password}
    login_resp = session.post(login_url, json=login_payload, timeout=TIMEOUT)
    assert login_resp.status_code == 200, f"Login failed: {login_resp.text}"
    assert "JSESSIONID" in session.cookies.get_dict(), "Session cookie missing after login"

    try:
        # 1. Change password with valid oldPassword and valid newPassword (meets policy)
        valid_new_password = "Valid@1234"
        change_pw_payload_valid = {
            "oldPassword": initial_password,
            "newPassword": valid_new_password
        }
        resp_valid = session.post(change_pw_url, json=change_pw_payload_valid, timeout=TIMEOUT)
        assert resp_valid.status_code == 200, f"Valid password change failed: {resp_valid.text}"
        json_valid = resp_valid.json()
        assert "ok" in json_valid and json_valid["ok"] is True
        assert "message" in json_valid

        # 2. Change password with correct oldPassword but newPassword violating policy
        invalid_new_password = "short"  # violates min length and policy
        change_pw_payload_invalid_policy = {
            "oldPassword": valid_new_password,
            "newPassword": invalid_new_password
        }
        resp_invalid_policy = session.post(change_pw_url, json=change_pw_payload_invalid_policy, timeout=TIMEOUT)
        assert resp_invalid_policy.status_code == 400, f"Policy violation not caught: {resp_invalid_policy.text}"
        json_invalid_policy = resp_invalid_policy.json()
        assert "error" in json_invalid_policy and isinstance(json_invalid_policy["error"], str)
        assert len(json_invalid_policy.get("blockers", [])) >= 0

        # 3. Change password with wrong oldPassword
        wrong_old_password = "WrongPass@123"
        change_pw_payload_wrong_old = {
            "oldPassword": wrong_old_password,
            "newPassword": "Another@1234"
        }
        resp_wrong_old = session.post(change_pw_url, json=change_pw_payload_wrong_old, timeout=TIMEOUT)
        assert resp_wrong_old.status_code == 400, f"Wrong old password not caught: {resp_wrong_old.text}"
        json_wrong_old = resp_wrong_old.json()
        assert "error" in json_wrong_old and isinstance(json_wrong_old["error"], str)
        assert len(json_wrong_old.get("blockers", [])) >= 0

    finally:
        # Restore original password to keep state consistent
        restore_pw_payload = {
            "oldPassword": valid_new_password,
            "newPassword": initial_password
        }
        resp_restore = session.post(change_pw_url, json=restore_pw_payload, timeout=TIMEOUT)
        # Logout in any case
        session.post(logout_url, timeout=TIMEOUT)

test_postapiauthchangepasswordwithvalidandinvalidpasswords()