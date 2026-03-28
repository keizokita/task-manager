package com.example.taskmanager.controller;

import com.example.taskmanager.config.SecurityConfigurations;
import com.example.taskmanager.security.filter.SecurityFilter;
import com.example.taskmanager.dto.request.ProjectRequestDTO;
import com.example.taskmanager.dto.response.ProjectResponseDTO;
import com.example.taskmanager.exception.ForbiddenOperationException;
import com.example.taskmanager.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = ProjectController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfigurations.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityFilter.class)
        }
)
@Import(ProjectControllerTest.TestSecurityConfig.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    // ==================== POST /projects ====================

    @Test
    @DisplayName("POST /projects com dados validos deve retornar 201")
    void create_dadosValidos_deveRetornar201() throws Exception {
        var response = new ProjectResponseDTO(1L, "Projeto", "Desc", 1L, Set.of(1L), LocalDateTime.now());
        when(projectService.create(any())).thenReturn(response);

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectRequestDTO("Projeto", "Desc"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Projeto"));
    }

    @Test
    @DisplayName("POST /projects sem nome deve retornar 400")
    void create_semNome_deveRetornar400() throws Exception {
        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectRequestDTO("", null))))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /projects ====================

    @Test
    @DisplayName("GET /projects deve retornar 200 com lista")
    void findAll_deveRetornar200() throws Exception {
        var response = new ProjectResponseDTO(1L, "Projeto", null, 1L, Set.of(1L), LocalDateTime.now());
        when(projectService.findAllForCurrentUser()).thenReturn(List.of(response));

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ==================== GET /projects/{id} ====================

    @Test
    @DisplayName("GET /projects/{id} existente deve retornar 200")
    void findById_existente_deveRetornar200() throws Exception {
        var response = new ProjectResponseDTO(1L, "Projeto", null, 1L, Set.of(1L), LocalDateTime.now());
        when(projectService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("GET /projects/{id} inexistente deve retornar 404")
    void findById_inexistente_deveRetornar404() throws Exception {
        when(projectService.findById(99L)).thenThrow(new EntityNotFoundException("Projeto não encontrado"));

        mockMvc.perform(get("/projects/99"))
                .andExpect(status().isNotFound());
    }

    // ==================== POST /projects/{id}/members ====================

    @Test
    @DisplayName("POST /projects/{id}/members deve retornar 200")
    void addMember_deveRetornar200() throws Exception {
        var response = new ProjectResponseDTO(1L, "Projeto", null, 1L, Set.of(1L, 2L), LocalDateTime.now());
        when(projectService.addMember(eq(1L), eq(2L))).thenReturn(response);

        mockMvc.perform(post("/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds.length()").value(2));
    }

    // ==================== DELETE /projects/{id}/members/{userId} ====================

    @Test
    @DisplayName("DELETE /projects/{id}/members/{userId} deve retornar 204")
    void removeMember_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/projects/1/members/2"))
                .andExpect(status().isNoContent());
    }

    // ==================== DELETE /projects/{id} ====================

    @Test
    @DisplayName("DELETE /projects/{id} deve retornar 204")
    void delete_existente_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/projects/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /projects/{id} por nao-owner deve retornar 403")
    void delete_naoOwner_deveRetornar403() throws Exception {
        doThrow(new ForbiddenOperationException("Apenas o owner pode realizar esta operação"))
                .when(projectService).delete(1L);

        mockMvc.perform(delete("/projects/1"))
                .andExpect(status().isForbidden());
    }
}
