package com.dtcc.intern.demo.controller;

import com.dtcc.intern.demo.dto.CreateSupportUserRequest;
import com.dtcc.intern.demo.dto.CreateSupportUserResponse;
import com.dtcc.intern.demo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support-users")
public class SupportUserController {

    private final AuthService authService;

    public SupportUserController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Create a SUPPORT account (SUPER_ADMIN only)",
            description = "Provisions a support engineer. The role is always SUPPORT and is never "
                    + "read from the request body. Requires a SUPER_ADMIN bearer token; any other "
                    + "role receives 403. The response never contains the password or its hash.")
    @PostMapping
    public ResponseEntity<CreateSupportUserResponse> createSupportUser(
            @Valid @RequestBody CreateSupportUserRequest request) {

        CreateSupportUserResponse response = authService.createSupportUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
