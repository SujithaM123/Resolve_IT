package com.dtcc.intern.demo.repository;

import com.dtcc.intern.demo.entity.Incident;
import com.dtcc.intern.demo.entity.IncidentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface IncidentAssignmentRepository extends JpaRepository<IncidentAssignment, Long> {

    @Query("""
            select a from IncidentAssignment a
            where a.incident.incidentId = :incidentId
              and a.assignmentId = (
                    select max(a2.assignmentId) from IncidentAssignment a2
                    where a2.incident.incidentId = :incidentId)
            """)
    Optional<IncidentAssignment> findCurrentByIncidentId(@Param("incidentId") Long incidentId);

    @Query("""
            select a.incident from IncidentAssignment a
            where a.supportUser.userId = :userId
              and a.assignmentId = (
                    select max(a2.assignmentId) from IncidentAssignment a2
                    where a2.incident.incidentId = a.incident.incidentId)
            order by a.incident.createdAt desc, a.incident.incidentId desc
            """)
    List<Incident> findCurrentIncidentsForSupportUser(@Param("userId") Long userId);

    @Query("""
            select a.incident from IncidentAssignment a
            where a.supportUser.userId = :userId
              and a.incident.status <> 'RESOLVED'
              and a.assignmentId = (
                    select max(a2.assignmentId) from IncidentAssignment a2
                    where a2.incident.incidentId = a.incident.incidentId)
            """)
    List<Incident> findActiveIncidentsForSupportUser(@Param("userId") Long userId);

    Optional<IncidentAssignment> findTopBySupportUser_UserIdOrderByAssignedAtDescAssignmentIdDesc(Long userId);
}
