package com.resolveit.controller;

import com.resolveit.dto.MarkReadRequest;
import com.resolveit.dto.MarkReadResponse;
import com.resolveit.dto.MessageResponse;
import com.resolveit.dto.SendMessageRequest;
import com.resolveit.security.AuthenticatedUser;
import com.resolveit.security.StompAuthChannelInterceptor;
import com.resolveit.service.IncidentMessageService;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class IncidentWebSocketController {

    private final IncidentMessageService messageService;

    public IncidentWebSocketController(IncidentMessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/incidents/{incidentId}/messages")
    public void sendMessage(@DestinationVariable Long incidentId,
                            SendMessageRequest request,
                            StompHeaderAccessor accessor) {

        AuthenticatedUser caller = requireCaller(accessor);
        MessageResponse message = messageService.sendMessage(incidentId, request.messageText(), caller);
        messageService.broadcastMessage(incidentId, message);
    }

    @MessageMapping("/incidents/{incidentId}/read")
    public void markRead(@DestinationVariable Long incidentId,
                         MarkReadRequest request,
                         StompHeaderAccessor accessor) {

        AuthenticatedUser caller = requireCaller(accessor);
        MarkReadResponse response = messageService.markRead(incidentId, request.messageIds(), caller);
        messageService.broadcastReadStatus(incidentId, response);
    }

    private static AuthenticatedUser requireCaller(StompHeaderAccessor accessor) {
        AuthenticatedUser caller = StompAuthChannelInterceptor.currentUser(accessor);
        if (caller == null) {
            throw new MessagingException("Authentication is required");
        }
        return caller;
    }
}
