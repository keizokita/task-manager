package com.example.taskmanager.dto.request;

import com.example.taskmanager.model.enums.TaskPriority;
import com.example.taskmanager.model.enums.TaskStatus;

import java.time.LocalDateTime;

public record TaskFilterDTO(
        TaskStatus status,
        TaskPriority priority,
        LocalDateTime deadlineFrom,
        LocalDateTime deadlineTo
) {}
