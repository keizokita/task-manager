package com.example.taskmanager.dto.response;

import com.example.taskmanager.model.enums.TaskPriority;
import com.example.taskmanager.model.enums.TaskStatus;

import java.util.Map;

public record TaskReportDTO(
        Map<TaskStatus, Long> byStatus,
        Map<TaskPriority, Long> byPriority,
        long total
) {}
