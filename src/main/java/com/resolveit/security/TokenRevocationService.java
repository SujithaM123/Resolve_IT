package com.resolveit.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side revocation list for issued access tokens - the mechanism behind
 * POST /api/auth/logout.
 *
 * A JWT cannot be un-signed, so "logging out" means remembering that one specific
 * token is no longer honoured. This holds the 'jti' of every logged-out token until
 * that token's own expiry, at which point the entry is dead weight and is dropped.
 * Memory is therefore bounded by the number of logouts inside one token lifetime,
 * not by uptime.
 *
 * This does NOT reintroduce HTTP sessions: nothing here is a session, Spring Security
 * stays STATELESS, and a request still authenticates purely from its own token. The
 * list only ever subtracts - it can reject a token, never accept one.
 *
 * Deliberately in memory. The alternative, a RESOLVE_ table, would need a DDL change
 * against a schema this application shares with other projects, and this deployment is
 * a single node. The trade-off: a restart clears the list, so a token logged out
 * shortly before a restart becomes usable again for the remainder of its 8 hours.
 * The class is the whole seam - backing it with a table or Redis is a one-class swap,
 * with no change to the filter, the interceptor or the endpoint.
 */
@Service
public class TokenRevocationService {

    /** Sweep only once the list is big enough to be worth sweeping. */
    private static final int SWEEP_THRESHOLD = 256;

    /** jti -> the moment that token expires on its own. */
    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    /**
     * Marks one token as no longer usable. Revoking an already-expired token is a
     * no-op: it is rejected by the signature/expiry check anyway, so storing it would
     * only leak memory.
     */
    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            return;
        }
        if (revokedUntil.size() >= SWEEP_THRESHOLD) {
            purgeExpired();
        }
        revokedUntil.put(tokenId, expiresAt);
    }

    /**
     * Fail closed: a token that carries no jti cannot be checked against this list,
     * so it is treated as revoked rather than trusted.
     */
    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return true;
        }
        Instant expiresAt = revokedUntil.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(Instant.now())) {
            // Past its own expiry; the token is dead for a different reason now.
            revokedUntil.remove(tokenId, expiresAt);
            return false;
        }
        return true;
    }

    /** Visible for tests: how many revocations are currently being tracked. */
    int trackedCount() {
        return revokedUntil.size();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revokedUntil.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
    }
}
