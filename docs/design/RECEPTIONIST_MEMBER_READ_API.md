# Receptionist member search and profile contract

## Existing APIs and scope

Source audit: AuthController exposes POST /api/auth/login, POST /api/auth/register,
and GET /api/auth/google/exchange. ReceptionistMemberController previously exposed
only POST /api/receptionist/members (Receptionist-only creation). No existing member
list/search/detail endpoint was available to reuse. Login UserResponse describes the
signed-in user, not an arbitrary member profile. Creation and authentication contracts
are unchanged. The existing controller, UserRepository, User/Role entities, DTO mapping
style, success/message/data envelope, and ErrorResponse are reused. No schema changes,
new dependencies, generic response framework, or frontend edits.

The current FE ReceptionistDashboard.jsx is a placeholder; it does not yet call these
read APIs. This change makes the backend ready for integration, not the frontend UI.

## Authentication and permissions

Send Authorization: Bearer <accessToken> using the existing login flow.
Both new GET endpoints require the exact existing ROLE_Receptionist authority.
Anonymous, invalid, or expired tokens receive 401. Member, Admin, Coach, Center Manager,
and other non-Receptionist roles receive 403. There was no prior member-read permission
for these roles; existing permissions on other APIs remain unchanged. Authorization
is enforced by SecurityConfig and the existing JWT filter; the project has no method
security or service-level authorization to amend. Returned records must have a role
name equal to Member ignoring case. Status is not an additional search filter.

## Search

GET /api/receptionist/members?keyword=an&page=0&size=20

| Parameter | Required | Default | Meaning |
| --- | --- | --- | --- |
| keyword | No | absent | Substring of fullName, username, email, or phone |
| page | No | 0 | Zero-based integer, >= 0 |
| size | No | 20 | Integer from 1 through 100 |

There is no member/user code field. Search does not interpret an ID as a code.
Outer whitespace is trimmed using String.trim(); internal spaces are preserved.
JPQL lower()/locate() performs case-insensitive substring matching on SQL Server.
Percent, underscore, and brackets are literal characters, not wildcard operators.
No accent removal or phone reformatting is performed; accent comparison follows the
configured database collation. Missing, empty, or whitespace-only keywords return an
empty result without a repository search. No match also returns HTTP 200 with data: [].
Invalid pagination is rejected even for blank keywords. Results are ordered by userId
ascending. total is the number of all matching members, not only the current page.
A page past the final result has empty data while retaining total. Fetch the next page
with page + 1 while (page + 1) * size < total. Page contents may change if data changes
between requests; no snapshot across requests is promised.

HTTP 200 example (fictional values):

```json
{
  "success": true,
  "message": "Tim hoc vien thanh cong",
  "total": 1,
  "page": 0,
  "size": 20,
  "data": [{
    "userId": 42,
    "fullName": "Nguyen An",
    "username": "nguyen.an",
    "email": "member@example.com",
    "phone": "0900000000",
    "dob": "2000-01-02",
    "gender": null,
    "address": null,
    "avatarUrl": null,
    "role": "Member",
    "status": "Active"
  }]
}
```

HTTP 200 empty example:

```json
{"success":true,"message":"Tim hoc vien thanh cong","total":0,"page":0,"size":20,"data":[]}
```

## Detail

GET /api/receptionist/members/{id}

id is required and is the selected result's userId (Java Integer, signed 32-bit).
A non-integer or out-of-range number returns 400. An unknown ID, including a negative
ID absent from the database, returns 404. IDs belonging to other roles also return 404.

HTTP 200 example:

```json
{
  "success": true,
  "message": "Lay thong tin hoc vien thanh cong",
  "data": {
    "userId": 42,
    "fullName": "Nguyen An",
    "username": "nguyen.an",
    "email": "member@example.com",
    "phone": "0900000000",
    "dob": "2000-01-02",
    "gender": null,
    "address": null,
    "avatarUrl": null,
    "role": "Member",
    "status": "Active"
  }
}
```

Both responses explicitly map the same 11 profile fields from User/Role. userId is an
integer; dob is an ISO date or null; other profile fields are strings or null according
to stored values (role is the stored Member role name). No entity is serialized.
Password/hash, tokens and secrets are excluded. There are no payment, package, health,
or schedule fields.

## Errors

Errors reuse ErrorResponse: {"success":false,"message":"..."}.

| HTTP | Trigger | message |
| --- | --- | --- |
| 400 | page < 0, size < 1 or > 100 | page must be >= 0; size must be between 1 and 100 |
| 400 | Malformed/out-of-range id, page or size | Invalid parameter: id (or page/size) |
| 401 | Missing/invalid/expired authentication | Unauthorized |
| 403 | Authenticated non-Receptionist | Forbidden |
| 404 | Missing member or ID of another role | Member not found |
| 500 | Unexpected application/database failure | Loi he thong |

The parameter conversion handler is local to ReceptionistMemberController, preserving
error behavior of unrelated controllers. FE should branch on status and success,
not localized text. Do not call search for blank input; clear the result list locally.
On selection retain userId and fetch detail. Handle nullable fields without fabricating
profile data. On 401 use the existing authentication flow; on 403 show lack of permission;
on 404 show that the member is unavailable.

## Swagger / OpenAPI

Existing springdoc dependency is reused. Both GET operations document parameters,
success/error responses, and bearer authentication. SwaggerIntegrationTest verifies
the generated /v3/api-docs, security scheme, paths, and GET operations. It also checks
Swagger UI HTML, JavaScript and redirect via HTTP. No browser-rendered Swagger UI or
interactive Try it out was inspected.

## Verification (2026-10-08)

Baseline (before code changes):

```powershell
.\mvnw.cmd -q '-Dtest=SubjectServiceTest,SubjectControllerTest,GoogleLoginExchangeServiceTest,ReceptionistMemberControllerTest' test
```

PASS: baseline selected tests. After implementation, full suite:

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -q '-Dmember.read.integration=true' package
```

Final package run: 55 tests, 0 failures, 0 errors, 0 skipped, including:

- 37 ReceptionistMemberControllerTest cases (existing creation plus new reads):
  empty/whitespace keyword, zero/one/multiple results, trim preserving internal spaces,
  default pagination, safe DTO fields, bad pagination/ID, valid/missing detail, JWT role
  guards, anonymous/invalid JWT, and unchanged creation permissions/validation.
- 5 MemberReadIntegrationTest cases: actual HTTP server, actual JWT signing and filter,
  actual SQL Server/JPA queries, existing data only. All four search fields, letter case,
  trim, blank/no match, literal wildcard characters, page boundaries/ordering/counts,
  MEMBER-only search/detail, existing other-role IDs, bad IDs, and HTTP authorization.
- Existing Subject service/controller and Google exchange tests: PASS.
- Application context/database schema validation and generated OpenAPI tests: PASS.

MemberReadIntegrationTest is opt-in because it reads the configured local SQL Server
and requires existing members with name/username/email/phone plus at least two searchable
email addresses. Run it only against an appropriate local/test database. It inserts,
updates and deletes nothing. It uses a synthetic test identity to sign JWTs in-process;
member response data comes from the real database, with no mocked repository. It does
not test interactive login, Google OAuth round trips, browser UI, or a deployed server.
No existing test failures were observed. Existing JDK/Lombok/Mockito warnings are not
test failures. Frontend remains unmodified.
