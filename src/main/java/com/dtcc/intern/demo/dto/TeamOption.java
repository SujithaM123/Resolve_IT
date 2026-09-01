package com.dtcc.intern.demo.dto;

/**
 * One entry of the Team dropdown a super admin picks from. The client shows
 * teamName and sends teamId back as CreateSupportUserRequest.teamId.
 */
public record TeamOption(
        Long teamId,
        String teamName) {
}
