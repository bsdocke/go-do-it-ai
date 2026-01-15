package com.antigravity.jira.repository;

import com.antigravity.jira.model.Status;
import com.antigravity.jira.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatusRepository extends JpaRepository<Status, Long> {
    List<Status> findAllByOrderByPriorityAsc();

    // Find global statuses (project is null)
    List<Status> findByProjectIsNullOrderByPriorityAsc();

    Status findByName(String name);

    Status findByNameAndProject(String name, Project project);

    // Fallback or specific lookup for migration/init
    List<Status> findByNameAndProjectIsNull(String name);

    List<Status> findByProjectOrderByPriorityAsc(Project project);
}
