package com.example.taskmanager.service;

import com.example.taskmanager.dto.response.TaskReportDTO;
import com.example.taskmanager.dto.request.TaskFilterDTO;
import com.example.taskmanager.dto.request.TaskRequestDTO;
import com.example.taskmanager.dto.response.TaskResponseDTO;
import com.example.taskmanager.exception.ForbiddenOperationException;
import com.example.taskmanager.exception.InvalidStatusTransitionException;
import com.example.taskmanager.exception.WipLimitExceededException;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.User;
import com.example.taskmanager.model.enums.TaskPriority;
import com.example.taskmanager.model.enums.TaskStatus;
import com.example.taskmanager.model.enums.UserRole;
import com.example.taskmanager.model.Project;
import com.example.taskmanager.repository.ProjectRepository;
import com.example.taskmanager.repository.TaskRepository;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private TaskService taskService;

    private User userComum;
    private User admin;
    private Task task;

    @BeforeEach
    void setUp() {
        userComum = new User(1L, "user@test.com", "senha", UserRole.MEMBER);
        admin = new User(2L, "admin@test.com", "senha", UserRole.ADMIN);

        task = new Task();
        task.setId(1L);
        task.setTitle("Tarefa teste");
        task.setPriority(TaskPriority.MEDIUM);
        task.setStatus(TaskStatus.TODO);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ==================== create ====================

    @Test
    @DisplayName("Deve criar tarefa com status TODO por padrão quando status não informado")
    void create_semStatus_devePadronizarTodo() {
        var dto = new TaskRequestDTO("Nova tarefa", null, null, TaskPriority.LOW, null, null, null);
        when(taskRepository.save(any())).thenReturn(buildTask(1L, TaskStatus.TODO, TaskPriority.LOW, null));

        TaskResponseDTO response = taskService.create(dto);

        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        verify(taskRepository).save(any());
    }

    @Test
    @DisplayName("Deve criar tarefa IN_PROGRESS quando responsável tem menos de 5 tarefas abertas")
    void create_comStatusInProgress_wipAbaixoDoLimite_deveSucesso() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userComum));
        when(taskRepository.countByResponsibleIdAndStatus(1L, TaskStatus.IN_PROGRESS)).thenReturn(4L);
        when(taskRepository.save(any())).thenReturn(buildTask(1L, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, userComum));

        var dto = new TaskRequestDTO("Nova", null, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null, 1L, null);

        assertThatNoException().isThrownBy(() -> taskService.create(dto));
    }

    @Test
    @DisplayName("Deve lançar WipLimitExceededException ao criar IN_PROGRESS quando responsável já tem 5 tarefas abertas")
    void create_comStatusInProgress_wipNoLimite_deveLancarExcecao() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userComum));
        when(taskRepository.countByResponsibleIdAndStatus(1L, TaskStatus.IN_PROGRESS)).thenReturn(5L);

        var dto = new TaskRequestDTO("Nova", null, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null, 1L, null);

        assertThatThrownBy(() -> taskService.create(dto))
                .isInstanceOf(WipLimitExceededException.class)
                .hasMessageContaining("Limite WIP atingido")
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("Deve lançar EntityNotFoundException ao criar com responsibleId inexistente")
    void create_comResponsavelInexistente_deveLancarExcecao() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        var dto = new TaskRequestDTO("Nova", null, null, TaskPriority.LOW, null, 99L, null);

        assertThatThrownBy(() -> taskService.create(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Usuário responsável não encontrado");
    }

    // ==================== findAll ====================

    @Test
    @DisplayName("Deve retornar lista de tarefas mapeada para DTO")
    void findAll_deveRetornarListaDeTarefas() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);
        task.setProject(project);
        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of(task));

        List<TaskResponseDTO> result = taskService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não há tarefas")
    void findAll_semTarefas_deveRetornarListaVazia() {
        autenticarComo(userComum);
        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of());
        when(taskRepository.findByProjectIdIn(List.of())).thenReturn(List.of());

        assertThat(taskService.findAll()).isEmpty();
    }

    // ==================== findById ====================

    @Test
    @DisplayName("Deve retornar DTO da tarefa quando ID existe")
    void findById_existente_deveRetornarDTO() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponseDTO result = taskService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Tarefa teste");
    }

    @Test
    @DisplayName("Deve lançar EntityNotFoundException quando ID não existe")
    void findById_inexistente_deveLancarExcecao() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Tarefa não encontrada");
    }

    // ==================== update: Regra 1 (transição de status) ====================

    @Test
    @DisplayName("Regra 1: transição DONE → TODO deve lançar InvalidStatusTransitionException")
    void update_doneParaTodo_deveLancarInvalidStatusTransitionException() {
        task.setStatus(TaskStatus.DONE);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null, null);

        assertThatThrownBy(() -> taskService.update(1L, dto))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("DONE")
                .hasMessageContaining("TODO");
    }

    @Test
    @DisplayName("Regra 1: transição DONE → IN_PROGRESS deve ser permitida")
    void update_doneParaInProgress_devePermitir() {
        task.setStatus(TaskStatus.DONE);
        task.setResponsible(userComum);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.countByResponsibleIdAndStatus(1L, TaskStatus.IN_PROGRESS)).thenReturn(0L);
        when(taskRepository.save(any())).thenReturn(task);

        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, null, null, null);

        assertThatNoException().isThrownBy(() -> taskService.update(1L, dto));
    }

    @Test
    @DisplayName("Regra 1: transição TODO → IN_PROGRESS deve ser permitida")
    void update_todoParaInProgress_devePermitir() {
        task.setStatus(TaskStatus.TODO);
        task.setResponsible(userComum);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.countByResponsibleIdAndStatus(1L, TaskStatus.IN_PROGRESS)).thenReturn(2L);
        when(taskRepository.save(any())).thenReturn(task);

        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, null, null, null);

        assertThatNoException().isThrownBy(() -> taskService.update(1L, dto));
    }

    // ==================== update: Regra 2 (CRITICAL + DONE) ====================

    @Test
    @DisplayName("Regra 2: USER não pode concluir tarefa com prioridade CRITICAL")
    void update_criticalDone_porUser_deveLancarForbiddenOperationException() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority(TaskPriority.CRITICAL);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        autenticarComo(userComum);

        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.DONE, TaskPriority.CRITICAL, null, null, null);

        assertThatThrownBy(() -> taskService.update(1L, dto))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("administradores");
    }

    @Test
    @DisplayName("Regra 2: ADMIN pode concluir tarefa com prioridade CRITICAL")
    void update_criticalDone_porAdmin_devePermitir() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority(TaskPriority.CRITICAL);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(task);
        autenticarComo(admin);

        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.DONE, TaskPriority.CRITICAL, null, null, null);

        assertThatNoException().isThrownBy(() -> taskService.update(1L, dto));
    }

    @Test
    @DisplayName("Regra 2: USER pode concluir tarefa com prioridade HIGH (não CRITICAL)")
    void update_highDone_porUser_devePermitir() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority(TaskPriority.HIGH);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(task);

        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.DONE, TaskPriority.HIGH, null, null, null);

        assertThatNoException().isThrownBy(() -> taskService.update(1L, dto));
    }

    // ==================== update: Regra 3 (WIP limit) ====================

    @Test
    @DisplayName("Regra 3: não deve permitir mover para IN_PROGRESS quando responsável já tem 5 tarefas abertas")
    void update_paraInProgress_wipNoLimite_deveLancarWipLimitExceededException() {
        task.setStatus(TaskStatus.TODO);
        task.setResponsible(userComum);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.countByResponsibleIdAndStatus(1L, TaskStatus.IN_PROGRESS)).thenReturn(5L);

        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, null, null, null);

        assertThatThrownBy(() -> taskService.update(1L, dto))
                .isInstanceOf(WipLimitExceededException.class)
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("Regra 3: tarefa já IN_PROGRESS não deve disparar verificação de WIP ao ser atualizada")
    void update_tarefaJaInProgress_naoDeveVerificarWip() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setResponsible(userComum);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(task);

        var dto = new TaskRequestDTO("Atualizada", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, null, null, null);

        assertThatNoException().isThrownBy(() -> taskService.update(1L, dto));
        verify(taskRepository, never()).countByResponsibleIdAndStatus(any(), any());
    }

    // ==================== update: Regra 3b (WIP limit na troca de responsavel) ====================

    @Test
    @DisplayName("Regra 3b: deve validar WIP do novo responsavel ao trocar responsavel de tarefa ja IN_PROGRESS")
    void update_trocarResponsavelComTarefaJaInProgress_deveValidarWipDoNovoResponsavel() {
        User novoResponsavel = new User(3L, "novo@test.com", "senha", UserRole.MEMBER);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setResponsible(userComum);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userRepository.findById(3L)).thenReturn(Optional.of(novoResponsavel));
        when(taskRepository.countByResponsibleIdAndStatus(3L, TaskStatus.IN_PROGRESS)).thenReturn(5L);

        var dto = new TaskRequestDTO("Tarefa teste", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, null, 3L, null);

        assertThatThrownBy(() -> taskService.update(1L, dto))
                .isInstanceOf(WipLimitExceededException.class)
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("Regra 3b: deve permitir troca de responsavel em tarefa IN_PROGRESS quando novo responsavel esta abaixo do limite WIP")
    void update_trocarResponsavelComTarefaInProgress_wipAbaixoLimite_devePermitir() {
        User novoResponsavel = new User(3L, "novo@test.com", "senha", UserRole.MEMBER);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setResponsible(userComum);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userRepository.findById(3L)).thenReturn(Optional.of(novoResponsavel));
        when(taskRepository.countByResponsibleIdAndStatus(3L, TaskStatus.IN_PROGRESS)).thenReturn(4L);
        when(taskRepository.save(any())).thenReturn(task);

        var dto = new TaskRequestDTO("Tarefa teste", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, null, 3L, null);

        assertThatNoException().isThrownBy(() -> taskService.update(1L, dto));
    }

    @Test
    @DisplayName("Regra 3b: nao deve revalidar WIP quando responsavel nao muda em tarefa IN_PROGRESS")
    void update_mesmoResponsavelTarefaInProgress_naoDeveRevalidarWip() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setResponsible(userComum);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(task);

        var dto = new TaskRequestDTO("Tarefa teste", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, null, 1L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userComum));

        assertThatNoException().isThrownBy(() -> taskService.update(1L, dto));
        verify(taskRepository, never()).countByResponsibleIdAndStatus(any(), any());
    }

    // ==================== update: Regra 4 (preservar campos no update parcial) ====================

    @Test
    @DisplayName("Deve manter titulo original quando DTO nao informa titulo")
    void update_semTituloNoDTO_deveManterTituloOriginal() {
        task.setStatus(TaskStatus.TODO);
        task.setTitle("Titulo Original");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = new TaskRequestDTO(null, null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null, null);

        TaskResponseDTO result = taskService.update(1L, dto);

        assertThat(result.title()).isEqualTo("Titulo Original");
    }

    @Test
    @DisplayName("Deve manter descricao original quando DTO nao informa descricao")
    void update_semDescricaoNoDTO_deveManterDescricaoOriginal() {
        task.setStatus(TaskStatus.TODO);
        task.setDescription("Descricao Original");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = new TaskRequestDTO("Titulo", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null, null);

        TaskResponseDTO result = taskService.update(1L, dto);

        assertThat(result.description()).isEqualTo("Descricao Original");
    }

    @Test
    @DisplayName("Deve atualizar titulo quando DTO informa novo titulo")
    void update_comTituloNoDTO_deveAtualizarTitulo() {
        task.setStatus(TaskStatus.TODO);
        task.setTitle("Titulo Antigo");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = new TaskRequestDTO("Titulo Novo", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null, null);

        TaskResponseDTO result = taskService.update(1L, dto);

        assertThat(result.title()).isEqualTo("Titulo Novo");
    }

    // ==================== findAll com filtros ====================

    @Test
    @DisplayName("Deve filtrar tarefas por status")
    void findAll_filtrarPorStatus_deveRetornarApenasTarefasComStatus() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);
        task.setProject(project);
        task.setStatus(TaskStatus.TODO);

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.findWithFilters(TaskStatus.TODO, null, null, null, List.of(1L), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(task)));

        var filter = new TaskFilterDTO(TaskStatus.TODO, null, null, null);
        Page<TaskResponseDTO> result = taskService.findAll(filter, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    @DisplayName("Deve filtrar tarefas por prioridade")
    void findAll_filtrarPorPrioridade_deveRetornarApenasTarefasComPrioridade() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);
        task.setProject(project);
        task.setPriority(TaskPriority.HIGH);

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.findWithFilters(null, TaskPriority.HIGH, null, null, List.of(1L), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(task)));

        var filter = new TaskFilterDTO(null, TaskPriority.HIGH, null, null);
        Page<TaskResponseDTO> result = taskService.findAll(filter, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("Deve retornar pagina correta com paginacao")
    void findAll_comPaginacao_deveRetornarPaginaCorreta() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);
        task.setProject(project);

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.findWithFilters(null, null, null, null, List.of(1L), PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));

        var filter = new TaskFilterDTO(null, null, null, null);
        Page<TaskResponseDTO> result = taskService.findAll(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getNumber()).isEqualTo(0);
    }

    // ==================== report ====================

    @Test
    @DisplayName("Deve retornar contadores agrupados por status")
    void report_deveAgruparPorStatus() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.countByStatusGrouped(List.of(1L))).thenReturn(List.of(
                new Object[]{TaskStatus.TODO, 3L},
                new Object[]{TaskStatus.IN_PROGRESS, 2L},
                new Object[]{TaskStatus.DONE, 5L}
        ));
        when(taskRepository.countByPriorityGrouped(List.of(1L))).thenReturn(List.of());

        TaskReportDTO result = taskService.getReport();

        assertThat(result.byStatus()).containsEntry(TaskStatus.TODO, 3L);
        assertThat(result.byStatus()).containsEntry(TaskStatus.IN_PROGRESS, 2L);
        assertThat(result.byStatus()).containsEntry(TaskStatus.DONE, 5L);
        assertThat(result.total()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Deve retornar contadores agrupados por prioridade")
    void report_deveAgruparPorPrioridade() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.countByStatusGrouped(List.of(1L))).thenReturn(List.of());
        when(taskRepository.countByPriorityGrouped(List.of(1L))).thenReturn(List.of(
                new Object[]{TaskPriority.LOW, 2L},
                new Object[]{TaskPriority.CRITICAL, 1L}
        ));

        TaskReportDTO result = taskService.getReport();

        assertThat(result.byPriority()).containsEntry(TaskPriority.LOW, 2L);
        assertThat(result.byPriority()).containsEntry(TaskPriority.CRITICAL, 1L);
    }

    @Test
    @DisplayName("Deve retornar contadores zerados quando nao ha tarefas")
    void report_semTarefas_deveRetornarContadoresVazios() {
        autenticarComo(userComum);

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of());
        when(taskRepository.countByStatusGrouped(List.of())).thenReturn(List.of());
        when(taskRepository.countByPriorityGrouped(List.of())).thenReturn(List.of());

        TaskReportDTO result = taskService.getReport();

        assertThat(result.byStatus()).isEmpty();
        assertThat(result.byPriority()).isEmpty();
        assertThat(result.total()).isEqualTo(0L);
    }

    // ==================== search ====================

    @Test
    @DisplayName("Deve retornar tarefas cujo titulo contem o termo buscado")
    void search_porTitulo_deveRetornarTarefasComTituloCorrespondente() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);
        task.setProject(project);
        task.setTitle("Deploy producao");

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.searchByTitleOrDescription("deploy", List.of(1L), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(task)));

        Page<TaskResponseDTO> result = taskService.search("deploy", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).contains("Deploy");
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando nenhuma tarefa corresponde ao termo")
    void search_semResultados_deveRetornarListaVazia() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.searchByTitleOrDescription("inexistente", List.of(1L), PageRequest.of(0, 20)))
                .thenReturn(Page.empty());

        Page<TaskResponseDTO> result = taskService.search("inexistente", PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    // ==================== create/findAll: vinculo com Project ====================

    @Test
    @DisplayName("Deve associar tarefa ao projeto ao criar com projectId")
    void create_comProjectId_deveAssociarTarefaAoProjeto() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(taskRepository.save(any())).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            return t;
        });

        var dto = new TaskRequestDTO("Tarefa", null, null, TaskPriority.LOW, null, null, 1L);

        TaskResponseDTO result = taskService.create(dto);

        assertThat(result.projectId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Deve retornar apenas tarefas dos projetos do usuario autenticado")
    void findAll_deveRetornarApenasTarefasDosProjetos() {
        autenticarComo(userComum);
        Project project = buildProject(1L, userComum);
        task.setProject(project);

        when(projectRepository.findByOwnerIdOrMembersId(1L, 1L)).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of(task));

        List<TaskResponseDTO> result = taskService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).projectId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Deve lancar excecao ao criar tarefa com projectId inexistente")
    void create_comProjectIdInexistente_deveLancarExcecao() {
        autenticarComo(userComum);
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        var dto = new TaskRequestDTO("Tarefa", null, null, TaskPriority.LOW, null, null, 99L);

        assertThatThrownBy(() -> taskService.create(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Projeto não encontrado");
    }

    // ==================== delete ====================

    @Test
    @DisplayName("Deve deletar tarefa existente com sucesso")
    void delete_existente_deveDeletar() {
        when(taskRepository.existsById(1L)).thenReturn(true);

        taskService.delete(1L);

        verify(taskRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Deve lançar EntityNotFoundException ao tentar deletar tarefa inexistente")
    void delete_inexistente_deveLancarExcecao() {
        when(taskRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Tarefa não encontrada");

        verify(taskRepository, never()).deleteById(any());
    }

    // ==================== helpers ====================

    private void autenticarComo(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Project buildProject(Long id, User owner) {
        Project p = new Project();
        p.setId(id);
        p.setName("Projeto");
        p.setOwner(owner);
        p.setMembers(new HashSet<>(Set.of(owner)));
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private Task buildTask(Long id, TaskStatus status, TaskPriority priority, User responsible) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Tarefa");
        t.setStatus(status);
        t.setPriority(priority);
        t.setResponsible(responsible);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return t;
    }
}
