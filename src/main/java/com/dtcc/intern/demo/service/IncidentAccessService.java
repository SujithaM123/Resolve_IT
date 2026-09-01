package com.dtcc.intern.demo.service;

import com.dtcc.intern.demo.exception.ForbiddenException;
import com.dtcc.intern.demo.exception.NotFoundException;
import com.dtcc.intern.demo.entity.Incident;
import com.dtcc.intern.demo.entity.IncidentAssignment;
import com.dtcc.intern.demo.repository.IncidentAssignmentRepository;
import com.dtcc.intern.demo.repository.IncidentRepository;
import com.dtcc.intern.demo.security.AuthenticatedUser;
import com.dtcc.intern.demo.security.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IncidentAccessService {

    private final IncidentRepository incidentRepository;
    private final IncidentAssignmentRepository assignmentRepository;

    public IncidentAccessService(IncidentRepository incidentRepository,
                                 IncidentAssignmentRepository assignmentRepository) {
        this.incidentRepository = incidentRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public Incident requireViewable(Long incidentId, AuthenticatedUser caller) {
        Incident incident = load(incidentId);
        if (!canView(incident, caller)) {
            throw new ForbiddenException("You are not authorized to access this incident");
        }
        return incident;
    }

    @Transactional(readOnly = true)
    public Incident requireModifiable(Long incidentId, AuthenticatedUser caller) {
        Incident incident = load(incidentId);
        if (!isAssignedEngineer(incident, caller)) {
            throw new ForbiddenException("You are not authorized to modify this incident");
        }
        return incident;
    }

    @Transactional(readOnly = true)
    public Incident requireConversationParticipant(Long incidentId, AuthenticatedUser caller) {
        Incident incident = load(incidentId);
        boolean participant = isReporter(incident, caller) || isAssignedEngineer(incident, caller);
        if (!participant) {
            throw new ForbiddenException("You are not a participant in this incident conversation");
        }
        return incident;
    }

    private Incident load(Long incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
    }

    private boolean canView(Incident incident, AuthenticatedUser caller) {
        if (RoleName.USER.equals(caller.getRole())) {
            return isReporter(incident, caller);
        }
        if (RoleName.SUPPORT.equals(caller.getRole())) {
            return isAssignedEngineer(incident, caller) || isSameTeam(incident, caller);
        }
        return false;
    }

    private boolean isReporter(Incident incident, AuthenticatedUser caller) {
        return incident.getReportedBy() != null
                && incident.getReportedBy().getUserId().equals(caller.getUserId());
    }

    private boolean isAssignedEngineer(Incident incident, AuthenticatedUser caller) {
        if (!RoleName.SUPPORT.equals(caller.getRole())) {
            return false;
        }
        return currentAssignment(incident.getIncidentId())
                .map(assignment -> assignment.getSupportUser().getUserId().equals(caller.getUserId()))
                .orElse(false);
    }

    private static boolean isSameTeam(Incident incident, AuthenticatedUser caller) {
        return caller.getTeamId() != null
                && incident.getTeam() != null
                && incident.getTeam().getTeamId().equals(caller.getTeamId());
    }

    public Optional<IncidentAssignment> currentAssignment(Long incidentId) {
        return assignmentRepository.findCurrentByIncidentId(incidentId);
    }
}
