package com.dtcc.intern.demo.service;

import com.dtcc.intern.demo.dto.IncidentDetailResponse.AssignedSupport;
import com.dtcc.intern.demo.dto.CreateIncidentRequest;
import com.dtcc.intern.demo.dto.CreateIncidentResponse;
import com.dtcc.intern.demo.dto.IncidentDetailResponse;
import com.dtcc.intern.demo.dto.MessageResponse;
import com.dtcc.intern.demo.dto.IncidentDetailResponse.StatusHistoryEntry;
import com.dtcc.intern.demo.dto.SupportIncidentUpdateResponse;
import com.dtcc.intern.demo.dto.UserDashboardResponse;
import com.dtcc.intern.demo.dto.UserDashboardResponse.UserIncidentSummary;
import com.dtcc.intern.demo.exception.BadRequestException;
import com.dtcc.intern.demo.exception.NotFoundException;
import com.dtcc.intern.demo.entity.AppUser;
import com.dtcc.intern.demo.entity.Incident;
import com.dtcc.intern.demo.entity.IncidentAssignment;
import com.dtcc.intern.demo.entity.IncidentLog;
import com.dtcc.intern.demo.entity.IncidentMessage;
import com.dtcc.intern.demo.entity.IncidentStatus;
import com.dtcc.intern.demo.entity.Priority;
import com.dtcc.intern.demo.entity.Severity;
import com.dtcc.intern.demo.entity.TeamService;
import com.dtcc.intern.demo.repository.AppUserRepository;
import com.dtcc.intern.demo.repository.IncidentAssignmentRepository;
import com.dtcc.intern.demo.repository.IncidentLogRepository;
import com.dtcc.intern.demo.repository.IncidentMessageRepository;
import com.dtcc.intern.demo.repository.IncidentRepository;
import com.dtcc.intern.demo.repository.TeamServiceRepository;
import com.dtcc.intern.demo.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentService {

    private static final String CODE_PREFIX = "INC-";

    private static final long CODE_OFFSET = 1000L;

    private final IncidentRepository incidentRepository;
    private final IncidentAssignmentRepository assignmentRepository;
    private final IncidentLogRepository logRepository;
    private final IncidentMessageRepository messageRepository;
    private final TeamServiceRepository teamServiceRepository;
    private final AppUserRepository appUserRepository;
    private final PriorityService priorityService;
    private final AssignmentService assignmentService;
    private final IncidentAccessService accessService;
    private final RealtimeNotifier notifier;

    public IncidentService(IncidentRepository incidentRepository,
                           IncidentAssignmentRepository assignmentRepository,
                           IncidentLogRepository logRepository,
                           IncidentMessageRepository messageRepository,
                           TeamServiceRepository teamServiceRepository,
                           AppUserRepository appUserRepository,
                           PriorityService priorityService,
                           AssignmentService assignmentService,
                           IncidentAccessService accessService,
                           RealtimeNotifier notifier) {
        this.incidentRepository = incidentRepository;
        this.assignmentRepository = assignmentRepository;
        this.logRepository = logRepository;
        this.messageRepository = messageRepository;
        this.teamServiceRepository = teamServiceRepository;
        this.appUserRepository = appUserRepository;
        this.priorityService = priorityService;
        this.assignmentService = assignmentService;
        this.accessService = accessService;
        this.notifier = notifier;
    }

    @Transactional(readOnly = true)
    public UserDashboardResponse userDashboard(AuthenticatedUser caller) {
        List<UserIncidentSummary> incidents = incidentRepository
                .findByReportedBy_UserIdOrderByCreatedAtDescIncidentIdDesc(caller.getUserId())
                .stream()
                .map(incident -> new UserIncidentSummary(
                        incident.getIncidentId(),
                        incident.getIncidentCode(),
                        incident.getTitle(),
                        incident.getStatus(),
                        incident.getSeverity(),
                        incident.getPriority(),
                        incident.getCreatedAt()))
                .toList();

        return new UserDashboardResponse(caller.getUserId(), caller.getName(), incidents);
    }

    @Transactional
    public CreateIncidentResponse createIncident(CreateIncidentRequest request, AuthenticatedUser caller) {
        Severity severity = Severity.fromStored(request.severity())
                .orElseThrow(() -> new BadRequestException(
                        "Severity must be one of LOW, MEDIUM, HIGH, CRITICAL"));

        TeamService team = teamServiceRepository.findByServiceNameIgnoreCase(request.service().trim())
                .orElseThrow(() -> new BadRequestException(
                        "Unknown service '" + request.service().trim() + "'"));

        AppUser reporter = appUserRepository.findById(caller.getUserId())
                .orElseThrow(() -> new NotFoundException("Reporting user not found"));

        Priority priority = priorityService.determinePriority(severity);
        LocalDateTime now = LocalDateTime.now();

        Incident incident = new Incident();
        incident.setIncidentCode(temporaryCode());
        incident.setTitle(request.title().trim());
        incident.setDescription(request.description());
        incident.setCategory(request.category().trim());
        incident.setSeverity(severity.name());
        incident.setPriority(priority.name());
        incident.setStatus(IncidentStatus.REPORTED.stored());
        incident.setReportedBy(reporter);
        incident.setTeam(team);
        incident.setCreatedAt(now);

        incident = incidentRepository.saveAndFlush(incident);
        incident.setIncidentCode(CODE_PREFIX + (CODE_OFFSET + incident.getIncidentId()));

        writeLog(incident, IncidentStatus.REPORTED, now);

        Optional<AssignmentService.Candidate> selected = assignmentService.selectEngineer(incident);

        Long assignedUserId = null;
        String assignedName = null;

        if (selected.isPresent()) {
            AssignmentService.Candidate candidate = selected.get();
            AppUser engineer = candidate.engineer();

            IncidentAssignment assignment = new IncidentAssignment();
            assignment.setIncident(incident);
            assignment.setSupportUser(engineer);
            assignment.setAssignmentScore(AssignmentService.toStoredScore(candidate.finalScore()));
            assignment.setAssignedAt(now);
            assignmentRepository.save(assignment);

            incident.setStatus(IncidentStatus.ASSIGNED.stored());
            writeLog(incident, IncidentStatus.ASSIGNED, now);

            assignedUserId = engineer.getUserId();
            assignedName = engineer.getName();
        }

        Incident persisted = incidentRepository.save(incident);
        notifyAssignmentAfterCommit(persisted);

        return new CreateIncidentResponse(
                persisted.getIncidentId(),
                persisted.getIncidentCode(),
                persisted.getTitle(),
                persisted.getStatus(),
                persisted.getSeverity(),
                persisted.getPriority(),
                assignedUserId,
                assignedName,
                persisted.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse incidentDetail(Long incidentId, AuthenticatedUser caller) {
        Incident incident = accessService.requireViewable(incidentId, caller);

        AssignedSupport assignedSupport = accessService.currentAssignment(incidentId)
                .map(assignment -> new AssignedSupport(
                        assignment.getSupportUser().getUserId(),
                        assignment.getSupportUser().getName()))
                .orElse(null);

        List<MessageResponse> messages = messageRepository
                .findByIncident_IncidentIdOrderBySentAtAscMessageIdAsc(incidentId)
                .stream()
                .map(IncidentService::toMessageResponse)
                .toList();

        List<StatusHistoryEntry> statusHistory = logRepository
                .findByIncident_IncidentIdOrderByChangedAtAscLogIdAsc(incidentId)
                .stream()
                .map(entry -> new StatusHistoryEntry(
                        entry.getStatus(),
                        entry.getChangedAt()))
                .toList();

        return new IncidentDetailResponse(
                incident.getIncidentId(),
                incident.getIncidentCode(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getTeam() == null ? null : incident.getTeam().getServiceName(),
                incident.getCategory(),
                incident.getSeverity(),
                incident.getPriority(),
                incident.getStatus(),
                assignedSupport,
                incident.getRootCause(),
                incident.getResolution(),
                incident.getCreatedAt(),
                incident.getResolvedAt(),
                messages,
                statusHistory);
    }

    public static MessageResponse toMessageResponse(IncidentMessage message) {
        return new MessageResponse(
                message.getMessageId(),
                message.getIncident().getIncidentId(),
                message.getSender().getUserId(),
                message.getSender().getName(),
                message.getSender().getRole().getRoleName().trim().toUpperCase(),
                message.getMessageText(),
                message.getSentAt(),
                Boolean.TRUE.equals(message.getIsRead()));
    }

    void writeLog(Incident incident, IncidentStatus status, LocalDateTime changedAt) {
        IncidentLog entry = new IncidentLog();
        entry.setIncident(incident);
        entry.setStatus(status.stored());
        entry.setChangedAt(changedAt);
        logRepository.save(entry);
    }

    private void notifyAssignmentAfterCommit(Incident incident) {
        SupportIncidentUpdateResponse payload = new SupportIncidentUpdateResponse(
                incident.getIncidentId(),
                incident.getIncidentCode(),
                incident.getStatus(),
                incident.getRootCause(),
                incident.getResolution(),
                incident.getResolvedAt());

        Long incidentId = incident.getIncidentId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifier.broadcastAssignment(incidentId, payload);
                }
            });
        } else {
            notifier.broadcastAssignment(incidentId, payload);
        }
    }

    private static String temporaryCode() {
        return ("TMP-" + UUID.randomUUID()).substring(0, 30);
    }
}
