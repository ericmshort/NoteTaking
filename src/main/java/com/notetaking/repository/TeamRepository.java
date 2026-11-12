package com.notetaking.repository;

import com.notetaking.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("SELECT t FROM Team t JOIN t.memberships m WHERE m.user.id = :userId")
    List<Team> findAllByMemberUserId(@Param("userId") Long userId);
}
