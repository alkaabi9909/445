import requests

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/auth/login"
CONSULTATIONS_URL = f"{BASE_URL}/api/consultations"
TIMEOUT = 30

def login(username: str, password: str):
    resp = requests.post(
        LOGIN_URL,
        json={"username": username, "password": password},
        timeout=TIMEOUT,
    )
    resp.raise_for_status()
    assert "JSESSIONID" in resp.cookies, "Session cookie missing after login"
    return resp.cookies

def create_consultation(session_cookies, payload):
    resp = requests.post(
        CONSULTATIONS_URL, json=payload, cookies=session_cookies, timeout=TIMEOUT
    )
    return resp

def delete_consultation(session_cookies, consultation_id):
    # No DELETE endpoint specified in PRD for consultations, so no deletion done.
    pass

def test_post_api_consultations_with_valid_and_invalid_priority():
    # Login as a valid user with full permissions (admin)
    username = "admin"
    password = "Qanoon@123"
    session_cookies = login(username, password)

    base_payload = {
        "clientId": 1,
        "subject": "Test Consultation",
        "requestText": "This is a test request."
    }

    valid_priorities = ["LOW", "NORMAL", "HIGH", "URGENT"]

    try:
        for priority in valid_priorities:
            payload = base_payload.copy()
            payload["priority"] = priority
            response = create_consultation(session_cookies, payload)
            assert response.status_code == 200, f"Expected 200 for priority {priority}, got {response.status_code}"
            json_data = response.json()
            assert "ok" in json_data, "Missing 'ok' key in response"
            assert json_data.get("ok") is True or json_data.get("ok") == True
            data = json_data.get("data")
            assert isinstance(data, dict), "Response 'data' missing or not an object"
            consultation_id = data.get("id")
            assert consultation_id is not None, f"Created consultation id missing for priority {priority}. Response data: {data}"

        invalid_priorities = ["low", "Normal", "HIGHER", "urgent", "UNKNOWN", "INVALID", ""]
        for invalid_priority in invalid_priorities:
            payload = base_payload.copy()
            if invalid_priority != "":
                payload["priority"] = invalid_priority
            else:
                # Explicitly include priority key with empty string
                payload["priority"] = ""
            response = create_consultation(session_cookies, payload)
            assert response.status_code == 400, f"Expected 400 for invalid priority '{invalid_priority}', got {response.status_code}"
            json_data = response.json()
            assert "error" in json_data, "Error message missing in 400 response"
            assert "blockers" in json_data and isinstance(json_data["blockers"], list)

        # Also test with priority explicitly set to None (JSON null)
        payload = base_payload.copy()
        payload["priority"] = None
        response = create_consultation(session_cookies, payload)
        assert response.status_code == 400, f"Expected 400 for priority None, got {response.status_code}"
        json_data = response.json()
        assert "error" in json_data, "Error message missing in 400 response when priority is None"
        assert "blockers" in json_data and isinstance(json_data["blockers"], list)

    finally:
        pass

test_post_api_consultations_with_valid_and_invalid_priority()
