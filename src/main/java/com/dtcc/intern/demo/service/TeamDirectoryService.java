package com.dtcc.intern.demo.service;

import com.dtcc.intern.demo.dto.TeamOption;
import com.dtcc.intern.demo.repository.TeamServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only lookup of the teams a super admin can assign a support engineer to.
 * Named TeamDirectoryService because the entity itself is called TeamService.
 */
@Service
public class TeamDirectoryService {

    private final TeamServiceRepository teamServiceRepository;

    public TeamDirectoryService(TeamServiceRepository teamServiceRepository) {
        this.teamServiceRepository = teamServiceRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamOption> listTeams() {
        return teamServiceRepository.findAllByOrderByTeamNameAsc()
                .stream()
                .map(team -> new TeamOption(team.getTeamId(), team.getTeamName()))
                .toList();
    }
}
