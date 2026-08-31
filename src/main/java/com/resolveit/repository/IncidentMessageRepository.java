package com.resolveit.repository;

import com.resolveit.entity.IncidentMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IncidentMessageRepository extends JpaRepository<IncidentMessage, Long> {

    List<IncidentMessage> findByIncident_IncidentIdOrderBySentAtAscMessageIdAsc(Long incidentId);

    List<IncidentMessage> findByMessageIdInAndIncident_IncidentId(List<Long> messageIds, Long incidentId);
}
