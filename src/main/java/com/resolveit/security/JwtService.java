package com.resolveit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(@Value("${resolveit.security.jwt.secret}") String secret,
                      @Value("${resolveit.security.jwt.expiration-minutes:480}") long expirationMinutes) {
        this.signingKey = buildKey(secret);
        this.expirationMillis = expirationMinutes * 60_000L;
    }

    private static SecretKey buildKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (DecodingException | IllegalArgumentException ex) {

            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "resolveit.security.jwt.secret must provide at least 256 bits of key material");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Every token gets a unique 'jti'. It is what POST /api/auth/logout revokes, so
     * logging out on one device cannot invalidate a token issued to another.
     */
    public String generateToken(AuthenticatedUser user) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claims(Map.of(
                        CLAIM_USER_ID, user.getUserId(),
                        CLAIM_ROLE, user.getRole(),
                        CLAIM_NAME, user.getName()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(signingKey)
                .compact();
    }

    public Optional<String> extractSubject(String token) {
        return parse(token).map(Claims::getSubject);
    }

    /**
     * Verifies the token and returns everything the security layer needs from it.
     *
     * Empty means "do not authenticate this request" - bad signature, expired, or
     * missing the jti/expiry a revocation check depends on. That last case is
     * deliberate: a token this service cannot identify cannot be checked against the
     * revocation list, so it is refused rather than trusted.
     */
    public Optional<TokenIdentity> extractIdentity(String token) {
        return parse(token).flatMap(claims -> {
            String subject = claims.getSubject();
            String tokenId = claims.getId();
            Date expiration = claims.getExpiration();

            if (subject == null || subject.isBlank() || tokenId == null || tokenId.isBlank()
                    || expiration == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenIdentity(subject, tokenId, Instant.ofEpochMilli(expiration.getTime())));
        });
    }

    private Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
