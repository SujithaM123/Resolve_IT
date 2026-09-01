package com.dtcc.intern.demo.controller;

import com.dtcc.intern.demo.dto.UserDashboardResponse;
import com.dtcc.intern.demo.security.AuthenticatedUser;
import com.dtcc.intern.demo.service.IncidentService;
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
