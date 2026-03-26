package com.example.taskmanager.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthenticationDTO(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail em formato inválido") String email,
        @NotBlank(message = "A senha é obrigatória") String password) {
}
