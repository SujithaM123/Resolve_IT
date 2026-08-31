package com.resolveit.controller;

import com.resolveit.dto.LoginRequest;
import com.resolveit.dto.LoginResponse;
import com.resolveit.dto.LogoutResponse;
import com.resolveit.dto.RegisterRequest;
import com.resolveit.dto.RegisterResponse;
import com.resolveit.security.BearerTokens;
import com.resolveit.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Log in and receive a JWT",
            description = "Authenticates both USER and SUPPORT accounts. The role comes from the account.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "Register a USER account",
            description = "Creates a self-service USER account. The role is always USER; "
                    + "SUPPORT accounts are provisioned outside the public API. "
                    + "Log in through /api/auth/login afterwards to obtain a JWT.")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Log out and revoke the current JWT",
            description = "Revokes the access token in the Authorization header. That exact token "
                    + "is immediately refused by the REST API and by WebSocket/STOMP CONNECT; "
                    + "reusing it returns 401. Log in again to obtain a new token. "
                    + "There is no refresh token in this system.")
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        return ResponseEntity.ok(authService.logout(BearerTokens.resolve(authorization)));
    }
}
