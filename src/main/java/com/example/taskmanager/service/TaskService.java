package com.example.taskmanager.service;

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
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

    private static final int WIP_LIMIT = 5;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    public TaskResponseDTO create(TaskRequestDTO dto) {
        Task task = new Task();
        task.setTitle(dto.title());
        task.setDescription(dto.description());
        task.setPriority(dto.priority());
        task.setDeadline(dto.deadline());

        TaskStatus status = dto.status() != null ? dto.status() : TaskStatus.TODO;
        task.setStatus(status);

        if (dto.responsibleId() != null) {
            User responsible = findUserById(dto.responsibleId());
            task.setResponsible(responsible);

            if (status == TaskStatus.IN_PROGRESS) {
                validateWipLimit(responsible.getId(), null);
            }
        }

        return TaskResponseDTO.from(taskRepository.save(task));
    }

    public List<TaskResponseDTO> findAll() {
        return taskRepository.findAll().stream().map(TaskResponseDTO::from).toList();
    }

    public TaskResponseDTO findById(Long id) {
        return TaskResponseDTO.from(findTaskById(id));
    }

    public TaskResponseDTO update(Long id, TaskRequestDTO dto) {
        Task task = findTaskById(id);

        TaskStatus currentStatus = task.getStatus();
        TaskStatus newStatus = dto.status() != null ? dto.status() : currentStatus;

        validateStatusTransition(currentStatus, newStatus);

        validateCriticalDonePermission(dto.priority() != null ? dto.priority() : task.getPriority(), newStatus);

        User responsible = resolveResponsible(task, dto.responsibleId());
        if (newStatus == TaskStatus.IN_PROGRESS && currentStatus != TaskStatus.IN_PROGRESS && responsible != null) {
            validateWipLimit(responsible.getId(), id);
        }

        task.setTitle(dto.title());
        task.setDescription(dto.description());
        task.setStatus(newStatus);
        task.setPriority(dto.priority() != null ? dto.priority() : task.getPriority());
        task.setDeadline(dto.deadline());
        task.setResponsible(responsible);

        return TaskResponseDTO.from(taskRepository.save(task));
    }

    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new EntityNotFoundException("Tarefa não encontrada");
        }
        taskRepository.deleteById(id);
    }

    private void validateStatusTransition(TaskStatus current, TaskStatus next) {
        if (current == TaskStatus.DONE && next == TaskStatus.TODO) {
            throw new InvalidStatusTransitionException("Transição inválida: uma tarefa DONE não pode voltar para TODO. Transição permitida: DONE → IN_PROGRESS");
        }
    }

    private void validateCriticalDonePermission(TaskPriority priority, TaskStatus newStatus) {
        if (priority == TaskPriority.CRITICAL && newStatus == TaskStatus.DONE) {
            User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (currentUser.getRole() != UserRole.ADMIN) {
                throw new ForbiddenOperationException("Apenas administradores podem concluir tarefas com prioridade CRITICAL");
            }
        }
    }

    private void validateWipLimit(Long responsibleId, Long excludeTaskId) {
        long inProgress = taskRepository.countByResponsibleIdAndStatus(responsibleId, TaskStatus.IN_PROGRESS);

        boolean taskAlreadyCounted = excludeTaskId != null && taskRepository.findById(excludeTaskId).map(t -> t.getStatus() == TaskStatus.IN_PROGRESS).orElse(false);

        long effectiveCount = taskAlreadyCounted ? inProgress - 1 : inProgress;

        if (effectiveCount >= WIP_LIMIT) {
            throw new WipLimitExceededException("Limite WIP atingido: o responsável já possui " + WIP_LIMIT + " tarefas IN_PROGRESS. " + "Conclua ou remova uma tarefa antes de iniciar outra.");
        }
    }

    private Task findTaskById(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Tarefa não encontrada"));
    }

    private User findUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Usuário responsável não encontrado"));
    }

    private User resolveResponsible(Task task, Long newResponsibleId) {
        if (newResponsibleId != null) {
            return findUserById(newResponsibleId);
        }
        return task.getResponsible();
    }
}
