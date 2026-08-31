# ResolveIT Backend

ResolveIT is an incident management backend. A user reports an incident, the
system works out which support team owns the affected service and automatically
picks the most suitable engineer on that team, and the two of them then work the
incident through to resolution in a conversation attached to that incident.

This is a learning project built against three specification documents in
[docs/](docs/), which are the source of truth for behaviour, database and API:

- [docs/Feature.md](docs/Feature.md) — what the product does
- [docs/Database_Schema.md](docs/Database_Schema.md) — the Oracle schema
- [docs/backendAPI.md](docs/backendAPI.md) — the REST contract
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — how a request moves through the code

---

## Main features

- **Three roles** — USER reports incidents, SUPPORT resolves them, and
  SUPER_ADMIN provisions SUPPORT accounts. SUPER_ADMIN is an administrative
  role only: it can read no incident and no conversation.
- **Self-registration and JWT login** — one login endpoint serves every role;
  the role comes from the account, never from the request.
- **Super-admin provisioning** — a SUPER_ADMIN picks a team from `GET /api/teams`
  and creates a SUPPORT engineer on it through `POST /api/support-users`. The team
  is mandatory, because assignment only ever considers engineers who have one.
- **Incident reporting** with an assisted classification step that suggests a
  service, category and severity from the incident text. The user confirms or
  overrides the suggestion.
- **Automatic priority** derived from severity.
- **Automatic assignment** — the service resolves to its owning team, and an
  engineer is scored on four factors (similar-incident experience, availability,
  current workload, idle time) with the highest score taking the incident.
- **Incident lifecycle** — a strictly linear six-state workflow from REPORTED to
  RESOLVED, with every transition written to a history table.
- **Per-incident conversation** with read/unread tracking, delivered live over
  WebSocket/STOMP as well as REST.
- **OpsAI** — an advisory assistant that summarises the conversation, finds
  similar past incidents, analyses the incident, suggests a root cause with a
  confidence score, and recommends resolution steps. It is computed from data
  already in Oracle and never writes to the incident.
- **Support dashboard** with workload and analytics for the signed-in engineer.

## Technology stack

Java 21 · Spring Boot 4.0.8 · Spring Security 7 + JWT (jjwt 0.12.6) · Spring Data JPA
(Hibernate 7) · Oracle Database 23ai (ojdbc11) · WebSocket/STOMP · springdoc-openapi 3
(Swagger UI) · Maven.

There is no `src/test/` directory. The API is exercised by hand through Swagger UI, which
carries the JWT for you automatically (see *Testing the APIs manually* below), and the
real-time chat through `docs/websocket-chat-test.html`.

---

## Configuring Oracle

The Oracle schema this application connects to is **shared with other projects**,
so every table ResolveIT owns is prefixed `RESOLVE_`. The application only ever
reads and writes those seven tables; anything else in the schema belongs to
someone else and is never touched.

It never creates or alters a table either — `spring.jpa.hibernate.ddl-auto` is
`none` and must stay that way. The schema is built once by hand from
`src/main/resources/db/schema-oracle.sql`.

All configuration lives in `src/main/resources/application.properties`. The
database settings are the first three lines:

```properties
spring.datasource.url=jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1
spring.datasource.username=OPSPULSE
spring.datasource.password=<the OPSPULSE user's password>
```

**To run this on another machine, change only those three lines.** No Java code
knows anything about the database host, user or password.

The other settings you may want to change:

| Property | Meaning |
|---|---|
| `resolveit.security.jwt.secret` | signing key, must be at least 32 characters |
| `resolveit.security.jwt.expiration-minutes` | how long a token stays valid (480 = 8 hours) |
| `resolveit.security.cors.allowed-origins` | browser origins allowed to call the API |
| `server.port` | HTTP port, 8080 by default |

> The password sits in `application.properties` because this is a local learning
> project and one file is easier to explain than an environment-variable setup.
> Do not push this file to a public repository as it stands.

Setting this up somewhere new for the first time? The next section walks the whole
thing end to end.

---

## Setting up on another machine

Everything below assumes a machine that has never run this project. Follow it top
to bottom and the application will start. Nothing in the Java code knows the
database host, user or password, so **the only file you edit is
`src/main/resources/application.properties`**.

### 1 · What to copy across

Copy the project folder, but **not** `target/`. It is build output, it is large,
and it is rebuilt in seconds on the other side. If you copy it anyway, run
`mvn clean` first thing.

What must come with you:

```
pom.xml
src/                  all of it, including src/main/resources/db/*.sql
docs/                 the specs, the learning guide, the WebSocket tester
README.md
.gitignore
```

A ZIP of the folder minus `target/` is the whole transfer. There is no local
state anywhere else — no config outside the project, no installed service, no
data directory. Everything the application remembers lives in Oracle.

### 2 · Install the prerequisites

| Need | Version | Check it with |
|---|---|---|
| JDK | **21 or newer** | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Oracle Database | 23ai Free is what this was built on | `sqlplus -v` |

Two things that catch people out:

- Maven must build with Java 21. Run `mvn -version` and read the `Java version:`
  line it prints — if it reports 17, set `JAVA_HOME` to the JDK 21 directory,
  reopen the terminal, and check again. A JDK 17 build fails on the language
  level, not at runtime.
- The **JDK**, not the JRE. `javac -version` must answer.

Everything else — Spring Boot, the Oracle JDBC driver, jjwt — is fetched by Maven
on the first build. That first build needs internet access; after that it works
offline. If the office network needs a proxy for Maven, configure it in
`~/.m2/settings.xml` before you start, or the first build will hang on downloads.

### 3 · Create the database user

The application never creates or alters tables — `spring.jpa.hibernate.ddl-auto`
is `none` and must stay that way — so the schema is built once by hand.

Connect as a DBA:

```bash
sqlplus sys/<sys-password>@//localhost:1521/FREEPDB1 as sysdba
```

```sql
CREATE USER OPSPULSE IDENTIFIED BY "<choose-a-password>";
GRANT CONNECT, RESOURCE TO OPSPULSE;
ALTER USER OPSPULSE QUOTA UNLIMITED ON USERS;
```

The user does not have to be called `OPSPULSE`, and the service does not have to
be `FREEPDB1` — whatever you choose, put it in step 5.

### 4 · Create the tables and load the data

Connected as that user, in this order:

```bash
sqlplus -S -L 'OPSPULSE/<password>@//localhost:1521/FREEPDB1' @src/main/resources/db/schema-oracle.sql
sqlplus -S -L 'OPSPULSE/<password>@//localhost:1521/FREEPDB1' @src/main/resources/db/seed-data-oracle.sql
```

`schema` creates the seven tables; `seed` fills them. The scripts look foreign
keys up by name rather than by hardcoded id, so it does not matter what ids
Oracle assigns.

`seed-data-oracle.sql` is only safe on an **empty** schema. The `RESOLVE_ROLE`
rows it inserts — `USER`, `SUPPORT`, `SUPER_ADMIN` — are not sample data: they
are required, because `AuthService` looks the role up by name on every
registration. Everything after that is demo data you could skip, though without
the historical incidents OpsAI has nothing to compare against and returns empty
results.

The other two scripts in `db/` are for a database that is **already** seeded and
running — skip both on a fresh setup, since `seed-data-oracle.sql` already
contains what they add.

### 5 · Point the application at your database

Edit `src/main/resources/application.properties` — these three lines and nothing
else:

```properties
spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
spring.datasource.username=OPSPULSE
spring.datasource.password=<the password you chose in step 3>
```

While you are in the file, change the JWT secret too. It must be at least 32
characters, and the one in the repository is a known development value:

```properties
resolveit.security.jwt.secret=<at least 32 characters, your own>
```

Changing the secret invalidates every token issued by the old one, which on a
fresh machine is exactly what you want.

### 6 · Verify the database before starting the app

Two queries, connected as `OPSPULSE`. They take a second and save a lot of
guessing later:

```sql
SELECT table_name FROM user_tables WHERE table_name LIKE 'RESOLVE%' ORDER BY 1;
-- expect 7 rows

SELECT role_name FROM RESOLVE_ROLE ORDER BY 1;
-- expect SUPER_ADMIN, SUPPORT, USER
```

If the first returns nothing, step 4 ran against a different user or service than
step 5 points at.

### 7 · Build and run

```bash
mvn clean package
java -jar target/resolveit-backend-1.0.0.jar
```

or, during development:

```bash
mvn spring-boot:run
```

It serves on port 8080. Change `server.port` in `application.properties` if that
port is taken on the office machine.

### 8 · Confirm it works

Open **http://localhost:8080/swagger-ui.html**, then:

1. `POST /api/auth/login` with `user@example.com` / `password123` → **200** and a
   token. The page keeps it for you.
2. `GET /api/user/dashboard` → **200**. That proves the JWT, the filter chain and
   Oracle are all working together.
3. `POST /api/auth/logout` → **200**, then call the dashboard again → **401**.
   That proves revocation works.

If all three behave, the setup is complete.

### Seeded accounts

Every seeded account uses the password `password123`, except the super admin,
which uses `admin123`.

| Email | Role | Team |
|---|---|---|
| `user@example.com` (Arjun) | USER | — |
| `meera@example.com` (Meera) | USER | — |
| `support@example.com` (Priya) | SUPPORT | Payment Service |
| `rahul@example.com` (Rahul) | SUPPORT | Payment Service |
| `kavya@example.com` (Kavya) | SUPPORT | Login Service |
| `dev@example.com` (Dev) | SUPPORT | Notification Service |
| `admin@resolve.com` (`admin123`) | SUPER_ADMIN | — |

These are development credentials in a seed script. Change them before the
application is reachable by anyone else.

### If something goes wrong

| Symptom | Cause | Fix |
|---|---|---|
| `ORA-01017: invalid username/password` | Step 5 does not match step 3 | Re-check the three datasource lines |
| `ORA-12541: TNS:no listener` | Oracle is not running, or wrong port | Start the Oracle service; confirm the listener is on 1521 |
| `ORA-12514` / unknown service | Wrong service name in the URL | It is the part after the last `/` — `FREEPDB1` here |
| `ORA-00942: table or view does not exist` | Schema scripts never ran, or ran as another user | Re-run step 4, then verify with step 6 |
| App starts, every login is **401** | Seed data missing, or wrong database | Run the step 6 queries |
| `resolveit.security.jwt.secret must provide at least 256 bits` | Secret under 32 characters | Lengthen it; this is a deliberate startup guard |
| `Web server failed to start. Port 8080 was already in use` | Something else holds the port | Change `server.port`, or stop the other process |
| `invalid target release: 21` | Maven is building with an older JDK | Point `JAVA_HOME` at JDK 21, reopen the terminal |
| First build hangs downloading | No internet, or a proxy is required | Configure `~/.m2/settings.xml` |

---

## Running the backend

Once the database is set up and `application.properties` points at it:

```bash
mvn spring-boot:run
```

Or build a jar and run that:

```bash
mvn clean package
java -jar target/resolveit-backend-1.0.0.jar
```

The application serves on port 8080. It needs Oracle to be running and reachable
at the URL in `application.properties`; if it is not, startup fails with an
`ORA-` error rather than coming up in a degraded state.

---

## Testing the APIs manually

Swagger UI is the quickest way in — start the application and open:

**http://localhost:8080/swagger-ui.html**

**There is no Authorize button, and you never copy a token.** The page keeps the JWT
for you: `POST /api/auth/login` returns one, the browser stores it, and every later
call is sent with it automatically.

1. Call `POST /api/auth/register` to create a USER account, or use a seeded one.
2. Call `POST /api/auth/login`. A banner appears showing who you are now.
3. Call any protected endpoint - the token is attached for you.
4. Call `POST /api/auth/logout` when finished. That revokes the token on the server
   and the page forgets it, so you are signed out. Log in again to continue.

Support-only endpoints need a token from a SUPPORT account, so log in as one.

That auto-token behaviour is browser-side only and grants nothing - it lives in
`config/SwaggerAutoTokenTransformer.java` and `resources/swagger/`. Enforcement is
entirely server-side, so an endpoint your role cannot reach still answers 403.

The OpenAPI document itself is at `/v3/api-docs`, which can be imported into
Postman, Bruno or the VS Code REST client if you prefer working there.

---

## The REST API

Fourteen endpoints. WebSocket/STOMP destinations are not REST endpoints and are
listed separately below.

| # | Method | Path | Role | Purpose |
|---|--------|------|------|---------|
| 1 | POST | `/api/auth/login` | public | Authenticate, return a JWT |
| 2 | GET | `/api/user/dashboard` | USER | The caller's own incidents |
| 3 | POST | `/api/incidents/classify` | USER | Suggest service/category/severity |
| 4 | POST | `/api/incidents` | USER | Create and automatically assign |
| 5 | GET | `/api/incidents/{id}` | USER/SUPPORT | Full incident page |
| 6 | POST | `/api/incidents/{id}/messages` | USER/SUPPORT | Send a message |
| 7 | PATCH | `/api/incidents/{id}/messages/read` | USER/SUPPORT | Mark messages read |
| 8 | GET | `/api/support/dashboard` | SUPPORT | Workload and analytics |
| 9 | PATCH | `/api/support/incidents/{id}` | SUPPORT | Status, root cause, resolution |
| 10 | POST | `/api/support/incidents/{id}/ops-ai` | SUPPORT | All five OpsAI actions |
| 11 | POST | `/api/auth/register` | public | USER self-registration |
| 12 | POST | `/api/support-users` | SUPER_ADMIN | Create a SUPPORT engineer on a team |
| 13 | GET | `/api/teams` | SUPER_ADMIN | Teams for the create-engineer dropdown |
| 14 | POST | `/api/auth/logout` | any logged-in role | Revoke the caller's own JWT |

Registration always creates the USER role. The request accepts no role field, so
neither a SUPPORT nor a SUPER_ADMIN account can be created through it. SUPPORT
accounts are created by a SUPER_ADMIN through `POST /api/support-users`, which
also has no role field and always writes SUPPORT. The SUPER_ADMIN account itself
is seeded in the database and is never created over the API.

Protected endpoints expect the token as `Authorization: Bearer <jwt>`. Missing or
invalid authentication is a 401, and a valid token without the right role is a
403. A URL that matches none of the fourteen returns 404 to an authenticated
caller, and 401 to an anonymous one.

Logout is not public. It revokes whichever token is in the `Authorization` header,
so you can only ever log yourself out of the session you are actually holding.

### WebSocket / STOMP

The handshake endpoint is `/ws` (SockJS, which also exposes a plain WebSocket
transport at `/ws/websocket`). The JWT travels in the `Authorization` header of
the STOMP `CONNECT` frame, and every `SUBSCRIBE` is authorised against the
incident by the same `IncidentAccessService` the REST endpoints use.

A client may only `SEND` to `/app/**`. A `SEND` addressed straight to a broker
destination (`/topic/**`) is rejected, because the simple broker would otherwise
relay it to every subscriber without it passing through a `@MessageMapping`
method — and so without the participant check.

| Direction | Destination | Purpose |
|---|---|---|
| client → server | `/app/incidents/{id}/messages` | send a chat message |
| client → server | `/app/incidents/{id}/read` | mark messages read |
| server → client | `/topic/incidents/{id}/messages` | a new message |
| server → client | `/topic/incidents/{id}/updates` | status / root cause / resolution changed |
| server → client | `/topic/incidents/{id}/read` | messages were marked read |

#### Testing the chat

Swagger cannot test WebSocket. Open **`docs/websocket-chat-test.html`** directly
in your browser instead — double-click it, no server or install needed. It is a
single self-contained page with no libraries: it writes the STOMP frames by
hand, so you can watch exactly what goes over the wire.

1. Open the file in **two** browser windows side by side.
2. In window 1 log in as the USER who **reported** the incident.
3. In window 2 log in as the SUPPORT engineer it was **assigned** to
   (the create-incident response tells you who that is).
4. Put the same incident ID in both and click **Connect**.
5. Type in either window — the message appears in both instantly, and is saved
   to `RESOLVE_INCIDENT_MESSAGE` before it is broadcast.

Anyone who may not view the incident is refused at `SUBSCRIBE`, and anyone who is
not a participant is refused when they send. The page shows the refusal in red. The page is only a testing aid; the backend
does not depend on it in any way.

---

## Authentication and authorization

Two different questions, answered by two different parts of the code.

**Authentication — "who are you?"**

```
POST /api/auth/login  { email, password }
        │
        ▼
AuthController ──► AuthService.login()
        │
        ▼
AuthenticationManager           ← Spring Security does the actual checking
   └─ DaoAuthenticationProvider
        ├─ CustomUserDetailsService.loadUserByUsername(email)
        │     └─ AppUserRepository ──► SELECT ... FROM RESOLVE_USER  (Oracle)
        └─ BCryptPasswordEncoder.matches(raw, password_hash)
        │
        ▼  success
JwtService.generateToken()  ──►  LoginResponse { token, userId, name, role }
```

The client then sends the token on every later request:

```
Authorization: Bearer <jwt>
        │
        ▼
JwtAuthenticationFilter   verifies the signature and expiry,
        │                 checks the token has not been logged out,
        │                 reloads the account, and puts an
        │                 AuthenticatedUser in the SecurityContext
        ▼
SecurityConfig  checks the role for this URL          → 401 / 403
        ▼
Controller ──► Service ──► Repository ──► Oracle
```

The filter never rejects anything itself. A missing, invalid or revoked token simply leaves the
request anonymous, and `SecurityConfig` is what turns that into a 401.

**Logout — "forget this token"**

A signed JWT cannot be un-signed, so logout does not destroy the token; it makes the server stop
honouring it. Every token carries a unique `jti`, and `POST /api/auth/logout` writes that one id
to `TokenRevocationService`. Both front doors — `JwtAuthenticationFilter` for REST and
`StompAuthChannelInterceptor` for WebSocket — consult that list after checking the signature and
**before** deciding who the caller is, so a revoked token never reaches the SecurityContext.

```
POST /api/auth/logout   Authorization: Bearer <jwt>
        │
        ▼
AuthController ──► AuthService.logout()
        │             └─ JwtService.extractIdentity()  → subject, jti, expiry
        ▼
TokenRevocationService.revoke(jti, expiry)
        │
        ▼  200 { "message": "Logged out successfully" }

same jwt ──► any protected REST endpoint   → 401
same jwt ──► WebSocket STOMP CONNECT       → rejected
new login ──► new jwt                      → works again
```

Two properties are worth naming. Revocation is **per token, not per user**: logging out on a
laptop leaves a phone session alive, because each login issued its own `jti`. And the list can
only ever *reject* a token, never accept one, so it cannot become a way in.

Entries are dropped once the token they name would have expired anyway, so the list is bounded by
logouts per token lifetime rather than by uptime. There is **no refresh token** in this system:
once the access token is revoked, the only way back is `POST /api/auth/login`.

The list is held in memory. That keeps `SessionCreationPolicy.STATELESS` intact — nothing here is
a session, and a request still authenticates purely from its own token — and it avoids a DDL
change to a schema shared with other projects. The trade-off is that a **restart clears the
list**, so a token logged out shortly before a restart works again for the remainder of its eight
hours. `TokenRevocationService` is the whole seam: backing it with a table or Redis is a
one-class swap that touches neither the filter, the interceptor nor the endpoint.

**Authorization — "are you allowed?"** — happens at two levels:

| Level | Where | Example |
|---|---|---|
| URL + role | `config/SecurityConfig.java` | only SUPPORT may call `/api/support/**` |
| Individual incident | `service/IncidentAccessService.java` | only the *assigned* engineer may update *this* incident |

### CORS — letting a browser page call the API

A browser will not let a page read a response from a *different* origin unless
the server says so. Before any `POST` carrying JSON it first sends a preflight
`OPTIONS` request; if that is rejected, the real request is never sent at all.

`SecurityConfig` therefore declares a `CorsConfigurationSource` bean and enables
`.cors(...)` on the filter chain. Enabling it also lets Spring Security answer
preflight `OPTIONS` requests before the authorization rules run, which is
required — a preflight carries no credentials by design.

Allowing every origin is safe **for this API specifically**, because
authentication is a Bearer token the client must attach deliberately. There is
no cookie or session for a malicious site to ride on, and it cannot read a token
belonging to another origin. Set `resolveit.security.cors.allowed-origins` to your
real frontend URL for a deployment that needs it locked down.

Note that WebSocket is **not** subject to CORS — the STOMP handshake is governed
separately by `setAllowedOriginPatterns("*")` in `WebSocketConfig`.

A URL rule cannot express the second one, because it depends on database rows — so it lives in
the service layer, and both REST and WebSocket go through the same three methods:

| Method | Who passes | Used by |
|---|---|---|
| `requireViewable` | reporter, assigned engineer, or same-team SUPPORT | incident details, STOMP SUBSCRIBE |
| `requireConversationParticipant` | reporter or assigned engineer only | send message, mark read |
| `requireModifiable` | assigned engineer only | status update, OpsAI |

## USER vs SUPPORT vs SUPER_ADMIN

| | USER | SUPPORT | SUPER_ADMIN |
|---|---|---|---|
| Self-registration | yes, `POST /api/auth/register` | **no** — created by a SUPER_ADMIN | **no** — seeded in the database |
| Login | yes | yes (same endpoint) | yes (same endpoint) |
| Logout | yes | yes (same endpoint) | yes (same endpoint) |
| Report an incident | yes | no | no |
| See own incidents | yes (only their own) | — | no |
| Support dashboard | no | yes | no |
| Change status / root cause / resolution | no | yes, assigned incidents only | no |
| OpsAI | no | yes, assigned incidents only | no |
| Incident conversation | yes, incidents they reported | yes, incidents assigned to them | **no** |
| List teams | no | no | yes |
| Create SUPPORT engineers | no | no | yes |

SUPER_ADMIN is deliberately **not** a superuser over incidents. `IncidentAccessService.canView`
returns false for any role that is not USER or SUPPORT, so a super admin receives 403 on every
incident, message and OpsAI endpoint. It provisions accounts and nothing else.

The role is a row in the `RESOLVE_ROLE` table, linked from `RESOLVE_USER.role_id`. It reaches Spring
Security as the authority `ROLE_USER`, `ROLE_SUPPORT` or `ROLE_SUPER_ADMIN`, which is what
`hasRole("USER")` and friends check. Because neither `RegisterRequest` nor
`CreateSupportUserRequest` has a role field at all, there is no payload that could ask for a
different role — an unexpected `"role"` in the JSON body is ignored.

## Super admin: creating a SUPPORT engineer

```
POST /api/auth/login   { "email": "admin@resolve.com", "password": "admin123" }
        |  returns a JWT whose account has role SUPER_ADMIN
        v
GET  /api/teams        Authorization: Bearer <jwt>
        |  [ { "teamId": 1, "teamName": "Payment Support Team" }, ... ]
        |  the UI shows teamName and keeps teamId
        v
POST /api/support-users   Authorization: Bearer <jwt>
{ "name": "Priya", "email": "priya@example.com", "password": "support123", "teamId": 1 }
        |
        v  201 Created
{ "userId": 12, "name": "Priya", "email": "priya@example.com",
  "role": "SUPPORT", "teamId": 1, "teamName": "Payment Support Team" }
```

What the backend guarantees:

- `teamId` is **mandatory** (`@NotNull`) and must reference a real row, else 400.
- The role is always `SUPPORT`; a `role` field in the body is ignored, so this
  endpoint can never mint another SUPER_ADMIN.
- The password is encoded with the shared `BCryptPasswordEncoder`; only the hash
  reaches `RESOLVE_USER.PASSWORD_HASH`, and neither the raw password nor the hash
  appears in the response.
- A duplicate email is a 409.
- The engineer is immediately eligible for automatic assignment, because
  `findSupportEngineersByTeam` matches on `RESOLVE_USER.TEAM_ID`.

## Database structure

Seven tables, all prefixed `RESOLVE_` because the Oracle schema is shared with other
projects. Every relationship is **many-to-one**; there are no one-to-one and no
many-to-many relationships, and no foreign key ever points outside the `RESOLVE_` set.

| Table | Holds | Points at |
|---|---|---|
| `RESOLVE_ROLE` | USER, SUPPORT and SUPER_ADMIN, three rows | — |
| `RESOLVE_TEAM_SERVICE` | a support team and the service it owns | — |
| `RESOLVE_USER` | every account, whatever the role. `TEAM_ID` is set for SUPPORT engineers, null for USER and SUPER_ADMIN | `RESOLVE_ROLE`, `RESOLVE_TEAM_SERVICE` |
| `RESOLVE_INCIDENT` | one row per incident; `status` is the **current** state | `RESOLVE_USER` (reporter), `RESOLVE_TEAM_SERVICE` |
| `RESOLVE_INCIDENT_ASSIGNMENT` | which engineer got it, and the winning score | `RESOLVE_INCIDENT`, `RESOLVE_USER` |
| `RESOLVE_INCIDENT_MESSAGE` | the incident conversation, with `is_read` | `RESOLVE_INCIDENT`, `RESOLVE_USER` (sender) |
| `RESOLVE_INCIDENT_LOGS` | status **history**, one row per status entered | `RESOLVE_INCIDENT` |

```
RESOLVE_ROLE ──┐
       ├──< RESOLVE_USER >──┬──< RESOLVE_INCIDENT >──┬──< RESOLVE_INCIDENT_ASSIGNMENT
RESOLVE_TEAM_SERVICE ─┬───────┘                ├──< RESOLVE_INCIDENT_MESSAGE
              └───────────────────────>┘└──< RESOLVE_INCIDENT_LOGS
```

`RESOLVE_INCIDENT_LOGS` is deliberately minimal — `LOG_ID`, `INCIDENT_ID`, `STATUS`, `CHANGED_AT`. One
row records one status the incident entered; the previous status is simply the previous row.
There is no `OLD_STATUS`, no `NEW_STATUS` and no `CHANGED_BY`.

Full column-level detail is in [docs/Database_Schema.md](docs/Database_Schema.md).

> **Sharing the schema.** The Oracle schema also contains tables belonging to other
> projects — `USER`, `ROLE`, `INCIDENT` and so on without the prefix. ResolveIT never
> reads, writes or alters any of them. Every `@Table` annotation in `entity/` names a
> `RESOLVE_` table, and `ddl-auto=none` means Hibernate cannot create or change a table
> even by accident.

## Decisions the specifications did not settle

Each of these was decided explicitly before implementation.

| Topic | Decision | Why it needed deciding |
|---|---|---|
| **Assignment weights** | Experience 40%, Availability 25%, Workload 20%, Idle 15% | Feature.md §9/§14 and Database_Schema.md §8 say 40/25/20/15; backendAPI.md §7 says 40/30/20/10. The two-document majority was chosen. |
| **Status after creation** | `REPORTED` always logged; `ASSIGNED` added only when an engineer was actually selected | backendAPI.md §6 shows `REPORTED` in the response, but the same call creates the assignment, and Feature.md §18 defines `ASSIGNED` as exactly that. |
| **Priority** | CRITICAL→P1, HIGH→P1, MEDIUM→P3, LOW→P4 | No rule is given, but every example in all three files shows HIGH→P1. P2 is therefore never produced. |
| **SUPPORT authorization** | View any incident owned by their team; modify only incidents assigned to them | "Authorized incident" is never defined. |
| **Availability** | Derived from active workload: `AVAILABLE` below 5 active incidents, `BUSY` at or above | Database_Schema.md §31 forbids an availability column. Nothing is derived as `OFFLINE`, so a team can always accept work. |
| **OpsAI** | Deterministic in-application engine behind the `OpsAiService` interface | Confirmed by backendAPI.md §19. No external provider, no API key. |

The `service` string in `POST /api/incidents` resolves against
`RESOLVE_TEAM_SERVICE.service_name` case-insensitively, and that row's `team_id` becomes
the incident's team. An unmatched service is a 400.

---

## Known limitations

These follow from the specifications as written rather than from defects in the
code, but they are worth knowing about.

1. **A new assignment has no channel to reach the engineer.** backendAPI.md §27
   asks for the assigned engineer to be notified over WebSocket, but every
   destination in §24 is per-incident, and an engineer cannot already be
   subscribed to an incident that did not exist a moment ago. In practice new
   work is discovered through the SUPPORT dashboard. A proper fix needs a
   per-user destination, which would go beyond the documented destinations.

2. **An unassignable incident cannot progress.** If a service's team has no
   SUPPORT engineer, the incident stays REPORTED and unassigned, and since only
   the assigned engineer may update it, nothing can move it. The condition is
   logged as a warning.

3. **Priority band P2 is unreachable** under the agreed severity mapping.

4. **The logout revocation list is in memory.** It keeps the `jti` of every
   logged-out token until that token would have expired anyway, which keeps
   Spring Security stateless and needs no new table. The cost is that a restart
   clears the list, so a token logged out shortly before a restart works again
   for the remainder of its eight hours, and the list is not shared between
   instances. `TokenRevocationService` is the whole seam — backing it with a
   table or Redis is a one-class change that touches neither the filter, the
   STOMP interceptor nor the endpoint.

5. **There are no automated tests.** Nothing re-checks the security rules after
   a change, so edits to `SecurityConfig`, `JwtAuthenticationFilter`,
   `TokenRevocationService` or `IncidentAccessService` have to be re-verified by
   hand through Swagger.

---

## Project layout

```
src/main/java/com/resolveit/
  ResolveItApplication.java  The main class

  controller/   Six REST controllers (fourteen endpoints) + one STOMP controller
  service/      Business logic: auth, incidents, conversation, support workflow,
                assignment, classification, priority, per-incident access
  repository/   Seven Spring Data JPA repositories
  entity/       Seven JPA entities (one per table) + the three enums describing
                their column values: IncidentStatus (lifecycle + transition
                rules), Severity, Priority
  dto/          Request/response records matching the documented JSON
  config/       SecurityConfig, WebSocketConfig, OpenApiConfig
  security/     JWT issue/verify, request filter, user lookup, STOMP
                CONNECT/SUBSCRIBE/SEND auth, logout token revocation,
                401/403 handlers, RoleName
  exception/    Typed exceptions + one @RestControllerAdvice
  opsai/        OpsAiService abstraction + the deterministic implementation

src/main/resources/
  application.properties          the only file to edit on a new machine
  db/           schema-oracle.sql       creates the seven RESOLVE_ tables
                seed-data-oracle.sql    roles, teams, demo accounts, history
                add-super-admin-oracle.sql        incremental, existing database
                add-network-infra-teams-oracle.sql  incremental, existing database
  swagger/      the browser-side scripts that make Swagger carry the JWT

docs/           The three specifications, architecture notes, the learning guide,
                and websocket-chat-test.html (a standalone WebSocket chat tester)
```

There is no `src/test/` directory.

### Design notes

- **Oracle is the source of truth.** Every message and status change is
  persisted and committed *before* anything is broadcast over WebSocket.
- **Identity always comes from the JWT.** No client-supplied `userId`,
  `senderId` or `supportUserId` is trusted.
- **OpsAI never writes.** It reads the incident, its conversation and history and
  returns advice. A suggested root cause becomes real only once SUPPORT confirms
  it through API 9.
- **The lifecycle is strictly linear.** Only the next state is allowed; skips,
  repeats and reversals are 409s. `ROOT CAUSE IDENTIFIED` requires a root cause,
  and `RESOLVED` requires both a root cause and a resolution.
- **The Java class is `AppUser`, the table is `RESOLVE_USER`.** The class avoids
  colliding with Spring Security's own `User` type. Because the table is prefixed
  it is no longer the Oracle reserved word `USER`, so nothing has to be quoted.
