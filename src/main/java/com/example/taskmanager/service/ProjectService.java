package com.example.taskmanager.service;

import com.example.taskmanager.dto.request.ProjectRequestDTO;
import com.example.taskmanager.dto.response.ProjectResponseDTO;
import com.example.taskmanager.exception.ForbiddenOperationException;
import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.User;
import com.example.taskmanager.repository.ProjectRepository;
import com.example.taskmanager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    public ProjectResponseDTO create(ProjectRequestDTO dto) {
        User currentUser = getCurrentUser();

        Project project = new Project();
        project.setName(dto.name());
        project.setDescription(dto.description());
        project.setOwner(currentUser);
        project.setMembers(new HashSet<>(Set.of(currentUser)));

        return ProjectResponseDTO.from(projectRepository.save(project));
    }

    public List<ProjectResponseDTO> findAllForCurrentUser() {
        User currentUser = getCurrentUser();
        return projectRepository.findByOwnerIdOrMembersId(currentUser.getId(), currentUser.getId())
                .stream()
                .map(ProjectResponseDTO::from)
                .toList();
    }

    public ProjectResponseDTO findById(Long id) {
        return ProjectResponseDTO.from(findProjectById(id));
    }

    public ProjectResponseDTO update(Long id, ProjectRequestDTO dto) {
        Project project = findProjectById(id);
        assertIsOwner(project);

        project.setName(dto.name() != null ? dto.name() : project.getName());
        project.setDescription(dto.description() != null ? dto.description() : project.getDescription());

        return ProjectResponseDTO.from(projectRepository.save(project));
    }

    public void delete(Long id) {
        Project project = findProjectById(id);
        assertIsOwner(project);
        projectRepository.delete(project);
    }

    public ProjectResponseDTO addMember(Long projectId, Long userId) {
        Project project = findProjectById(projectId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        if (project.getMembers().contains(user)) {
            throw new IllegalArgumentException("Usuário já é membro deste projeto");
        }

        project.getMembers().add(user);
        return ProjectResponseDTO.from(projectRepository.save(project));
    }

    public void removeMember(Long projectId, Long userId) {
        Project project = findProjectById(projectId);

        if (project.getOwner().getId().equals(userId)) {
            throw new ForbiddenOperationException("Não é possível remover o owner do projeto");
        }

        project.getMembers().removeIf(u -> u.getId().equals(userId));
        projectRepository.save(project);
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private Project findProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Projeto não encontrado"));
    }

    private void assertIsOwner(Project project) {
        User currentUser = getCurrentUser();
        if (!project.getOwner().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Apenas o owner pode realizar esta operação");
        }
    }
}
