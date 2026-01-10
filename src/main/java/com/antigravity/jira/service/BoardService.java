package com.antigravity.jira.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BoardService {

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
        java.util.List<AppUser> allUsers = appUserRepository.findAll();
        java.util.Map<String, java.util.List<AppUser>> usersByEmail = allUsers.stream()
                .collect(Collectors.groupingBy(AppUser::getEmail));

        for (java.util.Map.Entry<String, java.util.List<AppUser>> entry : usersByEmail.entrySet()) {
            java.util.List<AppUser> duplicates = entry.getValue();
            if (duplicates.size() > 1) {
                // Sort by ID to keep the oldest
                duplicates.sort(java.util.Comparator.comparing(AppUser::getId));
                AppUser keep = duplicates.get(0);

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
                    for (com.antigravity.jira.model.Project p : projectRepository.findAll()) {
                        if (p.getMembers().removeIf(m -> m.getId().equals(dupe.getId()))) {
                            // If removed, save project
                            projectRepository.save(p);
                        }
                    }
                    appUserRepository.delete(dupe);
                }
            }
        }

        if (projectRepository.count() == 0) {
            // This init logic is for migrating old data or setting up a first project
            // if no projects exist.
            // With user-aware project creation, this might be less critical,
            // but kept for initial setup or migration.
            Project initialProject = new Project(
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

            // Migrate global statuses to Initial Project (if any exist from old DB)
            List<Status> globalStatuses = statusRepository.findByProjectIsNullOrderByPriorityAsc();
            for (Status status : globalStatuses) {
                status.setProject(initialProject);
            }
            statusRepository.saveAll(globalStatuses);

            // If no statuses exist for init project (e.g. fresh DB), populate defaults
            if (getStatusesForProject(initialProject).isEmpty()) {
                String[] defaults = { "Analysis", "Ready", "In Progress", "Review", "Testing", "Staged", "Complete" };
                for (String name : defaults) {
                    createStatus(name, initialProject);
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
            String role = isFirstUser ? "ADMIN" : "USER";
            return appUserRepository.save(new AppUser(email, name, role));
        }
        // Handle duplicates if they exist
        if (users.size() > 1) {
            // Log warning?
            // Return the first one (oldest by ID usually if simpler)
            // Or sort by ID
            users.sort(java.util.Comparator.comparing(AppUser::getId));
            // We could delete others here too, but let's just be safe and return one.
            return users.get(0);
        }
        return users.get(0);
    }

    public List<com.antigravity.jira.model.Project> getProjectsForUser(AppUser user) {
        if ("ADMIN".equals(user.getRole())) {
            return projectRepository.findAll(org.springframework.data.domain.Sort.by("name"));
        }
        return projectRepository.findAll(org.springframework.data.domain.Sort.by("name")).stream()
                .filter(p -> p.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId())))
                .collect(Collectors.toList());
    }

    // --- Project Management ---

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

    public List<UserStory> getBacklogStories(com.antigravity.jira.model.Project project) {
        if (project == null)
            return java.util.Collections.emptyList();
        return userStoryRepository.findBySprintIsNullAndProjectOrderByIdAsc(project);
    }

    public List<Sprint> getSprintsForProject(com.antigravity.jira.model.Project project) {
        if (project == null)
            return java.util.Collections.emptyList();
        return sprintRepository.findAll().stream()
                .filter(s -> s.getProject() != null && s.getProject().getId().equals(project.getId()))
                .collect(Collectors.toList());
    }

    public List<Sprint> getActiveSprintsForBoard(com.antigravity.jira.model.Project project, java.time.LocalDate date) {
        if (project == null)
            return java.util.Collections.emptyList();
        List<Sprint> active = getActiveSprintsForBoard(date);
        return active.stream()
                .filter(s -> s.getProject() != null && s.getProject().getId().equals(project.getId()))
                .collect(Collectors.toList());
    }

    @Transactional
    public com.antigravity.jira.model.Project createProject(String name, String description) {
        return projectRepository.save(new com.antigravity.jira.model.Project(name, description));
    }

    @Transactional
    public com.antigravity.jira.model.Project createProject(String name, String description, AppUser owner) {
        com.antigravity.jira.model.Project project = new com.antigravity.jira.model.Project(name, description);
        project.getMembers().add(owner);
        return projectRepository.save(project);
    }

    @Transactional
    public com.antigravity.jira.model.Project updateProject(Long id, String name, String description) {
        com.antigravity.jira.model.Project project = getProject(id);
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
            users.sort(java.util.Comparator.comparing(AppUser::getId));
            user = users.get(0);
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

    public List<Status> getStatusesForProject(com.antigravity.jira.model.Project project) {
        // We'll need to add a repository method for this or filter
        // Ideally: return statusRepository.findByProjectOrderByPriorityAsc(project);
        // But for now, let's add the method to repository if not exists, or just filter
        // Converting to stream filter for immediate compilation if repo method misses,
        // but better to add repo method.
        // Checking StatusRepository again... it doesn't have it yet.
        // Let's rely on adding it to StatusRepo in a bit or use Example.
        // For now, let's assume we added findAllByProjectOrderByPriorityAsc to Repo or
        // use manual filter.
        // Since I can't edit Repo in same atomic step easily without checking,
        // I will implement a safe filter here or separate step.
        // Actually, let's update Repository first or use this:
        return statusRepository.findAll().stream()
                .filter(s -> s.getProject() != null && project.getId().equals(s.getProject().getId()))
                .sorted((s1, s2) -> s1.getPriority().compareTo(s2.getPriority()))
                .collect(Collectors.toList());
    }

    @Transactional
    public Status createStatus(String name, com.antigravity.jira.model.Project project) {
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
                .collect(Collectors.toList());
    }

    public List<UserStory> getBacklogStories() {
        return getAllStories().stream()
                .filter(story -> story.getSprint() == null)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserStory createStory(String title, String description, String assignee, Long sprintId,
            com.antigravity.jira.model.Project project) {
        if (project == null) {
            project = getDefaultProject(); // Fallback if somehow null
        }

        List<Status> projectStatuses = getStatusesForProject(project);

        if (projectStatuses.isEmpty()) {
            throw new RuntimeException("No statuses defined for project: " + project.getName());
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
            java.time.LocalDate endDate, com.antigravity.jira.model.Project project) {
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

    public Status getStatus(Long id) {
        return statusRepository.findById(id).orElse(null);
    }
}
