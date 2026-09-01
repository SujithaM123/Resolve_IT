package com.dtcc.intern.demo.controller;

import com.dtcc.intern.demo.dto.OpsAiRequest;
import com.dtcc.intern.demo.dto.OpsAiResponse;
import com.dtcc.intern.demo.dto.SupportDashboardResponse;
import com.dtcc.intern.demo.dto.SupportIncidentUpdateRequest;
import com.dtcc.intern.demo.dto.SupportIncidentUpdateResponse;
import com.dtcc.intern.demo.security.AuthenticatedUser;
import com.dtcc.intern.demo.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SupportDashboardResponse> dashboard(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(supportService.dashboard(caller));
    }

    @PatchMapping("/incidents/{incidentId}")
    public ResponseEntity<SupportIncidentUpdateResponse> update(
            @PathVariable Long incidentId,
            @Valid @RequestBody SupportIncidentUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        SupportIncidentUpdateResponse response = supportService.updateIncident(incidentId, request, caller);

        supportService.broadcastIncidentUpdate(response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/incidents/{incidentId}/ops-ai")
    public ResponseEntity<OpsAiResponse> opsAi(@PathVariable Long incidentId,
                                                @Valid @RequestBody OpsAiRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser caller) {

        return ResponseEntity.ok(supportService.assist(incidentId, request.action(), caller));
    }
}
