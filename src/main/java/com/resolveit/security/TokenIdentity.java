package com.resolveit.security;

import java.time.Instant;

/**
 * The three things the security layer needs out of a verified JWT:
 * who it is for, which token it is, and when it stops mattering.
 *
 * The tokenId is the standard 'jti' claim, and it is what the revocation list is
 * keyed on - never the token string itself, which is a live bearer credential.
 */
public record TokenIdentity(String subject, String tokenId, Instant expiresAt) {
}
