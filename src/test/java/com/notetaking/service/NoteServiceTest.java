package com.notetaking.service;

import com.notetaking.dto.request.CreateNoteRequest;
import com.notetaking.entity.*;
import com.notetaking.exception.ForbiddenException;
import com.notetaking.exception.NotFoundException;
import com.notetaking.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock NoteRepository noteRepository;
    @Mock UserRepository userRepository;
    @Mock NotebookRepository notebookRepository;
    @Mock TeamRepository teamRepository;
    @Mock TeamMembershipRepository membershipRepository;

    @InjectMocks NoteService noteService;

    private User alice() {
        return User.builder().id(1L).username("alice").email("a@a.com").passwordHash("h").build();
    }

    private User bob() {
        return User.builder().id(2L).username("bob").email("b@b.com").passwordHash("h").build();
    }

    @Test
    void createNote_noNotebook_noSharing_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice()));
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(10L);
            return n;
        });

        var req = new CreateNoteRequest();
        req.setTitle("My Note");
        req.setContent("Hello world");

        var result = noteService.createNote(1L, req);

        assertThat(result.getTitle()).isEqualTo("My Note");
        assertThat(result.getSharedWithTeamId()).isNull();
    }

    @Test
    void getNote_privateNote_forbiddenForOtherUser() {
        var note = Note.builder().id(10L).title("Secret").owner(alice()).build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob()));

        assertThatThrownBy(() -> noteService.getNote(2L, 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getNote_sharedNote_visibleToTeamMember() {
        var team = Team.builder().id(5L).name("Team A").createdBy(alice()).build();
        var note = Note.builder().id(10L).title("Shared").owner(alice()).sharedWithTeam(team).build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob()));
        when(membershipRepository.existsByUserAndTeam(any(User.class), eq(team))).thenReturn(true);

        var result = noteService.getNote(2L, 10L);

        assertThat(result.getTitle()).isEqualTo("Shared");
        assertThat(result.getSharedWithTeamId()).isEqualTo(5L);
    }

    @Test
    void deleteNote_notOwner_throws() {
        var note = Note.builder().id(10L).title("Mine").owner(alice()).build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> noteService.deleteNote(2L, 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getNote_notFound_throws() {
        when(noteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNote(1L, 99L))
                .isInstanceOf(NotFoundException.class);
    }
}
