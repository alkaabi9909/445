
# TestSprite AI Testing Report(MCP)

---

## 1️⃣ Document Metadata
- **Project Name:** New folder (2)
- **Date:** 2026-09-04
- **Prepared by:** TestSprite AI Team

---

## 2️⃣ Requirement Validation Summary

#### Test TC001 postapiauthloginwithvalidandinvalidcredentials
- **Test Code:** [TC001_postapiauthloginwithvalidandinvalidcredentials.py](./TC001_postapiauthloginwithvalidandinvalidcredentials.py)
- **Test Error:** Traceback (most recent call last):
  File "/var/task/handler.py", line 258, in run_with_retry
    exec(code, exec_env)
  File "<string>", line 78, in <module>
  File "<string>", line 42, in test_post_api_auth_login_with_valid_and_invalid_credentials
AssertionError: 'settings' key not in valid login response JSON

- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/f77be744-1ef8-4803-90ab-eb8f1f0bf410
- **Status:** ❌ Failed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC002 getapidashboardwithandwithoutsessioncookie
- **Test Code:** [TC002_getapidashboardwithandwithoutsessioncookie.py](./TC002_getapidashboardwithandwithoutsessioncookie.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/c3282bea-54c8-45f1-a0a9-aa86396c0b73
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC003 getapicaseswithvalidstatusandinvalidstatusfilter
- **Test Code:** [TC003_getapicaseswithvalidstatusandinvalidstatusfilter.py](./TC003_getapicaseswithvalidstatusandinvalidstatusfilter.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/ee0a7b9b-402b-4c9b-9cfd-af7b9080f0a0
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC004 postapiexecutionsidorderswithvalidandinvalidordertypes
- **Test Code:** [TC004_postapiexecutionsidorderswithvalidandinvalidordertypes.py](./TC004_postapiexecutionsidorderswithvalidandinvalidordertypes.py)
- **Test Error:** Traceback (most recent call last):
  File "/var/task/handler.py", line 258, in run_with_retry
    exec(code, exec_env)
  File "<string>", line 130, in <module>
  File "<string>", line 69, in post_api_executions_id_orders_with_valid_and_invalid_order_types
  File "<string>", line 31, in create_case
  File "/var/lang/lib/python3.12/site-packages/requests/models.py", line 1024, in raise_for_status
    raise HTTPError(http_error_msg, response=self)
requests.exceptions.HTTPError: 400 Client Error:  for url: http://localhost:8080/api/cases

- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/47c4488e-5d11-4a27-bf5d-2babf98b7f9c
- **Status:** ❌ Failed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC005 postapiconsultationswithvalidandinvalidpriority
- **Test Code:** [TC005_postapiconsultationswithvalidandinvalidpriority.py](./TC005_postapiconsultationswithvalidandinvalidpriority.py)
- **Test Error:** Traceback (most recent call last):
  File "/var/task/handler.py", line 258, in run_with_retry
    exec(code, exec_env)
  File "<string>", line 82, in <module>
  File "<string>", line 54, in test_post_api_consultations_with_valid_and_invalid_priority
AssertionError: Created consultation id missing for priority LOW. Response data: {'consultation': {'id': 35, 'consultationNumber': 'CON-2026-0013', 'client': {'id': 1, 'name': 'شركة الخليج للمقاولات ذ.م.م', 'phone': '04-5001000'}, 'subject': 'Test Consultation', 'requestText': 'This is a test request.', 'priority': 'LOW', 'priorityLabel': 'منخفضة', 'status': 'RECEIVED', 'statusLabel': 'استلام', 'assignedManually': False, 'receivedAt': '2026-09-04', 'overdue': False, 'locked': False, 'archived': False, 'returnedCount': 0, 'allowedActions': ['ASSIGN']}, 'actions': [{'id': 35, 'action': 'RECEIVE', 'actionLabel': 'استلام الطلب', 'toStatus': 'RECEIVED', 'toStatusLabel': 'استلام', 'actor': {'id': 1, 'fullName': 'مدير المكتب', 'username': 'admin', 'roleName': 'المدير'}, 'actedAt': '2026-09-04T22:45:01.959523', 'notes': 'استلام طلب استشارة من الموكل شركة الخليج للمقاولات ذ.م.م'}], 'attachments': []}

- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/859c2c6f-e1c8-4de2-97ee-08df03251a89
- **Status:** ❌ Failed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC006 postapifinancialidpaymentswithvalidandinvalidmethods
- **Test Code:** [TC006_postapifinancialidpaymentswithvalidandinvalidmethods.py](./TC006_postapifinancialidpaymentswithvalidandinvalidmethods.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/263fa108-5564-44ec-a976-dcce952a800b
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC007 postapipartieswithandwithoutclientsmanagepermission
- **Test Code:** [TC007_postapipartieswithandwithoutclientsmanagepermission.py](./TC007_postapipartieswithandwithoutclientsmanagepermission.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/1d0bb373-3555-4a0b-9872-7dccb02e0dea
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC008 postapiuserswithvalidandinvalidroleidandduplicateusername
- **Test Code:** [TC008_postapiuserswithvalidandinvalidroleidandduplicateusername.py](./TC008_postapiuserswithvalidandinvalidroleidandduplicateusername.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/9f983ad7-a44f-4386-9e6c-70bc8cdb1787
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC009 getapireportskindwithvalidandunknownkind
- **Test Code:** [TC009_getapireportskindwithvalidandunknownkind.py](./TC009_getapireportskindwithvalidandunknownkind.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/199d5ca8-f93c-44c3-92b6-8f8501fc1733
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC010 postapiauthchangepasswordwithvalidandinvalidpasswords
- **Test Code:** [TC010_postapiauthchangepasswordwithvalidandinvalidpasswords.py](./TC010_postapiauthchangepasswordwithvalidandinvalidpasswords.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/fe6c700f-9fd0-4179-a7ae-a7609a1e7e7a
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---


## 3️⃣ Coverage & Matching Metrics

- **70.00** of tests passed

| Requirement        | Total Tests | ✅ Passed | ❌ Failed  |
|--------------------|-------------|-----------|------------|
| ...                | ...         | ...       | ...        |
---


## 4️⃣ Key Gaps / Risks
{AI_GNERATED_KET_GAPS_AND_RISKS}
---