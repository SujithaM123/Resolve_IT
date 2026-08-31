package com.resolveit.repository;

import com.resolveit.entity.IncidentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IncidentLogRepository extends JpaRepository<IncidentLog, Long> {

    List<IncidentLog> findByIncident_IncidentIdOrderByChangedAtAscLogIdAsc(Long incidentId);
}
