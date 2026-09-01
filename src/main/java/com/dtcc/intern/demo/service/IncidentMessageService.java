package com.dtcc.intern.demo.service;

import com.dtcc.intern.demo.dto.MarkReadResponse;
import com.dtcc.intern.demo.dto.MessageResponse;
import com.dtcc.intern.demo.exception.BadRequestException;
import com.dtcc.intern.demo.exception.NotFoundException;
import com.dtcc.intern.demo.entity.AppUser;
import com.dtcc.intern.demo.entity.Incident;
import com.dtcc.intern.demo.entity.IncidentMessage;
import com.dtcc.intern.demo.repository.AppUserRepository;
import com.dtcc.intern.demo.repository.IncidentMessageRepository;
import com.dtcc.intern.demo.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class IncidentMessageService {

    private final IncidentMessageRepository messageRepository;
    private final AppUserRepository appUserRepository;
    private final IncidentAccessService accessService;
    private final RealtimeNotifier notifier;

    public IncidentMessageService(IncidentMessageRepository messageRepository,
                                  AppUserRepository appUserRepository,
                                  IncidentAccessService accessService,
                                  RealtimeNotifier notifier) {
        this.messageRepository = messageRepository;
        this.appUserRepository = appUserRepository;
        this.accessService = accessService;
        this.notifier = notifier;
    }

    @Transactional
    public MessageResponse sendMessage(Long incidentId, String messageText, AuthenticatedUser caller) {
        if (messageText == null || messageText.isBlank()) {
            throw new BadRequestException("Message text must not be blank");
        }

        Incident incident = accessService.requireConversationParticipant(incidentId, caller);
        AppUser sender = appUserRepository.findById(caller.getUserId())
                .orElseThrow(() -> new NotFoundException("Sender not found"));

        IncidentMessage message = new IncidentMessage();
        message.setIncident(incident);
        message.setSender(sender);
        message.setMessageText(messageText);
        message.setSentAt(LocalDateTime.now());

        message.setIsRead(false);

        IncidentMessage saved = messageRepository.save(message);
        return IncidentService.toMessageResponse(saved);
    }

    @Transactional
    public MarkReadResponse markRead(Long incidentId, List<Long> messageIds, AuthenticatedUser caller) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new BadRequestException("messageIds must contain at least one message ID");
        }

        accessService.requireConversationParticipant(incidentId, caller);

        List<IncidentMessage> messages =
                messageRepository.findByMessageIdInAndIncident_IncidentId(messageIds, incidentId);

        List<Long> updated = new ArrayList<>();
        for (IncidentMessage message : messages) {
            boolean ownMessage = message.getSender().getUserId().equals(caller.getUserId());
            if (ownMessage || Boolean.TRUE.equals(message.getIsRead())) {
                continue;
            }
            message.setIsRead(true);
            updated.add(message.getMessageId());
        }

        messageRepository.saveAll(messages);

        return new MarkReadResponse(incidentId, updated, "READ");
    }

    public void broadcastMessage(Long incidentId, MessageResponse message) {
        notifier.broadcastMessage(incidentId, message);
    }

    public void broadcastReadStatus(Long incidentId, MarkReadResponse readStatus) {
        notifier.broadcastReadStatus(incidentId, readStatus);
    }
}
