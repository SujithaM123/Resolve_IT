package com.dtcc.intern.demo.security;

import com.dtcc.intern.demo.exception.ApiException;
import com.dtcc.intern.demo.service.IncidentAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^/topic/incidents/(\\d+)/(messages|updates|read)$");

    /**
     * Clients may only SEND to the application prefix. A SEND addressed straight to
     * a broker destination (/topic/**) would be relayed to every subscriber without
     * ever reaching a @MessageMapping method, skipping the participant check.
     */
    private static final String APP_PREFIX = "/app/";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final IncidentAccessService accessService;
    private final TokenRevocationService revocationService;

    public StompAuthChannelInterceptor(JwtService jwtService,
                                       CustomUserDetailsService userDetailsService,
                                       IncidentAccessService accessService,
                                       TokenRevocationService revocationService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.accessService = accessService;
        this.revocationService = revocationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            authorizeClientSend(accessor);
        }

        return message;
    }

    /**
     * The WebSocket entry point applies exactly the same token rules as the REST
     * filter, revocation included - a token that has been logged out must not be able
     * to open a live channel that would then outlive the logout.
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            log.warn("WebSocket CONNECT rejected - no bearer token presented");
            throw new MessagingException("Authentication is required");
        }

        TokenIdentity identity = jwtService.extractIdentity(token)
                .filter(candidate -> !revocationService.isRevoked(candidate.tokenId()))
                .orElseThrow(() -> {
                    log.warn("WebSocket CONNECT rejected - token is invalid, expired or revoked");
                    return new MessagingException("Authentication is required");
                });

        try {
            AuthenticatedUser user = userDetailsService.loadUserByUsername(identity.subject());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            accessor.setUser(authentication);

            log.info("WebSocket connected for user {} ({})", user.getUserId(), user.getEmail());
        } catch (UsernameNotFoundException ex) {
            log.warn("WebSocket CONNECT rejected - no account for {}", identity.subject());
            throw new MessagingException("Authentication is required");
        }
    }

    /**
     * Only authenticated callers may send, and only to /app/** so that every client
     * frame passes through a controller where the participant rules are enforced.
     */
    private void authorizeClientSend(StompHeaderAccessor accessor) {
        if (currentUser(accessor) == null) {
            throw new MessagingException("Authentication is required");
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(APP_PREFIX)) {
            throw new MessagingException("Unsupported destination");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        AuthenticatedUser caller = currentUser(accessor);
        if (caller == null) {
            throw new MessagingException("Authentication is required");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("Destination is required");
        }

        Matcher matcher = TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            throw new MessagingException("Unsupported destination");
        }

        Long incidentId = Long.valueOf(matcher.group(1));

        try {
            accessService.requireViewable(incidentId, caller);
        } catch (ApiException ex) {
            log.warn("WebSocket SUBSCRIBE to incident {} denied for user {}: {}",
                    incidentId, caller.getUserId(), ex.getMessage());
            throw new MessagingException(ex.getMessage());
        }

        log.info("WebSocket subscription to incident {} accepted for user {}",
                incidentId, caller.getUserId());
    }

    public static AuthenticatedUser currentUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private static String resolveToken(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader(BearerTokens.HEADER);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return BearerTokens.resolve(values.get(0));
    }
}
