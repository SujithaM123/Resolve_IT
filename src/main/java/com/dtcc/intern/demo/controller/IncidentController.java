package com.dtcc.intern.demo.controller;

import com.dtcc.intern.demo.dto.ClassifyRequest;
import com.dtcc.intern.demo.dto.ClassifyResponse;
import com.dtcc.intern.demo.dto.CreateIncidentRequest;
import com.dtcc.intern.demo.dto.CreateIncidentResponse;
import com.dtcc.intern.demo.dto.IncidentDetailResponse;
import com.dtcc.intern.demo.dto.MarkReadRequest;
import com.dtcc.intern.demo.dto.MarkReadResponse;
import com.dtcc.intern.demo.dto.MessageResponse;
import com.dtcc.intern.demo.dto.SendMessageRequest;
import com.dtcc.intern.demo.security.AuthenticatedUser;
import com.dtcc.intern.demo.service.ClassificationService;
import com.dtcc.intern.demo.service.IncidentMessageService;
import com.dtcc.intern.demo.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/incidents")
public class IncidentController {

    private final ClassificationService classificationService;
    private final IncidentService incidentService;
    private final IncidentMessageService messageService;

    public IncidentController(ClassificationService classificationService,
                              IncidentService incidentService,
                              IncidentMessageService messageService) {
        this.classificationService = classificationService;
        this.incidentService = incidentService;
        this.messageService = messageService;
    }

    @PostMapping("/classify")
    public ResponseEntity<ClassifyResponse> classify(@Valid @RequestBody ClassifyRequest request) {
        return ResponseEntity.ok(classificationService.classify(request.title(), request.description()));
    }

    @PostMapping
    public ResponseEntity<CreateIncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser caller) {
        CreateIncidentResponse response = incidentService.createIncident(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{incidentId}")
    public ResponseEntity<IncidentDetailResponse> detail(@PathVariable Long incidentId,
                                                          @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(incidentService.incidentDetail(incidentId, caller));
    }

    @PostMapping("/{incidentId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(@PathVariable Long incidentId,
                                                        @Valid @RequestBody SendMessageRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser caller) {

        MessageResponse message = messageService.sendMessage(incidentId, request.messageText(), caller);
        messageService.broadcastMessage(incidentId, message);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @PatchMapping("/{incidentId}/messages/read")
    public ResponseEntity<MarkReadResponse> markRead(@PathVariable Long incidentId,
                                                      @Valid @RequestBody MarkReadRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser caller) {

        MarkReadResponse response = messageService.markRead(incidentId, request.messageIds(), caller);
        messageService.broadcastReadStatus(incidentId, response);
        return ResponseEntity.ok(response);
    }
}
