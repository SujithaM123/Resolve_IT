# ResolveIT — Backend API Specification

## 1. Backend Overview

ResolveIT is a three-role incident management system.

### Roles

- USER
- SUPPORT
- SUPER_ADMIN

SUPER_ADMIN is an administrative role only. It provisions SUPPORT accounts and
reads the team list; it has no access to incidents, conversations or OpsAI.

### Technology Stack

- Java 21
- Spring Boot 4.0.8
- Spring Data JPA
- Spring Security
- JWT
- Oracle Database
- WebSocket + STOMP
- Maven
- Bruno for API testing
- Toad for Oracle database management

The existing `Database_Schema.md` is the source of truth for the database structure.

The backend must follow the existing database schema exactly.

Do not add, remove, or redesign database tables, columns, relationships, or constraints.

---

# 2. EXACT REST API COUNT

The backend contains exactly 14 REST APIs.

1. `POST /api/auth/login`
2. `GET /api/user/dashboard`
3. `POST /api/incidents/classify`
4. `POST /api/incidents`
5. `GET /api/incidents/{incidentId}`
6. `POST /api/incidents/{incidentId}/messages`
7. `PATCH /api/incidents/{incidentId}/messages/read`
8. `GET /api/support/dashboard`
9. `PATCH /api/support/incidents/{incidentId}`
10. `POST /api/support/incidents/{incidentId}/ops-ai`
11. `POST /api/auth/register`
12. `POST /api/support-users`
13. `GET /api/teams`
14. `POST /api/auth/logout`

API 11 was added as an intentional feature addition: USER self-registration.
It creates USER accounts only.

API 14 was added as an intentional feature addition: direct logout. It revokes
the caller's own access token. It introduces no refresh token and no
refresh-token endpoint.

APIs 12 and 13 were added together as one intentional feature addition:
SUPER_ADMIN provisioning of SUPPORT engineers. API 13 supplies the team
dropdown that API 12 consumes. Both are restricted to SUPER_ADMIN. Neither
introduces a table, a column or a second authentication mechanism — they reuse
`RESOLVE_USER`, `RESOLVE_ROLE`, `RESOLVE_TEAM_SERVICE` and the existing JWT flow.

## STRICT NO-EXTRA-API RULE

Do NOT create any additional REST endpoints.

Do NOT create generic CRUD APIs.

Do NOT create separate REST APIs for:

- Assignment
- Status update
- Root cause
- Resolution
- Conversation
- Message history
- Read status
- Analytics
- Similar incidents
- AI summary
- AI analysis
- AI root cause
- AI resolution

These operations must be handled through the existing 14 REST APIs and WebSocket/STOMP where appropriate.

---

# 3. API 1 — LOGIN

## POST /api/auth/login

### Authorization

Public.

### Purpose

Authenticate both USER and SUPPORT users and return a JWT.

### Request

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

### Successful Response — 200 OK

```json
{
  "token": "<jwt-token>",
  "userId": 101,
  "name": "Arjun",
  "role": RESOLVE_USER
}
```

Example SUPPORT response:

```json
{
  "token": "<jwt-token>",
  "userId": 205,
  "name": "Priya",
  "role": "SUPPORT"
}
```

The JWT must contain:

- authenticated user identity
- role

Protected APIs use:

```text
Authorization: Bearer <jwt-token>
```

### Invalid Login — 401 Unauthorized

```json
{
  "timestamp": "2026-08-23T14:30:00",
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Invalid email or password",
  "path": "/api/auth/login"
}
```

Passwords must never be returned in API responses.

Passwords must be securely hashed.

Use the existing password field defined by `Database_Schema.md`.

---

# 3A. API 11 — USER REGISTRATION

## POST /api/auth/register

### Authorization

Public. No JWT is required, exactly as for API 1.

### Purpose

Allow a member of the public to create their own USER account.

This endpoint creates USER accounts only. It is not an account-management API
and must not be generalised into one.

### Request

```json
{
  "name": "User Name",
  "email": "user@example.com",
  "password": "password123"
}
```

The request must NOT contain a role, `roleId`, `role_id`, authority or team
field. The backend determines the role internally and always assigns USER.

### Registration Flow

```text
USER submits name, email, password
        |
        v
Validate input (name, email format, password present)
        |
        v
Check whether the email already exists (case-insensitive)
        |
        v
Hash the password with the application password encoder
        |
        v
Look up the USER role from the RESOLVE_ROLE table
        |
        v
Insert into the existing RESOLVE_USER table
        |
        v
Return 201 CREATED
```

### Successful Response — 201 CREATED

```json
{
  "userId": 42,
  "name": "User Name",
  "email": "user@example.com",
  "role": RESOLVE_USER
}
```

The response carries no token. The newly registered user obtains a JWT by
calling API 1:

```text
POST /api/auth/register  ->  POST /api/auth/login  ->  Bearer <jwt>  ->  protected USER APIs
```

The response must never contain `password`, `password_hash` or any other
password material.

### Duplicate Email — 409 CONFLICT

```json
{
  "timestamp": "2026-08-24T14:30:00",
  "status": 409,
  "error": "CONFLICT",
  "message": "Email is already registered",
  "path": "/api/auth/register"
}
```

### Validation Failure — 400 BAD REQUEST

```json
{
  "timestamp": "2026-08-24T14:30:00",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Email must be a valid email address",
  "path": "/api/auth/register"
}
```

### Role Rules

- The role is ALWAYS USER.
- A public caller can never obtain SUPPORT or SUPER_ADMIN through this endpoint.
- A `role` field in the request body is ignored; the record has no such field.
- SUPPORT accounts are provisioned by a SUPER_ADMIN through API 12. There is no
  SUPPORT self-registration.
- SUPER_ADMIN accounts are seeded directly in the database and are never created
  over any API.

### Database Rules

The account is written to the existing `RESOLVE_USER` table.

No new table, no new column, and no role column are introduced. The role is
carried by the existing `role_id` foreign key to `RESOLVE_ROLE`, unchanged.

`spring.jpa.hibernate.ddl-auto` remains `none`.

### Security Rules

- Passwords must be hashed with the same encoder API 1 verifies against.
- Plain-text passwords must never be stored or logged.
- The existing JWT login mechanism is unchanged.
- Every protected endpoint remains protected.

---

# 3B. API 12 — CREATE SUPPORT USER

## POST /api/support-users

### Authorization

SUPER_ADMIN only. `Authorization: Bearer <jwt>` is required.

Enforced in `SecurityConfig` with
`.requestMatchers(HttpMethod.POST, "/api/support-users").hasRole(RoleName.SUPER_ADMIN)`,
matching the URL-matcher pattern every other rule in this project uses.

### Purpose

Lets a super admin create a SUPPORT engineer and place them on a team in one call.
The team matters: automatic assignment only ever considers engineers whose
`RESOLVE_USER.TEAM_ID` matches the incident's team, so an engineer without one
would never receive work.

### Request

```json
{
  "name": "Priya",
  "email": "support@example.com",
  "password": "support123",
  "teamId": 2
}
```

| Field | Rule |
|---|---|
| `name` | required, max 100 |
| `email` | required, valid email, max 150, unique |
| `password` | required, non-blank |
| `teamId` | **required**, must reference an existing `RESOLVE_TEAM_SERVICE` row |

There is no `role` field. The role is always SUPPORT, so this endpoint can never
create another SUPER_ADMIN. An unexpected `"role"` key in the body is ignored.

### Successful Response — 201 CREATED

```json
{
  "userId": 12,
  "name": "Priya",
  "email": "support@example.com",
  "role": "SUPPORT",
  "teamId": 2,
  "teamName": "Identity Support Team"
}
```

Neither the raw password nor the BCrypt hash ever appears in the response.

### Error Responses

| Status | When |
|---|---|
| 400 | validation failed, `teamId` missing, or `teamId` does not exist |
| 401 | no JWT, or an invalid/expired JWT |
| 403 | a valid JWT whose role is USER or SUPPORT |
| 409 | the email is already registered |

All error bodies use the standard `ApiErrorResponse` shape.

### Database Rules

Writes one row to the existing `RESOLVE_USER` table:

```text
ROLE_ID       -> the SUPPORT row in RESOLVE_ROLE
TEAM_ID       -> the teamId supplied in the request
PASSWORD_HASH -> BCrypt hash produced by the shared PasswordEncoder
```

No new table and no new column. `spring.jpa.hibernate.ddl-auto` remains `none`.

### Security Rules

- The password is encoded with the same `BCryptPasswordEncoder` bean API 1 verifies
  against. Plain text is never stored or logged.
- The role is never read from the request.
- The caller's identity comes from the JWT; no field in the body affects authorization.

---

# 3C. API 13 — LIST TEAMS

## GET /api/teams

### Authorization

SUPER_ADMIN only. `Authorization: Bearer <jwt>` is required.

### Purpose

Supplies the Team dropdown the super admin picks from before calling API 12. The
client displays `teamName` and sends the matching `teamId`.

### Request

No body, no parameters.

### Response — 200 OK

```json
[
  { "teamId": 2, "teamName": "Identity Support Team" },
  { "teamId": 3, "teamName": "Notification Support Team" },
  { "teamId": 1, "teamName": "Payment Support Team" }
]
```

Ordered by team name. Only the two fields the dropdown needs are returned; the
`department`, `description` and `service_name` columns are not exposed here.

### Error Responses

| Status | When |
|---|---|
| 401 | no JWT, or an invalid/expired JWT |
| 403 | a valid JWT whose role is USER or SUPPORT |

### Database Rules

Read-only. `SELECT` against `RESOLVE_TEAM_SERVICE` only; writes nothing.

---

# 3D. API 14 — LOGOUT

## POST /api/auth/logout

### Authorization

Authenticated. Any role — USER, SUPPORT or SUPER_ADMIN.

This endpoint is NOT public. The caller must present the token they wish to
revoke, so a caller can only ever log out the session they are actually holding.

### Purpose

Direct logout: immediately revoke the access token presented in the
Authorization header.

There is no refresh token in this system and no refresh-token endpoint. Once the
access token is revoked, the only way back is API 1.

### Request

No body.

```text
POST /api/auth/logout
Authorization: Bearer <jwt-token>
```

### Logout Flow

```text
Client sends its current access token
        |
        v
JwtAuthenticationFilter verifies signature + expiry,
checks the revocation list, authenticates the caller
        |
        v
AuthController reads the raw token from the Authorization header
        |
        v
JwtService.extractIdentity()  ->  subject, jti, expiry
        |
        v
TokenRevocationService.revoke(jti, expiry)
        |
        v
Return 200 OK
```

### Successful Response — 200 OK

```json
{
  "message": "Logged out successfully"
}
```

### Revocation Mechanism

Every issued JWT carries a unique `jti` (JWT ID) claim. Logout records that one
`jti` on a server-side revocation list.

Both authentication entry points consult the list AFTER verifying the signature
and expiry and BEFORE establishing the caller's identity:

- `JwtAuthenticationFilter` — every protected REST request
- `StompAuthChannelInterceptor` — every WebSocket/STOMP CONNECT frame

A revoked token therefore never populates the SecurityContext. The request stays
anonymous and the standard 401 envelope is returned.

Revocation is per token, not per user. Each login issues a distinct `jti`, so
logging out on one device does not invalidate a token issued to another device.

A revocation entry is discarded once the token it names would have expired on its
own, so the list is bounded by the number of logouts within one token lifetime.

The list only ever REJECTS a token. It can never cause a token to be accepted.

### Using a Revoked Token — 401 Unauthorized

```json
{
  "timestamp": "2026-08-31T14:30:00",
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Authentication is required",
  "path": "/api/user/dashboard"
}
```

The same 401 is returned for a second logout attempt with an already-revoked
token, and for a revoked token presented at WebSocket CONNECT.

### Session Lifecycle

```text
POST /api/auth/login    ->  JWT issued            ->  protected APIs work
POST /api/auth/logout   ->  that JWT revoked      ->  200 OK
same JWT                ->  protected REST API    ->  401
same JWT                ->  WebSocket CONNECT     ->  rejected
POST /api/auth/login    ->  new JWT issued        ->  protected APIs work again
```

### Rules

- Logout revokes ONLY the token presented in the Authorization header.
- Logout must never be public.
- Logout adds no refresh token and no refresh-token endpoint.
- Spring Security remains STATELESS. The revocation list is not an HTTP session:
  a request still authenticates purely from its own token.
- Logout writes nothing to the database and changes no user record.

---

# 4. API 2 — USER DASHBOARD

## GET /api/user/dashboard

### Authorization

USER only.

### Purpose

Return the authenticated USER's dashboard and incidents.

The user ID must come from the authenticated JWT.

Do not accept `userId` from the client.

### Response — 200 OK

```json
{
  "userId": 101,
  "name": "Arjun",
  "incidents": [
    {
      "incidentId": 1024,
      "incidentCode": "INC-1024",
      "title": "Payment Failure",
      "status": "IN PROGRESS",
      "severity": "HIGH",
      "priority": "P1",
      "createdAt": "2026-08-23T10:35:00"
    }
  ]
}
```

Only incidents belonging to the authenticated USER are returned.

---

# 5. API 3 — RESOLVE_INCIDENT CLASSIFICATION

## POST /api/incidents/classify

### Authorization

USER only.

### Purpose

Analyze the incident title and description and suggest:

- Service
- Category
- Severity

This API does NOT create an incident.

### Request

```json
{
  "title": "Payment Failure",
  "description": "Payment is failing after clicking the Pay button."
}
```

### Response — 200 OK

```json
{
  "suggestedService": "Payment Support",
  "suggestedCategory": "Payment Failure",
  "suggestedSeverity": "HIGH"
}
```

The classification result is only a suggestion.

The USER can review or change the suggested values before submitting the incident.

---

# 6. API 4 — CREATE RESOLVE_INCIDENT

## POST /api/incidents

### Authorization

USER only.

### Purpose

Create a new incident and automatically assign it to an eligible SUPPORT engineer.

### Request

```json
{
  "title": "Payment Failure",
  "description": "Payment is failing after clicking the Pay button.",
  "service": "Payment Support",
  "category": "Payment Failure",
  "severity": "HIGH"
}
```

The USER does NOT provide:

- Team ID
- Support engineer ID
- Assignment score

The backend determines these automatically.

### Backend Flow

```text
Create Incident
      ↓
Determine Priority
      ↓
Determine Service Team
      ↓
Find Eligible SUPPORT Engineers
      ↓
Calculate Assignment Score
      ↓
Select Highest Score
      ↓
Create RESOLVE_INCIDENT_ASSIGNMENT
      ↓
Create RESOLVE_INCIDENT_LOGS entry
      ↓
Notify assigned SUPPORT through WebSocket
```

### Initial Status

```text
REPORTED
```

### Response — 201 Created

```json
{
  "incidentId": 1024,
  "incidentCode": "INC-1024",
  "title": "Payment Failure",
  "status": "REPORTED",
  "severity": "HIGH",
  "priority": "P1",
  "assignedSupportUserId": 205,
  "assignedSupportName": "Arjun",
  "createdAt": "2026-08-23T10:35:00"
}
```

---

# 7. AUTOMATIC ASSIGNMENT ALGORITHM

Assignment is performed internally during incident creation.

There is NO separate assignment REST API.

Only eligible SUPPORT engineers for the incident's service/team are considered.

The assignment algorithm considers:

1. Similar incident experience
2. Availability
3. Current workload
4. Idle time / fairness

## Weighted Score

```text
Assignment Score =
    40% Similar Incident Experience
  + 30% Availability
  + 20% Workload Balance
  + 10% Idle Time / Fairness
```

## Formula

```text
Final Score =
    (Experience × 0.40)
  + (Availability × 0.30)
  + (Workload × 0.20)
  + (IdleTime × 0.10)
```

The candidate with the highest final score is selected.

Example:

```text
Arjun  → 92.00
Priya  → 84.00
Rahul  → 76.00

Arjun is assigned.
```

The assignment score must be stored in the existing:

```text
RESOLVE_INCIDENT_ASSIGNMENT.assignment_score
```

Do not create a new assignment table.

Do not create an availability table.

Do not add an availability column.

Availability must be derived using the existing application/database information and implementation rules.

No separate SUPPORT availability REST API is required.

---

# 8. API 5 — RESOLVE_INCIDENT DETAILS

## GET /api/incidents/{incidentId}

### Authorization

USER:

- Own incident only.

SUPPORT:

- Only incidents they are authorized to access.

### Purpose

Return the complete incident information in one response.

The response should contain:

- Incident details
- Service
- Category
- Severity
- Priority
- Status
- Assigned support
- Conversation
- Status history
- Root cause
- Resolution
- Timestamps

### Response — 200 OK

```json
{
  "incidentId": 1024,
  "incidentCode": "INC-1024",
  "title": "Payment Failure",
  "description": "Payment is failing after clicking the Pay button.",
  "service": "Payment Support",
  "category": "Payment Failure",
  "severity": "HIGH",
  "priority": "P1",
  "status": "IN PROGRESS",

  "assignedSupport": {
    "userId": 205,
    "name": "Arjun"
  },

  "rootCause": null,
  "resolution": null,

  "createdAt": "2026-08-23T10:35:00",
  "resolvedAt": null,

  "messages": [
    {
      "messageId": 1,
      "senderId": 101,
      "senderName": "Arjun",
      "senderRole": RESOLVE_USER,
      "messageText": "Payment is failing again.",
      "sentAt": "2026-08-23T10:35:00",
      "isRead": true
    }
  ],

  "statusHistory": [
    {
      "status": "REPORTED",
      "changedAt": "2026-08-23T10:35:00"
    }
  ]
}
```

This endpoint provides the complete incident page.

Do not create separate REST APIs for:

- Conversation
- History
- Assignment
- Root cause
- Resolution

---

# 9. API 6 — SEND RESOLVE_INCIDENT MESSAGE

## POST /api/incidents/{incidentId}/messages

### Authorization

USER:

- Own incident only.

SUPPORT:

- Authorized incident only.

### Purpose

Send a message inside the incident conversation.

Each incident has its own continuous conversation.

Every message is stored individually in:

```text
RESOLVE_INCIDENT_MESSAGE
```

The UI displays these individual messages as one continuous conversation.

### Request

```json
{
  "messageText": "Payment is failing after clicking Pay."
}
```

The sender ID must come from the authenticated user.

Do not trust a client-provided sender ID.

### Backend Flow

```text
Authenticated Sender
        ↓
Validate Incident Access
        ↓
Save RESOLVE_INCIDENT_MESSAGE
        ↓
Broadcast Persisted Message
        ↓
WebSocket/STOMP
```

### Response — 201 Created

```json
{
  "messageId": 15,
  "incidentId": 1024,
  "senderId": 101,
  "senderName": "Arjun",
  "senderRole": RESOLVE_USER,
  "messageText": "Payment is failing after clicking Pay.",
  "sentAt": "2026-08-23T10:38:00",
  "isRead": false
}
```

---

# 10. API 7 — MARK MESSAGES AS READ

## PATCH /api/incidents/{incidentId}/messages/read

### Authorization

USER:

- Own incident only.

SUPPORT:

- Authorized incident only.

### Purpose

Mark messages as read for the authenticated participant.

### Request

```json
{
  "messageIds": [12, 13, 14]
}
```

### Response — 200 OK

```json
{
  "incidentId": 1024,
  "updatedMessageIds": [12, 13, 14],
  "status": "READ"
}
```

Update the existing:

```text
RESOLVE_INCIDENT_MESSAGE.is_read
```

field.

No separate read-status table is required.

---

# 11. API 8 — SUPPORT DASHBOARD

## GET /api/support/dashboard

### Authorization

SUPPORT only.

### Purpose

Return the authenticated SUPPORT user's dashboard.

### Response — 200 OK

```json
{
  "supportUserId": 205,
  "name": "Arjun",

  "summary": {
    "totalAssigned": 12,
    "currentlyOpen": 5,
    "resolved": 38,
    "averageResolutionTime": "04:32:00"
  },

  "incidents": [
    {
      "incidentId": 1024,
      "incidentCode": "INC-1024",
      "title": "Payment Failure",
      "severity": "HIGH",
      "priority": "P1",
      "status": "IN PROGRESS"
    }
  ],

  "analytics": {
    "mostCommonIssue": "Payment Failure",
    "recurringIncidents": 4
  }
}
```

Analytics must be calculated from existing Oracle data.

Do not create:

- Analytics tables
- Analytics REST APIs

---

# 12. API 9 — SUPPORT RESOLVE_INCIDENT UPDATE

## PATCH /api/support/incidents/{incidentId}

### Authorization

SUPPORT only.

The SUPPORT user must be authorized for the incident.

### Purpose

This ONE API handles:

- Status
- Root cause
- Resolution

Do not create separate APIs for these operations.

### Example — Status Update

```json
{
  "status": "IN PROGRESS"
}
```

### Example — Root Cause

```json
{
  "status": "ROOT CAUSE IDENTIFIED",
  "rootCause": "Database connection exhaustion"
}
```

### Example — Resolution in Progress

```json
{
  "status": "RESOLUTION IN PROGRESS",
  "resolution": "Checking database connection pool and transaction failures."
}
```

### Example — Resolve Incident

```json
{
  "status": "RESOLVED",
  "rootCause": "Database connection exhaustion",
  "resolution": "Connection pool was increased and payment transactions were verified."
}
```

## Allowed Incident Lifecycle

```text
REPORTED
   ↓
ASSIGNED
   ↓
IN PROGRESS
   ↓
ROOT CAUSE IDENTIFIED
   ↓
RESOLUTION IN PROGRESS
   ↓
RESOLVED
```

Only valid status transitions are allowed.

USER cannot change incident status.

Every status change must create an entry in:

```text
RESOLVE_INCIDENT_LOGS
```

When status becomes `RESOLVED`, populate the existing resolution timestamp field according to the database schema.

### Response — 200 OK

```json
{
  "incidentId": 1024,
  "incidentCode": "INC-1024",
  "status": "RESOLVED",
  "rootCause": "Database connection exhaustion",
  "resolution": "Connection pool was increased and payment transactions were verified.",
  "resolvedAt": "2026-08-23T14:20:00"
}
```

---

# 13. API 10 — OPSAI

## POST /api/support/incidents/{incidentId}/ops-ai

### Authorization

SUPPORT only.

The SUPPORT user must be authorized for the incident.

### Purpose

Provide OpsAI assistance for the current incident.

One endpoint handles all OpsAI operations.

### Request

```json
{
  "action": "SUMMARIZE"
}
```

Allowed actions:

```text
SUMMARIZE
SIMILAR
ANALYZE
ROOT_CAUSE
RESOLUTION
```

Do not create separate AI REST APIs.

---

# 14. OPSAI — SUMMARIZE

### Request

```json
{
  "action": "SUMMARIZE"
}
```

### Purpose

Summarize the incident and its conversation.

### Response

```json
{
  "action": "SUMMARIZE",
  "result": {
    "summary": "User reported repeated payment failures after clicking the Pay button. Support is investigating possible database connectivity issues."
  }
}
```

---

# 15. OPSAI — SIMILAR INCIDENTS

### Request

```json
{
  "action": "SIMILAR"
}
```

### Purpose

Find historically similar incidents.

The deterministic implementation should compare the current incident against historical incident data available in Oracle.

Similarity can consider relevant incident information such as:

- Service
- Category
- Title
- Description
- Root cause
- Resolution
- Conversation information where appropriate

### Response

```json
{
  "action": "SIMILAR",
  "result": {
    "similarIncidents": [
      {
        "incidentCode": "INC-0812",
        "similarity": 92
      },
      {
        "incidentCode": "INC-0742",
        "similarity": 87
      },
      {
        "incidentCode": "INC-0651",
        "similarity": 81
      }
    ]
  }
}
```

---

# 16. OPSAI — ANALYZE

### Request

```json
{
  "action": "ANALYZE"
}
```

### Purpose

Analyze the current incident using:

- Incident information
- Conversation
- Historical incident information

### Response

```json
{
  "action": "ANALYZE",
  "result": {
    "analysis": "The incident appears related to repeated payment processing failures and possible database connectivity problems.",
    "evidence": [
      "Current incident description",
      "Incident conversation",
      "Historical similar incidents"
    ]
  }
}
```

---

# 17. OPSAI — ROOT CAUSE

### Request

```json
{
  "action": "ROOT_CAUSE"
}
```

### Purpose

Suggest a possible root cause based on current and historical incident information.

### Response

```json
{
  "action": "ROOT_CAUSE",
  "result": {
    "possibleRootCause": "Database connection exhaustion",
    "confidence": 82,
    "evidence": [
      "3 similar historical incidents had the same root cause"
    ]
  }
}
```

The AI suggestion must NOT automatically update the incident.

The SUPPORT engineer decides whether to accept the suggestion.

If accepted, the SUPPORT engineer uses:

```text
PATCH /api/support/incidents/{incidentId}
```

to save the confirmed root cause.

---

# 18. OPSAI — RESOLUTION

### Request

```json
{
  "action": "RESOLUTION"
}
```

### Purpose

Suggest possible resolution steps.

### Response

```json
{
  "action": "RESOLUTION",
  "result": {
    "recommendedSteps": [
      "Check active database connections",
      "Check connection pool",
      "Compare connection usage with peak usage",
      "Increase connection pool if necessary",
      "Verify payment transactions"
    ]
  }
}
```

AI recommendations are advisory.

AI must NOT automatically resolve the incident.

---

# 19. OPSAI IMPLEMENTATION

OpsAI must be implemented as a deterministic in-application service using the current incident and historical incident data.

No external AI provider is required for the initial implementation.

The AI-like capabilities are:

- Conversation summarization
- Similar incident detection
- Incident analysis
- Root cause suggestion
- Resolution recommendation

The implementation must remain behind a service abstraction so that the internal implementation can be changed later without changing the REST API contract.

Do not create:

- AI database tables
- AI result tables
- Separate AI REST endpoints

Do not hardcode external API keys.

---

# 20. AUTHORIZATION RULES

## USER CAN

- Login
- Logout (revoking their own token)
- View own dashboard
- Classify incident
- Create incident
- View own incidents
- Send messages in own incidents
- Mark messages as read
- Receive real-time updates

## USER CANNOT

- Log another user out
- View another user's incident
- Select a support engineer
- Assign an incident
- Change incident status
- Change root cause
- Change resolution
- Access SUPPORT dashboard
- Use OpsAI

## SUPPORT CAN

- Login
- Logout (revoking their own token)
- View support dashboard
- View authorized incidents
- Send messages
- Mark messages as read
- Update incident status
- Confirm root cause
- Add resolution
- Use OpsAI

## SUPPORT CANNOT

- Log another user out
- Access unauthorized incidents
- Modify another support engineer's unauthorized incident
- Modify user credentials
- Manually assign an incident through the client

Assignment is backend-controlled.

---

# 21. VALIDATION RULES

## Login

- Email is required.
- Password is required.
- Email must have valid format.

## Registration

- Name is required and must not be blank.
- Name must not exceed 100 characters.
- Email is required and must have valid format.
- Email must not exceed 150 characters.
- Email must be unique across the USER table; the check is case-insensitive.
- Password is required and must not be blank.
- The request must not contain a role, roleId or authority field.
- The password must be hashed before it is stored; plain text is never persisted.

## Incident Classification

Required:

- Title
- Description

Title and description must not be blank.

## Incident Creation

Required:

- Title
- Description
- Service
- Category
- Severity

Title and description must not be blank.

## Message

`messageText`:

- Required
- Must not be blank

## Message Read Request

`messageIds`:

- Required
- Must contain at least one message ID

## Incident Status

Allowed values:

```text
REPORTED
ASSIGNED
IN PROGRESS
ROOT CAUSE IDENTIFIED
RESOLUTION IN PROGRESS
RESOLVED
```

Invalid status transitions must be rejected.

## OpsAI Action

Allowed values:

```text
SUMMARIZE
SIMILAR
ANALYZE
ROOT_CAUSE
RESOLUTION
```

Any other action must be rejected.

---

# 22. HTTP STATUS CODES

Use standard HTTP status codes.

### 200 OK

Successful GET, PATCH, and processing operations.

### 201 CREATED

Successful incident or message creation.

### 400 BAD REQUEST

Invalid request or validation failure.

### 401 UNAUTHORIZED

Missing or invalid authentication.

### 403 FORBIDDEN

Authenticated but not authorized - a valid token whose role does not permit the
endpoint. This includes any path under a role-gated namespace such as
`/api/support/**` when the caller is not SUPPORT.

### 404 NOT FOUND

The requested resource does not exist. This covers both:

- a domain resource that is not in the database, such as an unknown incident ID
- an unknown URL: a path that maps to no endpoint, or a static resource such as
  a Swagger UI file that does not exist

An unknown URL is only reported as 404 to an authenticated caller. Without a
token the request is 401, so an anonymous client still cannot probe which URLs
exist.

### 409 CONFLICT

Invalid business state or invalid status transition.

### 500 INTERNAL SERVER ERROR

Unexpected server-side error.

---

# 23. ERROR RESPONSE FORMAT

Use one consistent error response.

Example:

```json
{
  "timestamp": "2026-08-23T14:30:00",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Message text must not be blank",
  "path": "/api/incidents/1024/messages"
}
```

Use centralized exception handling with:

```text
@RestControllerAdvice
```

Do not expose:

- Stack traces
- SQL errors
- Internal implementation details
- Passwords
- JWT secrets

---

# 24. WEBSOCKET / STOMP

WebSocket is used for real-time communication.

WebSocket/STOMP destinations are NOT counted as REST APIs.

## WebSocket Endpoint

```text
/ws
```

STOMP is used over WebSocket.

## Client → Server

Send message:

```text
/app/incidents/{incidentId}/messages
```

Mark messages as read:

```text
/app/incidents/{incidentId}/read
```

## Server → Client

New messages:

```text
/topic/incidents/{incidentId}/messages
```

Incident updates:

```text
/topic/incidents/{incidentId}/updates
```

Read status:

```text
/topic/incidents/{incidentId}/read
```

---

# 25. REAL-TIME CHAT FLOW

When USER sends a message:

```text
USER
 ↓
POST /api/incidents/{incidentId}/messages
 ↓
Spring Boot
 ↓
Validate Authentication
 ↓
Validate Authorization
 ↓
Save RESOLVE_INCIDENT_MESSAGE in Oracle
 ↓
Broadcast persisted message
 ↓
/topic/incidents/{incidentId}/messages
 ↓
SUPPORT receives immediately
```

The same process applies when SUPPORT sends a message.

Oracle is the source of truth.

WebSocket provides real-time delivery.

The UI displays all incident messages as one continuous conversation.

Every message belongs to an incident.

There is no separate conversation entity or conversation table.

---

# 26. REAL-TIME RESOLVE_INCIDENT UPDATE FLOW

When SUPPORT changes an incident status:

```text
PATCH /api/support/incidents/{incidentId}
 ↓
Validate Authentication
 ↓
Validate Authorization
 ↓
Validate Status Transition
 ↓
Update RESOLVE_INCIDENT
 ↓
Create RESOLVE_INCIDENT_LOGS entry
 ↓
Commit Transaction
 ↓
Broadcast WebSocket Update
 ↓
/topic/incidents/{incidentId}/updates
 ↓
USER receives update immediately
```

The USER does not need to refresh the page to see the status update.

---

# 27. REAL-TIME ASSIGNMENT NOTIFICATION

When an incident is automatically assigned:

```text
POST /api/incidents
 ↓
Create RESOLVE_INCIDENT
 ↓
Calculate assignment score
 ↓
Select SUPPORT engineer
 ↓
Create RESOLVE_INCIDENT_ASSIGNMENT
 ↓
Create RESOLVE_INCIDENT_LOGS entry
 ↓
Commit transaction
 ↓
Notify assigned SUPPORT through WebSocket
```

No assignment REST API is required.

The assigned SUPPORT engineer receives the incident through the real-time channel.

---

# 28. DATABASE MAPPING

Use exactly the tables defined in `Database_Schema.md`.

The existing schema contains exactly these 7 tables:

```text
RESOLVE_ROLE
USER
RESOLVE_TEAM_SERVICE
RESOLVE_INCIDENT
RESOLVE_INCIDENT_ASSIGNMENT
RESOLVE_INCIDENT_MESSAGE
RESOLVE_INCIDENT_LOGS
```

Do not create additional tables.

Do not add columns.

Do not redesign relationships.

Do not introduce a separate conversation table.

Do not introduce an AI table.

Do not introduce an analytics table.

Do not introduce an availability table.

The incident conversation is represented by the existing `RESOLVE_INCIDENT_MESSAGE` records belonging to the incident.

Status history is represented by `RESOLVE_INCIDENT_LOGS`.

Assignment information is represented by `RESOLVE_INCIDENT_ASSIGNMENT`.

---

# 29. ORACLE SCHEMA RULE

The existing Oracle schema is the source of truth.

Hibernate must never automatically create or modify the database schema.

Use:

```text
ddl-auto: none
```

Do NOT use:

```text
ddl-auto=create
ddl-auto=create-drop
ddl-auto=update
```

The backend must work against the existing Oracle database structure.

The `USER` table is an Oracle reserved identifier and must be mapped appropriately without renaming the database table.

The Java entity may use `AppUser` to avoid collision with Spring Security's `User`.

The existing `RESOLVE_INCIDENT_MESSAGE.is_read` field is represented according to the database schema.

---

# 30. TRANSACTION RULES

Incident creation must be transactional.

The following operations must remain consistent:

```text
RESOLVE_INCIDENT
RESOLVE_INCIDENT_ASSIGNMENT
RESOLVE_INCIDENT_LOGS
```

If assignment creation fails, the incident creation transaction should not leave inconsistent assignment data.

Message persistence must complete before broadcasting the persisted message.

Incident status update and its corresponding `RESOLVE_INCIDENT_LOGS` entry must remain consistent.

Use `@Transactional` where required.

---

# 31. SECURITY RULES

Use:

```text
Spring Security + JWT
```

Passwords must be securely hashed.

Never return password information in an API response.

Authenticated user identity must come from Spring Security.

Do not trust client-provided:

```text
userId
senderId
supportUserId
```

for authorization-sensitive operations.

USER and SUPPORT permissions must be enforced server-side.

Every protected endpoint must verify the authenticated user's role and resource ownership/access.

## Token revocation (logout)

Every issued JWT carries a unique `jti` claim.

`POST /api/auth/logout` revokes the presented token by recording its `jti`
server-side. The revocation check runs after signature and expiry verification
and before the caller's identity is established, on BOTH authentication paths:

```text
JwtAuthenticationFilter        -> protected REST requests
StompAuthChannelInterceptor    -> WebSocket/STOMP CONNECT
```

A revoked token must be treated exactly like an invalid one: the request stays
anonymous and receives 401.

A token that cannot be identified — no `jti` — must fail closed and be refused,
never trusted.

There is no refresh token and no refresh-token endpoint. Session management stays
STATELESS; the revocation list is not an HTTP session and can only ever reject a
token, never accept one.

---

# 32. BRUNO API TESTING

Bruno is used for REST API testing.

Recommended testing order:

```text
1. Login
2. User Dashboard
3. Classify Incident
4. Create Incident
5. Incident Details
6. Send Message
7. Mark Messages Read
8. Support Dashboard
9. Update Incident
10. OpsAI
11. Logout
```

Use the JWT returned from login for protected APIs.

After step 11, re-issue any earlier request with the same token and confirm it
now returns 401, then log in again and confirm the new token works.

Test both USER and SUPPORT authorization.

Test invalid authentication.

Test that a logged-out token is rejected with 401 on REST and at WebSocket CONNECT.

Test unauthorized resource access.

Test validation failures.

Test invalid incident status transitions.

Test message sending and read status.

Test automatic assignment.

Do not create test-only REST endpoints.

---

# 33. FINAL API RESPONSIBILITY

```text
1. POST /api/auth/login
   → Authentication and JWT generation

2. GET /api/user/dashboard
   → USER dashboard and user's incidents

3. POST /api/incidents/classify
   → Suggest service, category, and severity

4. POST /api/incidents
   → Create incident and automatically assign SUPPORT

5. GET /api/incidents/{incidentId}
   → Complete incident details, conversation, and history

6. POST /api/incidents/{incidentId}/messages
   → Send incident conversation message

7. PATCH /api/incidents/{incidentId}/messages/read
   → Mark incident messages as read

8. GET /api/support/dashboard
   → SUPPORT dashboard, incidents, and analytics

9. PATCH /api/support/incidents/{incidentId}
   → Update status, root cause, and resolution

10. POST /api/support/incidents/{incidentId}/ops-ai
    → OpsAI assistance

11. POST /api/auth/register
    → USER self-registration

12. POST /api/support-users
    → SUPER_ADMIN provisions a SUPPORT engineer

13. GET /api/teams
    → Teams for the create-engineer dropdown

14. POST /api/auth/logout
    → Revoke the caller's access token
```

---

# 34. COMPLETE USER FLOW

```text
USER LOGIN
   ↓
JWT
   ↓
USER DASHBOARD
   ↓
REPORT RESOLVE_INCIDENT
   ↓
CLASSIFY RESOLVE_INCIDENT
   ↓
USER REVIEWS SERVICE / CATEGORY / SEVERITY
   ↓
CREATE RESOLVE_INCIDENT
   ↓
AUTOMATIC ASSIGNMENT
   ↓
SUPPORT ENGINEER SELECTED
   ↓
STATUS = REPORTED / ASSIGNED
   ↓
REAL-TIME NOTIFICATION
   ↓
SUPPORT OPENS RESOLVE_INCIDENT
   ↓
CHAT
   ↓
INVESTIGATION
   ↓
OPS AI
   ├── SUMMARIZE
   ├── SIMILAR
   ├── ANALYZE
   ├── ROOT CAUSE
   └── RESOLUTION
   ↓
SUPPORT CONFIRMS ROOT CAUSE
   ↓
SUPPORT ADDS RESOLUTION
   ↓
STATUS = RESOLVED
   ↓
REAL-TIME USER UPDATE
   ↓
RESOLVE_INCIDENT HISTORY
   ↓
USER LOGOUT
   ↓
TOKEN REVOKED — the same JWT now returns 401
```

---

# 35. COMPLETE SUPPORT FLOW

```text
SUPPORT LOGIN
   ↓
JWT
   ↓
SUPPORT DASHBOARD
   ↓
AUTOMATICALLY ASSIGNED RESOLVE_INCIDENT
   ↓
OPEN RESOLVE_INCIDENT
   ↓
VIEW RESOLVE_INCIDENT DETAILS
   ↓
VIEW COMPLETE CONVERSATION
   ↓
CHAT WITH USER
   ↓
INVESTIGATE
   ↓
USE OPSAI
   ├── SUMMARIZE
   ├── FIND SIMILAR
   ├── ANALYZE
   ├── SUGGEST ROOT CAUSE
   └── SUGGEST RESOLUTION
   ↓
CONFIRM ROOT CAUSE
   ↓
ADD RESOLUTION
   ↓
RESOLVE RESOLVE_INCIDENT
   ↓
USER RECEIVES REAL-TIME UPDATE
   ↓
SUPPORT LOGOUT
   ↓
TOKEN REVOKED — the same JWT now returns 401
```

---

# 36. STRICT IMPLEMENTATION RULES

The implementation MUST follow all of the following:

- Exactly 13 REST APIs.
- Only USER and SUPPORT roles.
- Use Java 21.
- Use Spring Boot 4.0.8.
- Use Spring Data JPA.
- Use Spring Security.
- Use JWT authentication.
- Use Oracle Database.
- Use WebSocket + STOMP for real-time communication.
- Use Bruno for API testing.
- Use Toad for Oracle database management.
- Preserve the existing database schema.
- Do not add database tables.
- Do not add database columns.
- Do not add roles.
- Do not add REST endpoints beyond the documented 11.
- Do not expose SUPPORT or ADMIN account creation through any public endpoint.
- Do not create generic CRUD APIs.
- Do not create a separate chat table.
- Do not create a separate conversation table.
- Do not create an AI table.
- Do not create an analytics table.
- Do not create an availability table.
- Store every message in `RESOLVE_INCIDENT_MESSAGE`.
- Store status history in `RESOLVE_INCIDENT_LOGS`.
- Store assignment and assignment score in `RESOLVE_INCIDENT_ASSIGNMENT`.
- Assignment must be automatic.
- USER must never select the support engineer.
- SUPPORT must control the incident lifecycle.
- OpsAI suggestions must not automatically resolve incidents.
- SUPPORT must confirm AI root-cause suggestions.
- WebSocket must provide real-time messages and incident updates.
- Oracle is the source of truth.
- REST APIs handle persistence and business operations.
- WebSocket handles real-time delivery.
- Do not invent additional APIs, tables, columns, roles, or features.

If something is unclear during implementation, do not silently invent a new API or database structure.

Follow:

```text
feature.md
Database_Schema.md
backendAPI.md
```

These three files together are the project specification.

When the three files already define the behavior, follow them exactly.

Only ask for clarification when the required behavior is genuinely undefined by all three files.