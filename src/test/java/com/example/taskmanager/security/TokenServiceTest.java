package com.example.taskmanager.security;

import com.example.taskmanager.model.User;
import com.example.taskmanager.model.enums.UserRole;
import com.example.taskmanager.security.provider.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "segredo-de-teste-com-32-chars-ok!");

        user = new User(1L, "user@test.com", "senha", UserRole.MEMBER);
    }

    @Test
    @DisplayName("Deve gerar token JWT não nulo e não vazio")
    void generateToken_deveRetornarTokenValido() {
        String token = tokenService.generateToken(user);

        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("Token gerado deve conter o email do usuário como subject")
    void generateToken_deveConterEmailDoUsuario() {
        String token = tokenService.generateToken(user);
        String subject = tokenService.validateToken(token);

        assertThat(subject).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("Deve retornar string vazia para token com assinatura inválida")
    void validateToken_assinaturaInvalida_deveRetornarVazio() {
        String tokenInvalido = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQHRlc3QuY29tIn0.assinatura-invalida";

        String result = tokenService.validateToken(tokenInvalido);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar string vazia para token completamente malformado")
    void validateToken_tokenMalformado_deveRetornarVazio() {
        String result = tokenService.validateToken("isso.nao.e.um.jwt");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Tokens gerados para usuários diferentes devem ser distintos")
    void generateToken_usuariosDiferentes_devemGerarTokensDistintos() {
        User outroUser = new User(2L, "outro@test.com", "senha", UserRole.ADMIN);

        String token1 = tokenService.generateToken(user);
        String token2 = tokenService.generateToken(outroUser);

        assertThat(token1).isNotEqualTo(token2);
    }
}
