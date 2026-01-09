package com.antigravity.jira.repository;

import com.antigravity.jira.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatusRepository extends JpaRepository<Status, Long> {
    List<Status> findAllByOrderByPriorityAsc();

    Status findByName(String name);
}
