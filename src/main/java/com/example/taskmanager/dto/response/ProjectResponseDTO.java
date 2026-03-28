package com.example.taskmanager.dto.response;

import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public record ProjectResponseDTO(
        Long id,
        String name,
        String description,
        Long ownerId,
        Set<Long> memberIds,
        LocalDateTime createdAt
) {
    public static ProjectResponseDTO from(Project project) {
        Set<Long> memberIds = project.getMembers().stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        return new ProjectResponseDTO(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId(),
                memberIds,
                project.getCreatedAt()
        );
    }
}
