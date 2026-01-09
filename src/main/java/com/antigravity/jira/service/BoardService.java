package com.antigravity.jira.service;

import com.antigravity.jira.model.Status;
import com.antigravity.jira.model.UserStory;
import com.antigravity.jira.repository.StatusRepository;
import com.antigravity.jira.repository.UserStoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.antigravity.jira.model.Sprint;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BoardService {

    private final StatusRepository statusRepository;
    private final UserStoryRepository userStoryRepository;
    private final com.antigravity.jira.repository.SprintRepository sprintRepository;
    private final com.antigravity.jira.repository.ProjectRepository projectRepository;

    public BoardService(StatusRepository statusRepository, UserStoryRepository userStoryRepository,
            com.antigravity.jira.repository.SprintRepository sprintRepository,
            com.antigravity.jira.repository.ProjectRepository projectRepository) {
        this.statusRepository = statusRepository;
        this.userStoryRepository = userStoryRepository;
        this.sprintRepository = sprintRepository;
        this.projectRepository = projectRepository;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (projectRepository.count() == 0) {
            com.antigravity.jira.model.Project initialProject = new com.antigravity.jira.model.Project(
                    "Initial Project",
                    "Migration Project for existing data");
            initialProject = projectRepository.save(initialProject);

            List<Sprint> sprints = sprintRepository.findAll();
            for (Sprint sprint : sprints) {
                if (sprint.getProject() == null) {
                    sprint.setProject(initialProject);
                }
            }
            sprintRepository.saveAll(sprints);

            List<UserStory> stories = userStoryRepository.findAll();
            for (UserStory story : stories) {
                if (story.getProject() == null) {
                    story.setProject(initialProject);
                }
            }
            userStoryRepository.saveAll(stories);
        }
    }

    public com.antigravity.jira.model.Project getDefaultProject() {
        List<com.antigravity.jira.model.Project> projects = projectRepository.findAll();
        if (projects.isEmpty()) {
            return projectRepository.save(new com.antigravity.jira.model.Project("Default Project", "Auto-created"));
        }
        return projects.get(0);
    }

    public List<com.antigravity.jira.model.Project> getAllProjects() {
        return projectRepository.findAllByOrderByNameAsc();
    }

    public com.antigravity.jira.model.Project getProject(Long id) {
        return projectRepository.findById(id).orElse(null);
    }

    @Transactional
    public com.antigravity.jira.model.Project createProject(String name, String description) {
        return projectRepository.save(new com.antigravity.jira.model.Project(name, description));
    }

    @Transactional
    public com.antigravity.jira.model.Project updateProject(Long id, String name, String description) {
        com.antigravity.jira.model.Project project = getProject(id);
        if (project != null) {
            project.setName(name);
            project.setDescription(description);
            return projectRepository.save(project);
        }
        return null;
    }

    @Transactional
    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }

    public List<Status> getAllStatuses() {
        return statusRepository.findAllByOrderByPriorityAsc();
    }

    public List<UserStory> getAllStories() {
        return userStoryRepository.findAll();
    }

    public List<UserStory> getStoriesByStatus(Status status) {
        return userStoryRepository.findByStatusOrderByIdDesc(status);
    }

    @Transactional
    public void updateStoryStatus(Long storyId, Long statusId) {
        Optional<UserStory> storyOpt = userStoryRepository.findById(storyId);
        Optional<Status> statusOpt = statusRepository.findById(statusId);

        if (storyOpt.isPresent() && statusOpt.isPresent()) {
            UserStory story = storyOpt.get();
            story.setStatus(statusOpt.get());
            userStoryRepository.save(story);
        }
    }

    public List<UserStory> getStoriesForSprint(Long sprintId) {
        return getAllStories().stream()
                .filter(story -> story.getSprint() != null && story.getSprint().getId().equals(sprintId))
                .collect(Collectors.toList());
    }

    public List<UserStory> getBacklogStories() {
        return getAllStories().stream()
                .filter(story -> story.getSprint() == null)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserStory createStory(String title, String description, String assignee, Long sprintId) {
        Status analysisStatus = statusRepository.findByName("Analysis");
        if (analysisStatus == null) {
            throw new RuntimeException("Analysis status not found");
        }
        UserStory story = new UserStory();
        story.setTitle(title);
        story.setDescription(description);
        story.setAssignee(assignee);
        story.setStatus(analysisStatus);
        story.setProject(getDefaultProject());

        if (sprintId != null) {
            sprintRepository.findById(sprintId).ifPresent(story::setSprint);
        }

        return userStoryRepository.save(story);
    }

    @Transactional
    public UserStory updateStoryDetails(Long id, String title, String description, String assignee, Long sprintId) {
        Optional<UserStory> storyOpt = userStoryRepository.findById(id);
        if (storyOpt.isPresent()) {
            UserStory story = storyOpt.get();
            story.setTitle(title);
            story.setDescription(description);
            story.setAssignee(assignee);

            if (sprintId != null) {
                sprintRepository.findById(sprintId).ifPresent(story::setSprint);
            } else {
                story.setSprint(null);
            }

            return userStoryRepository.save(story);
        }
        return null;
    }

    public List<com.antigravity.jira.model.Sprint> getActiveSprints() {
        return sprintRepository.findByActiveTrue();
    }

    public com.antigravity.jira.model.Sprint getCurrentSprint() {
        List<com.antigravity.jira.model.Sprint> sprints = sprintRepository
                .findActiveSprintForDate(java.time.LocalDate.now());
        return sprints.isEmpty() ? null : sprints.get(0);
    }

    public List<Sprint> getActiveSprintsForBoard(java.time.LocalDate date) {
        List<Sprint> sprints = sprintRepository.findActiveSprintForDate(date);
        sprints.sort((s1, s2) -> s2.getStartDate().compareTo(s1.getStartDate()));
        return sprints;
    }

    public List<Sprint> getAllSprints() {
        return sprintRepository.findAll(org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "startDate"));
    }

    public Sprint getSprint(Long id) {
        return sprintRepository.findById(id).orElse(null);
    }

    @Transactional
    public Sprint createSprint(String name, String description, java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        Sprint sprint = new Sprint();
        sprint.setName(name);
        sprint.setDescription(description);
        sprint.setStartDate(startDate);
        sprint.setEndDate(endDate);
        sprint.setProject(getDefaultProject());

        java.time.LocalDate now = java.time.LocalDate.now();
        boolean isActive = (startDate.isBefore(now) || startDate.equals(now))
                && (endDate.isAfter(now) || endDate.equals(now));
        sprint.setActive(isActive);

        return sprintRepository.save(sprint);
    }

    @Transactional
    public Sprint updateSprint(Long id, String name, String description, java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        Sprint sprint = getSprint(id);
        if (sprint != null) {
            sprint.setName(name);
            sprint.setDescription(description);
            sprint.setStartDate(startDate);
            sprint.setEndDate(endDate);
            java.time.LocalDate now = java.time.LocalDate.now();
            boolean isActive = (startDate.isBefore(now) || startDate.equals(now))
                    && (endDate.isAfter(now) || endDate.equals(now));
            sprint.setActive(isActive);
            return sprintRepository.save(sprint);
        }
        return null;
    }

    @Transactional
    public void deleteSprint(Long id) {
        // Unlink stories
        List<UserStory> stories = getStoriesForSprint(id);
        for (UserStory story : stories) {
            story.setSprint(null);
            userStoryRepository.save(story);
        }
        sprintRepository.deleteById(id);
        sprintRepository.flush();
    }

    @Transactional
    public void deleteStory(Long id) {
        userStoryRepository.deleteById(id);
    }

    public UserStory getStory(Long id) {
        return userStoryRepository.findById(id).orElse(null);
    }
}
