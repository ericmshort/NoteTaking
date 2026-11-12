package com.notetaking.controller;

import com.notetaking.dto.request.AddMemberRequest;
import com.notetaking.dto.request.CreateTeamRequest;
import com.notetaking.dto.response.TeamMemberResponse;
import com.notetaking.dto.response.TeamResponse;
import com.notetaking.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final com.notetaking.repository.UserRepository userRepository;

    @GetMapping
    public List<TeamResponse> myTeams(@AuthenticationPrincipal UserDetails principal) {
        return teamService.getMyTeams(resolveId(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(@AuthenticationPrincipal UserDetails principal,
                               @Valid @RequestBody CreateTeamRequest req) {
        return teamService.createTeam(resolveId(principal), req);
    }

    @PutMapping("/{id}")
    public TeamResponse update(@AuthenticationPrincipal UserDetails principal,
                               @PathVariable Long id,
                               @Valid @RequestBody CreateTeamRequest req) {
        return teamService.updateTeam(resolveId(principal), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        teamService.deleteTeam(resolveId(principal), id);
    }

    @GetMapping("/{id}/members")
    public List<TeamMemberResponse> members(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable Long id) {
        return teamService.listMembers(resolveId(principal), id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberResponse addMember(@AuthenticationPrincipal UserDetails principal,
                                        @PathVariable Long id,
                                        @Valid @RequestBody AddMemberRequest req) {
        return teamService.addMember(resolveId(principal), id, req);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal UserDetails principal,
                             @PathVariable Long id,
                             @PathVariable Long userId) {
        teamService.removeMember(resolveId(principal), id, userId);
    }

    private Long resolveId(UserDetails principal) {
        return userRepository.findByUsername(principal.getUsername()).orElseThrow().getId();
    }
}
