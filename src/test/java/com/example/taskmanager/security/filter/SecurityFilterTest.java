package com.example.taskmanager.security.filter;

import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.security.provider.TokenService;
import com.example.taskmanager.model.User;
import com.example.taskmanager.model.enums.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private SecurityFilter securityFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ==================== doFilterInternal ====================

    @Test
    @DisplayName("Deve continuar cadeia de filtros sem autenticar quando token e valido mas usuario nao existe")
    void doFilterInternal_tokenValidoMasUsuarioNaoEncontrado_deveContinuarSemAutenticacao() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(tokenService.validateToken("token-valido")).thenReturn("deleted@test.com");
        when(userRepository.findByEmail("deleted@test.com")).thenReturn(null);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Deve autenticar usuario quando token e valido e usuario existe")
    void doFilterInternal_tokenValidoUsuarioExiste_deveAutenticar() throws Exception {
        var user = new User(1L, "user@test.com", "senha", UserRole.MEMBER);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(tokenService.validateToken("token-valido")).thenReturn("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(user);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
    }

    @Test
    @DisplayName("Deve continuar sem autenticacao quando nao ha token no header")
    void doFilterInternal_semTokenNoHeader_deveContinuarSemAutenticacao() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenService);
    }
}
