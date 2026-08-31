package com.resolveit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenRevocationService revocationService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService,
                                   TokenRevocationService revocationService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.revocationService = revocationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = BearerTokens.resolve(request.getHeader(BearerTokens.HEADER));
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Order matters: a logged-out token must be discarded before it can ever
            // populate the SecurityContext. Leaving the context empty makes the request
            // anonymous, and RestAuthenticationEntryPoint answers 401.
            jwtService.extractIdentity(token)
                    .filter(identity -> !revocationService.isRevoked(identity.tokenId()))
                    .ifPresent(identity -> authenticate(identity.subject(), request));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String email, HttpServletRequest request) {
        try {
            AuthenticatedUser user = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ex) {

            SecurityContextHolder.clearContext();
        }
    }
}
