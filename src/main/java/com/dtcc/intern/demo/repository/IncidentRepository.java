package com.dtcc.intern.demo.repository;

import com.dtcc.intern.demo.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByReportedBy_UserIdOrderByCreatedAtDescIncidentIdDesc(Long userId);

    @Query("""
            select i from Incident i
            where i.incidentId <> :incidentId
            order by i.createdAt desc, i.incidentId desc
            """)
    List<Incident> findHistoricalExcluding(@Param("incidentId") Long incidentId);

    @Query("select i from Incident i order by i.createdAt desc, i.incidentId desc")
    List<Incident> findAllNewestFirst();
}
