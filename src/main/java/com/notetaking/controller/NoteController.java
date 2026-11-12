package com.notetaking.controller;

import com.notetaking.dto.request.CreateNoteRequest;
import com.notetaking.dto.request.UpdateNoteRequest;
import com.notetaking.dto.response.NoteResponse;
import com.notetaking.dto.response.PageResponse;
import com.notetaking.repository.UserRepository;
import com.notetaking.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final UserRepository userRepository;

    @GetMapping
    public PageResponse<NoteResponse> list(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) Long notebookId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noteService.listNotes(resolveId(principal), notebookId, q, page, size);
    }

    @GetMapping("/{id}")
    public NoteResponse get(@AuthenticationPrincipal UserDetails principal,
                            @PathVariable Long id) {
        return noteService.getNote(resolveId(principal), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@AuthenticationPrincipal UserDetails principal,
                               @Valid @RequestBody CreateNoteRequest req) {
        return noteService.createNote(resolveId(principal), req);
    }

    @PutMapping("/{id}")
    public NoteResponse update(@AuthenticationPrincipal UserDetails principal,
                               @PathVariable Long id,
                               @Valid @RequestBody UpdateNoteRequest req) {
        return noteService.updateNote(resolveId(principal), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserDetails principal,
                       @PathVariable Long id) {
        noteService.deleteNote(resolveId(principal), id);
    }

    private Long resolveId(UserDetails principal) {
        return userRepository.findByUsername(principal.getUsername()).orElseThrow().getId();
    }
}
