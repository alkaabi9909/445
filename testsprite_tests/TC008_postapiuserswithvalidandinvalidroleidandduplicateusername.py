import requests
import uuid

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/auth/login"
USERS_URL = f"{BASE_URL}/api/users"
ROLES_PERMISSIONS_URL = f"{BASE_URL}/api/roles/permissions"
session = requests.Session()
session.timeout = 30


def login_admin():
    """Login as admin and return session with cookie set."""
    resp = session.post(
        LOGIN_URL,
        json={"username": "admin", "password": "Qanoon@123"},
        timeout=30,
    )
    assert resp.status_code == 200, "Admin login failed"
    assert "JSESSIONID" in resp.cookies or session.cookies.get("JSESSIONID") is not None
    return session


def get_any_valid_role_id():
    """Get any valid roleId by listing users or roles permissions, then roles (alternative: pick from roles API if exists).
    Since roles API list not provided, try to deduce roleId from existing users."""
    # Try to get existing users to extract a roleId
    resp = session.get(USERS_URL, timeout=30)
    if resp.status_code == 200:
        users = resp.json()
        if isinstance(users, list) and len(users) > 0:
            role_id = users[0].get("roleId") or users[0].get("role", {}).get("id")
            if role_id:
                return role_id
    # If no roleId found from users, try to create a role or get from permissions (not in PRD)
    # For safety, return None to skip test or fail
    return None


def test_postapiuserswithvalidandinvalidroleidandduplicateusername():
    login_admin()

    # We need a valid roleId to test with:
    role_id = get_any_valid_role_id()
    assert role_id is not None, "Could not find a valid roleId for test"

    # Generate a unique username
    unique_username = f"testuser_{uuid.uuid4().hex[:8]}"

    created_user_id = None

    try:
        # 1) Test POST /api/users with valid roleId and unique username -> 200 with created user data
        payload_valid = {
            "username": unique_username,
            "fullName": "Test User",
            "roleId": role_id,
            "password": "Qanoon@123"  # policy-compliant password assumed
        }
        resp_valid = session.post(USERS_URL, json=payload_valid, timeout=30)
        assert resp_valid.status_code == 200, f"Expected 200, got {resp_valid.status_code}"
        data_valid = resp_valid.json()
        assert data_valid.get("ok") or "data" in data_valid, "Missing user creation confirmation or data"
        user_data = data_valid.get("data") or data_valid
        created_user_id = user_data.get("id") or user_data.get("user", {}).get("id") or user_data.get("userId") or user_data.get("user") and user_data.get("user").get("id")
        assert created_user_id is not None, "Created user ID not found in response"
        assert user_data.get("username") == unique_username or (user_data.get("user") and user_data.get("user").get("username") == unique_username)

        # 2) Test POST /api/users with unknown roleId -> 404
        payload_invalid_role = {
            "username": f"{unique_username}_invalidrole",
            "fullName": "Test User InvalidRole",
            "roleId": 9999999999,  # Very likely unknown
            "password": "Qanoon@123"
        }
        resp_invalid_role = session.post(USERS_URL, json=payload_invalid_role, timeout=30)
        assert resp_invalid_role.status_code == 404, f"Expected 404 unknown roleId, got {resp_invalid_role.status_code}"

        # 3) Test POST /api/users with duplicate username -> 400 validation error
        payload_duplicate_username = {
            "username": unique_username,
            "fullName": "Test User Duplicate",
            "roleId": role_id,
            "password": "Qanoon@123"
        }
        resp_duplicate = session.post(USERS_URL, json=payload_duplicate_username, timeout=30)
        assert resp_duplicate.status_code == 400, f"Expected 400 duplicate username, got {resp_duplicate.status_code}"
    finally:
        # Cleanup: delete created user if possible to not pollute db
        if created_user_id:
            del_url = f"{USERS_URL}/{created_user_id}"
            # Attempt DELETE if supported, else ignore quietly (the PRD doesn't mention DELETE user)
            # So we skip actual deletion as no DELETE user endpoint mentioned.
            pass


test_postapiuserswithvalidandinvalidroleidandduplicateusername()