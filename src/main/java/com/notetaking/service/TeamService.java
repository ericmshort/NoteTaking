package com.notetaking.service;

import com.notetaking.dto.request.AddMemberRequest;
import com.notetaking.dto.request.CreateTeamRequest;
import com.notetaking.dto.response.TeamMemberResponse;
import com.notetaking.dto.response.TeamResponse;
import com.notetaking.entity.TeamMembership;
import com.notetaking.exception.AppException;
import com.notetaking.exception.ForbiddenException;
import com.notetaking.exception.NotFoundException;
import com.notetaking.repository.TeamMembershipRepository;
import com.notetaking.repository.TeamRepository;
import com.notetaking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams(Long userId) {
        return teamRepository.findAllByMemberUserId(userId).stream()
                .map(TeamResponse::from)
                .toList();
    }

    @Transactional
    public TeamResponse createTeam(Long userId, CreateTeamRequest req) {
        var creator = userRepository.findById(userId).orElseThrow();
        var team = com.notetaking.entity.Team.builder()
                .name(req.getName())
                .description(req.getDescription())
                .createdBy(creator)
                .build();
        teamRepository.save(team);

        var membership = TeamMembership.builder()
                .user(creator)
                .team(team)
                .role(TeamMembership.Role.OWNER)
                .build();
        membershipRepository.save(membership);

        return TeamResponse.from(team);
    }

    @Transactional
    public TeamResponse updateTeam(Long userId, Long teamId, CreateTeamRequest req) {
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        requireOwner(userId, teamId);
        team.setName(req.getName());
        team.setDescription(req.getDescription());
        return TeamResponse.from(team);
    }

    @Transactional
    public void deleteTeam(Long userId, Long teamId) {
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        requireOwner(userId, teamId);
        teamRepository.delete(team);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(Long userId, Long teamId) {
        requireMember(userId, teamId);
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        return membershipRepository.findByTeam(team).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional
    public TeamMemberResponse addMember(Long requesterId, Long teamId, AddMemberRequest req) {
        requireOwner(requesterId, teamId);
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        var newUser = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found: " + req.getUsername()));
        if (membershipRepository.existsByUserAndTeam(newUser, team)) {
            throw new AppException(HttpStatus.CONFLICT, "User is already a member of this team");
        }
        var membership = TeamMembership.builder()
                .user(newUser)
                .team(team)
                .role(TeamMembership.Role.MEMBER)
                .build();
        membershipRepository.save(membership);
        return TeamMemberResponse.from(membership);
    }

    @Transactional
    public void removeMember(Long requesterId, Long teamId, Long targetUserId) {
        requireOwner(requesterId, teamId);
        if (requesterId.equals(targetUserId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Team owner cannot remove themselves");
        }
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        var target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        membershipRepository.deleteByUserAndTeam(target, team);
    }

    public void requireMember(Long userId, Long teamId) {
        var user = userRepository.findById(userId).orElseThrow();
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        if (!membershipRepository.existsByUserAndTeam(user, team)) {
            throw new ForbiddenException("You are not a member of this team");
        }
    }

    private void requireOwner(Long userId, Long teamId) {
        var user = userRepository.findById(userId).orElseThrow();
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        var membership = membershipRepository.findByUserAndTeam(user, team)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this team"));
        if (membership.getRole() != TeamMembership.Role.OWNER) {
            throw new ForbiddenException("Only the team owner can perform this action");
        }
    }
}
