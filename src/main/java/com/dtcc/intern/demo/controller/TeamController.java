package com.dtcc.intern.demo.controller;

import com.dtcc.intern.demo.dto.TeamOption;
import com.dtcc.intern.demo.service.TeamDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamDirectoryService teamDirectoryService;

    public TeamController(TeamDirectoryService teamDirectoryService) {
        this.teamDirectoryService = teamDirectoryService;
    }

    @Operation(
            summary = "List teams for the Team dropdown (SUPER_ADMIN only)",
            description = "Returns every team as an id/name pair, ordered by name. The client "
                    + "displays teamName and posts the matching teamId to POST /api/support-users.")
    @GetMapping
    public ResponseEntity<List<TeamOption>> listTeams() {
        return ResponseEntity.ok(teamDirectoryService.listTeams());
    }
}
