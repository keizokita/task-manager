package com.example.taskmanager.dto.response;

import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.enums.TaskPriority;
import com.example.taskmanager.model.enums.TaskStatus;

import java.time.LocalDateTime;

public record TaskResponseDTO(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deadline,
        ResponsibleDTO responsible,
        Long projectId
) {
    public record ResponsibleDTO(Long id, String email) {}

    public static TaskResponseDTO from(Task task) {
        ResponsibleDTO responsible = task.getResponsible() != null
                ? new ResponsibleDTO(task.getResponsible().getId(), task.getResponsible().getEmail())
                : null;

        return new TaskResponseDTO(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getDeadline(),
                responsible,
                task.getProject() != null ? task.getProject().getId() : null
        );
    }
}
