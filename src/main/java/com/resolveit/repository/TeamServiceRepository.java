package com.resolveit.repository;

import com.resolveit.entity.TeamService;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TeamServiceRepository extends JpaRepository<TeamService, Long> {

    Optional<TeamService> findByServiceNameIgnoreCase(String serviceName);

    List<TeamService> findAllByOrderByTeamNameAsc();
}
