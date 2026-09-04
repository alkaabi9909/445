import requests
import datetime

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/auth/login"
EXECUTIONS_URL = f"{BASE_URL}/api/executions"
CASES_URL = f"{BASE_URL}/api/cases"

USERNAME = "admin"
PASSWORD = "Qanoon@123"
TIMEOUT = 30

def login():
    resp = requests.post(
        LOGIN_URL,
        json={"username": USERNAME, "password": PASSWORD},
        timeout=TIMEOUT,
    )
    resp.raise_for_status()
    assert "JSESSIONID" in resp.cookies, "No session cookie received"
    return resp.cookies

def create_case(session_cookies):
    # Create a legal case to obtain an execution ID via transfer
    # Use POST /api/cases to create a case
    resp = requests.post(
        CASES_URL,
        cookies=session_cookies,
        timeout=TIMEOUT,
    )
    resp.raise_for_status()
    data = resp.json()
    assert data.get("ok") is True and "data" in data, "Failed to create case"
    case_id = data["data"].get("id")
    assert case_id is not None, "Case ID missing in response"
    return case_id

def transfer_case(case_id, session_cookies):
    transfer_url = f"{CASES_URL}/{case_id}/transfer"
    resp = requests.post(transfer_url, cookies=session_cookies, timeout=TIMEOUT)
    if resp.status_code == 400:
        # Unable to transfer due to gate not satisfied or already transferred
        return None
    resp.raise_for_status()
    data = resp.json()
    exec_number = data.get("data", {}).get("executionNumber")
    return exec_number

def get_execution_id_from_number(exec_number, session_cookies):
    # List executions and find matching executionNumber to get execution id
    resp = requests.get(EXECUTIONS_URL, cookies=session_cookies, timeout=TIMEOUT)
    resp.raise_for_status()
    executions_data = resp.json()
    items = executions_data.get("items", [])
    for item in items:
        if item.get("executionNumber") == exec_number and "id" in item:
            return item["id"]
    return None  # Not found

def post_api_executions_id_orders_with_valid_and_invalid_order_types():
    session_cookies = login()
    execution_id = None
    case_id = None
    try:
        # To test POST /api/executions/{id}/orders, we first need a valid execution ID.
        # Steps: create a case, transfer it, retrieve execution ID from executions list.

        # 1. Create a case
        case_id = create_case(session_cookies)

        # 2. Transfer the case to enforcement to generate an execution file
        exec_number = transfer_case(case_id, session_cookies)

        if exec_number is None:
            raise Exception(
                "Case transfer failed due to gate not satisfied or already transferred. Cannot continue test without execution id."
            )

        # 3. Get execution id from execution number
        execution_id = get_execution_id_from_number(exec_number, session_cookies)
        assert execution_id is not None, "Execution ID not found for transferred case"

        url = f"{EXECUTIONS_URL}/{execution_id}/orders"
        headers = {"Content-Type": "application/json"}

        # Test valid orderType: use "BANK_SEIZURE", "orderNumber" and "issuedDate"
        valid_payload = {
            "orderType": "BANK_SEIZURE",
            "orderNumber": "ORD12345",
            "issuedDate": datetime.date.today().isoformat(),
        }
        resp_valid = requests.post(
            url, json=valid_payload, cookies=session_cookies, headers=headers, timeout=TIMEOUT
        )
        assert resp_valid.status_code == 200, f"Expected 200 for valid orderType, got {resp_valid.status_code}"
        json_valid = resp_valid.json()
        assert json_valid.get("ok") is True, "Response JSON 'ok' must be True for valid orderType"
        assert "data" in json_valid, "Response JSON must contain 'data' for valid orderType"

        # Test missing orderType (omit orderType field)
        invalid_payload_missing = {
            "orderNumber": "ORD12346",
            "issuedDate": datetime.date.today().isoformat(),
        }
        resp_missing = requests.post(
            url, json=invalid_payload_missing, cookies=session_cookies, headers=headers, timeout=TIMEOUT
        )
        assert resp_missing.status_code == 400, f"Expected 400 for missing orderType, got {resp_missing.status_code}"
        json_missing = resp_missing.json()
        assert "error" in json_missing, "Error response must contain 'error' field for missing orderType"

        # Test unknown orderType (invalid string)
        invalid_payload_unknown = {
            "orderType": "UNKNOWN_TYPE",
            "orderNumber": "ORD12347",
            "issuedDate": datetime.date.today().isoformat(),
        }
        resp_unknown = requests.post(
            url, json=invalid_payload_unknown, cookies=session_cookies, headers=headers, timeout=TIMEOUT
        )
        assert resp_unknown.status_code == 400, f"Expected 400 for unknown orderType, got {resp_unknown.status_code}"
        json_unknown = resp_unknown.json()
        assert "error" in json_unknown, "Error response must contain 'error' field for unknown orderType"

    finally:
        # Cleanup: attempt to delete created resources if possible (no DELETE for executions or cases documented)
        # No explicit delete endpoint described for cases or executions, so omit cleanup here.
        pass

post_api_executions_id_orders_with_valid_and_invalid_order_types()
