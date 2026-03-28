package com.example.taskmanager.controller.auth;

import com.example.taskmanager.config.SecurityConfigurations;
import com.example.taskmanager.security.filter.SecurityFilter;
import com.example.taskmanager.dto.request.AuthenticationDTO;
import com.example.taskmanager.dto.request.RegisterDTO;
import com.example.taskmanager.model.User;
import com.example.taskmanager.model.enums.UserRole;
import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.security.provider.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AuthenticationController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfigurations.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityFilter.class)
        }
)
@Import(AuthenticationControllerTest.TestSecurityConfig.class)
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private UserRepository userRepository;

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

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Test
    @DisplayName("POST /auth/register com dados válidos deve retornar 200")
    void register_dadosValidos_deveRetornar200() throws Exception {
        when(userRepository.findByEmail("novo@test.com")).thenReturn(null);
        when(userRepository.save(any())).thenReturn(new User(1L, "novo@test.com", "encoded", UserRole.MEMBER));

        var dto = new RegisterDTO("novo@test.com", "senha123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/register com e-mail já cadastrado deve retornar 400")
    void register_emailJaCadastrado_deveRetornar400() throws Exception {
        User existente = new User(1L, "existente@test.com", "encoded", UserRole.MEMBER);
        when(userRepository.findByEmail("existente@test.com")).thenReturn(existente);

        var dto = new RegisterDTO("existente@test.com", "senha123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register com e-mail inválido deve retornar 400")
    void register_emailInvalido_deveRetornar400() throws Exception {
        var dto = new RegisterDTO("nao-e-um-email", "senha123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register com senha menor que 6 caracteres deve retornar 400")
    void register_senhaCurta_deveRetornar400() throws Exception {
        var dto = new RegisterDTO("user@test.com", "123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ==================== POST /auth/login ====================

    @Test
    @DisplayName("POST /auth/login com credenciais válidas deve retornar 200 com token")
    void login_credenciaisValidas_deveRetornar200ComToken() throws Exception {
        User user = new User(1L, "user@test.com", "encoded", UserRole.MEMBER);
        var authToken = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(tokenService.generateToken(user)).thenReturn("jwt.token.aqui");

        var dto = new AuthenticationDTO("user@test.com", "senha123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.token.aqui"));
    }

    @Test
    @DisplayName("POST /auth/login com credenciais inválidas deve retornar 403")
    void login_credenciaisInvalidas_deveRetornar403() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Credenciais inválidas"));

        var dto = new AuthenticationDTO("user@test.com", "senhaErrada");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /auth/login com e-mail em formato inválido deve retornar 400")
    void login_emailInvalido_deveRetornar400() throws Exception {
        var dto = new AuthenticationDTO("nao-e-email", "senha123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}
