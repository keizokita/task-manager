package com.example.taskmanager.repository;

import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.enums.TaskPriority;
import com.example.taskmanager.model.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    long countByResponsibleIdAndStatus(Long responsibleId, TaskStatus status);

    List<Task> findByProjectIdIn(List<Long> projectIds);

    @Query("SELECT t FROM Task t WHERE " +
            "t.project.id IN :projectIds AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:priority IS NULL OR t.priority = :priority) AND " +
            "(CAST(:deadlineFrom AS timestamp) IS NULL OR t.deadline >= :deadlineFrom) AND " +
            "(CAST(:deadlineTo AS timestamp) IS NULL OR t.deadline <= :deadlineTo)")
    Page<Task> findWithFilters(

            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("deadlineFrom") LocalDateTime deadlineFrom,
            @Param("deadlineTo") LocalDateTime deadlineTo,
            @Param("projectIds") List<Long> projectIds,
            Pageable pageable);

    @Query("SELECT t.status, COUNT(t) FROM Task t WHERE t.project.id IN :projectIds GROUP BY t.status")
    List<Object[]> countByStatusGrouped(@Param("projectIds") List<Long> projectIds);

    @Query("SELECT t.priority, COUNT(t) FROM Task t WHERE t.project.id IN :projectIds GROUP BY t.priority")
    List<Object[]> countByPriorityGrouped(@Param("projectIds") List<Long> projectIds);

    @Query("SELECT t FROM Task t WHERE " +
            "t.project.id IN :projectIds AND " +
            "(LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Task> searchByTitleOrDescription(
            @Param("query") String query,
            @Param("projectIds") List<Long> projectIds,
            Pageable pageable);
}
