package com.example.taskmanager.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddMemberDTO(
        @NotNull(message = "O userId é obrigatório")
        Long userId
) {}
