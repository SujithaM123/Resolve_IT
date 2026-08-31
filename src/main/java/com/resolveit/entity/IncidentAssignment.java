package com.resolveit.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "RESOLVE_INCIDENT_ASSIGNMENT")
public class IncidentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_user_id", nullable = false)
    private AppUser supportUser;

    @Column(name = "assignment_score", precision = 5, scale = 2)
    private BigDecimal assignmentScore;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public AppUser getSupportUser() {
        return supportUser;
    }

    public void setSupportUser(AppUser supportUser) {
        this.supportUser = supportUser;
    }

    public BigDecimal getAssignmentScore() {
        return assignmentScore;
    }

    public void setAssignmentScore(BigDecimal assignmentScore) {
        this.assignmentScore = assignmentScore;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }
}
