package com.notetaking.repository;

import com.notetaking.entity.Note;
import com.notetaking.entity.Notebook;
import com.notetaking.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<Note, Long> {

    // All notes visible to a user: their own notes + notes shared with their teams
    @Query("""
            SELECT n FROM Note n
            WHERE n.archived = false
              AND (
                n.owner.id = :userId
                OR (n.sharedWithTeam IS NOT NULL
                    AND EXISTS (
                        SELECT m FROM TeamMembership m
                        WHERE m.team = n.sharedWithTeam AND m.user.id = :userId
                    ))
              )
            """)
    Page<Note> findVisibleToUser(@Param("userId") Long userId, Pageable pageable);

    // Filtered by notebook
    @Query("""
            SELECT n FROM Note n
            WHERE n.archived = false
              AND n.notebook = :notebook
              AND (
                n.owner.id = :userId
                OR (n.sharedWithTeam IS NOT NULL
                    AND EXISTS (
                        SELECT m FROM TeamMembership m
                        WHERE m.team = n.sharedWithTeam AND m.user.id = :userId
                    ))
              )
            """)
    Page<Note> findVisibleToUserInNotebook(@Param("userId") Long userId,
                                           @Param("notebook") Notebook notebook,
                                           Pageable pageable);

    // Full-text search (LIKE-based; replace with DB-native search when migrating).
    // Caller must pass :query already lowercased and wrapped with % wildcards.
    @Query("""
            SELECT n FROM Note n
            WHERE n.archived = false
              AND (LOWER(n.title) LIKE :query OR LOWER(n.content) LIKE :query)
              AND (
                n.owner.id = :userId
                OR (n.sharedWithTeam IS NOT NULL
                    AND EXISTS (
                        SELECT m FROM TeamMembership m
                        WHERE m.team = n.sharedWithTeam AND m.user.id = :userId
                    ))
              )
            """)
    Page<Note> searchVisibleToUser(@Param("userId") Long userId,
                                   @Param("query") String query,
                                   Pageable pageable);

    // Combined search + notebook filter — used when both query and notebookId are supplied.
    @Query("""
            SELECT n FROM Note n
            WHERE n.archived = false
              AND (LOWER(n.title) LIKE :query OR LOWER(n.content) LIKE :query)
              AND n.notebook = :notebook
              AND (
                n.owner.id = :userId
                OR (n.sharedWithTeam IS NOT NULL
                    AND EXISTS (
                        SELECT m FROM TeamMembership m
                        WHERE m.team = n.sharedWithTeam AND m.user.id = :userId
                    ))
              )
            """)
    Page<Note> searchVisibleToUserInNotebook(@Param("userId") Long userId,
                                             @Param("query") String query,
                                             @Param("notebook") Notebook notebook,
                                             Pageable pageable);

    Page<Note> findByOwnerAndArchived(User owner, boolean archived, Pageable pageable);
}
