package com.notetaking.service;

import com.notetaking.dto.request.CreateNotebookRequest;
import com.notetaking.dto.request.UpdateNotebookRequest;
import com.notetaking.dto.response.NotebookResponse;
import com.notetaking.entity.Notebook;
import com.notetaking.exception.ForbiddenException;
import com.notetaking.exception.NotFoundException;
import com.notetaking.repository.NotebookRepository;
import com.notetaking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotebookService {

    private final NotebookRepository notebookRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotebookResponse> getMyNotebooks(Long userId) {
        var user = userRepository.findById(userId).orElseThrow();
        return notebookRepository.findByOwner(user).stream()
                .map(NotebookResponse::from)
                .toList();
    }

    @Transactional
    public NotebookResponse create(Long userId, CreateNotebookRequest req) {
        var user = userRepository.findById(userId).orElseThrow();
        var notebook = Notebook.builder()
                .name(req.getName())
                .owner(user)
                .build();
        return NotebookResponse.from(notebookRepository.save(notebook));
    }

    @Transactional
    public NotebookResponse update(Long userId, Long notebookId, UpdateNotebookRequest req) {
        var notebook = findAndVerifyOwner(userId, notebookId);
        notebook.setName(req.getName());
        return NotebookResponse.from(notebook);
    }

    @Transactional
    public void delete(Long userId, Long notebookId) {
        var notebook = findAndVerifyOwner(userId, notebookId);
        notebookRepository.delete(notebook);
    }

    public Notebook resolveAndVerifyOwner(Long userId, Long notebookId) {
        return findAndVerifyOwner(userId, notebookId);
    }

    private Notebook findAndVerifyOwner(Long userId, Long notebookId) {
        var notebook = notebookRepository.findById(notebookId)
                .orElseThrow(() -> new NotFoundException("Notebook not found"));
        if (!notebook.getOwner().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this notebook");
        }
        return notebook;
    }
}
