package com.resolveit.security;

/**
 * Single place that knows how a JWT is carried on the wire: the value of an
 * 'Authorization: Bearer <token>' header.
 *
 * Three call sites need this - the REST filter, the STOMP CONNECT interceptor and
 * the logout endpoint - and they must agree exactly, or a token could be revoked
 * under one spelling and still accepted under another.
 */
public final class BearerTokens {

    public static final String HEADER = "Authorization";

    private static final String PREFIX = "Bearer ";

    private BearerTokens() {
    }

    /** Returns the bare token, or null when the header is absent or not a Bearer header. */
    public static String resolve(String headerValue) {
        if (headerValue == null || !headerValue.startsWith(PREFIX)) {
            return null;
        }
        String token = headerValue.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
