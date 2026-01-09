package com.antigravity.jira.repository;

import com.antigravity.jira.model.UserStory;
import com.antigravity.jira.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserStoryRepository extends JpaRepository<UserStory, Long> {
    List<UserStory> findByStatusOrderByIdDesc(Status status);

    List<UserStory> findBySprintIsNullOrderByIdAsc();

    List<UserStory> findBySprintIsNullAndProjectOrderByIdAsc(com.antigravity.jira.model.Project project);
}
