package com.dtcc.intern.demo.service;

import com.dtcc.intern.demo.dto.MarkReadResponse;
import com.dtcc.intern.demo.dto.MessageResponse;
import com.dtcc.intern.demo.dto.SupportIncidentUpdateResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeNotifier {

    private static final String MESSAGES = "/topic/incidents/%d/messages";
    private static final String UPDATES = "/topic/incidents/%d/updates";
    private static final String READ = "/topic/incidents/%d/read";

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastMessage(Long incidentId, MessageResponse message) {
        messagingTemplate.convertAndSend(MESSAGES.formatted(incidentId), message);
    }

    public void broadcastIncidentUpdate(Long incidentId, SupportIncidentUpdateResponse update) {
        messagingTemplate.convertAndSend(UPDATES.formatted(incidentId), update);
    }

    public void broadcastReadStatus(Long incidentId, MarkReadResponse readStatus) {
        messagingTemplate.convertAndSend(READ.formatted(incidentId), readStatus);
    }

    public void broadcastAssignment(Long incidentId, SupportIncidentUpdateResponse assignment) {
        broadcastIncidentUpdate(incidentId, assignment);
    }
}
