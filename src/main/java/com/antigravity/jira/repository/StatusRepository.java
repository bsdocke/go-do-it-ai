package com.antigravity.jira.repository;

import com.antigravity.jira.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatusRepository extends JpaRepository<Status, Long> {
    List<Status> findAllByOrderByPriorityAsc();

    // Find global statuses (project is null)
    List<Status> findByProjectIsNullOrderByPriorityAsc();

    Status findByName(String name);

    com.antigravity.jira.model.Status findByNameAndProject(String name, com.antigravity.jira.model.Project project);

    // Fallback or specific lookup for migration/init
    List<Status> findByNameAndProjectIsNull(String name);
}
