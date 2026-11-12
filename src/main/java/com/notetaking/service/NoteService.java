package com.notetaking.service;

import com.notetaking.dto.request.CreateNoteRequest;
import com.notetaking.dto.request.UpdateNoteRequest;
import com.notetaking.dto.response.NoteResponse;
import com.notetaking.dto.response.PageResponse;
import com.notetaking.entity.Note;
import com.notetaking.exception.AppException;
import com.notetaking.exception.ForbiddenException;
import com.notetaking.exception.NotFoundException;
import com.notetaking.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final NotebookRepository notebookRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public PageResponse<NoteResponse> listNotes(Long userId, Long notebookId,
                                                 String query, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        if (query != null && !query.isBlank()) {
            var likeQuery = "%" + query.trim().toLowerCase() + "%";
            if (notebookId != null) {
                var notebook = notebookRepository.findById(notebookId)
                        .orElseThrow(() -> new NotFoundException("Notebook not found"));
                return PageResponse.from(
                        noteRepository.searchVisibleToUserInNotebook(userId, likeQuery, notebook, pageable)
                                .map(NoteResponse::from));
            }
            return PageResponse.from(
                    noteRepository.searchVisibleToUser(userId, likeQuery, pageable)
                            .map(NoteResponse::from));
        }
        if (notebookId != null) {
            var notebook = notebookRepository.findById(notebookId)
                    .orElseThrow(() -> new NotFoundException("Notebook not found"));
            return PageResponse.from(
                    noteRepository.findVisibleToUserInNotebook(userId, notebook, pageable)
                            .map(NoteResponse::from));
        }
        return PageResponse.from(
                noteRepository.findVisibleToUser(userId, pageable)
                        .map(NoteResponse::from));
    }

    @Transactional(readOnly = true)
    public NoteResponse getNote(Long userId, Long noteId) {
        var note = findVisibleNote(userId, noteId);
        return NoteResponse.from(note);
    }

    @Transactional
    public NoteResponse createNote(Long userId, CreateNoteRequest req) {
        var owner = userRepository.findById(userId).orElseThrow();
        var builder = Note.builder()
                .title(req.getTitle())
                .content(req.getContent())
                .owner(owner);

        if (req.getNotebookId() != null) {
            var notebook = notebookRepository.findById(req.getNotebookId())
                    .orElseThrow(() -> new NotFoundException("Notebook not found"));
            if (!notebook.getOwner().getId().equals(userId)) {
                throw new ForbiddenException("You do not own this notebook");
            }
            builder.notebook(notebook);
        }

        if (req.getSharedWithTeamId() != null) {
            var team = teamRepository.findById(req.getSharedWithTeamId())
                    .orElseThrow(() -> new NotFoundException("Team not found"));
            requireTeamMembership(owner, team);
            builder.sharedWithTeam(team);
        }

        return NoteResponse.from(noteRepository.save(builder.build()));
    }

    @Transactional
    public NoteResponse updateNote(Long userId, Long noteId, UpdateNoteRequest req) {
        var note = findOwnedNote(userId, noteId);

        if (req.getTitle() != null) note.setTitle(req.getTitle());
        if (req.getContent() != null) note.setContent(req.getContent());
        if (req.getArchived() != null) note.setArchived(req.getArchived());

        if (req.getNotebookId() != null) {
            var notebook = notebookRepository.findById(req.getNotebookId())
                    .orElseThrow(() -> new NotFoundException("Notebook not found"));
            if (!notebook.getOwner().getId().equals(userId)) {
                throw new ForbiddenException("You do not own this notebook");
            }
            note.setNotebook(notebook);
        }

        if (req.getSharedWithTeamId() != null) {
            if (req.getSharedWithTeamId() == -1L) {
                note.setSharedWithTeam(null); // explicitly unshare
            } else {
                var team = teamRepository.findById(req.getSharedWithTeamId())
                        .orElseThrow(() -> new NotFoundException("Team not found"));
                requireTeamMembership(note.getOwner(), team);
                note.setSharedWithTeam(team);
            }
        }

        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(Long userId, Long noteId) {
        var note = findOwnedNote(userId, noteId);
        noteRepository.delete(note);
    }

    private Note findVisibleNote(Long userId, Long noteId) {
        var note = noteRepository.findById(noteId)
                .orElseThrow(() -> new NotFoundException("Note not found"));
        var user = userRepository.findById(userId).orElseThrow();

        boolean isOwner = note.getOwner().getId().equals(userId);
        boolean isTeamMember = note.getSharedWithTeam() != null
                && membershipRepository.existsByUserAndTeam(user, note.getSharedWithTeam());

        if (!isOwner && !isTeamMember) {
            throw new ForbiddenException("You do not have access to this note");
        }
        return note;
    }

    private Note findOwnedNote(Long userId, Long noteId) {
        var note = noteRepository.findById(noteId)
                .orElseThrow(() -> new NotFoundException("Note not found"));
        if (!note.getOwner().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this note");
        }
        return note;
    }

    private void requireTeamMembership(com.notetaking.entity.User user,
                                        com.notetaking.entity.Team team) {
        if (!membershipRepository.existsByUserAndTeam(user, team)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "You must be a member of a team to share a note with it");
        }
    }
}
