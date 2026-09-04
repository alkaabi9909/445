import requests

BASE_URL = "http://localhost:8080"
TIMEOUT = 30


def test_post_api_parties_with_and_without_clients_manage_permission():
    session_with_permission = requests.Session()
    session_without_permission = requests.Session()

    # Login as admin (has CLIENTS_MANAGE permission)
    login_resp_admin = session_with_permission.post(
        f"{BASE_URL}/api/auth/login",
        json={"username": "admin", "password": "Qanoon@123"},
        timeout=TIMEOUT,
    )
    assert login_resp_admin.status_code == 200, "Admin login failed"
    assert 'JSESSIONID' in session_with_permission.cookies.get_dict()

    # Login as consult1 (assumed lacks CLIENTS_MANAGE permission)
    login_resp_consult = session_without_permission.post(
        f"{BASE_URL}/api/auth/login",
        json={"username": "consult1", "password": "Qanoon@123"},
        timeout=TIMEOUT,
    )
    assert login_resp_consult.status_code == 200, "consult1 login failed"
    assert 'JSESSIONID' in session_without_permission.cookies.get_dict()

    party_id = None

    try:
        # Test POST /api/parties with CLIENTS_MANAGE permission -> expect 200 and created data
        party_payload = {"name": "Test Party TC007", "kind": "CLIENT"}
        resp_with_perm = session_with_permission.post(
            f"{BASE_URL}/api/parties",
            json=party_payload,
            timeout=TIMEOUT,
        )
        assert resp_with_perm.status_code == 200, f"Expected 200, got {resp_with_perm.status_code}"
        json_data = resp_with_perm.json()
        assert "ok" in json_data and json_data["ok"] is True
        assert "data" in json_data and isinstance(json_data["data"], dict)
        assert json_data["data"].get("name") == party_payload["name"]
        assert json_data["data"].get("kind") == party_payload["kind"]
        party_id = json_data["data"].get("id")
        assert party_id is not None, "Created party has no ID"

        # Test POST /api/parties without CLIENTS_MANAGE permission -> expect 403
        resp_without_perm = session_without_permission.post(
            f"{BASE_URL}/api/parties",
            json=party_payload,
            timeout=TIMEOUT,
        )
        assert resp_without_perm.status_code == 403, f"Expected 403, got {resp_without_perm.status_code}"
        error_json = resp_without_perm.json()
        assert "error" in error_json

    finally:
        # Cleanup: delete created party if any
        if party_id is not None:
            del_resp = session_with_permission.delete(
                f"{BASE_URL}/api/parties/{party_id}",
                timeout=TIMEOUT,
            )
            # deletion returns 200 with confirmation or 404 if already deleted
            assert del_resp.status_code in (200, 404)

    # Logout sessions
    session_with_permission.post(f"{BASE_URL}/api/auth/logout", timeout=TIMEOUT)
    session_without_permission.post(f"{BASE_URL}/api/auth/logout", timeout=TIMEOUT)


test_post_api_parties_with_and_without_clients_manage_permission()