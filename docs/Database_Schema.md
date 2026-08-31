# ResolveIT — Database Schema Specification

## 1. Database Overview

ResolveIT uses Oracle Database as the primary relational database.

Database management and SQL development will be performed using Toad for Oracle.

The database stores:

- User accounts
- User roles
- Support teams and services
- Incidents
- Incident assignments
- Incident conversations/messages
- Incident status history
- Root cause information
- Resolution information

The database is incident-centric.

Each incident is an independent operational record containing its details, assignment, conversation, status history, root cause, and resolution.

The existing schema must be preserved.

IMPORTANT:

- Do not add new tables unless explicitly requested.
- Do not remove existing tables.
- Do not rename existing tables.
- Do not rename existing columns.
- Do not change existing relationships.
- Do not create a separate CONVERSATION table.
- RESOLVE_INCIDENT_MESSAGE already represents the conversation of an incident.
- Do not create a separate ASSIGNMENT_SCORE table.
- RESOLVE_INCIDENT_ASSIGNMENT already stores the calculated assignment score.
- Do not redesign the schema to implement the assignment algorithm.
- The assignment algorithm must work using the existing schema.

---

# 2. Database Technology

Database:

Oracle Database

Database Tool:

Toad for Oracle

The application backend will communicate with Oracle through Spring Boot.

The database is responsible for:

- Persistent data storage
- Primary key enforcement
- Foreign key enforcement
- Unique constraints
- Not-null constraints
- Referential integrity
- Transactional consistency

API testing is handled separately using Bruno and is not part of this database specification.

---

# 3. Existing Tables

The existing ResolveIT schema contains exactly these tables:

1. RESOLVE_ROLE
2. USER
3. RESOLVE_TEAM_SERVICE
4. RESOLVE_INCIDENT
5. RESOLVE_INCIDENT_ASSIGNMENT
6. RESOLVE_INCIDENT_MESSAGE
7. RESOLVE_INCIDENT_LOGS

These tables must remain the core database structure.

## Authentication adds no tables

Authentication is stateless and writes nothing outside `USER`. Login only reads;
it returns a signed JWT rather than creating a session row.

Logout (`POST /api/auth/logout`) likewise writes nothing. It revokes the caller's
token by recording that token's `jti` in an in-memory list held by
`TokenRevocationService`, which expires each entry when the token it names would
have expired anyway. So there is deliberately **no revoked-token table**, no
session table, and no column added to `USER` — logging out changes no database
row. There is also no refresh token, and therefore nothing to persist for one.

---

# 4. Table: RESOLVE_ROLE

## Purpose

RESOLVE_ROLE stores the roles available in the ResolveIT application.

ResolveIT has three application roles:

- USER
- SUPPORT
- SUPER_ADMIN

SUPER_ADMIN is an administrative role. It provisions SUPPORT accounts and reads
the team list; it is never granted access to incidents or conversations.

All three rows are REQUIRED seed data. `AuthService` resolves USER on every
self-registration and SUPPORT on every super-admin provisioning call, so a
missing row fails those operations at runtime.

The role assigned to a user determines which application functionality they are authorized to access.

## Columns

### role_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- Primary Key
- Auto-generated / identity

Purpose:

Unique identifier for a role.

### role_name

Logical type:

varchar(30)

Oracle type:

VARCHAR2(30)

Properties:

- NOT NULL

Purpose:

Stores the name of the role.

Expected values include:

- USER
- SUPPORT

---

# 5. Table: USER

## Purpose

USER stores all application users.

Both normal users and support engineers are represented in this table.

The user's role determines whether the account acts as a USER or SUPPORT user.

Rows are created two ways, both writing to this same table with no schema
change: public self-registration through `POST /api/auth/register`, which always
sets `role_id` to the USER role, and out-of-band provisioning for SUPPORT
accounts. There is no public path that creates a SUPPORT row.

## Columns

### user_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- Primary Key
- Auto-generated / identity

Purpose:

Unique identifier for the user.

### name

Logical type:

varchar(100)

Oracle type:

VARCHAR2(100)

Properties:

- NOT NULL

Purpose:

Stores the user's name.

### email

Logical type:

varchar(150)

Oracle type:

VARCHAR2(150)

Properties:

- UNIQUE
- NOT NULL

Purpose:

Stores the user's login email.

Two users must not have the same email address.

### password_hash

Logical type:

varchar(255)

Oracle type:

VARCHAR2(255)

Properties:

- NOT NULL

Purpose:

Stores the hashed password.

The application must never store plain-text passwords.

### role_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- NOT NULL
- Foreign Key → RESOLVE_ROLE.role_id

Purpose:

Identifies whether the account is a USER, a SUPPORT engineer or a SUPER_ADMIN.

### team_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- Nullable
- Foreign Key → RESOLVE_TEAM_SERVICE.team_id

Purpose:

Associates a user with a support/service team.

Population rules by role:

| Role | team_id |
|---|---|
| USER | NULL — reporters do not belong to a support team |
| SUPPORT | **always set** — supplied by the SUPER_ADMIN when the account is created |
| SUPER_ADMIN | NULL — the role never handles incidents |

The column stays nullable at the schema level because USER and SUPER_ADMIN rows
legitimately have none. For SUPPORT the value is mandatory in the application:
`POST /api/support-users` requires `teamId` and rejects an unknown one with 400.

This column is what makes an engineer assignable. The assignment query
`findSupportEngineersByTeam` joins on `u.team.teamId`, an inner join, so a
SUPPORT row with a NULL team_id is silently excluded from every candidate list
and would never receive work.

A normal USER may not require a team association.

A SUPPORT user should belong to the appropriate team.

---

# 6. Table: RESOLVE_TEAM_SERVICE

## Purpose

RESOLVE_TEAM_SERVICE represents the teams/services handled by ResolveIT.

The table stores the team information and the service associated with that team.

The incident assignment process uses the service/team relationship to determine which support team should handle an incident.

## Columns

### team_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- Primary Key
- Auto-generated / identity

Purpose:

Unique identifier for a team/service group.

### team_name

Logical type:

varchar(100)

Oracle type:

VARCHAR2(100)

Properties:

- NOT NULL

Purpose:

Stores the support team's name.

Example:

Payment Support Team

### service_name

Logical type:

varchar(120)

Oracle type:

VARCHAR2(120)

Properties:

- NOT NULL

Purpose:

Stores the service handled by the team.

Example:

Payment Service

### department

Logical type:

varchar(100)

Oracle type:

VARCHAR2(100)

Properties:

- Nullable

Purpose:

Stores the department associated with the team.

### description

Logical type:

varchar(500)

Oracle type:

VARCHAR2(500)

Properties:

- Nullable

Purpose:

Stores additional information about the team/service.

---

# 7. Table: RESOLVE_INCIDENT

## Purpose

RESOLVE_INCIDENT is the central table of ResolveIT.

Each record represents one incident reported by a USER.

The incident stores:

- Incident identification
- Title
- Description
- Category
- Severity
- Priority
- Status
- Reporting user
- Assigned team
- Root cause
- Resolution
- Creation time
- Resolution time

An incident can have multiple:

- Assignment records
- Messages
- Status log records

## Columns

### incident_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- Primary Key
- Auto-generated / identity

Purpose:

Internal unique identifier for the incident.

### incident_code

Logical type:

varchar(30)

Oracle type:

VARCHAR2(30)

Properties:

- UNIQUE
- NOT NULL

Purpose:

Human-readable incident identifier.

Example:

INC-1024

The application should display incident_code to users instead of exposing internal database identifiers where appropriate.

### title

Logical type:

varchar(200)

Oracle type:

VARCHAR2(200)

Properties:

- NOT NULL

Purpose:

Short title describing the incident.

Example:

Payment Failure

### description

Logical type:

text

Oracle type:

CLOB

Properties:

- Nullable according to existing schema

Purpose:

Stores the detailed description provided by the USER.

The description can contain a larger amount of text than the incident title.

### category

Logical type:

varchar(50)

Oracle type:

VARCHAR2(50)

Properties:

- Nullable

Purpose:

Stores the incident category.

Example:

Payment Failure

### severity

Logical type:

varchar(20)

Oracle type:

VARCHAR2(20)

Properties:

- Nullable

Purpose:

Stores the incident severity.

Example values may include:

- LOW
- MEDIUM
- HIGH
- CRITICAL

The application/business rules determine the allowed values.

### priority

Logical type:

varchar(10)

Oracle type:

VARCHAR2(10)

Properties:

- Nullable

Purpose:

Stores the incident priority.

Example:

- P1
- P2
- P3
- P4

Priority is determined by the system according to the application's business rules.

The USER does not directly choose the final priority.

### status

Logical type:

varchar(30)

Oracle type:

VARCHAR2(30)

Properties:

- Nullable according to the existing schema

Purpose:

Stores the current lifecycle status of the incident.

Supported lifecycle values are:

- REPORTED
- ASSIGNED
- IN PROGRESS
- ROOT CAUSE IDENTIFIED
- RESOLUTION IN PROGRESS
- RESOLVED

The application controls valid status transitions.

### reported_by

Logical type:

int

Oracle type:

NUMBER

Properties:

- NOT NULL
- Foreign Key → USER.user_id

Purpose:

Identifies the USER who reported the incident.

An incident belongs to the user who created/reported it.

### team_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- NOT NULL
- Foreign Key → RESOLVE_TEAM_SERVICE.team_id

Purpose:

Identifies the support/service team responsible for the incident.

The USER does not manually select the support team.

The system determines the appropriate team based on the selected/confirmed service.

### root_cause

Logical type:

text

Oracle type:

CLOB

Properties:

- Nullable

Purpose:

Stores the confirmed root cause of the incident.

OpsAI may suggest a possible root cause, but the final root cause is confirmed by SUPPORT.

This field should contain the final confirmed root cause.

### resolution

Logical type:

text

Oracle type:

CLOB

Properties:

- Nullable

Purpose:

Stores the final resolution details.

The SUPPORT engineer records the resolution after investigating and fixing the incident.

### created_at

Logical type:

timestamp

Oracle type:

TIMESTAMP

Purpose:

Stores when the incident was created.

### resolved_at

Logical type:

timestamp

Oracle type:

TIMESTAMP

Properties:

- Nullable

Purpose:

Stores when the incident was resolved.

This value remains NULL until the incident reaches RESOLVED status.

---

# 8. Table: RESOLVE_INCIDENT_ASSIGNMENT

## Purpose

RESOLVE_INCIDENT_ASSIGNMENT stores the assignment of incidents to SUPPORT users.

It represents which support engineer was assigned to an incident and the assignment score calculated by the assignment algorithm.

This table is also important for historical assignment information.

## Columns

### assignment_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- Primary Key
- Auto-generated / identity

Purpose:

Unique identifier for an assignment record.

### incident_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- NOT NULL
- Foreign Key → RESOLVE_INCIDENT.incident_id

Purpose:

Identifies the incident being assigned.

### support_user_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- NOT NULL
- Foreign Key → USER.user_id

Purpose:

Identifies the SUPPORT user assigned to the incident.

Only users with SUPPORT role should be assigned as support engineers.

### assignment_score

Logical type:

decimal(5,2)

Oracle type:

NUMBER(5,2)

Properties:

- Nullable

Purpose:

Stores the final assignment score calculated by the automatic assignment algorithm.

The score represents how suitable the selected support engineer was for the incident at the time of assignment.

The score is based on factors such as:

- Similar incident experience
- Availability
- Workload
- Idle time/fairness

Recommended assignment formula:

Assignment Score =
(Experience Score × 0.40)
+
(Availability Score × 0.25)
+
(Workload Score × 0.20)
+
(Idle/Fairness Score × 0.15)

The exact calculation is an application-level business rule.

The database only stores the resulting score.

IMPORTANT:

Do not create another table for assignment scoring.

The existing assignment_score column is sufficient to store the final calculated score.

### assigned_at

Logical type:

timestamp

Oracle type:

TIMESTAMP

Purpose:

Stores the time at which the assignment was created.

---

# 9. Table: RESOLVE_INCIDENT_MESSAGE

## Purpose

RESOLVE_INCIDENT_MESSAGE stores every individual message exchanged between USER and SUPPORT for an incident.

This table represents the incident's continuous conversation.

There is intentionally no separate CONVERSATION table.

The relationship is:

One RESOLVE_INCIDENT → Many RESOLVE_INCIDENT_MESSAGE records

The UI displays all messages belonging to the same incident as one continuous conversation.

## Columns

### message_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- Primary Key
- Auto-generated / identity

Purpose:

Unique identifier for an individual message.

### incident_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- NOT NULL
- Foreign Key → RESOLVE_INCIDENT.incident_id

Purpose:

Identifies the incident to which the message belongs.

Every message must belong to exactly one incident.

### sender_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- NOT NULL
- Foreign Key → USER.user_id

Purpose:

Identifies the user who sent the message.

The sender may be:

- USER who reported the incident
- SUPPORT engineer handling the incident

### message_text

Logical type:

text

Oracle type:

CLOB

Properties:

- Nullable according to existing logical schema

Purpose:

Stores the actual message content.

### sent_at

Logical type:

timestamp

Oracle type:

TIMESTAMP

Purpose:

Stores when the message was sent.

Messages should normally be displayed in chronological order using sent_at.

### is_read

Logical type:

boolean

Oracle implementation:

NUMBER(1)

Purpose:

Stores whether the message has been read by the recipient.

Recommended logical values:

- 0 = NOT READ
- 1 = READ

IMPORTANT:

The logical schema uses BOOLEAN, but Oracle versions commonly used with enterprise applications do not support BOOLEAN as a normal table column in the same way as newer Oracle releases.

Therefore, use NUMBER(1) for the physical Oracle implementation if required by the Oracle version.

This does not change the logical meaning of the existing schema.

---

# 10. Table: RESOLVE_INCIDENT_LOGS

## Purpose

RESOLVE_INCIDENT_LOGS stores the status history of incidents.

Whenever an incident status changes, a log entry is created holding the status
the incident has just entered.

One incident has many log rows. Reading them in order reconstructs how the
incident progressed; the previous status is the status of the previous row.

Each row records one status, not a pair. Example for incident 101:

```
LOG_ID | INCIDENT_ID | STATUS                  | CHANGED_AT
--------------------------------------------------------------
1      | 101         | REPORTED                | ...
2      | 101         | ASSIGNED                | ...
3      | 101         | IN PROGRESS             | ...
4      | 101         | ROOT CAUSE IDENTIFIED   | ...
5      | 101         | RESOLUTION IN PROGRESS  | ...
6      | 101         | RESOLVED                | ...
```

The previous status is read from the previous row, so no old/new pair is stored.
The six values above are the only ones the application writes, and they match
the `IncidentStatus` enum exactly (note `IN PROGRESS` is stored with a space).

## Columns

### log_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- Primary Key
- Auto-generated / identity

Purpose:

Unique identifier for a status history record.

### incident_id

Logical type:

int

Oracle type:

NUMBER

Properties:

- NOT NULL
- Foreign Key → RESOLVE_INCIDENT.incident_id

Purpose:

Identifies the incident whose status changed.

### status

Logical type:

varchar(30)

Oracle type:

VARCHAR2(30)

Properties:

- Nullable

Purpose:

Stores the status the incident held at this point in time.

One incident has many log rows, one per status it has entered. The previous
status is simply the `status` of the previous log row for the same incident.

### changed_at

Logical type:

timestamp

Oracle type:

TIMESTAMP

Purpose:

Stores when the status change occurred.

---

# 11. Relationships

The existing schema contains the following relationships.

## RESOLVE_ROLE → USER

Relationship:

RESOLVE_ROLE 1 : MANY USER

One role can be assigned to many users.

Each USER has one role.

Foreign key:

USER.role_id → RESOLVE_ROLE.role_id

Example:

RESOLVE_ROLE:
SUPPORT

can be associated with multiple SUPPORT users.

---

# 12. RESOLVE_TEAM_SERVICE → USER

Relationship:

RESOLVE_TEAM_SERVICE 1 : MANY USER

One team can contain multiple users.

A USER can be associated with a team through team_id.

Foreign key:

USER.team_id → RESOLVE_TEAM_SERVICE.team_id

This relationship applies to SUPPORT users. It is set when a SUPER_ADMIN creates
the engineer through `POST /api/support-users`, and it is the link automatic
assignment follows:

```text
INCIDENT.service (confirmed by the reporter)
    -> RESOLVE_TEAM_SERVICE.service_name
        -> RESOLVE_TEAM_SERVICE.team_id
            -> RESOLVE_USER.team_id  (SUPPORT engineers on that team)
                -> the highest-scoring engineer is assigned
```

USER and SUPER_ADMIN rows leave team_id NULL.

---

# 13. USER → RESOLVE_INCIDENT

Relationship:

USER 1 : MANY RESOLVE_INCIDENT

One USER can report multiple incidents.

Each RESOLVE_INCIDENT has one reporting USER.

Foreign key:

RESOLVE_INCIDENT.reported_by → USER.user_id

Example:

User A can report:

INC-1024

INC-1031

INC-1050

---

# 14. RESOLVE_TEAM_SERVICE → RESOLVE_INCIDENT

Relationship:

RESOLVE_TEAM_SERVICE 1 : MANY RESOLVE_INCIDENT

One team/service group can handle many incidents.

Each RESOLVE_INCIDENT is associated with one responsible team.

Foreign key:

RESOLVE_INCIDENT.team_id → RESOLVE_TEAM_SERVICE.team_id

The team is determined by the application.

The USER does not select the support engineer.

---

# 15. RESOLVE_INCIDENT → RESOLVE_INCIDENT_ASSIGNMENT

Relationship:

RESOLVE_INCIDENT 1 : MANY RESOLVE_INCIDENT_ASSIGNMENT

One incident can have assignment records.

Each assignment record belongs to one incident.

Foreign key:

RESOLVE_INCIDENT_ASSIGNMENT.incident_id → RESOLVE_INCIDENT.incident_id

The current implementation may normally have one active/current assignment per incident.

Historical assignment records should not be incorrectly deleted if the application needs assignment history.

The application controls whether reassignment is allowed.

---

# 16. USER → RESOLVE_INCIDENT_ASSIGNMENT

Relationship:

USER 1 : MANY RESOLVE_INCIDENT_ASSIGNMENT

A SUPPORT user can be assigned multiple incidents.

Foreign key:

RESOLVE_INCIDENT_ASSIGNMENT.support_user_id → USER.user_id

Only users with the SUPPORT role should be eligible for support assignment.

---

# 17. RESOLVE_INCIDENT → RESOLVE_INCIDENT_MESSAGE

Relationship:

RESOLVE_INCIDENT 1 : MANY RESOLVE_INCIDENT_MESSAGE

One incident can contain many messages.

Every message belongs to exactly one incident.

Foreign key:

RESOLVE_INCIDENT_MESSAGE.incident_id → RESOLVE_INCIDENT.incident_id

This is the database representation of the incident conversation.

IMPORTANT:

Do not create a separate CONVERSATION table.

The incident itself owns the conversation through RESOLVE_INCIDENT_MESSAGE.

---

# 18. USER → RESOLVE_INCIDENT_MESSAGE

Relationship:

USER 1 : MANY RESOLVE_INCIDENT_MESSAGE

One user can send many messages.

Every message has one sender.

Foreign key:

RESOLVE_INCIDENT_MESSAGE.sender_id → USER.user_id

The sender may be:

- The USER who reported the incident
- The assigned SUPPORT user

Authorization must ensure that users cannot send messages to incidents they are not authorized to access.

---

# 19. RESOLVE_INCIDENT → RESOLVE_INCIDENT_LOGS

Relationship:

RESOLVE_INCIDENT 1 : MANY RESOLVE_INCIDENT_LOGS

One incident can have multiple status history records.

Every log record belongs to one incident.

Foreign key:

RESOLVE_INCIDENT_LOGS.incident_id → RESOLVE_INCIDENT.incident_id

This allows the system to reconstruct the incident's lifecycle.

---

# 20. USER → RESOLVE_INCIDENT_LOGS

There is no relationship between USER and RESOLVE_INCIDENT_LOGS.

RESOLVE_INCIDENT_LOGS records only the status an incident held and when, not the user
who caused the change. Its only foreign key is incident_id → RESOLVE_INCIDENT.

---

# 21. Conversation Data Model

The conversation is incident-centric.

There is no global conversation table.

For an incident:

INC-1024

the application retrieves all records from RESOLVE_INCIDENT_MESSAGE where:

incident_id = INC-1024's incident_id

The messages are ordered chronologically using sent_at.

Example:

Message 1:
USER → Payment is failing.

Message 2:
SUPPORT → I'm checking the issue.

Message 3:
USER → It happens after clicking Pay.

Message 4:
SUPPORT → Got it. I'm investigating.

All four records belong to the same incident.

The frontend displays them as one continuous conversation.

---

# 22. Real-Time Chat Data Persistence

Real-time communication does not change the database design.

The application sends/receives messages in real time, but every message must still be persisted in RESOLVE_INCIDENT_MESSAGE.

The message lifecycle is:

1. USER/SUPPORT sends message.
2. Application validates the sender.
3. Message is associated with the incident.
4. Message is persisted in RESOLVE_INCIDENT_MESSAGE.
5. Real-time event is delivered to the other participant.
6. Recipient sees the message.
7. Message can later be marked as read.

The database remains the permanent source of conversation history.

---

# 23. Message Ordering

Messages should be retrieved in chronological order.

Primary ordering field:

sent_at

Recommended secondary ordering:

message_id

This helps maintain deterministic ordering when two messages have the same timestamp.

Conceptually:

ORDER BY sent_at ASC, message_id ASC

---

# 24. Incident Status History

RESOLVE_INCIDENT.status stores the CURRENT status.

RESOLVE_INCIDENT_LOGS stores the HISTORICAL status changes.

These two concepts must not be confused.

Example:

RESOLVE_INCIDENT:

status = IN PROGRESS

RESOLVE_INCIDENT_LOGS:

REPORTED

ASSIGNED

IN PROGRESS

Therefore:

RESOLVE_INCIDENT = current state

RESOLVE_INCIDENT_LOGS = state history

---

# 25. Incident Root Cause

The final confirmed root cause is stored in:

RESOLVE_INCIDENT.root_cause

OpsAI can suggest possible root causes.

However, the final root cause must be confirmed by SUPPORT.

The AI recommendation itself does not automatically become the confirmed root cause.

---

# 26. Incident Resolution

The final resolution is stored in:

RESOLVE_INCIDENT.resolution

SUPPORT records the final resolution after investigating and fixing the incident.

The incident should only become RESOLVED after the resolution has been verified.

When resolved:

RESOLVE_INCIDENT.status = RESOLVED

RESOLVE_INCIDENT.resolved_at = resolution timestamp

RESOLVE_INCIDENT.root_cause = confirmed root cause

RESOLVE_INCIDENT.resolution = final resolution details

---

# 27. Assignment Algorithm and Database

The assignment algorithm is implemented at the application/business-logic level.

The existing database already provides the information required to calculate assignment suitability.

The algorithm considers:

1. Similar Incident Experience
2. Availability
3. Current Workload
4. Idle Time/Fairness

The resulting score is stored in:

RESOLVE_INCIDENT_ASSIGNMENT.assignment_score

The database does not calculate the assignment score itself.

The backend calculates the score and stores the final value.

---

# 28. Similar Incident Experience

Historical incident and assignment data can be used to determine support engineer experience.

The application can examine previous assignments and incidents to determine whether a SUPPORT engineer has handled similar incidents.

Relevant information can include:

- Historical incident category
- Service/team
- Incident title
- Incident description
- Severity
- Historical assignment
- Historical root cause
- Historical resolution

The database schema does not require a separate experience table.

---

# 29. Workload Calculation

Current support workload can be determined from existing incident and assignment information.

The application can identify incidents currently assigned to a SUPPORT engineer and calculate workload.

Workload can consider:

- Number of active incidents
- Incident priority
- Incident severity
- Age of active incidents

No separate workload table is required by the current schema.

---

# 30. Idle Time / Fairness Calculation

The application can use existing assignment information to determine when a SUPPORT engineer last received an assignment.

The relevant field is:

RESOLVE_INCIDENT_ASSIGNMENT.assigned_at

This allows the application to estimate how long an eligible SUPPORT engineer has been without receiving a new assignment.

No separate idle-time table is required.

---

# 31. Availability

Availability is an application-level concept.

The current schema does not contain a dedicated availability column.

Therefore, the application must determine SUPPORT availability according to the implementation rules.

The existing schema should not be modified solely to introduce an availability table/column unless explicitly requested.

The assignment algorithm can use the application's current availability information together with existing incident/assignment data.

---

# 32. Assignment Score Storage

The final score for an assignment is stored in:

RESOLVE_INCIDENT_ASSIGNMENT.assignment_score

Example:

Arjun → 92.00

Priya → 84.00

Rahul → 76.00

The engineer with the highest valid score is selected according to the assignment algorithm.

The database does not need separate columns for:

- experience_score
- availability_score
- workload_score
- idle_score

The current schema stores the final assignment score only.

---

# 33. Primary Keys

Primary keys in the logical schema are:

RESOLVE_ROLE.role_id

USER.user_id

RESOLVE_TEAM_SERVICE.team_id

RESOLVE_INCIDENT.incident_id

RESOLVE_INCIDENT_ASSIGNMENT.assignment_id

RESOLVE_INCIDENT_MESSAGE.message_id

RESOLVE_INCIDENT_LOGS.log_id

Each primary key must uniquely identify its row.

---

# 34. Unique Constraints

The existing schema defines the following unique values:

USER.email

RESOLVE_INCIDENT.incident_code

Email must uniquely identify an application user.

Incident code must uniquely identify an incident.

---

# 35. Foreign Keys

The existing foreign-key relationships are:

USER.role_id
→ RESOLVE_ROLE.role_id

USER.team_id
→ RESOLVE_TEAM_SERVICE.team_id

RESOLVE_INCIDENT.reported_by
→ USER.user_id

RESOLVE_INCIDENT.team_id
→ RESOLVE_TEAM_SERVICE.team_id

RESOLVE_INCIDENT_ASSIGNMENT.incident_id
→ RESOLVE_INCIDENT.incident_id

RESOLVE_INCIDENT_ASSIGNMENT.support_user_id
→ USER.user_id

RESOLVE_INCIDENT_MESSAGE.incident_id
→ RESOLVE_INCIDENT.incident_id

RESOLVE_INCIDENT_MESSAGE.sender_id
→ USER.user_id

RESOLVE_INCIDENT_LOGS.incident_id
→ RESOLVE_INCIDENT.incident_id

→ USER.user_id

These relationships must be preserved.

---

# 36. Oracle Data Type Mapping

The logical schema uses generic data types.

When implementing the schema in Oracle, use the following mappings:

int
→ NUMBER

varchar(n)
→ VARCHAR2(n)

text
→ CLOB

timestamp
→ TIMESTAMP

decimal(5,2)
→ NUMBER(5,2)

boolean
→ NUMBER(1) where required by the Oracle version

For auto-incrementing integer primary keys, use Oracle identity columns or the project's chosen Oracle-supported identity mechanism.

The logical schema remains unchanged.

---

# 37. Oracle Identity Columns

The following primary keys are marked as auto-generated/incrementing in the logical schema:

RESOLVE_ROLE.role_id

USER.user_id

RESOLVE_TEAM_SERVICE.team_id

RESOLVE_INCIDENT.incident_id

RESOLVE_INCIDENT_ASSIGNMENT.assignment_id

RESOLVE_INCIDENT_MESSAGE.message_id

RESOLVE_INCIDENT_LOGS.log_id

When creating the Oracle tables, these should use an Oracle-supported identity strategy.

The application should not manually generate these primary key values.

---

# 38. Nullability Rules

The following fields are explicitly NOT NULL in the existing schema.

RESOLVE_ROLE:

role_name

USER:

name
email
password_hash
role_id

RESOLVE_TEAM_SERVICE:

team_name
service_name

RESOLVE_INCIDENT:

incident_code
title
reported_by
team_id

RESOLVE_INCIDENT_ASSIGNMENT:

incident_id
support_user_id

RESOLVE_INCIDENT_MESSAGE:

incident_id
sender_id

RESOLVE_INCIDENT_LOGS:

incident_id

All other nullable fields must remain nullable unless explicitly changed later.

---

# 39. Incident Creation Rules

When a USER creates an incident:

The database must store:

- incident_code
- title
- description
- category
- severity
- priority
- status
- reported_by
- team_id
- created_at

root_cause remains NULL until identified.

resolution remains NULL until resolved.

resolved_at remains NULL until resolved.

---

# 40. Incident Resolution Rules

Before an incident is resolved:

The incident should have:

- Confirmed root cause
- Resolution details
- Appropriate final status

When resolved:

status should become:

RESOLVED

resolved_at should contain the resolution timestamp.

The root cause and resolution remain stored permanently as part of incident history.

---

# 41. Data Ownership

USER owns/reports incidents through:

RESOLVE_INCIDENT.reported_by

RESOLVE_TEAM_SERVICE owns/handles incidents through:

RESOLVE_INCIDENT.team_id

SUPPORT ownership/assignment is represented through:

RESOLVE_INCIDENT_ASSIGNMENT.support_user_id

Conversation ownership is represented through:

RESOLVE_INCIDENT_MESSAGE.incident_id

Status history ownership is represented through:

RESOLVE_INCIDENT_LOGS.incident_id

---

# 42. Historical Data

Resolved incidents are not deleted as part of normal application operation.

Historical incidents are important because they provide information for:

- Similar incident detection
- Root cause analysis
- Resolution recommendations
- Recurring incident detection
- Support workload analysis
- Assignment experience calculation

RESOLVE_INCIDENT, RESOLVE_INCIDENT_ASSIGNMENT, RESOLVE_INCIDENT_MESSAGE, and RESOLVE_INCIDENT_LOGS together provide the historical record.

---

# 43. No Separate Conversation Table

IMPORTANT DATABASE RULE:

Do NOT create:

CONVERSATION

CONVERSATION_MESSAGE

CHAT

or any similar additional table.

The existing table:

RESOLVE_INCIDENT_MESSAGE

is the conversation storage mechanism.

Each RESOLVE_INCIDENT has its own conversation through RESOLVE_INCIDENT_MESSAGE.incident_id.

---

# 44. No Separate Assignment Score Table

IMPORTANT DATABASE RULE:

Do NOT create:

ASSIGNMENT_SCORE

SUPPORT_SCORE

ENGINEER_SCORE

or any similar additional table.

The final assignment score is already represented by:

RESOLVE_INCIDENT_ASSIGNMENT.assignment_score

The assignment algorithm belongs to the application/business layer.

---

# 45. No Separate Root Cause Table

The current schema stores the final root cause directly in:

RESOLVE_INCIDENT.root_cause

Do not create a separate ROOT_CAUSE table unless explicitly requested in the future.

OpsAI suggestions are application-level outputs.

The confirmed root cause is persisted in RESOLVE_INCIDENT.root_cause.

---

# 46. No Separate Resolution Table

The current schema stores the final resolution directly in:

RESOLVE_INCIDENT.resolution

Do not create a separate RESOLUTION table unless explicitly requested in the future.

---

# 47. Schema Integrity Rule

Claude Code must treat this document and the existing schema design as the database source of truth.

When implementing the backend:

DO NOT:

- Add unnecessary tables
- Remove tables
- Rename tables
- Rename columns
- Create a separate conversation table
- Create a separate chat table
- Create a separate assignment-score table
- Create a separate root-cause table
- Create a separate resolution table
- Change foreign-key relationships
- Duplicate existing data unnecessarily

The existing schema is intentionally designed to support:

- Two-role authentication
- Incident management
- Automatic assignment
- Incident-specific chat
- Real-time messaging
- Status history
- Root cause
- Resolution
- Historical incident intelligence

---

# 48. Schema-to-Feature Mapping

## Authentication

RESOLVE_ROLE + USER

## User Role

USER.role_id → RESOLVE_ROLE.role_id

## Support Team

USER.team_id → RESOLVE_TEAM_SERVICE.team_id

## Incident Reporting

RESOLVE_INCIDENT

## Incident Reporter

RESOLVE_INCIDENT.reported_by → USER.user_id

## Incident Service/Team

RESOLVE_INCIDENT.team_id → RESOLVE_TEAM_SERVICE.team_id

## Automatic Assignment

RESOLVE_INCIDENT_ASSIGNMENT

## Assigned Support Engineer

RESOLVE_INCIDENT_ASSIGNMENT.support_user_id → USER.user_id

## Assignment Score

RESOLVE_INCIDENT_ASSIGNMENT.assignment_score

## Incident Chat

RESOLVE_INCIDENT_MESSAGE

## Message Sender

RESOLVE_INCIDENT_MESSAGE.sender_id → USER.user_id

## Message Ownership

RESOLVE_INCIDENT_MESSAGE.incident_id → RESOLVE_INCIDENT.incident_id

## Read Status

RESOLVE_INCIDENT_MESSAGE.is_read

## Incident Status

RESOLVE_INCIDENT.status

## Status History

RESOLVE_INCIDENT_LOGS

## Root Cause

RESOLVE_INCIDENT.root_cause

## Resolution

RESOLVE_INCIDENT.resolution

## Creation Time

RESOLVE_INCIDENT.created_at

## Resolution Time

RESOLVE_INCIDENT.resolved_at

---

# 49. Final Database Structure

The final logical structure is:

RESOLVE_ROLE

Stores application roles.

USER

Stores both USER and SUPPORT accounts.

RESOLVE_TEAM_SERVICE

Stores support teams and their services.

RESOLVE_INCIDENT

Stores the central incident record.

RESOLVE_INCIDENT_ASSIGNMENT

Stores support engineer assignments and assignment scores.

RESOLVE_INCIDENT_MESSAGE

Stores every message belonging to an incident conversation.

RESOLVE_INCIDENT_LOGS

Stores incident status transition history.

These seven tables together form the complete database structure for the current ResolveIT project.

---

# 50. Final Database Principle

The ResolveIT database follows an incident-centric design.

The central entity is RESOLVE_INCIDENT.

An incident:

- Is reported by a USER.
- Belongs to a RESOLVE_TEAM_SERVICE.
- Can have assignment records.
- Is assigned to a SUPPORT user.
- Has its own continuous conversation.
- Contains multiple RESOLVE_INCIDENT_MESSAGE records.
- Has a lifecycle represented by RESOLVE_INCIDENT.status.
- Has historical status changes represented by RESOLVE_INCIDENT_LOGS.
- Can contain a confirmed root cause.
- Can contain a final resolution.
- Can become part of historical incident intelligence.

The existing seven-table schema is sufficient for the current ResolveIT feature set.

The implementation must preserve this schema and build the application logic around it.