package com.notetaking.repository;

import com.notetaking.entity.Team;
import com.notetaking.entity.TeamMembership;
import com.notetaking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {
    Optional<TeamMembership> findByUserAndTeam(User user, Team team);
    List<TeamMembership> findByTeam(Team team);
    boolean existsByUserAndTeam(User user, Team team);
    void deleteByUserAndTeam(User user, Team team);
}
