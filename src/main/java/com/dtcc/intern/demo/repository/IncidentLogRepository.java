package com.dtcc.intern.demo.repository;

import com.dtcc.intern.demo.entity.IncidentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IncidentLogRepository extends JpaRepository<IncidentLog, Long> {

    List<IncidentLog> findByIncident_IncidentIdOrderByChangedAtAscLogIdAsc(Long incidentId);
}
