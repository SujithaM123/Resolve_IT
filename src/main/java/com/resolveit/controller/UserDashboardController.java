package com.resolveit.controller;

import com.resolveit.dto.UserDashboardResponse;
import com.resolveit.security.AuthenticatedUser;
import com.resolveit.service.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserDashboardController {

    private final IncidentService incidentService;

    public UserDashboardController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<UserDashboardResponse> dashboard(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(incidentService.userDashboard(caller));
    }
}
