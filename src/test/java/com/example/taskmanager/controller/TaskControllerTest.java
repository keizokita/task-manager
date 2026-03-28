package com.example.taskmanager.controller;

import com.example.taskmanager.config.SecurityConfigurations;
import com.example.taskmanager.security.filter.SecurityFilter;
import com.example.taskmanager.dto.request.TaskFilterDTO;
import com.example.taskmanager.dto.request.TaskRequestDTO;
import com.example.taskmanager.dto.response.TaskResponseDTO;
import com.example.taskmanager.exception.ForbiddenOperationException;
import com.example.taskmanager.exception.InvalidStatusTransitionException;
import com.example.taskmanager.exception.WipLimitExceededException;
import com.example.taskmanager.model.enums.TaskPriority;
import com.example.taskmanager.model.enums.TaskStatus;
import com.example.taskmanager.service.TaskService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = TaskController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfigurations.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityFilter.class)
        }
)
@Import(TaskControllerTest.TestSecurityConfig.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

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

    // ==================== POST /tasks ====================

    @Test
    @DisplayName("POST /tasks com dados válidos deve retornar 201 Created")
    void create_dadosValidos_deveRetornar201() throws Exception {
        var dto = new TaskRequestDTO("Nova tarefa", "Descrição", TaskStatus.TODO, TaskPriority.HIGH, null, null, null);
        var response = buildResponse(1L, "Nova tarefa", TaskStatus.TODO, TaskPriority.HIGH);
        when(taskService.create(any())).thenReturn(response);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Nova tarefa"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    @DisplayName("POST /tasks sem título deve retornar 400 Bad Request")
    void create_semTitulo_deveRetornar400() throws Exception {
        var dto = new TaskRequestDTO("", null, null, TaskPriority.LOW, null, null, null);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /tasks sem prioridade deve retornar 400 Bad Request")
    void create_semPrioridade_deveRetornar400() throws Exception {
        var dto = new TaskRequestDTO("Tarefa", null, null, null, null, null, null);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /tasks com WIP excedido deve retornar 422 Unprocessable Entity")
    void create_wipExcedido_deveRetornar422() throws Exception {
        var dto = new TaskRequestDTO("Tarefa", null, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null, 1L, null);
        when(taskService.create(any())).thenThrow(new WipLimitExceededException("Limite WIP atingido: o responsável já possui 5 tarefas IN_PROGRESS."));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Limite WIP atingido"));
    }

    // ==================== GET /tasks ====================

    @Test
    @DisplayName("GET /tasks deve retornar 200 com pagina de tarefas")
    void findAll_deveRetornar200ComPagina() throws Exception {
        var page = new PageImpl<>(List.of(
                buildResponse(1L, "Tarefa 1", TaskStatus.TODO, TaskPriority.LOW),
                buildResponse(2L, "Tarefa 2", TaskStatus.IN_PROGRESS, TaskPriority.HIGH)
        ));
        when(taskService.findAll(any(TaskFilterDTO.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[1].id").value(2L));
    }

    @Test
    @DisplayName("GET /tasks?status=TODO deve filtrar por status")
    void findAll_comFiltroDeStatus_deveRetornar200() throws Exception {
        var page = new PageImpl<>(List.of(
                buildResponse(1L, "Tarefa", TaskStatus.TODO, TaskPriority.LOW)
        ));
        when(taskService.findAll(any(TaskFilterDTO.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/tasks").param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("TODO"));
    }

    // ==================== GET /tasks/{id} ====================

    @Test
    @DisplayName("GET /tasks/{id} com ID existente deve retornar 200")
    void findById_existente_deveRetornar200() throws Exception {
        when(taskService.findById(1L)).thenReturn(buildResponse(1L, "Tarefa", TaskStatus.TODO, TaskPriority.MEDIUM));

        mockMvc.perform(get("/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("GET /tasks/{id} com ID inexistente deve retornar 404")
    void findById_inexistente_deveRetornar404() throws Exception {
        when(taskService.findById(99L)).thenThrow(new EntityNotFoundException("Tarefa não encontrada"));

        mockMvc.perform(get("/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    // ==================== PUT /tasks/{id} ====================

    @Test
    @DisplayName("PUT /tasks/{id} com dados válidos deve retornar 200")
    void update_dadosValidos_deveRetornar200() throws Exception {
        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null, null, null);
        when(taskService.update(eq(1L), any())).thenReturn(buildResponse(1L, "Atualizada", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));

        mockMvc.perform(put("/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("PUT /tasks/{id} com transição DONE → TODO deve retornar 422")
    void update_transicaoInvalida_deveRetornar422() throws Exception {
        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null, null);
        when(taskService.update(eq(1L), any())).thenThrow(
                new InvalidStatusTransitionException("Transição inválida: uma tarefa DONE não pode voltar para TODO.")
        );

        mockMvc.perform(put("/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Transição de status inválida"));
    }

    @Test
    @DisplayName("PUT /tasks/{id} com CRITICAL + DONE por USER deve retornar 403")
    void update_criticalDonePorUser_deveRetornar403() throws Exception {
        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.DONE, TaskPriority.CRITICAL, null, null, null);
        when(taskService.update(eq(1L), any())).thenThrow(
                new ForbiddenOperationException("Apenas administradores podem concluir tarefas com prioridade CRITICAL")
        );

        mockMvc.perform(put("/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Operação não permitida"));
    }

    // ==================== DELETE /tasks/{id} ====================

    @Test
    @DisplayName("DELETE /tasks/{id} com ID existente deve retornar 204 No Content")
    void delete_existente_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/tasks/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /tasks/{id} com ID inexistente deve retornar 404")
    void delete_inexistente_deveRetornar404() throws Exception {
        doThrow(new EntityNotFoundException("Tarefa não encontrada")).when(taskService).delete(99L);

        mockMvc.perform(delete("/tasks/99"))
                .andExpect(status().isNotFound());
    }

    // ==================== GET /tasks/reports ====================

    @Test
    @DisplayName("GET /tasks/reports deve retornar 200 com relatorio de contadores")
    void getReport_deveRetornar200ComRelatorio() throws Exception {
        var report = new com.example.taskmanager.dto.response.TaskReportDTO(
                java.util.Map.of(TaskStatus.TODO, 3L, TaskStatus.DONE, 5L),
                java.util.Map.of(TaskPriority.HIGH, 2L),
                8L
        );
        when(taskService.getReport()).thenReturn(report);

        mockMvc.perform(get("/tasks/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.byStatus.TODO").value(3))
                .andExpect(jsonPath("$.byStatus.DONE").value(5))
                .andExpect(jsonPath("$.byPriority.HIGH").value(2));
    }

    // ==================== GET /tasks/search ====================

    @Test
    @DisplayName("GET /tasks/search?q=termo deve retornar 200 com resultados")
    void search_comTermoValido_deveRetornar200() throws Exception {
        var page = new PageImpl<>(List.of(
                buildResponse(1L, "Deploy", TaskStatus.TODO, TaskPriority.LOW)
        ));
        when(taskService.search(eq("deploy"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/tasks/search").param("q", "deploy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Deploy"));
    }

    // ==================== helper ====================

    private TaskResponseDTO buildResponse(Long id, String title, TaskStatus status, TaskPriority priority) {
        return new TaskResponseDTO(id, title, null, status, priority, LocalDateTime.now(), LocalDateTime.now(), null, null, null);
    }
}
