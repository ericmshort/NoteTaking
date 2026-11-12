package com.notetaking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notetaking.dto.request.CreateNoteRequest;
import com.notetaking.dto.response.NoteResponse;
import com.notetaking.dto.response.PageResponse;
import com.notetaking.entity.User;
import com.notetaking.repository.UserRepository;
import com.notetaking.security.JwtTokenProvider;
import com.notetaking.security.UserDetailsServiceImpl;
import com.notetaking.service.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NoteController.class)
class NoteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean NoteService noteService;
    @MockBean JwtTokenProvider tokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean UserRepository userRepository;

    private static final User ALICE = User.builder()
            .id(1L).username("alice").email("a@a.com").passwordHash("h").build();

    private NoteResponse sampleNote() {
        return NoteResponse.builder()
                .id(1L).title("Test Note").content("Content")
                .ownerId(1L).archived(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(username = "alice")
    void listNotes_authenticated_returns200() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        var page = PageResponse.<NoteResponse>builder()
                .content(List.of(sampleNote()))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(noteService.listNotes(1L, null, null, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Test Note"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listNotes_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void createNote_valid_returns201() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(noteService.createNote(eq(1L), any())).thenReturn(sampleNote());

        var req = new CreateNoteRequest();
        req.setTitle("Test Note");
        req.setContent("Content");

        mockMvc.perform(post("/api/v1/notes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Note"));
    }

    @Test
    @WithMockUser(username = "alice")
    void deleteNote_returns204() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(ALICE));

        mockMvc.perform(delete("/api/v1/notes/1").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
