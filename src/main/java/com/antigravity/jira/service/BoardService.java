package com.antigravity.jira.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.antigravity.jira.model.AppUser;
import com.antigravity.jira.model.Project;
import com.antigravity.jira.model.Sprint;
import com.antigravity.jira.model.Status;
import com.antigravity.jira.model.UserStory;
import com.antigravity.jira.repository.AppUserRepository;
import com.antigravity.jira.repository.ProjectRepository;
import com.antigravity.jira.repository.SprintRepository;
import com.antigravity.jira.repository.StatusRepository;
import com.antigravity.jira.repository.UserStoryRepository;
import com.antigravity.jira.exception.UserStoryException;

@Service
public class BoardService {

    private static final String USER = "USER";
    private static final String ADMIN = "ADMIN";

    private final StatusRepository statusRepository;
    private final UserStoryRepository userStoryRepository;
    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final AppUserRepository appUserRepository;

    public BoardService(UserStoryRepository userStoryRepository, SprintRepository sprintRepository,
            StatusRepository statusRepository, ProjectRepository projectRepository,
            AppUserRepository appUserRepository) {
        this.userStoryRepository = userStoryRepository;
        this.sprintRepository = sprintRepository;
        this.statusRepository = statusRepository;
        this.projectRepository = projectRepository;
        this.appUserRepository = appUserRepository;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        // Deduplicate Users if any exist (Fix for NonUniqueResultException)
        // Group by email, keep the one with lowest ID, delete others
        List<AppUser> allUsers = appUserRepository.findAll();
        Map<String, List<AppUser>> usersByEmail = allUsers.stream()
                .collect(Collectors.groupingBy(AppUser::getEmail));

        for (Map.Entry<String, List<AppUser>> entry : usersByEmail.entrySet()) {
            List<AppUser> duplicates = entry.getValue();
            if (duplicates.size() > 1) {
                // Sort by ID to keep the oldest
                duplicates.sort(Comparator.comparing(AppUser::getId));
                for (int i = 1; i < duplicates.size(); i++) {
                    // Remove from projects first to avoid FK constraint issues?
                    // But duplicates might be in project_members too.
                    // Let's just delete the user, cascade should handle it?
                    // Actually Project.members is ManyToMany, mapped by join table.
                    // We should probably rely on manual cleanup if CascadeType.ALL is on Project
                    // side?
                    // Let's just try deleting the user.
                    // Wait, we need to remove them from any projects they are in to be safe.
                    AppUser dupe = duplicates.get(i);
                    // We can't easily find projects for a user without a reverse lookup or scan.
                    // Since this is a patch, let's scan all projects.
                    for (Project p : projectRepository.findAll()) {
                        if (p.getMembers().removeIf(m -> m.getId().equals(dupe.getId()))) {
                            // If removed, save project
                            projectRepository.save(p);
                        }
                    }
                    appUserRepository.delete(dupe);
                }
            }
        }
    }

    public List<UserStory> getAllStories() {
        return userStoryRepository.findAll();
    }

    // --- User Sync and RBAC ---

    public AppUser getOrCreateUser(String email, String name) {
        List<AppUser> users = appUserRepository.findByEmail(email);
        if (users.isEmpty()) {
            boolean isFirstUser = appUserRepository.count() == 0;
            String role = isFirstUser ? ADMIN : USER;
            return appUserRepository.save(new AppUser(email, name, role));
        }
        // Handle duplicates if they exist
        if (users.size() > 1) {
            // Log warning?
            // Return the first one (oldest by ID usually if simpler)
            // Or sort by ID
            users.sort(Comparator.comparing(AppUser::getId));
            // We could delete others here too, but let's just be safe and return one.
            return users.get(0);
        }
        return users.get(0);
    }

    public List<Project> getProjectsForUser(AppUser user) {
        if (ADMIN.equals(user.getRole())) {
            return projectRepository.findAll(org.springframework.data.domain.Sort.by("name"));
        }
        return projectRepository.findAll(org.springframework.data.domain.Sort.by("name")).stream()
                .filter(p -> p.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId())))
                .toList();
    }

    // --- Project Management ---

    public Project getDefaultProject() {
        List<Project> projects = projectRepository.findAll();
        if (projects.isEmpty()) {
            return projectRepository.save(new Project("Default Project", "Auto-created"));
        }
        return projects.get(0);
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAllByOrderByNameAsc();
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id).orElse(null);
    }

    public List<UserStory> getBacklogStories(Project project) {
        if (project == null)
            return java.util.Collections.emptyList();
        return userStoryRepository.findBySprintIsNullAndProjectOrderByIdAsc(project);
    }

    public List<Sprint> getSprintsForProject(Project project) {
        if (project == null)
            return java.util.Collections.emptyList();
        return sprintRepository.findAll().stream()
                .filter(s -> s.getProject() != null && s.getProject().getId().equals(project.getId()))
                .toList();
    }

    public List<Sprint> getActiveSprintsForBoard(Project project, java.time.LocalDate date) {
        if (project == null)
            return java.util.Collections.emptyList();
        List<Sprint> active = getActiveSprintsForBoard(date);
        return active.stream()
                .filter(s -> s.getProject() != null && s.getProject().getId().equals(project.getId()))
                .toList();
    }

    @Transactional
    public Project createProject(String name, String description) {
        return projectRepository.save(new Project(name, description));
    }

    private static final int MAX_PROJECTS_PER_USER = 5;

    @Transactional
    public Project createProject(String name, String description, AppUser owner) {
        if (!ADMIN.equals(owner.getRole())) {
            List<Project> userProjects = getProjectsForUser(owner);
            if (userProjects.size() >= MAX_PROJECTS_PER_USER) {
                throw new IllegalStateException("Project limit reached (Max " + MAX_PROJECTS_PER_USER + ")");
            }
        }
        Project project = new Project(name, description);
        project.getMembers().add(owner);
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateProject(Long id, String name, String description) {
        Project project = getProject(id);
        if (project != null) {
            project.setName(name);
            project.setDescription(description);
            return projectRepository.save(project);
        }
        return null; // or throw
    }

    @Transactional
    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }

    @Transactional
    public void addProjectMember(Long projectId, String email) {
        Project project = getProject(projectId);
        List<AppUser> users = appUserRepository.findByEmail(email);

        if (users.isEmpty()) {
            throw new IllegalArgumentException("User not found with email: " + email);
        }

        // Pick the first one if duplicates exist
        AppUser user = users.get(0);
        if (users.size() > 1) {
            users.sort(Comparator.comparing(AppUser::getId));
            user = users.get(0);
        }

        if (!ADMIN.equals(user.getRole())) {
            List<Project> userProjects = getProjectsForUser(user);
            if (userProjects.size() >= MAX_PROJECTS_PER_USER) {
                throw new IllegalStateException(
                        "User has reached the maximum project limit (" + MAX_PROJECTS_PER_USER + ")");
            }
        }

        project.getMembers().add(user);
        // Transactional annotation handles the save/flush
    }

    @Transactional
    public void removeProjectMember(Long projectId, Long userId) {
        Project project = getProject(projectId);
        project.getMembers().removeIf(m -> m.getId().equals(userId));
        projectRepository.save(project);
    }

    public List<Status> getAllStatuses() {
        return statusRepository.findAllByOrderByPriorityAsc();
    }

    public List<Status> getStatusesForProject(Project project) {

        // TODO actually filter in the repository, don't fetch every status then just
        // filter in Java
        return statusRepository.findAll().stream()
                .filter(s -> s.getProject() != null && project.getId().equals(s.getProject().getId()))
                .sorted((s1, s2) -> s1.getPriority().compareTo(s2.getPriority()))
                .toList();
    }

    @Transactional
    public Status createStatus(String name, Project project) {
        Status existingName = statusRepository.findByNameAndProject(name, project);
        if (existingName != null) {
            throw new IllegalArgumentException("Status with name '" + name + "' already exists in this project.");
        }

        Status status = new Status();
        status.setName(name);
        status.setProject(project); // Can be null for global? No, req says project specific.

        // Auto-priority: last
        List<Status> existing = getStatusesForProject(project);
        int maxPriority = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getPriority();
        status.setPriority(maxPriority + 1);

        return statusRepository.save(status);
    }

    @Transactional
    public Status updateStatus(Long id, String name) {
        Optional<Status> opt = statusRepository.findById(id);
        if (opt.isPresent()) {
            Status s = opt.get();
            if (!s.getName().equals(name)) {
                Status existingName = statusRepository.findByNameAndProject(name, s.getProject());
                if (existingName != null) {
                    throw new IllegalArgumentException(
                            "Status with name '" + name + "' already exists in this project.");
                }
            }
            s.setName(name);
            return statusRepository.save(s);
        }
        return null;
    }

    @Transactional
    public void deleteStatus(Long id) {
        Status status = statusRepository.findById(id).orElse(null);
        if (status != null) {
            List<UserStory> stories = userStoryRepository.findByStatusOrderByIdDesc(status);
            if (!stories.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot delete status containing stories. Please move or delete the stories first.");
            }
            statusRepository.delete(status);
        }
    }

    @Transactional
    public void updateStatusOrdering(List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            int priority = i + 1;
            statusRepository.findById(id).ifPresent(s -> {
                s.setPriority(priority); // 1-based priority
                statusRepository.save(s);
            });
        }
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
                .toList();
    }

    public List<UserStory> getBacklogStories() {
        return getAllStories().stream()
                .filter(story -> story.getSprint() == null)
                .toList();
    }

    @Transactional
    public UserStory createStory(String title, String description, String assignee, Long sprintId,
            com.antigravity.jira.model.Project project) {
        if (project == null) {
            project = getDefaultProject(); // Fallback if somehow null
        }

        List<Status> projectStatuses = getStatusesForProject(project);

        if (projectStatuses.isEmpty()) {
            throw new UserStoryException("No statuses defined for project: " + project.getName());
        }

        Status defaultStatus = projectStatuses.get(0); // First one is lowest priority due to sorting in
                                                       // getStatusesForProject

        UserStory story = new UserStory();
        story.setTitle(title);
        story.setDescription(description);
        story.setAssignee(assignee);
        story.setStatus(defaultStatus);
        story.setProject(project);

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

    public List<Sprint> getActiveSprints() {
        return sprintRepository.findByActiveTrue();
    }

    public Sprint getCurrentSprint() {
        List<Sprint> sprints = sprintRepository
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
    public Sprint createSprint(String name, String description, LocalDate startDate,
            LocalDate endDate, Project project) {
        Sprint sprint = new Sprint();
        sprint.setName(name);
        sprint.setDescription(description);
        sprint.setStartDate(startDate);
        sprint.setEndDate(endDate);
        if (project == null)
            project = getDefaultProject();
        sprint.setProject(project);

        java.time.LocalDate now = java.time.LocalDate.now();
        boolean isActive = (startDate.isBefore(now) || startDate.equals(now))
                && (endDate.isAfter(now) || endDate.equals(now));
        sprint.setActive(isActive);

        return sprintRepository.save(sprint);
    }

    @Transactional
    public Sprint updateSprint(Long id, String name, String description, LocalDate startDate,
            LocalDate endDate) {
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

    public Status getStatus(Long id) {
        return statusRepository.findById(id).orElse(null);
    }
}
