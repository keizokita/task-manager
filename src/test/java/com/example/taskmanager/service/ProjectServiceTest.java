package com.example.taskmanager.service;

import com.example.taskmanager.dto.request.ProjectRequestDTO;
import com.example.taskmanager.dto.response.ProjectResponseDTO;
import com.example.taskmanager.exception.ForbiddenOperationException;
import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.User;
import com.example.taskmanager.model.enums.UserRole;
import com.example.taskmanager.repository.ProjectRepository;
import com.example.taskmanager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    private User owner;
    private User member;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = new User(1L, "owner@test.com", "senha", UserRole.MEMBER);
        member = new User(2L, "member@test.com", "senha", UserRole.MEMBER);

        project = new Project();
        project.setId(1L);
        project.setName("Projeto Teste");
        project.setDescription("Descricao");
        project.setOwner(owner);
        project.setMembers(new HashSet<>(Set.of(owner)));
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ==================== create ====================

    @Test
    @DisplayName("Deve criar projeto e definir usuario autenticado como owner")
    void create_dadosValidos_deveCriarProjeto() {
        autenticarComo(owner);
        when(projectRepository.save(any())).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        var dto = new ProjectRequestDTO("Novo Projeto", "Descricao");
        ProjectResponseDTO result = projectService.create(dto);

        assertThat(result.name()).isEqualTo("Novo Projeto");
        assertThat(result.ownerId()).isEqualTo(1L);
        assertThat(result.memberIds()).contains(1L);
    }

    // ==================== findById ====================

    @Test
    @DisplayName("Deve retornar projeto quando ID existe")
    void findById_existente_deveRetornarDTO() {
        autenticarComo(owner);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        ProjectResponseDTO result = projectService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Projeto Teste");
    }

    @Test
    @DisplayName("Deve lancar EntityNotFoundException quando projeto nao existe")
    void findById_inexistente_deveLancarExcecao() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.findById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Projeto não encontrado");
    }

    // ==================== findAllForCurrentUser ====================

    @Test
    @DisplayName("Deve retornar apenas projetos do usuario autenticado")
    void findAll_deveRetornarProjetosDoUsuario() {
        autenticarComo(owner);
        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));

        List<ProjectResponseDTO> result = projectService.findAllForCurrentUser();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Projeto Teste");
    }

    // ==================== addMember ====================

    @Test
    @DisplayName("Deve adicionar membro ao projeto")
    void addMember_usuarioValido_deveAdicionarAoProjeto() {
        autenticarComo(owner);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponseDTO result = projectService.addMember(1L, 2L);

        assertThat(result.memberIds()).contains(2L);
    }

    @Test
    @DisplayName("Deve lancar excecao ao adicionar usuario que ja e membro")
    void addMember_usuarioJaMembro_deveLancarExcecao() {
        autenticarComo(owner);
        project.getMembers().add(member);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> projectService.addMember(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já é membro");
    }

    // ==================== removeMember ====================

    @Test
    @DisplayName("Deve remover membro do projeto")
    void removeMember_membroExistente_deveRemover() {
        autenticarComo(owner);
        project.getMembers().add(member);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        projectService.removeMember(1L, 2L);

        verify(projectRepository).save(project);
        assertThat(project.getMembers()).doesNotContain(member);
    }

    @Test
    @DisplayName("Nao deve permitir remover o owner do projeto")
    void removeMember_owner_deveLancarExcecao() {
        autenticarComo(owner);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.removeMember(1L, 1L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("owner");
    }

    // ==================== delete ====================

    @Test
    @DisplayName("Deve deletar projeto existente")
    void delete_existente_deveDeletar() {
        autenticarComo(owner);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        projectService.delete(1L);

        verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("Deve lancar excecao ao deletar projeto de outro usuario")
    void delete_outroUsuario_deveLancarExcecao() {
        autenticarComo(member);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.delete(1L))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
