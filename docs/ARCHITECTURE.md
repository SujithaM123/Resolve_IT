# ResolveIT — how a request moves through the code

Every feature traced from the HTTP entry point to the Oracle row it writes and
back out again: which file handles which step, and why it sits where it does.

Companion to the three specification files in this folder. Where those three
disagreed, the resolutions are recorded in [../README.md](../README.md).

---

## The layers

One Spring Boot application, four layers, strictly one direction. A controller
never touches a repository, and a repository never knows who is calling.

| Layer | Responsibility | Package |
|---|---|---|
| **Controller** | Parse and validate the request, take identity from the security context, return a DTO. No business rules. | `controller/` |
| **Service** | All business logic and every `@Transactional` boundary. Decides what is legal and what gets written. | `service/` |
| **Repository** | Spring Data JPA queries. Seven interfaces, one per table. | `repository/` |
| **Oracle** | The source of truth. Seven tables, created by hand, never altered by Hibernate (`ddl-auto=none`). | — |

**The rule that shapes everything: persist first, broadcast second.** A
WebSocket message is only ever sent *after* the transaction that created its row
has committed, so Oracle can never disagree with what a client was told.

---

## Auth — runs before every controller below

This is why no endpoint ever reads a user id from the request body.

1. **`security/JwtAuthenticationFilter.java`** — pulls the
   `Authorization: Bearer …` header. No header means no authentication is set;
   the request continues as anonymous.
2. **`security/JwtService.java`** — verifies the HMAC signature and expiry. A
   bad or expired token yields an empty result rather than an exception, so a
   forged token is indistinguishable from none. Every token carries a unique
   `jti`, which is what logout revokes.
3. **`security/TokenRevocationService.java`** — has this token been logged out?
   Checked *before* identity is established, so a revoked token never reaches
   the security context. A token with no `jti` cannot be checked and is refused
   rather than trusted.
4. **`security/CustomUserDetailsService.java`** — loads the account fresh from
   Oracle on every request, so a role change takes effect immediately instead of
   living on inside an old token.
5. **`security/AuthenticatedUser.java`** — the principal placed in the security
   context: `userId`, `name`, `role`, `teamId`. The **only** source of caller
   identity anywhere in the application.
6. **`config/SecurityConfig.java`** — applies the role gate for the matched
   route. Anything unmatched is denied outright.
7. **`controller/…`** — receives the principal via `@AuthenticationPrincipal`.

**Why per-incident checks are not in `SecurityConfig`.** A URL can express
"SUPPORT only", but not "the engineer actually assigned to *this* incident".
That question needs a database read, so it lives beside the data in
`service/IncidentAccessService.java`.

---

# USER journey

## 0 · `POST /api/auth/register` — public

Self-registration. The role is decided here, not by the caller. This is one of two
endpoints that create an account: this one creates USER accounts for the public,
and `POST /api/support-users` creates SUPPORT accounts for a SUPER_ADMIN.

| # | File | What happens |
|---|---|---|
| 1 | `dto/RegisterRequest.java` | Name, well-formed email and password present → **400**. The record has no role component at all |
| 2 | `controller/AuthController.java` | Thin pass-through, `201 CREATED` |
| 3 | `service/AuthService.java · register` | `existsByEmailIgnoreCase` → **409** before the insert |
| 4 | `repository/RoleRepository.java` | Looks up USER; SUPPORT is never queried |
| 5 | `BCryptPasswordEncoder` | Hashes into `password_hash`; plain text is never persisted |
| 6 | `dto/RegisterResponse.java` | userId, name, email, role — no token, no password |

Privilege escalation is closed off by type rather than by a check: there is no
field on `RegisterRequest` through which SUPPORT or SUPER_ADMIN could be
requested, so no payload can ask for one. The same holds for
`CreateSupportUserRequest`, which always yields SUPPORT. SUPER_ADMIN accounts are
seeded in the database and are never created over any API. The duplicate-email
check runs before the insert so the caller gets the documented **409** rather than
an opaque 500 from the UNIQUE constraint.

The account lands in the existing `RESOLVE_USER` table via the existing `role_id`
foreign key. No schema change; `ddl-auto` stays `none`.

## 1 · `POST /api/auth/login` — public

One endpoint for every role. The role comes from the account, never the request.

| # | File | What happens |
|---|---|---|
| 1 | `dto/LoginRequest.java` | Email present and well-formed, password present → **400** |
| 2 | `controller/AuthController.java` | Thin pass-through |
| 3 | `service/AuthService.java · login` | Hands the credentials to `AuthenticationManager`; decides only what success and failure mean |
| 4 | `DaoAuthenticationProvider` (from `config/SecurityConfig.java`) | Calls `CustomUserDetailsService` — `SELECT RESOLVE_USER` — then compares against `password_hash` with `BCryptPasswordEncoder` → **401** |
| 5 | `security/JwtService.java` | Sign a token carrying `userId`, `role`, `name`, plus a unique `jti` so logout can revoke this one token |
| 6 | `dto/LoginResponse.java` | token, userId, name, role |

**The password check is Spring Security's, not the service's.** `AuthService` never
reads the hash; it passes the email and password to the `AuthenticationManager` bean and
receives either an authenticated principal or an `AuthenticationException`.

A missing account and a wrong password return the **identical** message —
`setHideUserNotFoundExceptions(true)` collapses both into `BadCredentialsException` — so the
endpoint cannot be used to discover which emails exist. `LoginResponse` has no password field
at all, so a hash cannot leak by accident.

## 1A · `POST /api/auth/logout` — any authenticated role

Direct logout. Not public: you can only revoke a token you are already holding,
which is why it sits under `.authenticated()` rather than `permitAll()`.

| # | File | What happens |
|---|---|---|
| 1 | `security/JwtAuthenticationFilter.java` | The caller authenticates normally first — an invalid or already-revoked token never reaches the controller → **401** |
| 2 | `controller/AuthController.java` | Reads the raw token back out of the `Authorization` header via `security/BearerTokens.java` |
| 3 | `service/AuthService.java · logout` | `JwtService.extractIdentity` → subject, `jti`, expiry |
| 4 | `security/TokenRevocationService.java` | Records that `jti` until the token's own expiry |
| 5 | `dto/LogoutResponse.java` | `{ "message": "Logged out successfully" }` |

**A signed JWT cannot be un-signed**, so logout does not destroy the token — it
makes the server stop honouring it. The revocation list is consulted on both
authentication paths, `JwtAuthenticationFilter` for REST and
`StompAuthChannelInterceptor` for WebSocket, so one logout closes both doors.

Revocation is **per token, not per user**: each login mints its own `jti`, so
logging out on a laptop leaves a phone session alive. The list only ever
*rejects* a token, never accepts one, so it cannot become a way in. Entries are
dropped once the token they name would have expired anyway.

No database row is written or read. `AuthService.logout` is deliberately not
`@Transactional`.

**Why in memory.** A `RESOLVE_` table would mean a DDL change to a schema shared
with other projects, and `ddl-auto=none` makes a missing table fail at query time
rather than at startup. The cost is that a restart clears the list, so a token
logged out shortly before a restart works again for the remainder of its eight
hours. `TokenRevocationService` is the entire seam — swapping it for a
table- or Redis-backed implementation touches neither the filter, the interceptor
nor the endpoint.

There is **no refresh token** and no refresh-token endpoint. Once revoked, the
only way back is `POST /api/auth/login`.

## 2 · `GET /api/user/dashboard` — USER

1. `controller/UserDashboardController.java` — takes the principal. There is no
   `userId` parameter to tamper with.
2. `service/IncidentService.java · userDashboard` — queries strictly by the
   authenticated reporter's id, newest first. **Ownership is enforced by the
   query itself**, not by filtering afterwards.
3. `dto/UserDashboardResponse.java · UserIncidentSummary` — id, code, title, status,
   severity, priority, createdAt. The row record is nested in the response it belongs to.

## 3 · `POST /api/incidents/classify` — USER

Suggests service, category and severity from free text. **Writes nothing** — the
user reviews and may override all three.

1. `service/ClassificationService.java` — loads historical incidents.
2. `service/IncidentSimilarityService.java` — scores the new text against every
   past incident, keeps the closest if it clears a 15% relevance floor.
3. `· suggestService` — token overlap between the incident text and each team's
   service name, team name, department and description → `RESOLVE_TEAM_SERVICE`.
4. `· suggestCategory` — reuses the closest historical incident's category,
   because a real person confirmed it. Falls back to the title.
5. `· suggestSeverity` — keyword rules checked strongest-first, so an outage that
   also says "slow" still reads CRITICAL.

## 4 · `POST /api/incidents` — USER

The largest flow. Creation, prioritisation, team resolution, engineer selection
and status history all happen in **one transaction**, so a failed assignment
cannot leave a half-created incident behind.

| # | File | What happens |
|---|---|---|
| 1 | `dto/CreateIncidentRequest.java` | title, description, service, category, severity. **No** team, engineer or priority field → **400** |
| 2 | `entity/Severity.java` | Rejects anything outside LOW/MEDIUM/HIGH/CRITICAL |
| 3 | `repository/TeamServiceRepository.java` | Resolve service name → owning team. This is how the user picks a service without picking a team |
| 4 | `service/PriorityService.java` | Derive priority from severity. The user never sets it |
| 5 | `service/IncidentService.java · createIncident` | `INSERT RESOLVE_INCIDENT`, flush to get the identity key, then stamp the readable `INC-1024` code derived from it |
| 6 | `· writeLog` | `INSERT RESOLVE_INCIDENT_LOGS` — one row, `status = REPORTED` |
| 7 | `service/AssignmentService.java` | Score every eligible engineer, pick the highest (see below) |
| 8 | `entity/IncidentAssignment.java` | `INSERT RESOLVE_INCIDENT_ASSIGNMENT` with the score rounded to `NUMBER(5,2)` |
| 9 | `· writeLog` | `INSERT RESOLVE_INCIDENT_LOGS` — a second row, `status = ASSIGNED`, only if an engineer was found |
| 10 | `service/RealtimeNotifier.java` | Registered as an **after-commit** callback → `/topic/…/updates` |

> **If the team has no engineer:** the incident stays REPORTED and unassigned and
> a warning is logged. Since only the assigned engineer may update an incident
> and there is no manual assignment endpoint, nothing can then move it. This
> follows from the specified API surface, not from a coding choice.

## 5 · `GET /api/incidents/{id}` — USER · SUPPORT

The entire incident page in one response — which is why no separate
conversation, history, assignment, root-cause or resolution endpoint exists.

1. `service/IncidentAccessService.java · requireViewable` — a USER sees only
   incidents they reported; a SUPPORT engineer sees any incident owned by their
   team → **403 / 404**
2. `· currentAssignment` — highest `assignment_id` wins, so reassignment history
   is preserved without confusing the current view.
3. `repository/IncidentMessageRepository.java` — the whole conversation, ordered
   by `sent_at` then `message_id` so identical timestamps stay deterministic.
4. `repository/IncidentLogRepository.java` — full status history.

## 6 · `POST /api/incidents/{id}/messages` — USER · SUPPORT

One incident is one continuous conversation. Every message is a row keyed by
`incident_id`; **there is no conversation table.**

1. `IncidentAccessService · requireConversationParticipant` — stricter than
   viewing: only the reporter and the assigned engineer may post. A team
   colleague who can *read* the incident still cannot speak in it → **403**
2. `service/IncidentMessageService.java` — sender taken from the principal,
   never the body. `INSERT RESOLVE_INCIDENT_MESSAGE` with `is_read = 0`.
3. `controller/IncidentController.java` — the transaction has now committed.
   Only here does the broadcast go out → `/topic/…/messages`

## 7 · `PATCH /api/incidents/{id}/messages/read` — USER · SUPPORT

1. `dto/MarkReadRequest.java` — at least one id required → **400**
2. `repository/IncidentMessageRepository.java` — the lookup is scoped **by
   incident as well as by id**, so passing another incident's message ids
   touches nothing.
3. `service/IncidentMessageService.java · markRead` — skips your own messages and
   ones already read; flips `is_read` on the rest and returns exactly which ids
   changed.
4. `RealtimeNotifier` after commit → `/topic/…/read`

---

# SUPPORT journey

## 8 · `GET /api/support/dashboard` — SUPPORT

Every figure is computed from existing rows at request time. There is no
analytics table and no analytics endpoint.

1. `repository/IncidentAssignmentRepository.java` — incidents whose *latest*
   assignment is this engineer.
2. `service/SupportService.java · dashboard` — splits resolved from open.
3. `· averageResolutionTime` — mean of `resolved_at − created_at` as `HH:mm:ss`.
4. `· analytics` — most common category, and how many categories recur twice or
   more (the recurring-problem signal).

## 9 · `PATCH /api/support/incidents/{id}` — SUPPORT

One endpoint carries status, root cause and resolution together — which is why
none of them has an endpoint of its own.

1. `IncidentAccessService · requireModifiable` — stricter than viewing: only the
   **assigned** engineer may change anything → **403**
2. `entity/IncidentStatus.java` — the lifecycle is strictly linear, so only the
   very next state is legal. Skips, repeats and reversals rejected → **409**
3. `service/SupportService.java · applyStatusRules` — ROOT CAUSE IDENTIFIED
   needs a root cause; RESOLVED needs both a root cause and a resolution → **409**
4. `entity/Incident.java` — `UPDATE RESOLVE_INCIDENT`, stamping `resolved_at` on RESOLVED.
5. `IncidentService · writeLog` — every transition appends a history row.
6. `controller/SupportController.java` — broadcasts after commit → `/topic/…/updates`

## 10 · `POST /api/support/incidents/{id}/ops-ai` — SUPPORT

Five capabilities behind one endpoint, selected by an `action` field. No
external model and no API key — every answer is computed from the incident, its
conversation and past incidents already in Oracle.

1. `opsai/OpsAiAction.java` — SUMMARIZE · SIMILAR · ANALYZE ·
   ROOT_CAUSE · RESOLUTION. Anything else → **400**
2. `service/SupportService.java · assist` — runs the access check and the AI work
   in **one** read-only transaction, so the incident's lazy associations stay
   loadable.
3. `opsai/OpsAiService.java` — the interface. The implementation behind
   it can be swapped without touching the REST contract.
4. `opsai/DeterministicOpsAiService.java` — ranks past incidents by
   similarity, then answers per action: a conversation digest, ranked matches, an
   analysis with evidence, the root cause those matches agreed on plus a
   confidence figure, or resolution steps mined from what actually fixed them
   before.

> **OpsAI never writes.** A suggested root cause stays a suggestion; it reaches
> the incident only when the engineer submits it through endpoint 9. This is
> enforced by construction: `SupportService.assist` is
> `@Transactional(readOnly = true)`, and `DeterministicOpsAiService` holds only
> read repositories and never calls `save`.

---

## Assignment scoring

Four factors, normalised to 0–100 across the candidate pool, then weighted. The
goal is the best overall candidate — not the most experienced, not the least
busy, not the longest idle.

| Factor | Weight | How it is derived |
|---|---|---|
| **Experience** | 40% | Each incident the engineer handled contributes its similarity to the new one, so many near-identical incidents beat many unrelated ones. Resolved ones count double. Floored at 40 so a new joiner still competes. |
| **Availability** | 25% | Derived from live workload, because the schema forbids an availability column. Under 5 active incidents is AVAILABLE (100), at or above is BUSY (50). Nobody is derived OFFLINE, so a team can always take work. |
| **Workload** | 20% | Not a headcount: each active incident costs its priority weight plus a capped age penalty, so two ageing P1s outweigh four fresh P4s. Lower load scores higher. |
| **Fairness** | 15% | Time since the last assignment, saturating at four hours. Never assigned scores full marks. |

Observed on the live database: with three past payment incidents to her name,
**Priya scored 100.00** against **Rahul's 76** on the same team — the two were
level on availability, workload and fairness, so experience decided it. That
single number is all that persists, in `RESOLVE_INCIDENT_ASSIGNMENT.assignment_score`.

---

## Real-time delivery

WebSocket carries delivery only. Oracle remains the record.

1. `config/WebSocketConfig.java` — endpoint `/ws`; clients publish to `/app/…`
   and subscribe to `/topic/…`.
2. `security/StompAuthChannelInterceptor.java` — the CONNECT frame carries the
   same JWT as HTTP, and is put through the same checks including revocation, so
   a logged-out token cannot open a live channel that would outlive the logout.
   Each SUBSCRIBE is then authorised against the specific incident behind the
   destination.
3. `controller/IncidentWebSocketController.java` — delegates to the very same
   service methods the REST endpoints use, so a message sent over WebSocket is
   validated, authorised and persisted identically. Registers zero HTTP routes.
4. `service/RealtimeNotifier.java` — publishes to `messages`, `updates` and
   `read` for the incident, always post-commit.

> **A gap inherited from the spec.** Every documented destination is
> per-incident, so there is no channel on which to tell an engineer about an
> incident they have not yet heard of. The new-assignment broadcast therefore
> lands on a topic with no subscribers, and engineers discover new work through
> the support dashboard instead.

---

## Error handling

Every failure leaves through `exception/GlobalExceptionHandler.java` in one
shape: `timestamp`, `status`, `error`, `message`, `path`.

| Code | Raised by | Meaning |
|---|---|---|
| 400 | `BadRequestException`, bean validation | Blank title, unknown severity, unresolvable service, empty id list, invalid OpsAI action |
| 401 | `UnauthorizedException`, entry point | Wrong credentials, or missing/expired token |
| 403 | `ForbiddenException`, role gate | Another user's incident, or SUPPORT features reached by a USER |
| 404 | `NotFoundException` | No such incident |
| 409 | `ConflictException` | Illegal status transition, or resolving without root cause and resolution |
| 500 | catch-all | Logged in full server-side; the client is told only that something failed |

No stack trace, SQL error or internal detail ever reaches a client. When the
Oracle password was rotated out from under the application, the client saw a
bare 500 while the real `ORA-01017` stayed in the server log — intended
behaviour.
