package com.dtcc.intern.demo.repository;

import com.dtcc.intern.demo.entity.IncidentMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IncidentMessageRepository extends JpaRepository<IncidentMessage, Long> {

    List<IncidentMessage> findByIncident_IncidentIdOrderBySentAtAscMessageIdAsc(Long incidentId);

    List<IncidentMessage> findByMessageIdInAndIncident_IncidentId(List<Long> messageIds, Long incidentId);
}
