package com.example.taskmanager.dto.request;

import com.example.taskmanager.model.enums.TaskPriority;
import com.example.taskmanager.model.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TaskRequestDTO(

        @NotBlank(message = "O título é obrigatório")
        String title,

        String description,

        TaskStatus status,

        @NotNull(message = "A prioridade é obrigatória")
        TaskPriority priority,

        LocalDateTime deadline,

        Long responsibleId,

        Long projectId
) {}
