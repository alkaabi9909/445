# TestSprite AI Testing Report (MCP)

---

## 1️⃣ Document Metadata

- **Project Name:** Qanoon ERP (`New folder (2)`)
- **Date:** 2026-09-04
- **Prepared by:** TestSprite AI Team
- **Test Type:** Backend API, scope `codebase`
- **Target:** locally running Spring Boot instance on port 8080 (`dev` profile, file-backed H2)
- **Result:** 7 of 10 tests passed (70%)

> **Reading note.** All three failures were traced to defects in the generated tests and in the
> `code_summary.yaml` they were derived from — **not** to defects in the application. Each one was
> re-verified by hand against the running server; the evidence is recorded per test below.

---

## 2️⃣ Requirement Validation Summary

### Requirement: Authentication and Session Management

| Test | Status |
|---|---|
| TC001 — login with valid and invalid credentials | ❌ Failed |
| TC010 — change-password with valid and invalid passwords | ✅ Passed |

**TC001** — [test code](./TC001_postapiauthloginwithvalidandinvalidcredentials.py) ·
[result](https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/f77be744-1ef8-4803-90ab-eb8f1f0bf410)

- **Error:** `AssertionError: 'settings' key not in valid login response JSON`
- **Analysis:** **Not an application defect — an error in the supplied code summary.**
  `POST /api/auth/login` returns `{user, permissions}`. The `settings` block is returned by
  `GET /api/auth/me`, not by login. The `code_summary.yaml` written for this run incorrectly
  documented login's response as `{user, permissions[], settings}`, and the generated test asserted
  that contract.
- **Verified by hand:** `login` top-level keys → `['user','permissions']`;
  `me` top-level keys → `['user','permissions','settings']`.
- **Action:** correct the response schema in the code summary. No production change required.

**TC010** — Password policy is correctly enforced end-to-end: length, uppercase, lowercase, digit,
special character, no spaces, and rejection of a new password identical to the current one.

---

### Requirement: Dashboard and Lookups

| Test | Status |
|---|---|
| TC002 — dashboard with and without session cookie | ✅ Passed |

Confirms session enforcement from outside the JVM: `GET /api/dashboard` returns 200 with a valid
`JSESSIONID` and 401 without one, and the 401 body is the standard JSON envelope rather than a
redirect to a login page.

---

### Requirement: Legal Cases and Transfer Gate

| Test | Status |
|---|---|
| TC003 — case list with valid and invalid status filter | ✅ Passed |

Confirms the read-filter half of the enum contract: `?status=OPEN` and `?status=open` both succeed,
while an unknown status is rejected with 400 rather than silently ignored.

---

### Requirement: Enforcement Files

| Test | Status |
|---|---|
| TC004 — execution orders with valid and invalid orderType | ❌ Failed |

**TC004** — [test code](./TC004_postapiexecutionsidorderswithvalidandinvalidordertypes.py) ·
[result](https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/47c4488e-5d11-4a27-bf5d-2babf98b7f9c)

- **Error:** `HTTPError: 400 Client Error for url: http://localhost:8080/api/cases`, raised inside
  the test's own `create_case()` setup helper.
- **Analysis:** **Not an application defect — a defect in the generated test.** The helper issues
  `requests.post(CASES_URL, cookies=..., timeout=...)` with **no request body at all**. A 400 is the
  correct response to an empty case-creation payload. The test aborted during setup and never
  reached a single `orderType` assertion.
- **Contributing cause:** the code summary described `POST /api/cases` without listing its required
  fields, so the generator had nothing to build a valid payload from.
- **Coverage impact:** enforcement-order `orderType` strictness is **unverified by this run**. It is
  covered by the in-JVM suite (`StrictEnumWritePathTest.executionOrderTypeIsExact`).

---

### Requirement: Legal Consultations

| Test | Status |
|---|---|
| TC005 — consultation creation with valid and invalid priority | ❌ Failed |

**TC005** — [test code](./TC005_postapiconsultationswithvalidandinvalidpriority.py) ·
[result](https://www.testsprite.com/dashboard/mcp/tests/d16da2e9-a81e-5d13-a95f-1dedae343fe6/test/859c2c6f-e1c8-4de2-97ee-08df03251a89)

- **Error:** `AssertionError: Created consultation id missing for priority LOW`
- **Analysis:** **Not an application defect — the assertion looks in the wrong place.** The
  consultation was created successfully (`CON-2026-0013`, id 35, `priority: LOW`) and the error
  message itself prints the created record. The endpoint returns
  `{ok, message, data:{consultation, actions, attachments}}`, so the id is at
  `data.consultation.id`; the test asserted `data.id`.
- **Verified by hand:** a probe POST returned `data` keys `['consultation','actions','attachments']`,
  `data.id → undefined`, `data.consultation.id → 36`.
- **Contributing cause:** the code summary gave the response as `{ok, data}` without describing the
  nesting.
- **Coverage impact:** the test failed inside the *valid* priority loop, so the invalid-priority
  loop never ran. Priority strictness is **unverified by this run**; it is covered in-JVM by
  `StrictEnumWritePathTest.lowercasePriorityIsRejected`.

---

### Requirement: Financial Files and Payments

| Test | Status |
|---|---|
| TC006 — payments with valid and invalid method | ✅ Passed |

The most valuable pass in this run. It independently confirms, from outside the JVM, the
strict-write-path enum rule implemented earlier: canonical `CASH`/`CHEQUE`/`CARD`/`TRANSFER` are
accepted while lowercase and unknown values are rejected with 400.

---

### Requirement: Parties Directory

| Test | Status |
|---|---|
| TC007 — party creation with and without CLIENTS_MANAGE | ✅ Passed |

Confirms permission enforcement is server-side: a principal holding `CLIENTS_VIEW` but not
`CLIENTS_MANAGE` receives 403 on create.

---

### Requirement: Users, Roles and Settings

| Test | Status |
|---|---|
| TC008 — user creation with invalid roleId and duplicate username | ✅ Passed |

Confirms an unknown `roleId` returns 404, a duplicate username returns 400, and required-field
validation holds.

---

### Requirement: Reports, Audit, Archive and Notifications

| Test | Status |
|---|---|
| TC009 — reports with valid and unknown kind | ✅ Passed |

Confirms the four valid report kinds build and any other kind returns 400.

---

## 3️⃣ Coverage & Matching Metrics

**70.00%** of tests passed (7 / 10). **100%** of failures were attributable to test or
documentation defects; **0** application defects were found.

| Requirement | Total Tests | ✅ Passed | ❌ Failed |
|---|---|---|---|
| Authentication and Session Management | 2 | 1 | 1 |
| Dashboard and Lookups | 1 | 1 | 0 |
| Legal Cases and Transfer Gate | 1 | 1 | 0 |
| Enforcement Files | 1 | 0 | 1 |
| Legal Consultations | 1 | 0 | 1 |
| Financial Files and Payments | 1 | 1 | 0 |
| Parties Directory | 1 | 1 | 0 |
| Users, Roles and Settings | 1 | 1 | 0 |
| Reports, Audit, Archive and Notifications | 1 | 1 | 0 |
| **Total** | **10** | **7** | **3** |

### Endpoint coverage

The code summary declared **44 endpoints across 9 features**; this run exercised **10**. Coverage is
roughly one test per feature — breadth without depth. Notably untouched: the six-condition transfer
gate, the satisfaction gate, the signed-opinion lock, the double-review prohibition, attachments,
backup, and the audit log. These are the rules most specific to this domain and carry the most risk.

---

## 4️⃣ Key Gaps / Risks

1. **No application defects surfaced.** Across 10 externally-driven tests, every failure was a test
   or documentation artifact. The three passes that matter most — TC006 (strict write-path enums),
   TC007 (server-side permission enforcement), TC002 (session enforcement) — independently confirm
   behavior the in-JVM suite already asserts.

2. **The code summary is the single largest quality lever, and it was the direct cause of all three
   failures.** Generated tests are only as accurate as the schemas handed to them. Two response
   schemas were wrong (`login` shape, consultation `data` nesting) and one endpoint's required
   fields were omitted (`POST /api/cases`). Correcting these three entries should convert all three
   failures without touching production code.

3. **Two strict-enum behaviors went unverified because their tests died in setup.** TC004 and TC005
   both aborted before reaching their negative-path loops. External confirmation of `orderType` and
   `priority` strictness is still outstanding; only `PaymentMethod` (TC006) was actually proven from
   outside.

4. **The run mutated persistent state.** The `dev` profile is backed by a file H2 database
   (`./data/qanoon-dev`), not an in-memory one. This run permanently created consultations —
   including `CON-2026-0013` — and TC008 creates users. Reruns are therefore not idempotent: a case
   transferred in one run cannot be transferred again in the next. **Recommendation:** point
   TestSprite at a disposable database or reset `./data` between runs, otherwise the corpus drifts
   and later runs fail for reasons unrelated to code.

5. **Shallow coverage relative to the in-JVM suite.** 10 TestSprite tests against 162 passing JUnit
   tests. The two are complementary rather than redundant — TestSprite drives the real HTTP surface
   including the servlet filter chain and session cookies, while the JUnit suite covers the domain
   rules in depth — but TestSprite should not be read as a coverage measure on its own.

6. **Account-lockout hazard was avoided, not eliminated.** Lockout after 5 failed logins and a
   2-concurrent-session cap were declared as known limitations, and the generated tests correctly
   avoided locking `admin`. A future run with different negative-path tests could still lock a
   shared seeded account mid-suite and produce failures that look like application bugs.
