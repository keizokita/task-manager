package com.example.taskmanager.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequestDTO(
        @NotBlank(message = "O nome do projeto é obrigatório")
        String name,
        String description
) {}
