package com.notetaking.controller;

import com.notetaking.dto.request.CreateNotebookRequest;
import com.notetaking.dto.request.UpdateNotebookRequest;
import com.notetaking.dto.response.NotebookResponse;
import com.notetaking.repository.UserRepository;
import com.notetaking.service.NotebookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notebooks")
@RequiredArgsConstructor
public class NotebookController {

    private final NotebookService notebookService;
    private final UserRepository userRepository;

    @GetMapping
    public List<NotebookResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return notebookService.getMyNotebooks(resolveId(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotebookResponse create(@AuthenticationPrincipal UserDetails principal,
                                   @Valid @RequestBody CreateNotebookRequest req) {
        return notebookService.create(resolveId(principal), req);
    }

    @PutMapping("/{id}")
    public NotebookResponse update(@AuthenticationPrincipal UserDetails principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody UpdateNotebookRequest req) {
        return notebookService.update(resolveId(principal), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        notebookService.delete(resolveId(principal), id);
    }

    private Long resolveId(UserDetails principal) {
        return userRepository.findByUsername(principal.getUsername()).orElseThrow().getId();
    }
}
