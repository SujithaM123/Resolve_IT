# ResolveIT Backend — Learning & Evaluation Guide

This guide explains **your actual code**, file by file and feature by feature, in the
order a request travels through it.

Everything here was read from the real project. Where something does not exist, it
says **Not implemented** instead of guessing.

**Quick facts about the current project**

| Thing | Count |
|---|---|
| Spring Boot version | 4.0.8 (Java 21) |
| Java files under `src/main/java` | 98 |
| REST controllers | 6 (+1 STOMP controller) |
| REST endpoints | 14 |
| Entities | 7 (+3 domain enums in the same package) |
| Repositories | 7 |
| Oracle tables | 7 |
| DTO records | 34 |
| Automated tests | **None** — there is no `src/test/` directory; the API is exercised by hand through Swagger UI |

---

# PART 1 — THE PROJECT FOUNDATION

## 1.1 Folder structure

```
<project folder>/
├── src/main/java/com/dtcc/intern/demo/
│   ├── ResolveItApplication.java   ← the starting point
│   ├── config/                    ← settings: security, websocket, swagger
│   ├── controller/                ← receives HTTP and STOMP requests
│   ├── dto/                       ← the JSON shapes going in and out
│   ├── entity/                    ← Java classes mapped to Oracle tables,
│   │                                plus the enums describing their column values
│   ├── exception/                 ← error types + one central error handler
│   ├── opsai/                     ← the OpsAI assistant
│   ├── repository/                ← talks to the database
│   ├── security/                  ← JWT, login identity, request filter,
│   │                                logout token revocation
│   └── service/                   ← the actual business logic
├── src/main/resources/
│   ├── application.properties     ← configuration values
│   └── db/                        ← schema + seed SQL scripts
├── docs/                          ← specifications and this guide
├── pom.xml                        ← dependencies and build
└── .gitignore
```

### Why each folder exists

| Folder | What it holds | Why it is separate |
|---|---|---|
| `controller/` | Classes that receive a request | Their only job is HTTP/STOMP. No business rules here. |
| `dto/` | Request and response shapes | So we never send database objects straight to the client. |
| `service/` | Business logic | The rules live in one place, reusable by REST *and* WebSocket. |
| `repository/` | Database access | Spring writes the SQL for us from method names. |
| `entity/` | Table mappings | One Java class = one Oracle table, plus the enums with the rules for their column values (e.g. which status may follow which). |
| `security/` | JWT, identity and the logout revocation list | Keeps authentication out of controllers, and lets REST and WebSocket share one set of token rules. |
| `config/` | Startup settings | Spring reads these once when the app boots. |
| `exception/` | Error types + handler | Every error comes out in the same JSON shape. |
| `opsai/` | The OpsAI assistant | One self-contained feature with its own interface and implementation, so it does not clutter the ordinary services. |

### How they relate

```
controller  →  service  →  repository  →  entity  →  Oracle table
     ↑                                        ↑
    dto                                   enums with rules
```

`security/` sits **before** the controller. `exception/` sits **around** everything.

---

## 1.2 `pom.xml`

**What is this file?**
The Maven build file. It lists every library the project uses and how to build the jar.

**Why do we need it?**
Without it, Java would not know where Spring Boot, Oracle drivers or JWT classes come from.

**Important parts in your file**

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.8</version>
</parent>

<properties>
    <java.version>21</java.version>
    <jjwt.version>0.12.6</jjwt.version>
    <springdoc.version>3.0.3</springdoc.version>
</properties>
```

The `parent` gives sensible default versions so you don't have to pick them yourself.

**Your 10 dependencies**

| Dependency | Scope | Why your project needs it |
|---|---|---|
| `spring-boot-starter-web` | compile | REST controllers, Tomcat, JSON |
| `spring-boot-starter-data-jpa` | compile | Repositories + Hibernate |
| `spring-boot-starter-security` | compile | Login rules, roles, BCrypt |
| `spring-boot-starter-websocket` | compile | WebSocket/STOMP chat |
| `spring-boot-starter-validation` | compile | `@NotBlank`, `@Email` checks |
| `springdoc-openapi-starter-webmvc-ui` | compile | Swagger UI page |
| `ojdbc11` | runtime | The Oracle JDBC driver |
| `jjwt-api` | compile | Create and read JWTs |
| `jjwt-impl` | runtime | The JWT implementation |
| `jjwt-jackson` | runtime | JSON inside the JWT |

**Two things worth knowing about the Spring Boot 4 versions**

- `springdoc-openapi` must be **3.x** here. The 2.x line was built for Spring Boot 3 and
  does not start on Spring Boot 4.
- Spring Boot 4 ships **Jackson 3**, whose classes live in `tools.jackson.databind` instead
  of the old `com.fasterxml.jackson.databind`. Almost nothing in the project notices, because
  Spring converts your DTOs to JSON for you. The two exceptions are
  `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler`, which write JSON by hand and
  therefore import `tools.jackson.databind.ObjectMapper`.

**If it did not exist:** nothing would compile or run.

> **Say to the evaluator:** "`pom.xml` is my build file. It declares Java 21, Spring Boot
> 4.0.8, and the libraries I use — web, JPA, security, websocket, validation, springdoc for
> Swagger, the Oracle driver, and jjwt for tokens. Maven downloads them and packages my jar."

---

## 1.3 `ResolveItApplication.java`

**The whole file:**

```java
@SpringBootApplication
public class ResolveItApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResolveItApplication.class, args);
    }
}
```

**What is this file?** The entry point. Running the jar calls this `main` method.

**What does `@SpringBootApplication` mean?** It is three annotations in one:

| Part | Meaning |
|---|---|
| `@SpringBootConfiguration` | This class holds configuration |
| `@EnableAutoConfiguration` | Spring sets up Tomcat, JPA, security automatically |
| `@ComponentScan` | Spring scans `com.dtcc.intern.demo` and everything below it |

That scan is *why* your `@RestController`, `@Service`, `@Repository` and `@Component`
classes get found without you registering them anywhere.

**If it did not exist:** the application could not start.

> **Say to the evaluator:** "This is my main class. `@SpringBootApplication` turns on
> auto-configuration and component scanning of the `com.dtcc.intern.demo` package, so all my
> controllers, services and repositories are discovered and wired automatically."

---

## 1.4 `application.properties`

**What is this file?** Configuration values, kept out of Java code.

### Oracle section

```properties
spring.datasource.url=jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1
spring.datasource.username=OPSPULSE
spring.datasource.password=<the OPSPULSE user's password>
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
spring.datasource.hikari.pool-name=ResolveItHikari
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=10000
```

Reading the URL: `jdbc:oracle:thin:` is the driver type, `//127.0.0.1:1521` is the
host and listener port, and `FREEPDB1` is the Oracle service name.

**These three lines are the only thing you change to move the project to another
machine.** No Java class mentions a host, user or password anywhere.

**Hikari** is the connection pool: instead of opening a new Oracle connection for
every request (slow), it keeps up to 10 open and reuses them. The 10-second
timeout means a wrong host fails quickly with a clear error rather than hanging.

> **Say to the evaluator:** "My database settings are in `application.properties`,
> not in Java. If the database moves I change three lines and nothing else. For a
> real deployment I would keep the password out of this file, but for a local
> learning project one file is simpler to follow."

### Which class reads what

| Property | Read by |
|---|---|
| `spring.datasource.*` | Spring Boot, to build the `DataSource` |
| `spring.jpa.*` | Hibernate |
| `resolveit.security.jwt.*` | `JwtService` |
| `resolveit.security.cors.allowed-origins` | `SecurityConfig` |

### JPA section

```properties
spring.jpa.hibernate.ddl-auto=none
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
```

| Setting | Why it matters in your project |
|---|---|
| `ddl-auto=none` | **Hibernate must never create, change or drop your tables.** Oracle is the source of truth. |
| `open-in-view=false` | The database session closes when the service finishes, not during JSON writing. Keeps DB work inside the service layer. |

### Web section

```properties
spring.mvc.throw-exception-if-no-handler-found=true
spring.web.resources.add-mappings=false
```

Together these make an unknown URL throw `NoHandlerFoundException`, which your
`GlobalExceptionHandler` turns into a clean 404.

### Security section

```properties
resolveit.security.jwt.secret=resolveit-local-dev-secret-key-at-least-32-bytes-long
resolveit.security.jwt.expiration-minutes=480
resolveit.security.cors.allowed-origins=*
```

The first two are read by `JwtService`; 480 minutes is an 8-hour token life. The
secret must be at least 32 characters or `JwtService` refuses to start — HMAC-SHA
needs at least 256 bits of key material.

Eight hours is the *maximum* life of a token, not the guaranteed one:
`POST /api/auth/logout` can end it sooner by revoking it. There is no property for
revocation — the list is held in memory by `TokenRevocationService` and needs no
configuration.

`allowed-origins` is read by `SecurityConfig` and controls which web pages may
call the API from a browser.

### Server + Swagger section

```properties
server.port=8080
server.error.include-message=never
server.error.include-stacktrace=never
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.persistAuthorization=true
```

`include-stacktrace=never` means an internal error never leaks Java details to the client.

> **Say to the evaluator:** "All configuration is here, and credentials come from
> environment variables so no password is in the repository. The two lines I always point
> out are `ddl-auto=none`, because Oracle owns the schema, and the physical naming strategy,
> because the Oracle schema is shared, so every table my project owns is prefixed `RESOLVE_`."

---

## 1.5 Database configuration

**There is no `DatabaseConfig.java` class — and that is correct.**

Your database setup is entirely in `application.properties`. Spring Boot's
auto-configuration reads those `spring.datasource.*` properties and builds:

1. a **HikariCP connection pool**
2. a **Hibernate `EntityManagerFactory`**
3. a **transaction manager**

Then it scans `entity/` for `@Entity` classes and `repository/` for interfaces extending
`JpaRepository`, and creates an implementation of each repository at startup.

```
application.properties
      ↓
Spring Boot auto-configuration
      ↓
HikariCP pool → Oracle (FREEPDB1)
      ↓
Hibernate reads @Entity classes
      ↓
Spring Data builds repository implementations
```

> **Say to the evaluator:** "I did not write a database config class because Spring Boot
> auto-configures the DataSource, Hibernate and the transaction manager from the properties
> file. My job was to supply the URL, credentials, driver and `ddl-auto=none`."

---

## 1.6 `config/SecurityConfig.java`

**What is this file?** The rulebook for who may call which URL.

**Annotations**

| Annotation | Meaning |
|---|---|
| `@Configuration` | This class defines Spring beans |
| `@EnableWebSecurity` | Turn on Spring Security's filter chain |

**What it injects (constructor injection):**
`JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`.

### The `filterChain` bean

```java
http
  .csrf(csrf -> csrf.disable())
  .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
  .exceptionHandling(h -> h
      .authenticationEntryPoint(authenticationEntryPoint)
      .accessDeniedHandler(accessDeniedHandler))
```

| Line | Why |
|---|---|
| `csrf.disable()` | CSRF protection is for browser form+cookie apps. You use a JWT header, so it does not apply. |
| `STATELESS` | No server-side session. Every request must carry its own token. |
| `authenticationEntryPoint` | Produces your JSON **401** body |
| `accessDeniedHandler` | Produces your JSON **403** body |

### The URL rules, in order (order matters — first match wins)

```java
.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
.requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
.requestMatchers("/ws/**").permitAll()
.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

.requestMatchers(HttpMethod.GET,  "/api/user/dashboard").hasRole(RoleName.USER)
.requestMatchers(HttpMethod.POST, "/api/incidents/classify").hasRole(RoleName.USER)
.requestMatchers(HttpMethod.POST, "/api/incidents").hasRole(RoleName.USER)

.requestMatchers(HttpMethod.GET,   "/api/incidents/*").authenticated()
.requestMatchers(HttpMethod.POST,  "/api/incidents/*/messages").authenticated()
.requestMatchers(HttpMethod.PATCH, "/api/incidents/*/messages/read").authenticated()

.requestMatchers("/api/support/**").hasRole(RoleName.SUPPORT)

.anyRequest().authenticated()
```

Three levels of protection:

1. **`permitAll()`** — login, register, the WebSocket handshake, Swagger.
   Note that **logout is not on this list**: you can only revoke a token you are
   already holding, so it must be `authenticated()`.
2. **`hasRole(...)`** — role gate. USER-only or SUPPORT-only.
3. **`authenticated()`** — any logged-in user; the *real* check happens per-incident in
   `IncidentAccessService` because it depends on data, not on the URL.

`/ws/**` is `permitAll` at HTTP level because the JWT is checked later, inside the STOMP
`CONNECT` frame by `StompAuthChannelInterceptor`.

The final `.anyRequest().authenticated()` catches unknown URLs: an anonymous caller gets
401, a logged-in caller passes through to the dispatcher which throws
`NoHandlerFoundException` → **404**.

### The filter position

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

Your JWT filter must run **before** Spring's normal login filter, so the token is read and
the user is identified before the authorization rules are evaluated.

### CORS — why a browser page needs it

```java
.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

A browser refuses to let a page read a response from a **different origin**
unless the server allows it. Before sending a `POST` with JSON it first sends a
**preflight `OPTIONS`** request to ask permission.

Without this line, that preflight fell through to `.anyRequest().authenticated()`
and came back **401**, so the browser never sent the real request — and any web
page calling the API just saw "cannot reach the server". Enabling `.cors(...)`
also lets Spring Security answer preflights *before* the authorization rules run,
which is correct: a preflight deliberately carries no credentials.

The `corsConfigurationSource()` bean reads the allowed origins from
`resolveit.security.cors.allowed-origins` (default `*`). Allowing every origin is
safe here because authentication is a **Bearer token the client attaches
deliberately** — there is no cookie or session a malicious site could ride on.

> **Say to the evaluator:** "CORS is a *browser* rule, not a server security
> feature. It controls which web pages may read my responses. My API is
> token-based with no cookies, so allowing all origins does not let anyone in —
> they would still need a valid JWT. Postman and curl ignore CORS entirely,
> which is why the API worked there before I added this."

### The two other beans

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

@Bean
public AuthenticationManager authenticationManager(CustomUserDetailsService userDetailsService,
                                                   PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    provider.setHideUserNotFoundExceptions(true);
    return new ProviderManager(provider);
}
```

`BCryptPasswordEncoder` is the **single** encoder used by both registration (to hash) and
login (to verify). That is why they always agree.

`AuthenticationManager` is the component that actually checks an email and password.
`AuthService.login()` hands it the submitted credentials, and it does three things:

1. `DaoAuthenticationProvider` calls `CustomUserDetailsService.loadUserByUsername(email)`,
   which reads the row from the `RESOLVE_USER` table.
2. It compares the submitted password against the stored BCrypt hash using the encoder above.
3. On success it returns an `Authentication` whose principal is your `AuthenticatedUser`.

`setHideUserNotFoundExceptions(true)` makes an unknown email fail in exactly the same way as
a wrong password, so the login endpoint cannot be used to discover which email addresses are
registered.

> **Spring Boot 4 note:** in Spring Security 7 `DaoAuthenticationProvider` no longer has a
> no-argument constructor — the `UserDetailsService` is passed in through the constructor and
> `setUserDetailsService(...)` has been removed. This was the only Spring Security API change
> the upgrade required.

**If this file did not exist:** every endpoint would be open to everyone.

> **Say to the evaluator:** "This is my security rulebook. It is stateless, CSRF is off
> because I use bearer tokens, my JWT filter runs before the username-password filter, and
> the rules go public → role-gated → authenticated. Per-incident ownership is not checked
> here because it depends on data, so it is done in `IncidentAccessService`."

---

## 1.7 The JWT classes (`security/`)

There are 6 JWT-related classes plus 2 error handlers.

### `JwtService.java` — creates and verifies tokens

```java
public JwtService(@Value("${resolveit.security.jwt.secret}") String secret,
                  @Value("${resolveit.security.jwt.expiration-minutes:480}") long expirationMinutes) {
    this.signingKey = buildKey(secret);
    this.expirationMillis = expirationMinutes * 60_000L;
}
```

`buildKey` accepts a base64 secret or a raw string, and **throws if the key is shorter
than 32 bytes** (256 bits) — a short key is not safe for HMAC signing.

```java
public String generateToken(AuthenticatedUser user) {
    return Jwts.builder()
            .id(UUID.randomUUID().toString())   // the jti - what logout revokes
            .subject(user.getEmail())
            .claims(Map.of("userId", user.getUserId(),
                           "role",   user.getRole(),
                           "name",   user.getName()))
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMillis))
            .signWith(signingKey)
            .compact();
}
```

The token carries the email as **subject**, plus `userId`, `role` and `name`, and a
unique **`jti`** (JWT ID). The `jti` is what makes logout possible: it names *this one
token*, so revoking it does not touch tokens issued to the same person on other devices.

```java
public Optional<String> extractSubject(String token) { ... }
public Optional<TokenIdentity> extractIdentity(String token) { ... }
```

Both return a value **only if the signature and expiry are valid**; otherwise
`Optional.empty()`. Notice they never throw — an invalid token simply means "no user".

`extractIdentity` returns the three things the security layer needs — subject, `jti` and
expiry — as a `TokenIdentity` record. It also returns empty when the token has no `jti`,
which is **failing closed**: a token that cannot be checked against the revocation list is
refused rather than trusted.

### `AuthenticatedUser.java` — who is logged in

Implements Spring Security's `UserDetails`. Holds `userId`, `name`, `email`,
`passwordHash`, `role`, `teamId`.

```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
}
```

This is the link to `hasRole("USER")` in `SecurityConfig` — Spring adds the `ROLE_` prefix
automatically, so the authority must be `ROLE_USER` / `ROLE_SUPPORT`.

The static factory `from(AppUser user)` converts a database row into this principal.

### `CustomUserDetailsService.java` — loads the user

```java
@Override
@Transactional(readOnly = true)
public AuthenticatedUser loadUserByUsername(String email) {
    return appUserRepository.findByEmailIgnoreCase(email)
            .map(AuthenticatedUser::from)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
}
```

One repository call, converted into the principal.

### `JwtAuthenticationFilter.java` — runs on every request

Extends `OncePerRequestFilter` (guaranteed to run exactly once per request).

```java
String token = BearerTokens.resolve(request.getHeader(BearerTokens.HEADER));
if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    jwtService.extractIdentity(token)
            .filter(identity -> !revocationService.isRevoked(identity.tokenId()))
            .ifPresent(identity -> authenticate(identity.subject(), request));
}
filterChain.doFilter(request, response);
```

Key beginner points:

- It reads the `Authorization: Bearer ...` header.
- **No header means no authentication is set — but the request still continues.**
  The filter never rejects anything. Rejection is `SecurityConfig`'s job.
- The `.filter(...)` line is the logout check, and **its position is the whole point**:
  it runs before `authenticate(...)`, so a logged-out token can never populate the
  SecurityContext. A revoked token therefore behaves exactly like no token at all.
- If the token is valid and not revoked it builds a `UsernamePasswordAuthenticationToken`
  and stores it in `SecurityContextHolder`, which is how `@AuthenticationPrincipal` later
  works.

### `TokenRevocationService.java` — the logout list

```java
private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

public void revoke(String tokenId, Instant expiresAt) { ... }
public boolean isRevoked(String tokenId) { ... }
```

A signed JWT cannot be un-signed — the maths says it is valid until it expires. So logout
cannot destroy the token; it makes the server **stop honouring** it. This class remembers
the `jti` of every logged-out token until that token's own expiry, at which point the entry
is dead weight and is dropped.

Four things worth understanding:

- **Bounded memory.** Entries expire with the tokens they name, so the map is limited by
  logouts-per-token-lifetime, not by uptime.
- **Still stateless.** This is not an HTTP session. A request still authenticates purely
  from its own token; `SessionCreationPolicy.STATELESS` is untouched.
- **It can only subtract.** The list can reject a token, never accept one, so it can never
  become a way in.
- **Per token, not per user.** Because each login mints its own `jti`, logging out on a
  laptop leaves a phone session alive.

It is held in memory rather than in a table, which is the right trade for a single-node
deployment sharing its Oracle schema with other projects — but it means a **restart clears
the list**. The class is the whole seam: swapping it for a table- or Redis-backed version
changes nothing else.

### `BearerTokens.java` — one parser, three callers

A four-line helper that turns `Authorization: Bearer <token>` into the bare token. The REST
filter, the STOMP interceptor and the logout endpoint all use it, so they cannot drift apart
— which matters, because a token revoked under one spelling must not still be accepted under
another.

### `RestAuthenticationEntryPoint` (401) and `RestAccessDeniedHandler` (403)

Both are `@Component`s that write your standard JSON error body directly to the response,
because these failures happen in the **filter chain, before any controller runs**, so
`@RestControllerAdvice` cannot catch them.

```java
ApiErrorResponse body = ApiErrorResponse.of(
        HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.name(),
        "Authentication is required", request.getRequestURI());
```

> **Say to the evaluator:** "`JwtService` signs and verifies tokens, and stamps each one with
> a unique `jti`. `JwtAuthenticationFilter` runs once per request, reads the Bearer header,
> checks the token has not been logged out, and if it is valid puts an `AuthenticatedUser` in
> the SecurityContext. It never rejects — `SecurityConfig` decides that, and the entry point
> and access-denied handler write my standard 401/403 JSON. Logout works by revoking the
> token's `jti` in `TokenRevocationService`, which both the REST filter and the WebSocket
> interceptor consult before establishing identity."

---

## 1.8 `config/WebSocketConfig.java`

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
```

| Method | Meaning in plain English |
|---|---|
| `registerStompEndpoints` | The browser connects to `ws://host/ws`. SockJS is a fallback for old browsers. |
| `enableSimpleBroker("/topic")` | Server → client messages go to `/topic/...`. An in-memory broker, no external message server. |
| `setApplicationDestinationPrefixes("/app")` | Client → server messages go to `/app/...` and land on `@MessageMapping` methods. |
| `configureClientInboundChannel` | Plugs in your JWT check for STOMP. |

### `security/StompAuthChannelInterceptor.java`

HTTP security does not protect STOMP frames, so this class does the equivalent job.

```java
if (StompCommand.CONNECT.equals(accessor.getCommand())) {
    authenticate(accessor);            // read JWT from the CONNECT frame
} else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
    authorizeSubscription(accessor);   // may this user listen to this incident?
}
```

`authorizeSubscription` matches the destination against:

```java
Pattern.compile("^/topic/incidents/(\\d+)/(messages|updates|read)$")
```

extracts the incident id and calls `accessService.requireViewable(incidentId, caller)`.
Anything that does not match the pattern is refused. So a user cannot subscribe to another
person's incident conversation.

**Logout applies here too.** `authenticate` puts the CONNECT token through exactly the same
checks as the REST filter, revocation included:

```java
TokenIdentity identity = jwtService.extractIdentity(token)
        .filter(candidate -> !revocationService.isRevoked(candidate.tokenId()))
        .orElseThrow(() -> new MessagingException("Authentication is required"));
```

Without that line logout would be half-done: a logged-out token could still open a live
channel, and that channel would outlive the logout.

> **Say to the evaluator:** "WebSocket gives me live chat. Clients connect to `/ws`, send to
> `/app/...` and subscribe to `/topic/...`. Because HTTP security does not cover STOMP
> frames, `StompAuthChannelInterceptor` authenticates the CONNECT frame using the same
> `JwtService` and the same revocation list as REST, so a logged-out token cannot connect,
> and it authorises every SUBSCRIBE against the incident."

---

## 1.9 `config/OpenApiConfig.java`

```java
@Bean
public OpenAPI resolveItOpenAPI() {
    return new OpenAPI()
        .info(new Info().title("ResolveIT API").version("1.0.0").description(...))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components().addSecuritySchemes("bearerAuth",
            new SecurityScheme().name("bearerAuth")
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
}
```

This is what gives Swagger UI its **Authorize** button. `addSecurityItem` applies the token
requirement to every operation; `AuthController` then marks login and register as public
with `@SecurityRequirements` so they show without a lock.

**If it did not exist:** Swagger UI would still list endpoints but you could not attach a
token, so every protected endpoint would return 401 from the UI.

---

# PART 2 & 3 — FEATURE BY FEATURE

Every implemented feature, in learning order.

---

## FEATURE 1 — USER REGISTRATION

### A. Purpose
Let a new person create their own USER account.

### B. Endpoint
`POST /api/auth/register` — public.

### C. Controller — `AuthController`

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Operation(summary = "Register a USER account", description = "...")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

| Annotation | Meaning |
|---|---|
| `@RestController` | `@Controller` + `@ResponseBody` — every return value becomes JSON |
| `@RequestMapping("/api/auth")` | Base path for the class |
| `@PostMapping("/register")` | Full path = `/api/auth/register`, method POST |
| `@RequestBody` | Convert incoming JSON into `RegisterRequest` |
| `@Valid` | Run the validation annotations **before** the method body |
| `@SecurityRequirements` | Tells Swagger this endpoint needs no token |

**Request DTO — `RegisterRequest`**

```java
public record RegisterRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    String email,

    @NotBlank(message = "Password is required")
    String password) {}
```

**The most important security point in the whole project:** this record has **no role
field**. There is physically no way for a caller to ask for SUPPORT. Extra JSON keys like
`"role":"SUPPORT"` are simply ignored by Jackson.

The `@Size` limits match the Oracle column widths (name 100, email 150), so oversized input
is a clean 400 instead of a database error.

**Response DTO — `RegisterResponse`**

```java
public record RegisterResponse(Long userId, String name, String email, String role) {}
```

No password, no token.

**What the controller does:** validates, calls the service, returns 201.
**What it does NOT do:** it does not hash passwords, check duplicates, or touch the database.

### D. Service — `AuthService.register()`

```java
@Transactional
public RegisterResponse register(RegisterRequest request) {
    String email = request.email().trim();

    if (appUserRepository.existsByEmailIgnoreCase(email)) {
        throw new ConflictException("Email is already registered");
    }

    Role userRole = roleRepository.findByRoleNameIgnoreCase(RoleName.USER)
            .orElseThrow(() -> new IllegalStateException("USER role is missing from the RESOLVE_ROLE table"));

    AppUser user = new AppUser();
    user.setName(request.name().trim());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRole(userRole);

    AppUser saved = appUserRepository.save(user);
    return new RegisterResponse(saved.getUserId(), saved.getName(),
                                saved.getEmail(), saved.getRole().getRoleName());
}
```

Step by step:

1. **Trim the email.**
2. **Duplicate check first.** Why before inserting? Because `USER.email` has a UNIQUE
   constraint. Without this check Oracle would throw and the user would see a confusing
   500. With it, they get the documented **409 Conflict**.
3. **Look up the USER role from the database.** The role is decided *here*, in server code
   — never taken from the request. This is what makes privilege escalation impossible.
4. **Hash the password** with `passwordEncoder.encode(...)` — BCrypt. The plain password is
   never stored.
5. **Save** and return.

`@Transactional` means all of this is one database transaction — if anything fails, nothing
is written.

### E. Repositories

| Repository | Method | What it does |
|---|---|---|
| `AppUserRepository` | `existsByEmailIgnoreCase(String)` | `SELECT COUNT(*) ... WHERE UPPER(email)=UPPER(?)` |
| `AppUserRepository` | `save(AppUser)` | `INSERT INTO RESOLVE_USER ...` |
| `RoleRepository` | `findByRoleNameIgnoreCase(String)` | `SELECT * FROM "RESOLVE_ROLE" WHERE UPPER(role_name)=UPPER(?)` |

You wrote **no SQL**. Spring Data reads the method name and generates the query.

### F. Entity — `AppUser`

```java
@Entity
@Table(name = "\"USER\"")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id") private Long userId;

    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "email", nullable = false, unique = true, length = 150) private String email;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false) private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id") private TeamService team;
}
```

- `@Table(name = "\"USER\"")` — the escaped quotes matter: `USER` is an Oracle reserved
  word, so the table name must be quoted in SQL.
- The class is called **`AppUser`**, not `User`, to avoid clashing with Spring Security's
  own `User` class. The *table* name is unchanged.
- `role` is **EAGER** because you need the role name immediately at login.
- `team` is **LAZY** and nullable — a self-registered reporter belongs to no team.

### G. Full path

```
POST /api/auth/register
  → RegisterRequest (DTO)
  → AuthController
  → AuthService.register()
  → AppUserRepository / RoleRepository
  → AppUser entity
  → Oracle RESOLVE_USER table
```

### H. Response

```
Request JSON {name,email,password}
  → validation
  → duplicate check
  → BCrypt hash
  → INSERT
  → RegisterResponse {userId,name,email,role:RESOLVE_USER}
  → HTTP 201 CREATED
```

### I. Simple complete flow

```
POST /api/auth/register
        ↓
SecurityConfig  (permitAll — no token needed)
        ↓
AuthController.register()
        ↓
RegisterRequest + @Valid   → invalid? 400
        ↓
AuthService.register()  @Transactional
        ↓
AppUserRepository.existsByEmailIgnoreCase()  → exists? 409
        ↓
RoleRepository.findByRoleNameIgnoreCase(RESOLVE_USER)
        ↓
passwordEncoder.encode(password)     ← BCrypt
        ↓
AppUserRepository.save(AppUser)
        ↓
Oracle RESOLVE_USER table (INSERT)
        ↓
RegisterResponse → 201 CREATED
```

---

## FEATURE 2 — LOGIN

### A. Purpose
Prove who you are and receive a JWT. Works for **all three** roles — USER,
SUPPORT and SUPER_ADMIN.

### B. Endpoint
`POST /api/auth/login` — public.

### C. Controller — `AuthController.login()`

```java
@Operation(summary = "Log in and receive a JWT", ...)
@SecurityRequirements
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
}
```

**`LoginRequest`**: `email` (`@NotBlank`, `@Email`) and `password` (`@NotBlank`).
**`LoginResponse`**: `token`, `userId`, `name`, `role` — deliberately no password field.

### D. Service — `AuthService.login()`

```java
@Transactional(readOnly = true)
public LoginResponse login(LoginRequest request) {
    Authentication authentication;
    try {
        authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    } catch (AuthenticationException ex) {
        throw new UnauthorizedException("Invalid email or password");
    }

    AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
    String token = jwtService.generateToken(user);

    return new LoginResponse(token, user.getUserId(), user.getName(), user.getRole());
}
```

**This is the most important thing to understand about login: the service does not check the
password itself. Spring Security does.**

`authenticationManager.authenticate(...)` is handed an *unauthenticated* token holding only
the email and the raw password. Inside it, the `DaoAuthenticationProvider` configured in
`SecurityConfig`:

1. calls `CustomUserDetailsService.loadUserByUsername(email)`, which runs
   `AppUserRepository.findByEmailIgnoreCase(email)` against the `RESOLVE_USER` table;
2. compares the submitted password with the stored BCrypt hash using the shared
   `PasswordEncoder` bean;
3. returns an **authenticated** `Authentication` whose principal is your `AuthenticatedUser`
   — or throws an `AuthenticationException` if either step fails.

So `AuthService` is left with only the two decisions that are genuinely its own: what to do
on success (issue a JWT) and what to do on failure (a 401 with a deliberately vague message).

Important details:

- **Unknown email and wrong password produce the *identical* response.** They both surface as
  `BadCredentialsException`, because `setHideUserNotFoundExceptions(true)` is set on the
  provider. If they differed, an attacker could use login to discover which emails exist.
- BCrypt is **one-way**. Verification re-hashes the attempt with the salt stored inside the
  hash and compares; nothing is ever decrypted.
- `@Transactional(readOnly = true)` — nothing is written, so the database is told this is a
  read. The transaction also keeps the user's `role` association loadable while the principal
  is being built.

### E–F. Repository & Entity
`AppUserRepository.findByEmailIgnoreCase(email)` → `AppUser` → `RESOLVE_USER` table.
`AuthenticatedUser.from(appUser)` converts the row into the principal.

### I. Simple complete flow

```
POST /api/auth/login
        ↓
SecurityConfig (permitAll)
        ↓
AuthController.login()
        ↓
LoginRequest + @Valid          → invalid? 400
        ↓
AuthService.login()
        ↓
AuthenticationManager.authenticate(email + raw password)
        ↓
   DaoAuthenticationProvider
        ↓
   CustomUserDetailsService.loadUserByUsername(email)
        ↓
   AppUserRepository.findByEmailIgnoreCase()  → SELECT from RESOLVE_USER
        ↓
   BCrypt compare (raw password vs password_hash)
        ↓
   unknown email OR wrong password → AuthenticationException
                                   → UnauthorizedException → 401
        ↓
   success → Authentication(principal = AuthenticatedUser)
        ↓
JwtService.generateToken()     ← signs with the HMAC key
        ↓
LoginResponse {token,userId,name,role} → 200 OK
```

After this, the client sends `Authorization: Bearer <token>` on every protected call, until
it logs out (Feature 2A) or the token expires eight hours later.

---

## FEATURE 2A — LOGOUT

### A. Purpose

End the session **on the server**, immediately. Not just "the browser forgot the token" —
the same token must stop working everywhere.

### B. Security rule

`POST /api/auth/logout` — **authenticated**, any role.

This is the one auth endpoint that is *not* public, and the reason is worth stating: you can
only revoke a token you are already holding. That single rule is what stops one user logging
another one out.

### C. The problem it has to solve

A JWT is *self-contained and signed*. There is no server-side record of it, which is exactly
what makes it stateless and fast — and exactly what makes logout hard. You cannot un-sign a
token. If a token is valid at 10:00, the maths still says it is valid at 10:01.

So logout cannot destroy the token. It has to make the server **stop honouring** it.

### D. The mechanism

Three pieces:

1. `JwtService.generateToken` stamps every token with a unique **`jti`** (JWT ID).
2. `TokenRevocationService` keeps the `jti` of every logged-out token, until that token's
   own expiry.
3. Both authentication paths consult that list **before** establishing identity.

```java
// AuthService.logout
TokenIdentity identity = jwtService.extractIdentity(rawToken)
        .orElseThrow(() -> new UnauthorizedException("Authentication is required"));

revocationService.revoke(identity.tokenId(), identity.expiresAt());
```

### E. Where the check goes — and why the position matters

```java
// JwtAuthenticationFilter
jwtService.extractIdentity(token)
        .filter(identity -> !revocationService.isRevoked(identity.tokenId()))   // <-- here
        .ifPresent(identity -> authenticate(identity.subject(), request));
```

The check sits **between** "is this token genuine?" and "who is this?". A revoked token
therefore never populates the SecurityContext, the request stays anonymous, and
`RestAuthenticationEntryPoint` returns the standard 401. No new error path was needed.

The identical check is in `StompAuthChannelInterceptor.authenticate`, or logout would be
half-done: the token would be dead for REST but could still open a WebSocket.

### F. Simple complete flow

```
POST /api/auth/logout   Authorization: Bearer <jwt>
        ↓
JwtAuthenticationFilter  → verifies signature + expiry
                         → checks revocation list
                         → authenticates the caller
        ↓
SecurityConfig (authenticated)   → no/invalid/revoked token? 401
        ↓
AuthController.logout()
        ↓
BearerTokens.resolve(header)   → the raw token string
        ↓
AuthService.logout()
        ↓
JwtService.extractIdentity()   → subject, jti, expiry
        ↓
TokenRevocationService.revoke(jti, expiry)
        ↓
LogoutResponse {"message":"Logged out successfully"} → 200 OK
```

Afterwards:

```
same jwt  → any protected REST API   → 401
same jwt  → WebSocket STOMP CONNECT  → rejected
same jwt  → POST /api/auth/logout    → 401  (already revoked)
new login → new jwt                  → works again
```

### G. Four things an evaluator may probe

**"Doesn't a revocation list break statelessness?"** No. `SessionCreationPolicy.STATELESS`
is untouched and there is no `HttpSession`. A request still authenticates entirely from its
own token. The list is a *deny* list consulted during that check — it can reject a token,
never accept one, so it can never become a way in.

**"Does logging out on my laptop kill my phone?"** No. Each login mints its own `jti`, so
revocation is per token, not per user.

**"Why not just store a per-user `loggedOutAt` timestamp?"** That is simpler, but it logs
the user out of *every* device at once, which is different behaviour. The `jti` list revokes
exactly the token that was presented.

**"What happens on restart?"** The list is in memory, so a restart clears it and a token
logged out shortly before the restart works again for the rest of its eight hours. That is a
deliberate trade — a database table would mean a DDL change to a schema shared with other
projects — and `TokenRevocationService` is the whole seam, so backing it with a table or
Redis changes nothing else.

### H. What logout is NOT

There is **no refresh token** and **no refresh-token endpoint** in this system. Once the
access token is revoked, the only way back is `POST /api/auth/login`. Logout also writes
nothing to the database and changes no user record — `AuthService.logout` is deliberately
not `@Transactional`.

---

## FEATURE 3 — USER DASHBOARD

### A. Purpose
Show the logged-in user their own incidents.

### B. Endpoint
`GET /api/user/dashboard` — **USER only**.

### C. Controller — `UserDashboardController`

```java
@RestController
@RequestMapping("/api/user")
public class UserDashboardController {

    @GetMapping("/dashboard")
    public ResponseEntity<UserDashboardResponse> dashboard(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(incidentService.userDashboard(caller));
    }
}
```

**`@AuthenticationPrincipal`** injects the `AuthenticatedUser` that
`JwtAuthenticationFilter` put into the SecurityContext.

**There is no `userId` parameter — on purpose.** If the client could send a userId, anyone
could read anyone's dashboard. The identity comes only from the token.

### D. Service — `IncidentService.userDashboard()`

```java
@Transactional(readOnly = true)
public UserDashboardResponse userDashboard(AuthenticatedUser caller) {
    List<UserIncidentSummary> incidents = incidentRepository
            .findByReportedBy_UserIdOrderByCreatedAtDescIncidentIdDesc(caller.getUserId())
            .stream()
            .map(incident -> new UserIncidentSummary(
                    incident.getIncidentId(), incident.getIncidentCode(),
                    incident.getTitle(), incident.getStatus(),
                    incident.getSeverity(), incident.getPriority(),
                    incident.getCreatedAt()))
            .toList();

    return new UserDashboardResponse(caller.getUserId(), caller.getName(), incidents);
}
```

The entity list is converted into `UserIncidentSummary` DTOs — the entity itself is never
returned.

### E. Repository

`findByReportedBy_UserIdOrderByCreatedAtDescIncidentIdDesc(Long userId)`

Read the name in pieces:

| Piece | Meaning |
|---|---|
| `findBy` | SELECT |
| `ReportedBy_UserId` | follow the `reportedBy` field into its `userId` (the underscore navigates the relationship) |
| `OrderByCreatedAtDesc` | newest first |
| `IncidentIdDesc` | tie-break so the order is stable |

### I. Simple complete flow

```
GET /api/user/dashboard  +  Bearer token
        ↓
JwtAuthenticationFilter → validates token, sets AuthenticatedUser
        ↓
SecurityConfig → hasRole("USER")   → SUPPORT token? 403 / no token? 401
        ↓
UserDashboardController.dashboard(@AuthenticationPrincipal)
        ↓
IncidentService.userDashboard(caller)
        ↓
IncidentRepository.findByReportedBy_UserId...(caller.getUserId())
        ↓
Oracle RESOLVE_INCIDENT table  (SELECT ... WHERE reported_by = ?)
        ↓
List<Incident> → List<UserIncidentSummary>
        ↓
UserDashboardResponse → 200 OK
```

---

## FEATURE 4 — RESOLVE_INCIDENT CLASSIFICATION (suggestion only)

### A. Purpose
Suggest a service, category and severity from the incident text, so the user does not have
to guess. **It does not create anything.**

### B. Endpoint
`POST /api/incidents/classify` — USER only.

### C. Controller — `IncidentController.classify()`

```java
@PostMapping("/classify")
public ResponseEntity<ClassifyResponse> classify(@Valid @RequestBody ClassifyRequest request) {
    return ResponseEntity.ok(classificationService.classify(request.title(), request.description()));
}
```

`ClassifyRequest`: `title` (`@NotBlank`, max 200), `description` (`@NotBlank`).
`ClassifyResponse`: `suggestedService`, `suggestedCategory`, `suggestedSeverity`.

Note the controller takes no principal here — it only needs the text.

### D. Service — `ClassificationService.classify()`

```java
@Transactional(readOnly = true)
public ClassifyResponse classify(String title, String description) {
    List<Incident> history = incidentRepository.findAllNewestFirst();
    Incident closest = closestHistoricalIncident(title, description, history);

    String service  = suggestService(title, description, closest);
    String category = suggestCategory(title, description, closest);
    String severity = suggestSeverity(title, description, closest);

    return new ClassifyResponse(service, category, severity);
}
```

Three separate rules:

**Service** — tokenises your text and every `RESOLVE_TEAM_SERVICE` row (service name, team name,
department, description), then picks the team with the highest word overlap:

```java
double score = TextSimilarity.jaccard(incidentTokens, teamTokens);
```

**Category** — reuses the category of the closest past incident; if there is none, it trims
the title to fit `VARCHAR2(50)`.

**Severity** — keyword matching, checked strongest-first:

```java
SEVERITY_KEYWORDS.put(Severity.CRITICAL, List.of("outage","down","unavailable", ... ));
SEVERITY_KEYWORDS.put(Severity.HIGH,     List.of("fail","failing","error","cannot", ... ));
SEVERITY_KEYWORDS.put(Severity.MEDIUM,   List.of("slow","delay","latency", ... ));
SEVERITY_KEYWORDS.put(Severity.LOW,      List.of("question","typo","minor", ... ));
```

Order matters — an outage that also says "slow" is still CRITICAL. If no keyword matches
it falls back to the closest incident's severity, then to `MEDIUM`.

Only history scoring at least `RELEVANCE_FLOOR = 15.0` is used.

### E. Repositories
`IncidentRepository.findAllNewestFirst()` (custom `@Query`) and
`TeamServiceRepository.findAll()`.

### I. Simple complete flow

```
POST /api/incidents/classify + Bearer(USER)
        ↓
SecurityConfig hasRole("USER")
        ↓
IncidentController.classify()
        ↓
ClassifyRequest + @Valid
        ↓
ClassificationService.classify(title, description)
        ↓
IncidentRepository.findAllNewestFirst()   → RESOLVE_INCIDENT
TeamServiceRepository.findAll()           → RESOLVE_TEAM_SERVICE
        ↓
IncidentSimilarityService + TextSimilarity   (pure Java, no DB)
        ↓
ClassifyResponse {suggestedService, suggestedCategory, suggestedSeverity}
        ↓
200 OK        ← NO incident row is created
```

---

## FEATURE 5 — RESOLVE_INCIDENT CREATION + AUTOMATIC ASSIGNMENT

This is the biggest feature. Read it slowly.

### A. Purpose
Create the incident, work out its priority and owning team, automatically pick the best
support engineer, and record the history — all in one transaction.

### B. Endpoint
`POST /api/incidents` — USER only, returns **201**.

### C. Controller — `IncidentController.create()`

```java
@PostMapping
public ResponseEntity<CreateIncidentResponse> create(
        @Valid @RequestBody CreateIncidentRequest request,
        @AuthenticationPrincipal AuthenticatedUser caller) {
    CreateIncidentResponse response = incidentService.createIncident(request, caller);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

**`CreateIncidentRequest`** — `title`, `description`, `service`, `category`, `severity`
(all `@NotBlank`, with `@Size` limits mirroring the Oracle columns).

Its javadoc states the key idea: it **deliberately has no team, engineer or score field**,
because the backend decides those.

**`CreateIncidentResponse`** — `incidentId`, `incidentCode`, `title`, `status`, `severity`,
`priority`, `assignedSupportUserId`, `assignedSupportName`, `createdAt`.

### D. Service — `IncidentService.createIncident()` (step by step)

```java
@Transactional
public CreateIncidentResponse createIncident(CreateIncidentRequest request, AuthenticatedUser caller) {
```

**Step 1 — validate severity against the enum**

```java
Severity severity = Severity.fromStored(request.severity())
        .orElseThrow(() -> new BadRequestException("Severity must be one of LOW, MEDIUM, HIGH, CRITICAL"));
```

**Step 2 — resolve the service to its owning team**

```java
TeamService team = teamServiceRepository.findByServiceNameIgnoreCase(request.service().trim())
        .orElseThrow(() -> new BadRequestException("Unknown service '" + ... + "'"));
```

The user confirms a *service*; the backend derives the *team*. An unknown service is a 400.

**Step 3 — load the reporter**

```java
AppUser reporter = appUserRepository.findById(caller.getUserId())
        .orElseThrow(() -> new NotFoundException("Reporting user not found"));
```

**Step 4 — decide the priority (calls another service)**

```java
Priority priority = priorityService.determinePriority(severity);
```

`PriorityService` is tiny:

```java
return switch (severity) {
    case CRITICAL, HIGH -> Priority.P1;
    case MEDIUM         -> Priority.P3;
    case LOW            -> Priority.P4;
};
```

**P2 is never produced** — that is intentional and documented in your README.

**Step 5 — build and save the incident, then stamp its code**

```java
incident.setIncidentCode(temporaryCode());          // "TMP-<uuid>"
incident.setStatus(IncidentStatus.REPORTED.stored());
...
incident = incidentRepository.saveAndFlush(incident);
incident.setIncidentCode(CODE_PREFIX + (CODE_OFFSET + incident.getIncidentId()));
```

Why the temporary code? `incident_code` is NOT NULL and UNIQUE, but the final code
(`INC-1043`) is derived from the generated id — which does not exist until after the
insert. So: insert with a unique placeholder, `saveAndFlush` to get the id, then overwrite
with the real code before the transaction commits. **The placeholder never reaches a client.**

**Step 6 — write the first history row**

```java
writeLog(incident, null, IncidentStatus.REPORTED, reporter, "Incident created", now);
```

**Step 7 — run the assignment algorithm**

```java
Optional<AssignmentService.Candidate> selected = assignmentService.selectEngineer(incident);
```

**Step 8 — if an engineer was found, save the assignment and move to ASSIGNED**

```java
if (selected.isPresent()) {
    IncidentAssignment assignment = new IncidentAssignment();
    assignment.setIncident(incident);
    assignment.setSupportUser(engineer);
    assignment.setAssignmentScore(AssignmentService.toStoredScore(candidate.finalScore()));
    assignment.setAssignedAt(now);
    assignmentRepository.save(assignment);

    incident.setStatus(IncidentStatus.ASSIGNED.stored());
    writeLog(incident, IncidentStatus.REPORTED, IncidentStatus.ASSIGNED, null,
             "Automatically assigned to " + engineer.getName(), now);
}
```

If no engineer is found the incident stays `REPORTED` and unassigned (a known limitation).

**Step 9 — notify over WebSocket, but only after commit**

```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { notifier.broadcastAssignment(incidentId, payload); }
    });
}
```

**Why?** If you broadcast *inside* the transaction and the transaction then rolls back, you
would have announced an incident that does not exist. Oracle stays the source of truth.

### D2. The assignment algorithm — `AssignmentService.selectEngineer()`

**The weights** (from Feature.md §14):

```java
private static final double EXPERIENCE_WEIGHT   = 0.40;
private static final double AVAILABILITY_WEIGHT = 0.25;
private static final double WORKLOAD_WEIGHT     = 0.20;
private static final double FAIRNESS_WEIGHT     = 0.15;
```

**Step A — get the candidate pool** (SUPPORT engineers on the owning team only):

```java
List<AppUser> engineers = appUserRepository.findSupportEngineersByTeam(teamId);
```

```java
@Query("""
        select u from AppUser u
        where upper(u.role.roleName) = 'SUPPORT'
          and u.team.teamId = :teamId
        """)
```

**Step B — load each engineer's state** (`loadState`): their active incidents, all handled
incidents, and their last assignment time.

**Step C — drop ineligible engineers**

```java
List<EngineerState> eligible = states.stream()
        .filter(state -> state.availability().isEligible())
        .toList();
```

**Step D — compute the four factors**

| Factor | How your code computes it | Score |
|---|---|---|
| **Experience** | For every incident this engineer handled, `similarity(new, handled)/100 × completionFactor` (1.0 if resolved, 0.5 if still open), summed. Then normalised: `40 + (raw/maxRaw)×100×0.60`. If **nobody** has relevant history, everyone gets a neutral `50.0` | 40–100 |
| **Availability** | Derived, not stored: `active < 5` → AVAILABLE (100), else BUSY (50) | 50 or 100 |
| **Workload** | Each active incident costs `priorityWeight + min(days×0.5, 3.0)`; P1=4.0, P2=3.0, P3=2.0, P4=1.0. Then `100 − (units/maxUnits)×100` | 0–100 |
| **Fairness** | `idleTime / 4 hours × 100`, capped at 100. Never assigned = 100 | 0–100 |

The experience score has a **floor of 40** so a brand-new engineer still competes —
experience must not completely dominate.

**Step E — combine and pick the highest**

```java
double finalScore = (experience   * EXPERIENCE_WEIGHT)
                  + (availability * AVAILABILITY_WEIGHT)
                  + (workload     * WORKLOAD_WEIGHT)
                  + (fairness     * FAIRNESS_WEIGHT);
...
return candidates.stream()
        .max(Comparator.comparingDouble(Candidate::finalScore)
                .thenComparing(candidate -> candidate.engineer().getUserId()));
```

The `thenComparing` is a deterministic tie-break so repeated runs give the same answer.

**Step F — only the final score is stored**

```java
public static BigDecimal toStoredScore(double score) {
    return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
}
```

Rounded to 2 decimals to fit `assignment_score NUMBER(5,2)`. The four individual factor
scores are **not** stored — the schema has no columns for them.

**Worked example (real numbers from this project):**

```
Priya  experience 100.00 ×0.40 = 40.00
       availability 100.00 ×0.25 = 25.00
       workload     100.00 ×0.20 = 20.00
       fairness       0.75 ×0.15 =  0.11
                                  -------
                            TOTAL  85.11   ← selected

Rahul  experience  40.00 ×0.40 = 16.00
       availability 100.00 ×0.25 = 25.00
       workload     100.00 ×0.20 = 20.00
       fairness     100.00 ×0.15 = 15.00
                                  -------
                            TOTAL  76.00
```

Priya wins on experience despite Rahul's perfect fairness score. As Priya's workload grows,
her workload score falls and Rahul starts winning — that is the balancing behaviour.

### E. Repositories used in this one feature

| Repository | Method |
|---|---|
| `TeamServiceRepository` | `findByServiceNameIgnoreCase` |
| `AppUserRepository` | `findById`, `findSupportEngineersByTeam` |
| `IncidentRepository` | `saveAndFlush`, `save` |
| `IncidentAssignmentRepository` | `findActiveIncidentsForSupportUser`, `findCurrentIncidentsForSupportUser`, `findTopBySupportUser_UserIdOrderByAssignedAtDescAssignmentIdDesc`, `save` |
| `IncidentLogRepository` | `save` (via `writeLog`) |

### F. Entities involved
`Incident`, `IncidentAssignment`, `IncidentLog`, `AppUser`, `TeamService`.

### I. Simple complete flow

```
POST /api/incidents  + Bearer(USER)
        ↓
SecurityConfig hasRole("USER")
        ↓
IncidentController.create()
        ↓
CreateIncidentRequest + @Valid
        ↓
IncidentService.createIncident()   @Transactional  ← ONE transaction starts
        ↓
Severity.fromStored()                    → bad? 400
        ↓
TeamServiceRepository.findByServiceNameIgnoreCase()  → unknown? 400
        ↓
AppUserRepository.findById(caller)
        ↓
PriorityService.determinePriority()      → HIGH → P1
        ↓
IncidentRepository.saveAndFlush()   → INSERT RESOLVE_INCIDENT (TMP- code)
        ↓
set real code  INC-<1000+id>
        ↓
IncidentLogRepository.save()        → INSERT RESOLVE_INCIDENT_LOGS (status = REPORTED)
        ↓
AssignmentService.selectEngineer()
        ├── AppUserRepository.findSupportEngineersByTeam()
        ├── IncidentAssignmentRepository (active / handled / last assigned)
        └── IncidentSimilarityService → TextSimilarity   (pure Java)
        ↓
IncidentAssignmentRepository.save() → INSERT RESOLVE_INCIDENT_ASSIGNMENT (score)
        ↓
status = ASSIGNED
IncidentLogRepository.save()        → INSERT RESOLVE_INCIDENT_LOGS (status = ASSIGNED)
        ↓
IncidentRepository.save()
        ↓
COMMIT  ← transaction ends here
        ↓
afterCommit → RealtimeNotifier.broadcastAssignment() → /topic/incidents/{id}/updates
        ↓
CreateIncidentResponse → 201 CREATED
```

---

## FEATURE 6 — RESOLVE_INCIDENT DETAILS

### A. Purpose
Return the whole incident page in one response: details + conversation + status history.

### B. Endpoint
`GET /api/incidents/{incidentId}` — USER **or** SUPPORT (`authenticated()` in
SecurityConfig; the real check is per-incident).

### C. Controller — `IncidentController.detail()`

```java
@GetMapping("/{incidentId}")
public ResponseEntity<IncidentDetailResponse> detail(@PathVariable Long incidentId,
                                                      @AuthenticationPrincipal AuthenticatedUser caller) {
    return ResponseEntity.ok(incidentService.incidentDetail(incidentId, caller));
}
```

`@PathVariable` binds `{incidentId}` from the URL. If you pass `abc` instead of a number,
`MethodArgumentTypeMismatchException` → handled → **400**.

### D. Service — `IncidentService.incidentDetail()`

```java
@Transactional(readOnly = true)
public IncidentDetailResponse incidentDetail(Long incidentId, AuthenticatedUser caller) {
    Incident incident = accessService.requireViewable(incidentId, caller);
    ...
}
```

**This is the "Service A → Service B" pattern in your project.** `IncidentService` calls
`IncidentAccessService` first:

```java
@Transactional(readOnly = true)
public Incident requireViewable(Long incidentId, AuthenticatedUser caller) {
    Incident incident = load(incidentId);          // NotFoundException → 404
    if (!canView(incident, caller)) {
        throw new ForbiddenException("You are not authorized to access this incident");
    }
    return incident;
}

public boolean canView(Incident incident, AuthenticatedUser caller) {
    if (RoleName.USER.equals(caller.getRole()))    return isReporter(incident, caller);
    if (RoleName.SUPPORT.equals(caller.getRole())) return isAssignedEngineer(incident, caller)
                                                       || isSameTeam(incident, caller);
    return false;
}
```

| Role | Can view |
|---|---|
| USER | only incidents they reported |
| SUPPORT | incidents assigned to them, **or** owned by their team |

`IncidentAccessService` exists so this rule is written **once** and used by REST *and*
STOMP — no duplication, no chance of the two disagreeing.

Then the service gathers three things: the current assignment, the messages, the logs — and
maps each into DTOs.

### E. Repositories
`IncidentRepository.findById`, `IncidentAssignmentRepository.findCurrentByIncidentId`,
`IncidentMessageRepository.findByIncident_IncidentIdOrderBySentAtAscMessageIdAsc`,
`IncidentLogRepository.findByIncident_IncidentIdOrderByChangedAtAscLogIdAsc`.

The "current assignment" query is worth knowing:

```java
@Query("""
        select a from IncidentAssignment a
        where a.incident.incidentId = :incidentId
          and a.assignmentId = (
                select max(a2.assignmentId) from IncidentAssignment a2
                where a2.incident.incidentId = :incidentId)
        """)
```

An incident could accumulate assignment history, so the **highest assignment_id** is
treated as the current one.

### I. Simple complete flow

```
GET /api/incidents/43 + Bearer
        ↓
SecurityConfig authenticated()
        ↓
IncidentController.detail()
        ↓
IncidentService.incidentDetail()
        ↓
IncidentAccessService.requireViewable()   → missing? 404 / not yours? 403
        ↓
IncidentAssignmentRepository.findCurrentByIncidentId()
IncidentMessageRepository.findByIncident_...()
IncidentLogRepository.findByIncident_...()
        ↓
RESOLVE_INCIDENT + RESOLVE_INCIDENT_ASSIGNMENT + RESOLVE_INCIDENT_MESSAGE + RESOLVE_INCIDENT_LOGS
        ↓
IncidentDetailResponse (with AssignedSupport, List<MessageResponse>, List<StatusHistoryEntry>)
        ↓
200 OK
```

---

## FEATURE 7 — SENDING A MESSAGE (REST)

### A. Purpose
Add one message to an incident's conversation.

### B. Endpoint
`POST /api/incidents/{incidentId}/messages` — participants only, returns **201**.

### C. Controller — `IncidentController.sendMessage()`

```java
@PostMapping("/{incidentId}/messages")
public ResponseEntity<MessageResponse> sendMessage(@PathVariable Long incidentId,
                                                    @Valid @RequestBody SendMessageRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
    MessageResponse message = messageService.sendMessage(incidentId, request.messageText(), caller);
    messageService.broadcastMessage(incidentId, message);
    return ResponseEntity.status(HttpStatus.CREATED).body(message);
}
```

**Look carefully at the order.** The controller does two calls:

1. `sendMessage(...)` — `@Transactional`, saves to Oracle. When it returns, the transaction
   has committed.
2. `broadcastMessage(...)` — pushes to WebSocket subscribers.

**Save first, broadcast second.** Never the other way around.

`SendMessageRequest` has only `messageText` — the sender is taken from the token.

### D. Service — `IncidentMessageService.sendMessage()`

```java
@Transactional
public MessageResponse sendMessage(Long incidentId, String messageText, AuthenticatedUser caller) {
    if (messageText == null || messageText.isBlank()) {
        throw new BadRequestException("Message text must not be blank");
    }

    Incident incident = accessService.requireConversationParticipant(incidentId, caller);
    AppUser sender = appUserRepository.findById(caller.getUserId())
            .orElseThrow(() -> new NotFoundException("Sender not found"));

    IncidentMessage message = new IncidentMessage();
    message.setIncident(incident);
    message.setSender(sender);
    message.setMessageText(messageText);
    message.setSentAt(LocalDateTime.now());
    message.setIsRead(false);          // unread until the recipient views it

    IncidentMessage saved = messageRepository.save(message);
    return IncidentService.toMessageResponse(saved);
}
```

`requireConversationParticipant` is **stricter than viewing**:

```java
boolean participant = isReporter(incident, caller) || isAssignedEngineer(incident, caller);
```

A SUPPORT colleague on the same team may *read* the incident but may **not** post into the
conversation.

### E–F. Repository & Entity

`IncidentMessageRepository.save()` → `IncidentMessage` → `RESOLVE_INCIDENT_MESSAGE` table.

```java
@Convert(converter = NumericBooleanConverter.class)
@Column(name = "is_read")
private Boolean isRead;
```

Oracle has no boolean type — the column is `NUMBER(1)`. This converter maps
Java `false/true` ↔ Oracle `0/1`.

### I. Simple complete flow

```
POST /api/incidents/43/messages + Bearer
        ↓
SecurityConfig authenticated()
        ↓
IncidentController.sendMessage()
        ↓
SendMessageRequest + @Valid    → blank? 400
        ↓
IncidentMessageService.sendMessage()  @Transactional
        ↓
IncidentAccessService.requireConversationParticipant() → not a participant? 403
        ↓
AppUserRepository.findById(sender)
        ↓
IncidentMessageRepository.save()  → INSERT RESOLVE_INCIDENT_MESSAGE (is_read = 0)
        ↓
COMMIT
        ↓
messageService.broadcastMessage() → RealtimeNotifier → /topic/incidents/43/messages
        ↓
MessageResponse → 201 CREATED
```

---

## FEATURE 8 — MARK MESSAGES AS READ

### A. Purpose
Flip `is_read` from 0 to 1 for messages the caller has now seen.

### B. Endpoint
`PATCH /api/incidents/{incidentId}/messages/read`

### C. Controller

```java
@PatchMapping("/{incidentId}/messages/read")
public ResponseEntity<MarkReadResponse> markRead(@PathVariable Long incidentId,
                                                  @Valid @RequestBody MarkReadRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser caller) {
    MarkReadResponse response = messageService.markRead(incidentId, request.messageIds(), caller);
    messageService.broadcastReadStatus(incidentId, response);
    return ResponseEntity.ok(response);
}
```

`MarkReadRequest` — `@NotEmpty List<Long> messageIds`.
`MarkReadResponse` — `incidentId`, `updatedMessageIds`, `status`.

### D. Service — `IncidentMessageService.markRead()`

```java
List<IncidentMessage> messages =
        messageRepository.findByMessageIdInAndIncident_IncidentId(messageIds, incidentId);

List<Long> updated = new ArrayList<>();
for (IncidentMessage message : messages) {
    boolean ownMessage = message.getSender().getUserId().equals(caller.getUserId());
    if (ownMessage || Boolean.TRUE.equals(message.getIsRead())) {
        continue;
    }
    message.setIsRead(true);
    updated.add(message.getMessageId());
}
messageRepository.saveAll(messages);
```

Two protections:

1. **Your own messages are skipped.** Marking your own message as read is meaningless.
2. **The query is scoped by `incidentId`.** Even if you pass message ids from another
   incident, they will not be found — so you cannot touch someone else's data.

### I. Simple complete flow

```
PATCH /api/incidents/43/messages/read  {"messageIds":[41,42]}
        ↓
IncidentController.markRead()
        ↓
IncidentMessageService.markRead()  @Transactional
        ↓
empty list? → 400
        ↓
IncidentAccessService.requireConversationParticipant() → 403 if not a participant
        ↓
IncidentMessageRepository.findByMessageIdInAndIncident_IncidentId()
        ↓
skip own messages / already-read → set isRead = true
        ↓
saveAll()  → UPDATE RESOLVE_INCIDENT_MESSAGE SET is_read = 1
        ↓
COMMIT → broadcastReadStatus() → /topic/incidents/43/read
        ↓
MarkReadResponse {incidentId, updatedMessageIds:[41], status:"READ"} → 200 OK
```

---

## FEATURE 9 — SUPPORT DASHBOARD

### A. Purpose
Show a SUPPORT engineer their workload and personal analytics.

### B. Endpoint
`GET /api/support/dashboard` — SUPPORT only.

### C. Controller — `SupportController.dashboard()`

```java
@RestController
@RequestMapping("/api/support")
public class SupportController {

    @GetMapping("/dashboard")
    public ResponseEntity<SupportDashboardResponse> dashboard(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(supportService.dashboard(caller));
    }
}
```

### D. Service — `SupportService.dashboard()`

```java
List<Incident> assigned = assignmentRepository.findCurrentIncidentsForSupportUser(caller.getUserId());

List<Incident> resolved = assigned.stream()
        .filter(i -> IncidentStatus.RESOLVED.stored().equalsIgnoreCase(i.getStatus())).toList();
List<Incident> open = assigned.stream()
        .filter(i -> !IncidentStatus.RESOLVED.stored().equalsIgnoreCase(i.getStatus())).toList();

SupportSummary summary = new SupportSummary(assigned.size(), open.size(), resolved.size(),
                                            averageResolutionTime(resolved));
```

Open incidents are sorted heaviest-first:

```java
.sorted(Comparator.comparing((Incident i) -> priorityRank(i.getPriority()))
        .thenComparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
```

`averageResolutionTime` computes the mean of `resolvedAt − createdAt` and formats it as
`HH:mm:ss`.

`analytics(...)` counts categories and returns `mostCommonIssue` plus `recurringIncidents`
(categories seen at least `RECURRENCE_THRESHOLD = 2` times).

**Important:** every number is **calculated from existing rows**. There is no analytics
table.

### I. Simple complete flow

```
GET /api/support/dashboard + Bearer(SUPPORT)
        ↓
SecurityConfig  /api/support/** → hasRole("SUPPORT")  → USER token? 403
        ↓
SupportController.dashboard()
        ↓
SupportService.dashboard()  @Transactional(readOnly)
        ↓
IncidentAssignmentRepository.findCurrentIncidentsForSupportUser()
        ↓
RESOLVE_INCIDENT_ASSIGNMENT joined to RESOLVE_INCIDENT
        ↓
split open/resolved → summary + analytics (computed in Java)
        ↓
SupportDashboardResponse {supportUserId,name,summary,incidents,analytics} → 200 OK
```

---

## FEATURE 10 — RESOLVE_INCIDENT STATUS / ROOT CAUSE / RESOLUTION UPDATE

### A. Purpose
One endpoint moves the incident through its lifecycle **and** records root cause and
resolution text.

### B. Endpoint
`PATCH /api/support/incidents/{incidentId}` — SUPPORT only, and only the **assigned**
engineer.

### C. Controller — `SupportController.update()`

```java
@PatchMapping("/incidents/{incidentId}")
public ResponseEntity<SupportIncidentUpdateResponse> update(
        @PathVariable Long incidentId,
        @Valid @RequestBody SupportIncidentUpdateRequest request,
        @AuthenticationPrincipal AuthenticatedUser caller) {

    SupportIncidentUpdateResponse response = supportService.updateIncident(incidentId, request, caller);
    supportService.broadcastIncidentUpdate(response);   // only after it committed
    return ResponseEntity.ok(response);
}
```

`SupportIncidentUpdateRequest` — `status` (`@NotBlank`), plus **optional** `rootCause` and
`resolution`.

### D. Service — `SupportService.updateIncident()`

```java
Incident incident = accessService.requireModifiable(incidentId, caller);
```

`requireModifiable` is the **strictest** access check:

```java
public Incident requireModifiable(Long incidentId, AuthenticatedUser caller) {
    Incident incident = load(incidentId);
    if (!isAssignedEngineer(incident, caller)) {
        throw new ForbiddenException("You are not authorized to modify this incident");
    }
    return incident;
}
```

Then:

```java
IncidentStatus target  = IncidentStatus.fromStored(request.status())
        .orElseThrow(() -> new BadRequestException("Status must be one of ..."));
IncidentStatus current = IncidentStatus.fromStored(incident.getStatus())
        .orElseThrow(() -> new ConflictException("Incident has an unrecognized current status ..."));

if (!current.canTransitionTo(target)) {
    throw new ConflictException("Invalid status transition from " + current.stored() + " to " + target.stored());
}
```

**The lifecycle rule lives in the enum** `IncidentStatus`:

```java
REPORTED, ASSIGNED, IN_PROGRESS("IN PROGRESS"),
ROOT_CAUSE_IDENTIFIED("ROOT CAUSE IDENTIFIED"),
RESOLUTION_IN_PROGRESS("RESOLUTION IN PROGRESS"), RESOLVED;

public boolean canTransitionTo(IncidentStatus target) {
    return target != null && target.ordinal() == this.ordinal() + 1;
}
```

Only the **very next** state is allowed. Skipping, repeating and going backwards are all
rejected with **409**. The declaration order *is* the workflow.

Then the text fields are applied, and the business rules checked:

```java
if (request.rootCause()  != null && !request.rootCause().isBlank())  incident.setRootCause(...);
if (request.resolution() != null && !request.resolution().isBlank()) incident.setResolution(...);

applyStatusRules(incident, target);
```

```java
private static void applyStatusRules(Incident incident, IncidentStatus target) {
    if (target == ROOT_CAUSE_IDENTIFIED && isBlank(incident.getRootCause()))
        throw new ConflictException("Root cause must be confirmed before moving to ROOT CAUSE IDENTIFIED");
    if (target == RESOLVED) {
        if (isBlank(incident.getRootCause()))  throw new ConflictException("Root cause must be recorded ...");
        if (isBlank(incident.getResolution())) throw new ConflictException("Resolution details must be recorded ...");
    }
}
```

Finally:

```java
if (target == IncidentStatus.RESOLVED) incident.setResolvedAt(now);
incident.setStatus(target.stored());
incidentService.writeLog(incident, target, now);
Incident saved = incidentRepository.save(incident);
```

Note it calls **`incidentService.writeLog(...)`** — one service reusing another so history
is written the same way everywhere.

### I. Simple complete flow

```
PATCH /api/support/incidents/43  {"status":"ROOT CAUSE IDENTIFIED","rootCause":"..."}
        ↓
SecurityConfig hasRole("SUPPORT")            → USER? 403
        ↓
SupportController.update()
        ↓
SupportService.updateIncident()  @Transactional
        ↓
IncidentAccessService.requireModifiable()    → not the assignee? 403 / missing? 404
        ↓
IncidentStatus.fromStored()                  → unknown status? 400
        ↓
current.canTransitionTo(target)              → illegal jump? 409
        ↓
apply rootCause / resolution text
        ↓
applyStatusRules()                           → missing root cause? 409
        ↓
IncidentRepository.save()   → UPDATE RESOLVE_INCIDENT
IncidentLogRepository.save()→ INSERT RESOLVE_INCIDENT_LOGS (status = new status)
        ↓
COMMIT
        ↓
supportService.broadcastIncidentUpdate() → /topic/incidents/43/updates
        ↓
SupportIncidentUpdateResponse → 200 OK
```

---

## FEATURE 11 — OPSAI

### A. Purpose
Help the support engineer by summarising, finding similar incidents, analysing, suggesting a
root cause, and recommending resolution steps.

**It is deterministic** — no external AI provider, no API key. Everything is computed from
data already in Oracle.

### B. Endpoint
`POST /api/support/incidents/{incidentId}/ops-ai` — SUPPORT only, assigned engineer only.
**One endpoint for all five actions.**

### C. Controller — `SupportController.opsAi()`

```java
@PostMapping("/incidents/{incidentId}/ops-ai")
public ResponseEntity<OpsAiResponse> opsAi(@PathVariable Long incidentId,
                                            @Valid @RequestBody OpsAiRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser caller) {

    return ResponseEntity.ok(supportService.assist(incidentId, request.action(), caller));
}
```

The controller passes the raw `action` string straight through. `SupportService.assist`
resolves it to an `OpsAiAction` and throws `BadRequestException` (400) if it is not one of
the five — a business decision, so it belongs in the service, not the controller.

`OpsAiRequest` is just `{ "action": "SUMMARIZE" }`. `OpsAiResponse` is
`{ action, result }` where `result` is an `Object` — a different record per action.

### D. Services — `SupportService.assist()` → `OpsAiService`

```java
@Transactional(readOnly = true)
public OpsAiResponse assist(Long incidentId, OpsAiAction action, AuthenticatedUser caller) {
    Incident incident = accessService.requireModifiable(incidentId, caller);
    return opsAiService.assist(incident, action);
}
```

`OpsAiService` is an **interface**; `DeterministicOpsAiService` is the implementation.
That is why the REST layer would not change if the engine were ever replaced.

```java
@Override
@Transactional(readOnly = true)
public OpsAiResponse assist(Incident incident, OpsAiAction action) {
    return switch (action) {
        case SUMMARIZE  -> new OpsAiResponse(action.name(), summarize(incident));
        case SIMILAR    -> new OpsAiResponse(action.name(), similar(incident));
        case ANALYZE    -> new OpsAiResponse(action.name(), analyze(incident));
        case ROOT_CAUSE -> new OpsAiResponse(action.name(), rootCause(incident));
        case RESOLUTION -> new OpsAiResponse(action.name(), resolution(incident));
    };
}
```

**`@Transactional(readOnly = true)` — OpsAI never writes.** A suggested root cause becomes
real only if SUPPORT confirms it through the PATCH endpoint.

### The five actions

| Action | Result record | How your code produces it |
|---|---|---|
| `SUMMARIZE` | `SummarizeResult(summary)` | Builds a sentence from title, service, severity, priority, message counts, first and latest message, status, and any confirmed root cause/resolution |
| `SIMILAR` | `SimilarIncidentsResult(List<SimilarIncident>)` | Ranks all other incidents by similarity, returns top `SIMILAR_LIMIT = 3` with a percentage |
| `ANALYZE` | `AnalyzeResult(analysis, evidence)` | Names the closest match, flags a recurring problem if ≥2 incidents score above `SIMILARITY_FLOOR = 20.0`, and lists the evidence used |
| `ROOT_CAUSE` | `RootCauseResult(possibleRootCause, confidence, evidence)` | Groups similar past incidents by their confirmed root cause, picks the group with strongest combined similarity |
| `RESOLUTION` | `ResolutionResult(recommendedSteps)` | Splits past resolution text into steps; if there is no usable history, falls back to generic steps built from this incident's own service and category |

**How confidence is computed:**

```java
int confidence = (int) Math.round(Math.min(99.0, (averageSimilarity * 0.7) + (agreement * 30.0)));
```

Similarity carries most of the weight; agreement across several incidents raises it.

**The shared ranking helper:**

```java
private List<ScoredIncident> rankedHistory(Incident incident) {
    return incidentRepository.findHistoricalExcluding(incident.getIncidentId()).stream()
            .map(c -> new ScoredIncident(c, similarityService.similarityPercent(incident, c)))
            .filter(s -> s.similarity() > 0.0)
            .sorted(Comparator.comparingDouble(ScoredIncident::similarity).reversed())
            .toList();
}
```

### How similarity works — `IncidentSimilarityService` + `TextSimilarity`

```java
private static final double SERVICE_WEIGHT  = 0.20;
private static final double CATEGORY_WEIGHT = 0.25;
private static final double TEXT_WEIGHT     = 0.40;
private static final double SEVERITY_WEIGHT = 0.15;
```

- service, category, severity → `exactMatch` (1.0 or 0.0)
- title/description/rootCause/resolution → `jaccard` word overlap

`TextSimilarity.tokenize` lowercases, removes punctuation, drops stop words and
single-character tokens. `jaccard` = shared words ÷ total distinct words.

**This is the same class the assignment algorithm uses for experience scoring** — one
similarity definition, used in two places.

### I. Simple complete flow

```
POST /api/support/incidents/43/ops-ai  {"action":"ROOT_CAUSE"}
        ↓
SecurityConfig hasRole("SUPPORT")
        ↓
SupportController.opsAi()
        ↓
OpsAiAction.fromRequest()          → unknown action? 400
        ↓
SupportService.assist()  @Transactional(readOnly)
        ↓
IncidentAccessService.requireModifiable()  → 403 if not the assignee
        ↓
DeterministicOpsAiService.assist()
        ↓
IncidentRepository.findHistoricalExcluding()   → RESOLVE_INCIDENT
IncidentMessageRepository.findByIncident_...() → RESOLVE_INCIDENT_MESSAGE
        ↓
IncidentSimilarityService → TextSimilarity  (pure Java)
        ↓
OpsAiResponse {action, result:{possibleRootCause, confidence, evidence}}
        ↓
200 OK    ← nothing written to the database
```

---

## FEATURE 12 — REAL-TIME CHAT (WebSocket / STOMP)

### A. Purpose
Deliver messages and status updates instantly, without the page refreshing.

### B. Destinations — **these are NOT REST endpoints**

| Direction | Destination |
|---|---|
| Client → server | `/app/incidents/{incidentId}/messages` |
| Client → server | `/app/incidents/{incidentId}/read` |
| Server → client | `/topic/incidents/{incidentId}/messages` |
| Server → client | `/topic/incidents/{incidentId}/updates` |
| Server → client | `/topic/incidents/{incidentId}/read` |

Handshake endpoint: `/ws`.

### C. Controller — `IncidentWebSocketController`

```java
@Controller                       // NOT @RestController
public class IncidentWebSocketController {

    @MessageMapping("/incidents/{incidentId}/messages")
    public void sendMessage(@DestinationVariable Long incidentId,
                            SendMessageRequest request,
                            StompHeaderAccessor accessor) {
        AuthenticatedUser caller = requireCaller(accessor);
        MessageResponse message = messageService.sendMessage(incidentId, request.messageText(), caller);
        messageService.broadcastMessage(incidentId, message);
    }
}
```

| Annotation | REST equivalent |
|---|---|
| `@Controller` | `@RestController` (no `@ResponseBody` — STOMP replies go to a topic) |
| `@MessageMapping` | `@PostMapping` |
| `@DestinationVariable` | `@PathVariable` |

**The most important point:** this controller calls **the same `IncidentMessageService`
methods** as the REST controller. So a message sent over WebSocket is validated,
authorised and persisted **identically** to one sent over REST. No duplicated logic.

### D. How security works here

```java
private static AuthenticatedUser requireCaller(StompHeaderAccessor accessor) {
    AuthenticatedUser caller = StompAuthChannelInterceptor.currentUser(accessor);
    if (caller == null) throw new MessagingException("Authentication is required");
    return caller;
}
```

The principal was bound at CONNECT time by the interceptor.

### The complete chat round trip

```
USER browser
   │  STOMP CONNECT  (Authorization: Bearer <jwt>)
   ▼
StompAuthChannelInterceptor.authenticate()
   │  JwtService.extractIdentity() → TokenRevocationService.isRevoked(jti)?
   │      → revoked: reject the CONNECT
   │      → not revoked: CustomUserDetailsService → AuthenticatedUser
   ▼
   │  STOMP SUBSCRIBE /topic/incidents/43/messages
   ▼
StompAuthChannelInterceptor.authorizeSubscription()
   │  regex match → IncidentAccessService.requireViewable()   ← refused if not allowed
   ▼
   │  STOMP SEND /app/incidents/43/messages
   ▼
IncidentWebSocketController.sendMessage()
   ▼
IncidentMessageService.sendMessage()   @Transactional
   ▼
IncidentMessageRepository.save()  →  Oracle RESOLVE_INCIDENT_MESSAGE
   ▼  (committed)
RealtimeNotifier.broadcastMessage()
   ▼
SimpMessagingTemplate → /topic/incidents/43/messages
   ▼
SUPPORT browser receives it live
```

### `RealtimeNotifier`

```java
private static final String MESSAGES = "/topic/incidents/%d/messages";
private static final String UPDATES  = "/topic/incidents/%d/updates";
private static final String READ     = "/topic/incidents/%d/read";

public void broadcastMessage(Long incidentId, MessageResponse message) {
    messagingTemplate.convertAndSend(MESSAGES.formatted(incidentId), message);
}
```

One small class holding every destination string, so the topic names are written once.

`broadcastAssignment(...)` simply calls `broadcastIncidentUpdate(...)` — the assignment
notification rides the incident's own updates topic.

---

## FEATURE 13 — EXCEPTION HANDLING

### A. Purpose
Every error, anywhere in the app, comes back in **one consistent JSON shape**, and internal
details never leak.

### B. The error shape — `ApiErrorResponse`

```java
public record ApiErrorResponse(LocalDateTime timestamp, int status,
                               String error, String message, String path) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(LocalDateTime.now(), status, error, message, path);
    }
}
```

```json
{
  "timestamp": "2026-08-24T14:30:00",
  "status": 409,
  "error": "CONFLICT",
  "message": "Email is already registered",
  "path": "/api/auth/register"
}
```

### C. The exception family

```
RuntimeException
   └── ApiException (abstract, holds an HttpStatus)
         ├── BadRequestException    → 400
         ├── UnauthorizedException  → 401
         ├── ForbiddenException     → 403
         ├── NotFoundException      → 404
         └── ConflictException      → 409
```

```java
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    protected ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
```

Because each exception **carries its own status**, one handler covers all five.

### D. `GlobalExceptionHandler` — `@RestControllerAdvice`

`@RestControllerAdvice` means "apply to every controller in the application".

| Handler | Catches | Produces |
|---|---|---|
| `handleApiException` | `ApiException` | its own status |
| `handleValidation` | `MethodArgumentNotValidException` | **400**, joining all field messages |
| `handleUnreadable` | `HttpMessageNotReadableException` | **400** "Malformed request body" |
| `handleTypeMismatch` | `MethodArgumentTypeMismatchException` | **400** e.g. `/api/incidents/abc` |
| `handleAuthentication` | `AuthenticationException` | **401** |
| `handleAccessDenied` | `AccessDeniedException` | **403** |
| `handleNotFound` | `NoHandlerFoundException`, `NoResourceFoundException` | **404** unknown URL or missing static file |
| `handleUnexpected` | `Exception` | **500**, logs the cause, returns nothing internal |

The validation handler is worth reading:

```java
String message = ex.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .distinct()
        .collect(Collectors.joining("; "));
```

That is why an empty registration body returns
`"Name is required; Password is required; Email is required"` in one response.

And the last-resort handler:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
}
```

**The stack trace goes to the server log; the client gets one safe sentence.**

### E. Where the handler does NOT apply

401 and 403 raised by Spring Security happen in the **filter chain, before any controller**.
`@RestControllerAdvice` cannot see them. That is exactly why you have
`RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` writing the same JSON shape
manually.

```
Request
  │
  ├── Filter chain  ── fails ──► RestAuthenticationEntryPoint (401)
  │                              RestAccessDeniedHandler      (403)
  │
  └── Controller/Service ── throws ──► GlobalExceptionHandler (400/401/403/404/409/500)
```

---

## Features that are NOT implemented

Say these clearly if asked — do not pretend they exist.

| Feature | Status |
|---|---|
| Logout / token revocation | **Implemented** — `POST /api/auth/logout` revokes the presented token by its `jti`; see Feature 2A |
| Refresh tokens | **Not implemented by design** — there is no refresh token and no refresh-token endpoint |
| Automated tests | **Not implemented** — there is no `src/test/` directory; the API is exercised by hand through Swagger UI |
| Password reset / change password | **Not implemented** |
| SUPPORT self-registration | **Not implemented by design** — SUPPORT accounts are created by a SUPER_ADMIN through `POST /api/support-users` |
| SUPER_ADMIN self-registration | **Not implemented by design** — the account is seeded in the database and is never created over an API |
| Manual assignment / reassignment API | **Not implemented** — assignment is automatic only |
| File attachments on incidents | **Not implemented** |
| Email/SMS notification | **Not implemented** |
| Pagination on dashboards | **Not implemented** — full lists are returned |
| Per-user WebSocket destination | **Not implemented** — all topics are per-incident |
| External AI provider | **Not used** — OpsAI is deterministic Java |

---

# PART 4 — DEEP EXPLANATIONS USING YOUR CODE

## 1. Spring Boot application startup

**Simple:** `main()` runs, Spring builds every object, connects to Oracle, starts Tomcat.

**In your project, in order:**

```
java -jar resolveit-backend-1.0.0.jar
   ↓
ResolveItApplication.main()
   ↓
@ComponentScan finds every @RestController/@Service/@Repository/@Component/@Configuration in com.dtcc.intern.demo
   ↓
application.properties is read
   ↓
HikariCP opens Oracle connections   ← "ResolveItHikari - Added connection"
   ↓
Hibernate reads the 7 @Entity classes (ddl-auto=none → no DDL)
   ↓
Spring Data creates the 7 repository implementations
   ↓
SecurityConfig builds the filter chain
WebSocketConfig registers /ws and the broker
OpenApiConfig builds the Swagger document
   ↓
Tomcat starts on port 8080
   ↓
"Started ResolveItApplication"
```

## 2. Dependency Injection

**Simple:** you never write `new AuthService(...)`. Spring creates objects and hands them to
whoever needs them.

**Your code uses constructor injection everywhere:**

```java
public AuthService(CustomUserDetailsService userDetailsService,
                   PasswordEncoder passwordEncoder,
                   JwtService jwtService,
                   AppUserRepository appUserRepository,
                   RoleRepository roleRepository) { ... }
```

Spring sees `@Service` on `AuthService`, sees the constructor needs five things, finds them
all, and builds it.

**Why constructor injection?** The fields can be `final`, so a half-built object is
impossible, and it is obvious what the class depends on.

## 3. Controller → Service → Repository

**But your project is often deeper than that.** Be honest about it:

```
IncidentController
   → IncidentService
        → IncidentAccessService      (may I touch this incident?)
        → PriorityService            (severity → priority)
        → AssignmentService          (which engineer?)
             → IncidentSimilarityService → TextSimilarity
             → AppUserRepository, IncidentAssignmentRepository
        → IncidentRepository, IncidentLogRepository
        → RealtimeNotifier           (broadcast after commit)
```

Each layer has one job:

| Layer | Job | Never does |
|---|---|---|
| Controller | HTTP in/out | business rules, SQL |
| Service | rules, transactions | HTTP details |
| Repository | database | rules |
| Entity | table shape | logic |

## 4. DTO vs Entity

| | Entity | DTO |
|---|---|---|
| Example | `AppUser` | `LoginResponse` |
| Represents | an Oracle table row | a JSON message |
| Annotations | `@Entity`, `@Column` | validation only |
| Sent to client? | **never** | yes |

**Why never expose the entity?** `AppUser` contains `passwordHash`. Returning it would leak
the hash. `LoginResponse` has no such field — the leak is impossible **by design**, not by
remembering to hide it.

Your DTOs are Java **records** — short, immutable, and they generate `equals`, `hashCode`
and accessors automatically.

## 5. JPA / Hibernate

**Simple:** you write Java; Hibernate writes SQL.

```java
List<Incident> findByReportedBy_UserIdOrderByCreatedAtDescIncidentIdDesc(Long userId);
```

becomes roughly:

```sql
SELECT * FROM RESOLVE_INCIDENT WHERE reported_by = ? ORDER BY created_at DESC, incident_id DESC
```

**In your project:** `@Entity` maps class→table, `@Column` maps field→column,
`@ManyToOne`/`@JoinColumn` map foreign keys, and `ddl-auto=none` means Hibernate reads the
schema but never changes it.

**Two Oracle-specific mappings to remember:**

```java
@Lob @Column(name = "description") private String description;   // Oracle CLOB
@Convert(converter = NumericBooleanConverter.class)
@Column(name = "is_read") private Boolean isRead;                // NUMBER(1) ↔ boolean
```

## 6. Oracle connection

```
application.properties
   ↓
spring.datasource.url = jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1
   ↓
HikariCP pool ("ResolveItHikari", max 10 connections)
   ↓
ojdbc11 driver
   ↓
Oracle FREEPDB1, schema OPSPULSE
```

`jdbc:oracle:thin` is the pure-Java driver (no Oracle client install needed).
`FREEPDB1` is the pluggable database name.

**If Oracle is down:** Hikari waits `connection-timeout=10000` (10s) then fails. At startup
the app refuses to start; at runtime the request ends in the generic 500 handler and the
real cause is logged, not returned.

## 7. Transactions

**Simple:** all-or-nothing. Either every write happens, or none does.

**Your clearest example** — `IncidentService.createIncident()` writes to **four** tables:
RESOLVE_INCIDENT, RESOLVE_INCIDENT_LOGS (twice), RESOLVE_INCIDENT_ASSIGNMENT. One `@Transactional` wraps them all.
If assignment failed halfway, you would not be left with an incident that has no history.

| Form | Meaning | Example in your code |
|---|---|---|
| `@Transactional` | read-write | `createIncident`, `register`, `sendMessage` |
| `@Transactional(readOnly = true)` | read-only, a hint to the DB | `userDashboard`, `login`, `assist` |

**And the subtle one:** broadcasting happens **after** commit, via
`TransactionSynchronizationManager` in `createIncident`, or simply by the controller calling
the broadcast method after the service returns.

## 8. JWT authentication

A JWT has three parts: `header.payload.signature`.

Your payload:

```json
{ "sub":"user@example.com", "role":RESOLVE_USER, "name":"Arjun", "userId":1,
  "jti":"6f1c2a94-...", "iat":1787534269, "exp":1787563069 }
```

The `jti` is a unique id for this one token. It is what logout revokes.

**It is signed, not encrypted** — anyone can read it, but nobody can change it without the
secret, because the signature would stop matching.

```
Login → JwtService.generateToken() → signed token (with a unique jti)
Client stores it
Every request: Authorization: Bearer <token>
   ↓
JwtAuthenticationFilter → JwtService.extractIdentity()
   ↓ (valid?)
TokenRevocationService.isRevoked(jti)?
   ↓ (not revoked?)
CustomUserDetailsService → AuthenticatedUser → SecurityContext
```

**Why stateless is useful:** the server stores no session, so any instance can serve any
request.

**The classic trade-off — and how this project answers it.** A pure JWT cannot be cancelled
before it expires, because the signature keeps verifying. That is why `POST /api/auth/logout`
exists: each token carries a `jti`, logout records that `jti` in `TokenRevocationService`,
and both the REST filter and the STOMP interceptor check the list before establishing
identity. It is the smallest possible amount of server state — a deny list that can only
ever reject a token, never accept one — so `STATELESS` still holds and no session is created.

The honest caveat: that list lives in memory, so it does not survive a restart or span
multiple instances. Making it durable is a one-class change to `TokenRevocationService`.

## 9. Authentication vs Authorization

| | Authentication | Authorization |
|---|---|---|
| Question | **Who are you?** | **Are you allowed?** |
| Fails with | **401** | **403** |
| Your code | `AuthenticationManager` (at login), `JwtAuthenticationFilter` + `TokenRevocationService` (every later request) | `SecurityConfig` role rules, `IncidentAccessService` |

Your project authorises at **two** levels:

1. **URL level** — `SecurityConfig`: "only SUPPORT may call `/api/support/**`"
2. **Data level** — `IncidentAccessService`: "only *this* engineer may modify *this*
   incident". URL rules cannot express that, because it depends on database rows.

Three methods, three strictness levels:

| Method | Rule | Used by |
|---|---|---|
| `requireViewable` | reporter, assignee, or same team | incident details, STOMP SUBSCRIBE |
| `requireConversationParticipant` | reporter or assignee only | send message, mark read |
| `requireModifiable` | assigned engineer only | status update, OpsAI |

## 10. USER vs SUPPORT roles

```java
public final class RoleName {
    public static final String USER = RESOLVE_USER;
    public static final String SUPPORT = "SUPPORT";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
}
```

Three roles, stored as rows in the `RESOLVE_ROLE` table, linked by `USER.role_id`.
SUPER_ADMIN provisions SUPPORT engineers and reads the team list; it has no access
to incidents or conversations.

| | USER | SUPPORT | SUPER_ADMIN |
|---|---|---|---|
| Register themselves | yes | **no** | **no** |
| Report incidents | yes | no | no |
| See own incidents | yes | — | no |
| Support dashboard | no | yes | no |
| Change status / root cause / resolution | no | yes (assigned only) | no |
| OpsAI | no | yes (assigned only) | no |
| Chat | yes | yes | **no** |
| List teams | no | no | yes |
| Create SUPPORT engineers | no | no | yes |

The role reaches Spring Security through `AuthenticatedUser.getAuthorities()` as
`ROLE_USER` / `ROLE_SUPPORT` / `ROLE_SUPER_ADMIN`, which is what `hasRole("USER")`,
`hasRole("SUPPORT")` and `hasRole("SUPER_ADMIN")` check.

## 11. 401 vs 403 vs 404 vs 500

| Code | Meaning | When it happens in your app |
|---|---|---|
| **400** | Bad request | Validation failed, unknown severity, unknown OpsAI action, `/api/incidents/abc` |
| **401** | Not authenticated | No token, expired token, tampered token, wrong password |
| **403** | Authenticated but not allowed | USER calling `/api/support/**`; SUPPORT modifying an incident not assigned to them |
| **404** | Does not exist | Unknown incident id, unknown URL, missing Swagger file |
| **409** | Conflict with current state | Duplicate email, illegal status transition, resolving without a root cause |
| **500** | Unexpected | Anything not covered — logged in full, returned as one safe sentence |

**Remember the difference:** 401 = "I don't know who you are." 403 = "I know who you are,
and no."

## 12. Password hashing

```java
@Bean
public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
```

- **Register:** `AuthService` calls `passwordEncoder.encode(raw)` → `$2a$10$...` (60 characters)
- **Login:** `DaoAuthenticationProvider` calls `passwordEncoder.matches(raw, hash)` → true/false.
  `AuthService` never touches the hash itself; it only asks the `AuthenticationManager`.

**Hashing is one-way.** You cannot get the password back from the hash — even you, the
developer, cannot. `matches()` re-hashes the attempt using the salt embedded in the stored
hash and compares.

BCrypt automatically uses a **different salt per user**, so two people with the same
password get different hashes.

## 13. WebSocket / STOMP

**HTTP:** client asks, server answers, connection closes. The server cannot start a
conversation.
**WebSocket:** one connection stays open; either side can send at any time.
**STOMP:** a simple messaging format on top of WebSocket, giving you destinations and
subscriptions.

Your project uses it so a support reply appears on the user's screen **without refreshing**.

`enableSimpleBroker("/topic")` is an **in-memory** broker inside your app — no RabbitMQ or
ActiveMQ needed. Good for a learning project; it does not survive a restart and does not
share across multiple servers.

## 14. Automatic assignment algorithm

Covered in Feature 5. The one-line version:

> Four factors — experience 40%, availability 25%, workload 20%, fairness 15% — each scored
> 0–100, combined into one final score; the highest-scoring eligible engineer on the owning
> team gets the incident, and only that final score is stored.

## 15. Incident lifecycle

```
REPORTED → ASSIGNED → IN PROGRESS → ROOT CAUSE IDENTIFIED → RESOLUTION IN PROGRESS → RESOLVED
```

- Enforced by `IncidentStatus.canTransitionTo()` — **only the next state**, never a skip,
  repeat or reversal.
- `RESOLVE_INCIDENT.status` = where it is **now**. `RESOLVE_INCIDENT_LOGS` = **every** change.
- `REPORTED → ASSIGNED` happens automatically at creation; the rest are driven by SUPPORT
  through the PATCH endpoint.
- Extra rules: `ROOT CAUSE IDENTIFIED` needs a root cause; `RESOLVED` needs both a root
  cause and a resolution, and stamps `resolved_at`.

## 16. OpsAI implementation

Covered in Feature 11. The one-line version:

> `OpsAiService` is an interface; `DeterministicOpsAiService` implements all five actions by
> reading the incident, its conversation and historical incidents from Oracle and scoring
> them with `IncidentSimilarityService`/`TextSimilarity`. It is read-only and uses no
> external AI provider.

## 17. Exception handling

Covered in Feature 13. The one-line version:

> Business code throws a typed `ApiException` subclass carrying its own HTTP status;
> `GlobalExceptionHandler` (`@RestControllerAdvice`) turns every exception into the same
> `ApiErrorResponse` JSON, and the two security handlers do the same for 401/403 raised
> before any controller runs.

---

# PART 5 — DATABASE UNDERSTANDING

## The 7 tables

| Java Entity | Oracle table | What it stores |
|---|---|---|
| `Role` | `RESOLVE_ROLE` | exactly 2 rows: USER, SUPPORT |
| `AppUser` | `RESOLVE_USER` (quoted) | all accounts, both roles |
| `TeamService` | `RESOLVE_TEAM_SERVICE` | a support team + the service it owns |
| `Incident` | `RESOLVE_INCIDENT` | the incident itself, current status |
| `IncidentAssignment` | `RESOLVE_INCIDENT_ASSIGNMENT` | which engineer + the final score |
| `IncidentMessage` | `RESOLVE_INCIDENT_MESSAGE` | the conversation |
| `IncidentLog` | `RESOLVE_INCIDENT_LOGS` | status change history |

## Table by table

### `RESOLVE_ROLE`

| Column | Type | Key |
|---|---|---|
| `role_id` | NUMBER | **PK**, identity |
| `role_name` | VARCHAR2(30) | NOT NULL |

Accessed by `RoleRepository.findByRoleNameIgnoreCase(...)` during registration.

### `RESOLVE_USER`

| Column | Type | Key |
|---|---|---|
| `user_id` | NUMBER | **PK**, identity |
| `name` | VARCHAR2(100) | NOT NULL |
| `email` | VARCHAR2(150) | NOT NULL, **UNIQUE** |
| `password_hash` | VARCHAR2(255) | NOT NULL |
| `role_id` | NUMBER | **FK → RESOLVE_ROLE** |
| `team_id` | NUMBER | **FK → RESOLVE_TEAM_SERVICE**, nullable |

```java
@ManyToOne(fetch = FetchType.EAGER, optional = false)
@JoinColumn(name = "role_id", nullable = false) private Role role;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "team_id") private TeamService team;
```

`team_id` is nullable because a reporting USER belongs to no support team.

### `RESOLVE_TEAM_SERVICE`

| Column | Type | Key |
|---|---|---|
| `team_id` | NUMBER | **PK** |
| `team_name` | VARCHAR2(100) | NOT NULL |
| `service_name` | VARCHAR2(120) | NOT NULL |
| `department` | VARCHAR2(100) | |
| `description` | VARCHAR2(500) | |

### `RESOLVE_INCIDENT`

| Column | Type | Key |
|---|---|---|
| `incident_id` | NUMBER | **PK** |
| `incident_code` | VARCHAR2(30) | NOT NULL, **UNIQUE** |
| `title` | VARCHAR2(200) | NOT NULL |
| `description` | CLOB | `@Lob` |
| `category` | VARCHAR2(50) | |
| `severity` | VARCHAR2(20) | |
| `priority` | VARCHAR2(10) | |
| `status` | VARCHAR2(30) | current state |
| `reported_by` | NUMBER | **FK → RESOLVE_USER** |
| `team_id` | NUMBER | **FK → RESOLVE_TEAM_SERVICE** |
| `root_cause` | CLOB | `@Lob`, null until confirmed |
| `resolution` | CLOB | `@Lob`, null until resolved |
| `created_at` | TIMESTAMP | |
| `resolved_at` | TIMESTAMP | null until RESOLVED |

### `RESOLVE_INCIDENT_ASSIGNMENT`

| Column | Type | Key |
|---|---|---|
| `assignment_id` | NUMBER | **PK** |
| `incident_id` | NUMBER | **FK → RESOLVE_INCIDENT** |
| `support_user_id` | NUMBER | **FK → RESOLVE_USER** |
| `assignment_score` | NUMBER(5,2) | the final score only |
| `assigned_at` | TIMESTAMP | |

### `RESOLVE_INCIDENT_MESSAGE`

| Column | Type | Key |
|---|---|---|
| `message_id` | NUMBER | **PK** |
| `incident_id` | NUMBER | **FK → RESOLVE_INCIDENT** |
| `sender_id` | NUMBER | **FK → RESOLVE_USER** |
| `message_text` | CLOB | `@Lob` |
| `sent_at` | TIMESTAMP | |
| `is_read` | NUMBER(1) | 0/1 via `NumericBooleanConverter` |

### `RESOLVE_INCIDENT_LOGS`

| Column | Type | Key |
|---|---|---|
| `log_id` | NUMBER | **PK** |
| `incident_id` | NUMBER | **FK → RESOLVE_INCIDENT** |
| `status` | VARCHAR2(30) | the status the incident entered |
| `changed_at` | TIMESTAMP | |

## Relationships that actually exist

All relationships in your code are **Many-to-One** (`@ManyToOne`), which is the same as
One-to-Many seen from the other side.

```
RESOLVE_ROLE ──1─────M──► RESOLVE_USER                 (many users share one role)
RESOLVE_TEAM_SERVICE ──1───M──► RESOLVE_USER           (many engineers in one team; nullable)
RESOLVE_TEAM_SERVICE ──1───M──► RESOLVE_INCIDENT         (many incidents for one service)
RESOLVE_USER ──1───M──► RESOLVE_INCIDENT               (reported_by)
RESOLVE_INCIDENT ──1───M──► RESOLVE_INCIDENT_ASSIGNMENT
RESOLVE_USER ──1───M──► RESOLVE_INCIDENT_ASSIGNMENT    (support_user_id)
RESOLVE_INCIDENT ──1───M──► RESOLVE_INCIDENT_MESSAGE
RESOLVE_USER ──1───M──► RESOLVE_INCIDENT_MESSAGE       (sender_id)
RESOLVE_INCIDENT ──1───M──► RESOLVE_INCIDENT_LOGS
```

**There are NO one-to-one relationships and NO many-to-many relationships in this schema.**
Say that plainly if asked — do not invent any.

Also note: your entities only declare the **`@ManyToOne` side**. There is no
`@OneToMany List<Incident>` inside `AppUser`. That is a deliberate choice — it avoids
accidentally loading thousands of rows, and the repositories fetch what is needed instead.

## Fetch types used

| Relationship | Fetch | Why |
|---|---|---|
| `AppUser.role` | **EAGER** | needed immediately on every login |
| `AppUser.team` | LAZY | only needed during assignment |
| `Incident.reportedBy` | LAZY | not always needed |
| `Incident.team` | LAZY | |
| `IncidentAssignment.incident` / `.supportUser` | LAZY | |
| `IncidentMessage.incident` / `.sender` | LAZY | |
| `IncidentLog.incident` | LAZY | |

LAZY means "fetch only when actually used" — this is why service methods that touch lazy
fields are `@Transactional` (the session must still be open).

---

# PART 6 — THE COMPLETE REQUEST LIFECYCLE

## The general path

```
User / Swagger / Bruno
        │  HTTP request + Authorization: Bearer <jwt>
        ▼
Tomcat (port 8080)
        ▼
Spring Security filter chain
   ├── JwtAuthenticationFilter → JwtService.extractIdentity()
   │        → TokenRevocationService.isRevoked(jti)?  → yes: stay anonymous → 401
   │        → CustomUserDetailsService → AuthenticatedUser → SecurityContext
   └── authorizeHttpRequests rules
            ├── not authenticated → RestAuthenticationEntryPoint  → 401
            └── wrong role        → RestAccessDeniedHandler       → 403
        ▼
DispatcherServlet  → no mapping? NoHandlerFoundException → 404
        ▼
Controller  (@RestController)
   ├── @RequestBody  → JSON becomes a Request DTO
   ├── @Valid        → fails → MethodArgumentNotValidException → 400
   └── @AuthenticationPrincipal → AuthenticatedUser
        ▼
Service  (@Transactional)  ← transaction BEGINS
   ├── IncidentAccessService  → 403 / 404
   ├── entity enums           → 400 / 409
   └── other services
        ▼
Repository (Spring Data)
        ▼
Hibernate → SQL
        ▼
HikariCP → ojdbc11 → Oracle FREEPDB1
        ▼
rows → Entity objects
        ▼
Service maps Entity → Response DTO   ← transaction COMMITS
        ▼
(if applicable) RealtimeNotifier broadcasts to /topic/...
        ▼
Controller returns ResponseEntity
        ▼
Jackson turns the DTO into JSON
        ▼
HTTP response
```

## Where each feature differs

**Registration** — no token needed; no `@AuthenticationPrincipal`; adds the duplicate-email
check (409) and BCrypt hashing; returns 201.

```
Request → [no security check] → AuthController → AuthService → 2 repositories → RESOLVE_USER → 201
```

**Login** — no token in, token **out**; reads only; adds `JwtService.generateToken()` at the
end.

```
Request → [no security check] → AuthController → AuthService → CustomUserDetailsService
        → AppUserRepository → BCrypt matches → JwtService → 200 + token
```

**Logout** — token **in**, nothing out but a message; the only auth endpoint that requires a
token; touches no repository and no table.

```
Request → security (must authenticate, and the token must not already be revoked)
        → AuthController → BearerTokens.resolve → AuthService → JwtService.extractIdentity
        → TokenRevocationService.revoke(jti) → 200
```

Every later request carrying that same token now stops at the security step and gets 401.

**Incident creation** — the deepest path; four tables written in one transaction; the
broadcast is registered to run **after commit**.

```
Request → security → IncidentController → IncidentService
        → PriorityService + TeamServiceRepository + AssignmentService(+2 repos +similarity)
        → RESOLVE_INCIDENT, RESOLVE_INCIDENT_LOGS ×2, RESOLVE_INCIDENT_ASSIGNMENT → commit → broadcast → 201
```

**Incident update** — access check is the strictest (`requireModifiable`); the enum decides
whether the transition is legal (409); the controller broadcasts **after** the service
returns.

**Chat / WebSocket** — **no HTTP security filter at all**. Security is the STOMP
interceptor. There is no `ResponseEntity`; the reply goes to a topic.

```
STOMP CONNECT (JWT) → StompAuthChannelInterceptor  (signature + expiry + revocation)
STOMP SUBSCRIBE     → requireViewable
STOMP SEND → IncidentWebSocketController → IncidentMessageService → RESOLVE_INCIDENT_MESSAGE
           → commit → RealtimeNotifier → /topic/... → other browser
```

**OpsAI** — reaches the database but writes **nothing**; `readOnly = true`; the heavy work
is pure Java scoring after the reads.

```
Request → security → SupportController → OpsAiAction enum → SupportService
        → requireModifiable → DeterministicOpsAiService → 2 repositories (SELECT only)
        → similarity scoring in memory → 200
```

---

# PART 7 — EVALUATION QUESTIONS & ANSWERS

## Foundation

**Q: Why did you use `@SpringBootApplication`?**
**ANSWER:** "It combines configuration, auto-configuration and component scanning, so Spring
finds all my classes under `com.dtcc.intern.demo` and sets up Tomcat, JPA and security for me."
**DEEPER:** Without `@ComponentScan`, none of my `@Service` or `@RestController` classes
would be registered and every injection would fail at startup.

**Q: How does your application connect to Oracle?**
**ANSWER:** "Through JDBC. `application.properties` has the URL, username, password and the
`oracle.jdbc.OracleDriver`. Spring Boot builds a HikariCP connection pool from those, and
Hibernate uses it."
**DEEPER:** The URL `jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1` is the thin (pure Java)
driver, port 1521, service `FREEPDB1`. Hikari keeps up to 10 connections open and reuses
them so we don't pay the connection cost per request. Credentials come from environment
variables, so no password is committed.

**Q: Why is `ddl-auto` set to `none`?**
**ANSWER:** "Because Oracle owns the schema. The tables already exist and Hibernate must
never create, alter or drop them."
**DEEPER:** With `update` or `create`, Hibernate would try to change my tables to match my
entities — that could drop data or break constraints. `none` makes the database the source
of truth and my entities must match it, not the other way round.

**Q: Why are all your tables called RESOLVE_something?**
**ANSWER:** "The Oracle schema is shared with other projects on my team, so every table
ResolveIT owns is prefixed `RESOLVE_`. That keeps my tables separate from theirs, and every
foreign key in my schema points at another `RESOLVE_` table, never at a shared one."
**DEEPER:** It also avoids a real problem — `USER` on its own is an Oracle reserved word and
would have to be written as a quoted identifier. `RESOLVE_USER` is not reserved, so nothing
needs quoting.

**Q: What happens if the database is down?**
**ANSWER:** "At startup the app fails to start because Hikari cannot get a connection. At
runtime the request fails, my generic handler logs the real error and returns a 500 with a
safe message."
**DEEPER:** `connection-timeout=10000` means it waits 10 seconds before giving up. The
client never sees the ORA- code — that stays in the server log.

## Architecture

**Q: Why did you use `@RestController` instead of `@Controller`?**
**ANSWER:** "`@RestController` is `@Controller` plus `@ResponseBody`, so whatever I return is
automatically converted to JSON instead of being treated as a view name."
**DEEPER:** My WebSocket controller *is* a plain `@Controller`, because STOMP replies are
published to a topic rather than returned in the response body.

**Q: Why is the logic in the Service and not the Controller?**
**ANSWER:** "So the controller only deals with HTTP, and the rules can be reused. My
`IncidentMessageService.sendMessage` is called by both the REST controller and the WebSocket
controller — if the logic were in the controller I would have to duplicate it."
**DEEPER:** It also gives me one place for `@Transactional`, and keeps the rules testable
independently of HTTP.

**Q: Why use a Repository instead of writing SQL?**
**ANSWER:** "Spring Data generates the query from the method name, so I write less code and
make fewer mistakes. Where I need something more complex I use `@Query` with JPQL."
**DEEPER:** e.g. `findByReportedBy_UserIdOrderByCreatedAtDescIncidentIdDesc` becomes a
SELECT with a WHERE and an ORDER BY. My custom `findSupportEngineersByTeam` uses `@Query`
because it filters on a joined entity's field.

**Q: Why use DTOs? Why not return the Entity?**
**ANSWER:** "Because `AppUser` contains `password_hash`. If I returned the entity that hash
would be in the JSON. My `LoginResponse` has only token, userId, name and role, so it is
impossible to leak."
**DEEPER:** DTOs also decouple the API from the database — I can change a column without
breaking clients — and they carry the validation annotations.

**Q: Why is `@Transactional` needed?**
**ANSWER:** "So a group of database writes either all succeed or all fail. Creating an
incident writes to four tables — if one failed, I don't want half an incident."
**DEEPER:** `readOnly = true` on queries tells the database it is a read, which is a small
optimisation and prevents accidental writes. It also keeps the Hibernate session open so
LAZY relationships can still be loaded.

## Security

**Q: Why JWT and not sessions?**
**ANSWER:** "JWT is stateless — the token carries the identity, so the server stores
nothing. My security config is set to `STATELESS`."
**DEEPER:** The classic trade-off is that a signed token cannot be cancelled early. I solve
that for logout specifically: every token carries a unique `jti`, and `POST /api/auth/logout`
records that `jti` in `TokenRevocationService`, which the REST filter and the STOMP
interceptor both check before establishing identity. It is a deny list — it can reject a
token, never accept one — so the design stays stateless and no session is created. It lives
in memory, so it does not survive a restart; making it durable is a one-class change.

**Q: How does logout work if a JWT cannot be un-signed?**
**ANSWER:** "It does not destroy the token — it makes the server stop honouring it. Each
token has a unique `jti`. Logout puts that `jti` on a revocation list, and both
`JwtAuthenticationFilter` and `StompAuthChannelInterceptor` check the list *before* deciding
who the caller is, so the token never reaches the SecurityContext and the request gets 401."
**DEEPER:** Revocation is per token, not per user — each login mints its own `jti` — so
logging out on one device leaves other devices signed in. Entries are dropped once the token
they name would have expired anyway, so the list cannot grow without bound.

**Q: How is the JWT validated?**
**ANSWER:** "`JwtAuthenticationFilter` reads the `Authorization: Bearer` header and calls
`JwtService.extractIdentity`, which verifies the signature and expiry. I then check the token
has not been logged out, and only then load the user and put them in the SecurityContext."
**DEEPER:** `extractIdentity` returns an `Optional` and never throws — an invalid, expired or
revoked token simply means no authentication is set, and then `SecurityConfig` produces the
401. It also returns empty if the token has no `jti`, which fails closed: a token I cannot
check against the revocation list is refused rather than trusted.

**Q: What stops someone editing the token to become SUPPORT?**
**ANSWER:** "The signature. If you change the payload, the signature no longer matches the
secret, and verification fails, so it is treated as no token at all — 401."
**DEEPER:** I proved this: changing the last characters of a token gives 401, not access.

**Q: How are roles checked?**
**ANSWER:** "`AuthenticatedUser.getAuthorities()` returns `ROLE_USER` or `ROLE_SUPPORT`, and
`SecurityConfig` uses `hasRole("SUPPORT")` on `/api/support/**`."
**DEEPER:** Spring adds the `ROLE_` prefix automatically, which is why my authority string
must include it. Role checks are URL-level; anything that depends on data is checked in
`IncidentAccessService`.

**Q: Why do you have both `SecurityConfig` and `IncidentAccessService`?**
**ANSWER:** "`SecurityConfig` can only see the URL and the role. It cannot know whether
*this* engineer is assigned to *this* incident — that is in the database. So data-level
checks live in `IncidentAccessService`."
**DEEPER:** That class has three levels: `requireViewable`, `requireConversationParticipant`
and `requireModifiable`. Writing them once means REST and WebSocket cannot disagree.

**Q: Can someone register as SUPPORT?**
**ANSWER:** "No. `RegisterRequest` has no role field at all, and `AuthService.register` looks
up the USER role itself. Extra JSON keys are ignored."
**DEEPER:** I tested sending `role`, `roleId`, `authorities` and `role: ADMIN` — all created
plain USER accounts. It is closed off by the type, not by a check I could forget.

**Q: How are passwords stored?**
**ANSWER:** "BCrypt-hashed, never plain text. Registration calls `encode`, login calls
`matches`."
**DEEPER:** Hashing is one-way — even I cannot recover the password. BCrypt salts each hash
differently, so two users with the same password have different hashes.

**Q: What happens if the user is unauthorized?**
**ANSWER:** "If they have no valid token, the entry point returns 401. If they are logged in
but lack the role or the ownership, they get 403. Both in my standard JSON error shape."
**DEEPER:** These are written by `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler`
rather than by `GlobalExceptionHandler`, because they happen in the filter chain before any
controller runs.

## Features

**Q: How does automatic assignment work?**
**ANSWER:** "When an incident is created I find the team that owns the service, get the
SUPPORT engineers on that team, and score each on four factors — experience 40%,
availability 25%, workload 20%, fairness 15%. The highest total wins."
**DEEPER:** Experience is the sum of similarity to past incidents they handled, normalised
with a floor of 40 so a new engineer still competes. Availability is derived from active
incident count because the schema has no availability column. Workload uses priority weight
plus a bounded age penalty. Fairness is idle time capped at four hours. Only the final score
is stored, in `RESOLVE_INCIDENT_ASSIGNMENT.assignment_score`.

**Q: Does it always pick the most experienced engineer?**
**ANSWER:** "No, and it shouldn't. As an engineer's workload grows their workload score
falls, so the incident goes to someone less loaded."
**DEEPER:** I observed this — over eight consecutive incidents the split was 4/4 between two
engineers even though one had far more experience.

**Q: How does the incident status change?**
**ANSWER:** "SUPPORT calls `PATCH /api/support/incidents/{id}` with the new status. The
`IncidentStatus` enum only allows the very next state, so skipping or going backwards is a
409."
**DEEPER:** `canTransitionTo` compares `ordinal() + 1`, so the enum declaration order *is*
the workflow. Every change also writes a row into `RESOLVE_INCIDENT_LOGS`, and `RESOLVED` requires
both a root cause and a resolution.

**Q: Why is there one endpoint for status, root cause and resolution?**
**ANSWER:** "Because they happen together in the same workflow step, and the specification
says not to create separate CRUD endpoints for each."
**DEEPER:** `SupportIncidentUpdateRequest` has a required status and optional rootCause and
resolution, and `applyStatusRules` enforces which ones are required for which target status.

**Q: How does a message become read?**
**ANSWER:** "The recipient calls the mark-read endpoint with message ids. I set `is_read` to
1, but only for messages the *other* person sent."
**DEEPER:** The query is scoped by incident id, so passing ids from another incident finds
nothing. Oracle has no boolean, so `is_read` is `NUMBER(1)` mapped with
`NumericBooleanConverter`.

**Q: Why WebSocket instead of just REST?**
**ANSWER:** "With REST the browser would have to keep asking 'any new messages?'. WebSocket
keeps one connection open so the server can push instantly."
**DEEPER:** I measured about 20–40 ms delivery. STOMP adds destinations and subscriptions on
top of raw WebSocket, so the client can subscribe to one incident's topic.

**Q: Is the WebSocket secure?**
**ANSWER:** "Yes. The JWT is sent in the STOMP CONNECT frame, and every SUBSCRIBE is checked
against the incident by `StompAuthChannelInterceptor`."
**DEEPER:** I tested it — a third user connecting with a valid token still gets refused when
subscribing to an incident they are not part of, and connecting with no token is rejected
outright.

**Q: How does OpsAI work? Is it real AI?**
**ANSWER:** "It is deterministic, not machine learning. It reads the incident, its
conversation and past incidents from Oracle and scores them with a weighted similarity —
service 20%, category 25%, text overlap 40%, severity 15%."
**DEEPER:** Text overlap is Jaccard similarity on tokenised words after removing stop words.
Root cause suggestions group similar past incidents by their confirmed root cause and take
the strongest group; confidence is `averageSimilarity × 0.7 + agreement × 30`. Because it is
pure computation, the same input always gives the same output.

**Q: Can OpsAI change the incident?**
**ANSWER:** "No. It is `@Transactional(readOnly = true)` and never calls save. A suggestion
becomes real only when SUPPORT confirms it through the update endpoint."

**Q: Why is `OpsAiService` an interface?**
**ANSWER:** "So the REST layer depends on the contract, not the implementation. If I ever
replaced the deterministic engine, no controller would change."

**Q: What happens if the request is invalid?**
**ANSWER:** "Validation annotations catch it before my method runs, and
`GlobalExceptionHandler` turns it into a 400 with all the messages joined."
**DEEPER:** An empty registration body returns "Name is required; Password is required;
Email is required" — one response listing every problem, not just the first.

**Q: Why one global exception handler?**
**ANSWER:** "So every error looks the same to the client and no stack trace ever leaks. It
is annotated `@RestControllerAdvice`, which applies it to every controller."
**DEEPER:** My typed exceptions each carry their own `HttpStatus`, so one handler covers
400/401/403/404/409, and a final `Exception` handler logs anything unexpected and returns a
safe 500.

**Q: Why don't you have any tests?**
**ANSWER:** "I had unit tests earlier but removed them to keep this first project simple, and
I test the APIs manually through Swagger UI."
**DEEPER:** Be honest here. If asked what you *would* test: the `IncidentStatus` transition
rules, the priority mapping, the assignment scoring, and that registration always produces
the USER role.

---

# PART 8 — "EXPLAIN THIS FILE TO THE EVALUATOR"

### `ResolveItApplication.java`
1. **Purpose:** starts the app.
2. **Annotations:** `@SpringBootApplication`.
3. **Methods:** `main` → `SpringApplication.run`.
4. **Dependencies:** none.
5. **Called by:** the JVM.
6. **Calls:** the whole Spring container.
7. **Verbal:** "My entry point. It enables auto-configuration and scans `com.dtcc.intern.demo`."

### `SecurityConfig.java`
1. **Purpose:** decides who may call which URL.
2. **Annotations:** `@Configuration`, `@EnableWebSecurity`, `@Bean`.
3. **Methods:** `filterChain`, `passwordEncoder`, `authenticationManager`.
4. **Dependencies:** `JwtAuthenticationFilter`, entry point, access-denied handler.
5. **Called by:** Spring at startup; then the filter chain on every request.
6. **Calls:** the JWT filter.
7. **Verbal:** "Stateless security. Login and register are public, logout is authenticated
   because you can only revoke a token you hold, `/api/support/**` is SUPPORT-only, and
   everything else needs a token. My JWT filter runs before the standard username-password
   filter."

### `JwtService.java`
1. **Purpose:** create and verify JWTs.
2. **Annotations:** `@Service`, `@Value` on the constructor.
3. **Methods:** `generateToken`, `extractSubject`, `extractIdentity`, `buildKey`.
4. **Dependencies:** the secret and expiry from properties.
5. **Called by:** `AuthService` (login and logout), `JwtAuthenticationFilter`,
   `StompAuthChannelInterceptor`.
6. **Calls:** the jjwt library.
7. **Verbal:** "Signs tokens with an HMAC key built from my configured secret, stamps each
   with a unique `jti` so logout can revoke one specific token, and rejects any token whose
   signature or expiry does not check out."

### `TokenRevocationService.java`
1. **Purpose:** remember which tokens have been logged out.
2. **Annotations:** `@Service`.
3. **Methods:** `revoke`, `isRevoked`, `purgeExpired`.
4. **Dependencies:** none — an in-memory `ConcurrentHashMap` of `jti` → expiry.
5. **Called by:** `AuthService.logout` (writes), `JwtAuthenticationFilter` and
   `StompAuthChannelInterceptor` (read).
6. **Calls:** nothing.
7. **Verbal:** "A JWT cannot be un-signed, so logout records the token's `jti` here and both
   authentication paths check it before establishing identity. Entries drop out when the
   token would have expired anyway, and the list can only ever reject a token, never accept
   one, so I am still stateless."

### `JwtAuthenticationFilter.java`
1. **Purpose:** identify the caller on every request.
2. **Annotations:** `@Component`; extends `OncePerRequestFilter`.
3. **Methods:** `doFilterInternal`, `authenticate`.
4. **Dependencies:** `JwtService`, `CustomUserDetailsService`, `TokenRevocationService`.
5. **Called by:** the security filter chain.
6. **Calls:** `JwtService`, `TokenRevocationService`, `CustomUserDetailsService`,
   `SecurityContextHolder`.
7. **Verbal:** "Reads the Bearer header, checks the token is genuine and has not been logged
   out, and if it is good puts an `AuthenticatedUser` into the SecurityContext. It never
   rejects — that is the config's job."

### `AuthController.java`
1. **Purpose:** the two public endpoints.
2. **Annotations:** `@RestController`, `@RequestMapping("/api/auth")`, `@PostMapping`,
   `@Valid`, `@RequestBody`, `@Operation`, `@SecurityRequirements`.
3. **Methods:** `login` (200), `register` (201).
4. **Dependencies:** `AuthService`.
5. **Called by:** clients.
6. **Calls:** `AuthService`.
7. **Verbal:** "Thin pass-through. It validates the DTO and returns the service's result —
   no logic here."

### `AuthService.java`
1. **Purpose:** registration and login rules.
2. **Annotations:** `@Service`, `@Transactional`, `@Transactional(readOnly = true)`.
3. **Methods:** `register`, `login`.
4. **Dependencies:** `CustomUserDetailsService`, `PasswordEncoder`, `JwtService`,
   `AppUserRepository`, `RoleRepository`.
5. **Called by:** `AuthController`.
6. **Calls:** repositories and BCrypt for registration; `AuthenticationManager` then
   `JwtService` for login.
7. **Verbal:** "Registration checks the email is free, hashes the password with BCrypt, looks
   up the USER role itself so no request can ask for SUPPORT, and saves. Login hands the
   email and password to Spring Security's `AuthenticationManager` — that is what actually
   verifies them — and on success issues a JWT. Unknown email and wrong password give the
   same 401 message so the endpoint cannot be used to discover which emails exist."

### `IncidentController.java`
1. **Purpose:** APIs 3–7.
2. **Annotations:** `@RestController`, `@RequestMapping("/api/incidents")`, `@PostMapping`,
   `@GetMapping`, `@PatchMapping`, `@PathVariable`, `@AuthenticationPrincipal`.
3. **Methods:** `classify`, `create`, `detail`, `sendMessage`, `markRead`.
4. **Dependencies:** `ClassificationService`, `IncidentService`, `IncidentMessageService`.
5. **Called by:** clients.
6. **Calls:** those three services.
7. **Verbal:** "Five endpoints. For messages it saves first and broadcasts second, so the
   database is always the source of truth."

### `IncidentService.java`
1. **Purpose:** dashboard, incident creation with assignment, incident details.
2. **Annotations:** `@Service`, `@Transactional`.
3. **Methods:** `userDashboard`, `createIncident`, `incidentDetail`, `writeLog`,
   `toMessageResponse`.
4. **Dependencies:** six repositories plus `PriorityService`, `AssignmentService`,
   `IncidentAccessService`, `RealtimeNotifier`.
5. **Called by:** `IncidentController`, `UserDashboardController`, `SupportService`.
6. **Calls:** everything above.
7. **Verbal:** "The heart of the project. `createIncident` does severity validation, team
   resolution, priority, insert, code stamping, assignment and history in one transaction,
   then broadcasts after commit."

### `AssignmentService.java`
1. **Purpose:** pick the best engineer.
2. **Annotations:** `@Service`.
3. **Methods:** `selectEngineer`, `toStoredScore`, and the private factor calculations.
4. **Dependencies:** `AppUserRepository`, `IncidentAssignmentRepository`,
   `IncidentSimilarityService`.
5. **Called by:** `IncidentService.createIncident`.
6. **Calls:** repositories and the similarity service.
7. **Verbal:** "Scores each eligible engineer on four weighted factors and returns the
   highest, with a deterministic tie-break. Only the final score is persisted."

### `IncidentAccessService.java`
1. **Purpose:** per-incident authorisation.
2. **Annotations:** `@Service`, `@Transactional(readOnly = true)`.
3. **Methods:** `requireViewable`, `requireConversationParticipant`, `requireModifiable`,
   `canView`, `isReporter`, `isAssignedEngineer`, `currentAssignment`.
4. **Dependencies:** `IncidentRepository`, `IncidentAssignmentRepository`.
5. **Called by:** `IncidentService`, `IncidentMessageService`, `SupportService`,
   `StompAuthChannelInterceptor`.
6. **Calls:** the two repositories.
7. **Verbal:** "One place for data-level authorisation, with three strictness levels, used
   by both REST and WebSocket so they can never disagree."

### `SupportService.java`
1. **Purpose:** support dashboard and the incident update workflow.
2. **Annotations:** `@Service`, `@Transactional`.
3. **Methods:** `dashboard`, `updateIncident`, `assist`, `broadcastIncidentUpdate`, plus
   private analytics helpers.
4. **Dependencies:** `IncidentRepository`, `IncidentAssignmentRepository`,
   `IncidentAccessService`, `IncidentService`, `OpsAiService`, `RealtimeNotifier`.
5. **Called by:** `SupportController`.
6. **Calls:** the above.
7. **Verbal:** "Analytics are computed from existing rows — there is no analytics table. The
   update method checks the transition with the enum and enforces that a root cause and
   resolution exist before RESOLVED. `assist` resolves the OpsAI action string and rejects
   an unknown one with 400 before the access check."

### `DeterministicOpsAiService.java`
1. **Purpose:** the five OpsAI actions.
2. **Annotations:** `@Service`, `@Transactional(readOnly = true)`; implements `OpsAiService`.
3. **Methods:** `assist` and the five private actions, plus `rankedHistory`.
4. **Dependencies:** `IncidentRepository`, `IncidentMessageRepository`,
   `IncidentSimilarityService`.
5. **Called by:** `SupportService.assist`.
6. **Calls:** the repositories and the similarity scorer.
7. **Verbal:** "No external AI. Everything is computed from Oracle data with weighted
   similarity, and it never writes."

### `GlobalExceptionHandler.java`
1. **Purpose:** one consistent error response.
2. **Annotations:** `@RestControllerAdvice`, `@ExceptionHandler`.
3. **Methods:** eight handlers plus a private `build`.
4. **Dependencies:** `ApiErrorResponse`.
5. **Called by:** Spring, whenever a controller or service throws.
6. **Calls:** nothing.
7. **Verbal:** "Turns every exception into the same JSON. Typed exceptions carry their own
   status; the last-resort handler logs the real cause and returns a safe 500."

### `AppUser.java`
1. **Purpose:** maps the `RESOLVE_USER` table.
2. **Annotations:** `@Entity`, `@Table(name = "\"USER\"")`, `@Id`, `@GeneratedValue`,
   `@Column`, `@ManyToOne`, `@JoinColumn`.
3. **Methods:** getters and setters only.
4. **Dependencies:** `Role`, `TeamService`.
5. **Called by:** repositories and services.
6. **Calls:** nothing.
7. **Verbal:** "One row of the USER table. Named `AppUser` to avoid clashing with Spring
   Security's `User`. It maps to `RESOLVE_USER`, prefixed because the Oracle schema is shared."

---

# PART 9 — FINAL REVISION SHEET

## Controllers (5)

| Class | Base path | Endpoints |
|---|---|---|
| `AuthController` | `/api/auth` | login, register |
| `UserDashboardController` | `/api/user` | dashboard |
| `IncidentController` | `/api/incidents` | classify, create, detail, messages, mark read |
| `SupportController` | `/api/support` | dashboard, update, ops-ai |
| `IncidentWebSocketController` | — | 2 STOMP destinations (not REST) |

## The 11 endpoints

| # | Method | Path | Role |
|---|---|---|---|
| 1 | POST | `/api/auth/login` | public |
| 2 | GET | `/api/user/dashboard` | USER |
| 3 | POST | `/api/incidents/classify` | USER |
| 4 | POST | `/api/incidents` | USER |
| 5 | GET | `/api/incidents/{incidentId}` | USER/SUPPORT |
| 6 | POST | `/api/incidents/{incidentId}/messages` | participant |
| 7 | PATCH | `/api/incidents/{incidentId}/messages/read` | participant |
| 8 | GET | `/api/support/dashboard` | SUPPORT |
| 9 | PATCH | `/api/support/incidents/{incidentId}` | assigned SUPPORT |
| 10 | POST | `/api/support/incidents/{incidentId}/ops-ai` | assigned SUPPORT |
| 11 | POST | `/api/auth/register` | public |

## Service-layer classes

13 classes carry `@Service`. Ten of them are in `service/`; the other three live where they
belong: `CustomUserDetailsService` and `JwtService` in `security/`, and
`DeterministicOpsAiService` in `opsai/`. `TextSimilarity` is a static utility with no
annotation, `Availability` is an enum, and `OpsAiService` is an interface.

| Class | One line |
|---|---|
| `AuthService` | register (hash + USER role) and login (verify + issue JWT) |
| `IncidentService` | dashboard, create+assign, incident details, writes history |
| `IncidentAccessService` | per-incident authorisation, three strictness levels |
| `IncidentMessageService` | send message, mark read |
| `SupportService` | support dashboard, status/root cause/resolution update, OpsAI entry |
| `AssignmentService` | four-factor scoring, picks the engineer |
| `ClassificationService` | suggests service, category, severity |
| `PriorityService` | severity → priority (P2 never used) |
| `IncidentSimilarityService` | weighted similarity between two incidents |
| `TextSimilarity` | tokenising, Jaccard overlap, exact match (static utility) |
| `RealtimeNotifier` | holds the three topic names, sends broadcasts |
| `OpsAiService` | the interface, in `opsai/` |
| `DeterministicOpsAiService` | the five OpsAI actions, read-only, in `opsai/` |
| `CustomUserDetailsService` | loads a user by email for Spring Security, in `security/` |
| `JwtService` | signs and verifies tokens, in `security/` |

## Repositories (7)

| Repository | Notable methods |
|---|---|
| `AppUserRepository` | `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`, `findSupportEngineersByTeam` |
| `RoleRepository` | `findByRoleNameIgnoreCase` |
| `TeamServiceRepository` | `findByServiceNameIgnoreCase` |
| `IncidentRepository` | `findByReportedBy_UserId...`, `findHistoricalExcluding`, `findAllNewestFirst` |
| `IncidentAssignmentRepository` | `findCurrentByIncidentId`, `findCurrentIncidentsForSupportUser`, `findActiveIncidentsForSupportUser`, `findTopBySupportUser_UserId...` |
| `IncidentMessageRepository` | `findByIncident_IncidentIdOrderBySentAtAsc...`, `findByMessageIdInAndIncident_IncidentId` |
| `IncidentLogRepository` | `findByIncident_IncidentIdOrderByChangedAtAsc...` |

## The five enums — and where each one lives

There is no `domain` package. Each enum sits with the layer that owns it:

| Enum | Package | Why there |
|---|---|---|
| `IncidentStatus` | `entity` | the allowed values of `RESOLVE_INCIDENT.status`, plus the transition rule |
| `Severity` | `entity` | the allowed values of `RESOLVE_INCIDENT.severity` |
| `Priority` | `entity` | the allowed values of `RESOLVE_INCIDENT.priority`, plus `fromStored()` and `workloadWeight()` |
| `RoleName` | `security` | drives `hasRole(...)` in `SecurityConfig` and the authorisation checks |
| `Availability` | `service` | derived from live workload; deliberately has **no** database column |

`RoleName` is a `final class` of `String` constants rather than an enum, because Spring
Security's `hasRole(...)` takes a String.

## Entities → tables (7)

`Role`→`RESOLVE_ROLE` · `AppUser`→`RESOLVE_USER` · `TeamService`→`RESOLVE_TEAM_SERVICE` · `Incident`→`RESOLVE_INCIDENT` ·
`IncidentAssignment`→`RESOLVE_INCIDENT_ASSIGNMENT` · `IncidentMessage`→`RESOLVE_INCIDENT_MESSAGE` ·
`IncidentLog`→`RESOLVE_INCIDENT_LOGS`

## Key DTOs

| Request | Response |
|---|---|
| `RegisterRequest`, `LoginRequest` | `RegisterResponse`, `LoginResponse` |
| `ClassifyRequest`, `CreateIncidentRequest` | `ClassifyResponse`, `CreateIncidentResponse` |
| `SendMessageRequest`, `MarkReadRequest` | `MessageResponse`, `MarkReadResponse` |
| `SupportIncidentUpdateRequest`, `OpsAiRequest` | `SupportIncidentUpdateResponse`, `OpsAiResponse` |
| — | `UserDashboardResponse`, `SupportDashboardResponse`, `IncidentDetailResponse` |
| — | OpsAI results: `SummarizeResult`, `SimilarIncidentsResult`, `AnalyzeResult`, `RootCauseResult`, `ResolutionResult` |

## Configuration classes

| Class | One line |
|---|---|
| `SecurityConfig` | stateless JWT security, URL rules, CORS, BCrypt and AuthenticationManager beans |
| `WebSocketConfig` | `/ws` endpoint, `/topic` broker, `/app` prefix, STOMP interceptor |
| `OpenApiConfig` | Swagger metadata and the bearer Authorize button |

## Relationships

All **Many-to-One**. **No 1:1, no M:M.**
`RESOLVE_ROLE→USER`, `RESOLVE_TEAM_SERVICE→USER`, `RESOLVE_TEAM_SERVICE→RESOLVE_INCIDENT`, `USER→RESOLVE_INCIDENT`,
`RESOLVE_INCIDENT→ASSIGNMENT`, `USER→ASSIGNMENT`, `RESOLVE_INCIDENT→MESSAGE`, `USER→MESSAGE`,
`RESOLVE_INCIDENT→LOGS`.

`RESOLVE_INCIDENT_LOGS` deliberately has **no** link to `USER`. A log row records only *which status
the incident entered and when* — `LOG_ID`, `INCIDENT_ID`, `STATUS`, `CHANGED_AT` — so there is
no `CHANGED_BY`, and no `OLD_STATUS`/`NEW_STATUS` pair either. The previous status is simply
the `STATUS` of the previous row.

## Authentication flow

`POST /api/auth/login` → `AuthService` → `AuthenticationManager` →
`DaoAuthenticationProvider` → `CustomUserDetailsService` → `AppUserRepository` →
BCrypt `matches` → back to `AuthService` → `JwtService.generateToken` → token.
Then every request: `JwtAuthenticationFilter` → `JwtService.extractIdentity` →
`TokenRevocationService.isRevoked?` → `AuthenticatedUser` in SecurityContext.

`POST /api/auth/logout` → `AuthService.logout` → `TokenRevocationService.revoke(jti)`.
That same token now fails the revocation check on both REST and STOMP → 401.

## Authorization flow

URL level in `SecurityConfig` (`permitAll` / `hasRole` / `authenticated`), then data level in
`IncidentAccessService` (`requireViewable` → `requireConversationParticipant` →
`requireModifiable`).

## Incident lifecycle

`REPORTED → ASSIGNED → IN PROGRESS → ROOT CAUSE IDENTIFIED → RESOLUTION IN PROGRESS →
RESOLVED`, one step at a time, enforced by `IncidentStatus.canTransitionTo`, every change
logged to `RESOLVE_INCIDENT_LOGS`.

## Assignment algorithm

Experience 40% + Availability 25% + Workload 20% + Fairness 15%, each 0–100; highest total
among eligible engineers on the owning team wins; final score stored as `NUMBER(5,2)`.

## WebSocket flow

CONNECT (JWT) → `StompAuthChannelInterceptor` → SUBSCRIBE checked with `requireViewable` →
SEND `/app/...` → `IncidentWebSocketController` → `IncidentMessageService` → Oracle → commit
→ `RealtimeNotifier` → `/topic/...` → other browser.

## OpsAI flow

`POST .../ops-ai` → action enum → `SupportService.assist` → `requireModifiable` →
`DeterministicOpsAiService` → history + conversation from Oracle → similarity scoring →
result. **Read-only.**

## Exception flow

Business code throws `ApiException` subclass → `GlobalExceptionHandler` → `ApiErrorResponse`
JSON. Security failures before the controller → `RestAuthenticationEntryPoint` (401) /
`RestAccessDeniedHandler` (403). Unknown URL → 404. Anything else → logged, safe 500.

## Status codes at a glance

400 validation · 401 no/bad token · 403 wrong role or not yours · 404 missing resource or
unknown URL · 409 duplicate email or illegal transition · 500 unexpected.

---

## Final tips for the evaluation

1. **Start with the flow, not the file.** "A request arrives, security identifies the user,
   the controller validates, the service applies rules, the repository talks to Oracle."
2. **Always be able to name the layer.** If asked "where does this happen?", answer with the
   class name.
3. **Be honest about what is not implemented.** Automated tests, refresh tokens, password
   reset, manual reassignment, pagination — saying "not implemented" confidently is better
   than inventing. Note that logout *is* implemented (Feature 2A), and refresh tokens are
   deliberately absent rather than missing.
4. **Know your three strongest talking points:**
   - the four-factor assignment algorithm,
   - deterministic OpsAI with no external provider,
   - the save-then-broadcast rule that keeps Oracle the source of truth.
