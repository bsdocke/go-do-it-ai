package com.antigravity.jira.controller;

import com.antigravity.jira.model.Sprint;
import com.antigravity.jira.model.Status;
import com.antigravity.jira.model.UserStory;
import com.antigravity.jira.service.BoardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.antigravity.jira.model.AppUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Controller
public class BoardController {

    private static final String EMAIL = "email";
    private static final String BACKLOG_VIEW = "backlog";
    private static final String DEFAULT_VIEW = "board";

    private static final String ATTR_CURRENT_PROJECT = "currentProject";
    private static final String ATTR_PROJECT = "project";
    private static final String ATTR_PROJECTS = "projects";
    private static final String ATTR_SPRINT = "sprint";
    private static final String ATTR_SPRINTS = "sprints";
    private static final String ATTR_STORY = "story";
    private static final String ATTR_STORIES = "stories";
    private static final String ATTR_STATUS = "status";
    private static final String ATTR_STATUSES = "statuses";
    private static final String ATTR_BOARD_DATA = "boardData";

    private static final String VIEW_ADMIN = "admin";
    private static final String VIEW_PROJECTS = "projects";
    private static final String VIEW_SPRINTS = "sprints";
    private static final String VIEW_STATUSES = "statuses";
    private static final String VIEW_CURRENT = "current";

    private static final String REDIRECT_PREFIX = "redirect:/";

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @PutMapping("/context/project")
    public org.springframework.http.ResponseEntity<Void> switchProject(@RequestParam Long projectId,
            jakarta.servlet.http.HttpSession session) {
        session.setAttribute("projectId", projectId);
        return org.springframework.http.ResponseEntity.ok().header("HX-Refresh", "true").build();
    }

    @GetMapping("/current")
    public String currentBoard(Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute(ATTR_CURRENT_PROJECT);
        if (currentProject == null) {
            model.addAttribute(ATTR_BOARD_DATA, new ArrayList<>());
        } else {

            List<Sprint> activeSprints = boardService.getActiveSprintsForBoard(currentProject,
                    java.time.LocalDate.now());

            // We will enrich boardData to include statuses for that sprint's project.
            List<Map<String, Object>> enrichedBoardData = new ArrayList<>();
            if (!activeSprints.isEmpty()) {
                for (Sprint sprint : activeSprints) {
                    List<UserStory> stories = boardService.getStoriesForSprint(sprint.getId());
                    Map<String, List<UserStory>> storiesByStatus = stories.stream()
                            .collect(Collectors.groupingBy(s -> s.getStatus().getName()));

                    Map<String, Object> sprintData = new java.util.HashMap<>();
                    sprintData.put(ATTR_SPRINT, sprint);
                    sprintData.put("storiesByStatus", storiesByStatus);
                    // Add project statuses here
                    sprintData.put(ATTR_STATUSES, boardService.getStatusesForProject(sprint.getProject()));

                    enrichedBoardData.add(sprintData);
                }
            }

            model.addAttribute(ATTR_BOARD_DATA, enrichedBoardData);
        }
        return VIEW_CURRENT;
    }

    @GetMapping("/")
    public String landing(@AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            return REDIRECT_PREFIX + VIEW_CURRENT;
        }
        return "index";
    }

    @GetMapping("/backlog")
    public String backlog(Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute(ATTR_CURRENT_PROJECT);
        List<UserStory> stories = boardService.getBacklogStories(currentProject);
        model.addAttribute(ATTR_STORIES, stories);
        return BACKLOG_VIEW;
    }

    @GetMapping("/sprints")
    public String sprints(Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute(ATTR_CURRENT_PROJECT);
        model.addAttribute(ATTR_SPRINTS, boardService.getSprintsForProject(currentProject));
        return VIEW_SPRINTS;
    }

    @GetMapping("/sprints/form")
    public String getSprintForm(@RequestParam(required = false) Long id, Model model) {
        Sprint sprint = new Sprint();
        if (id != null) {
            sprint = boardService.getSprint(id);
        }
        model.addAttribute(ATTR_SPRINT, sprint);
        return "fragments :: sprintForm(sprint=${sprint})";
    }

    @PutMapping("/stories/{storyId}/move")
    @ResponseBody
    public String moveStory(@PathVariable Long storyId, @RequestParam Long statusId) {
        boardService.updateStoryStatus(storyId, statusId);
        // Returning empty string as HTMX will swap nothing if we don't return content
        // But since we are just doing a move, we might not want to re-render the whole
        // list
        // If we want to strictly follow "return application/html", we could return the
        // card again
        // but SortableJS has already moved it. So we just need to confirm.
        return "";
    }

    @PostMapping("/stories")
    public String createStory(@RequestParam String title,
            @RequestParam String description,
            @RequestParam String assignee,

            @RequestParam(required = false) Integer points,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false, defaultValue = DEFAULT_VIEW) String view,
            Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute(ATTR_CURRENT_PROJECT);
        UserStory story = boardService.createStory(title, description, assignee, points, sprintId, currentProject);
        model.addAttribute(ATTR_STORY, story);

        String oobTarget = null;
        if (story.getSprint() != null) {
            oobTarget = "beforeend:#story-list-" + story.getSprint().getId() + "-" + story.getStatus().getId();
        } else if (BACKLOG_VIEW.equals(view)) {
            oobTarget = "beforeend:#backlog-list";
        }

        model.addAttribute("oobTarget", oobTarget);

        if (BACKLOG_VIEW.equals(view)) {
            // If we are in backlog view
            if (story.getSprint() == null) {
                // New backlog item - render it
                return "fragments :: backlogItemOob(story=${story}, oobTarget=${oobTarget})";
            } else {
                // New sprint item created from backlog - don't render in backlog list
                return "";
            }
        }

        // Board view
        if (oobTarget != null) {
            return "fragments :: cardOob(story=${story}, oobTarget=${oobTarget})";
        }

        return ""; // No UI update needed
    }

    @GetMapping("/stories/form")
    public String getStoryForm(@RequestParam(required = false) Long id,
            @RequestParam(required = false, defaultValue = DEFAULT_VIEW) String view,
            Model model) {
        UserStory story = new UserStory();
        if (id != null) {
            story = boardService.getStory(id);
        }
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute(ATTR_CURRENT_PROJECT);
        List<Sprint> activeSprints = boardService.getActiveSprintsForBoard(currentProject, java.time.LocalDate.now());

        model.addAttribute(ATTR_STORY, story);
        model.addAttribute("view", view);
        model.addAttribute("activeSprints", activeSprints);
        return "fragments :: storyForm(story=${story}, activeSprints=${activeSprints}, view=${view}, projects=${projects})";
    }

    @GetMapping("/story/{id}")
    public String viewStory(@PathVariable Long id, Model model) {
        UserStory story = boardService.getStory(id);
        com.antigravity.jira.model.Project currentProject = story.getProject();

        // Ensure the view renders with the correct project context even if session is
        // different
        model.addAttribute(ATTR_CURRENT_PROJECT, currentProject);

        List<Sprint> activeSprints = boardService.getActiveSprintsForBoard(currentProject, java.time.LocalDate.now());

        model.addAttribute(ATTR_STORY, story);
        model.addAttribute("activeSprints", activeSprints);
        return "story_view";
    }

    @PutMapping("/stories/{id}")
    public String updateStory(@PathVariable Long id,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String assignee,

            @RequestParam(required = false) Integer points,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false, defaultValue = DEFAULT_VIEW) String view,
            Model model) {
        UserStory story = boardService.updateStoryDetails(id, title, description, assignee, points, sprintId);
        model.addAttribute(ATTR_STORY, story);

        // No OOB for update currently, just replace in place
        // But we must satisfy the fragment signature
        model.addAttribute("oobTarget", null);

        if (BACKLOG_VIEW.equals(view)) {
            return "fragments :: backlogItem(story=${story}, oobTarget=${oobTarget})";
        }
        if (story.getParentStory() != null) {
            return "fragments :: subTaskTile(subtask=${story})";
        }
        return "fragments :: card(story=${story}, oobTarget=${oobTarget})";
    }

    @DeleteMapping("/stories/{id}")
    @ResponseBody
    public String deleteStory(@PathVariable Long id) {
        boardService.deleteStory(id);
        return "";
    }

    @PostMapping("/stories/{id}/comments")
    public String addComment(@PathVariable Long id, @RequestParam String text, Model model,
            @AuthenticationPrincipal OAuth2User principal) {
        AppUser user = boardService.getOrCreateUser(principal.getAttribute(EMAIL), principal.getAttribute("name"));
        boardService.commentOnStory(id, text, user);
        // Reload story to show updated comments
        UserStory story = boardService.getStory(id);
        model.addAttribute(ATTR_STORY, story);
        // We only want to return the updated comment list or the whole form?
        // Requirement: "Add button... When clicked, it should show a modal containing
        // as its
        // only input a text box... Below the text box should be the usual Save and
        // Cancel
        // buttons... Clicking outside the modal acts as a cancel action, but does not
        // dismiss
        // the User Story modal."
        // Wait, the ADD button logic is likely separate.
        // But here we are POSTing the comment.
        // After adding, we probably want to update the comment list in the Story Modal.
        // So we should return the comment list fragment (which we'll need to create or
        // identify).
        // Let's assume we return the 'comments' fragment of the story form.
        return "fragments :: commentList(story=${story})";
    }

    @GetMapping("/stories/{id}/comments/form")
    public String getCommentForm(@PathVariable Long id, Model model) {
        model.addAttribute("storyId", id);
        return "fragments :: commentForm(storyId=${storyId})";
    }

    @DeleteMapping("/comments/{id}")
    @ResponseBody
    public String deleteComment(@PathVariable Long id) {
        boardService.deleteComment(id);
        return "";
    }

    @GetMapping("/stories/{id}/subtasks/form")
    public String getSubTaskForm(@PathVariable Long id, Model model) {
        model.addAttribute("parentStoryId", id);
        return "fragments :: subTaskForm(parentStoryId=${parentStoryId})";
    }

    @PostMapping("/stories/{id}/subtasks")
    public String createSubTask(@PathVariable Long id, @RequestParam String title, @RequestParam String description,
            Model model, @AuthenticationPrincipal OAuth2User principal) {
        AppUser user = boardService.getOrCreateUser(principal.getAttribute(EMAIL), principal.getAttribute("name"));
        boardService.createSubTask(id, title, description, user);

        // Return updated subtask list
        UserStory parent = boardService.getStory(id);
        model.addAttribute(ATTR_STORY, parent);
        return "fragments :: subTaskList(story=${story})";
    }

    @GetMapping("/projects")
    public String projects(Model model, @AuthenticationPrincipal OAuth2User principal) {
        // GlobalControllerAdvice adds currentUser, but we can also get it via principal
        // if needed
        // safer to use the service to ensure it's loaded bound to session logic if any
        AppUser user = boardService.getOrCreateUser(principal.getAttribute(EMAIL), principal.getAttribute("name"));
        model.addAttribute(ATTR_PROJECTS, boardService.getProjectsForUser(user));
        return VIEW_PROJECTS;
    }

    @GetMapping("/projects/form")
    public String getProjectForm(@RequestParam(required = false) Long id, Model model) {
        com.antigravity.jira.model.Project project = new com.antigravity.jira.model.Project();
        if (id != null) {
            project = boardService.getProject(id);
        }
        model.addAttribute(ATTR_PROJECT, project);
        return "fragments :: projectForm(project=${project})";
    }

    @PostMapping("/projects")
    public Object createProject(@RequestParam("projectName") String name,
            @RequestParam(required = false) String description,
            Model model,
            @AuthenticationPrincipal OAuth2User principal) {
        try {
            AppUser user = boardService.getOrCreateUser(principal.getAttribute(EMAIL),
                    principal.getAttribute("name"));
            com.antigravity.jira.model.Project project = boardService.createProject(name, description, user);
            model.addAttribute(ATTR_PROJECT, project);
            return "fragments :: projectRow(project=${project})";
        } catch (IllegalStateException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/projects/{id}")
    public String updateProject(@PathVariable Long id,
            @RequestParam("projectName") String name,
            @RequestParam(required = false) String description,
            Model model) {
        com.antigravity.jira.model.Project project = boardService.updateProject(id, name, description);
        model.addAttribute(ATTR_PROJECT, project);
        return "fragments :: projectRow(project=${project})";
    }

    @DeleteMapping("/projects/{id}")
    @ResponseBody
    public String deleteProject(@PathVariable Long id) {
        boardService.deleteProject(id);
        return "";
    }

    // --- Status Management ---

    @PostMapping("/sprints")
    public String createSprint(@RequestParam("sprintName") String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        java.time.LocalDate start;
        java.time.LocalDate end;

        try {
            if (startDate != null && !startDate.isEmpty()) {
                start = java.time.LocalDate.parse(startDate);
            } else {
                start = java.time.LocalDate.now();
            }
            if (endDate != null && !endDate.isEmpty()) {
                end = java.time.LocalDate.parse(endDate);
            } else {
                end = java.time.LocalDate.now().plusWeeks(2);
            }
            com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                    .getAttribute(ATTR_CURRENT_PROJECT);
            boardService.createSprint(name, description, start, end, currentProject);
            // Return updated list
            model.addAttribute(ATTR_SPRINTS, boardService.getSprintsForProject(currentProject));
            // HTMX expects partials. We can return the list fragment.
            return "fragments :: sprintList(sprints=${sprints})";
        } catch (Exception e) {
            // Handle error - simplistic
            return "";
        }
    }

    @PutMapping("/sprints/{id}")
    public String updateSprint(@PathVariable Long id,
            @RequestParam("sprintName") String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        try {
            java.time.LocalDate start = java.time.LocalDate.parse(startDate);
            java.time.LocalDate end = java.time.LocalDate.parse(endDate);
            boardService.updateSprint(id, name, description, start, end);

            Sprint sprint = boardService.getSprint(id);
            model.addAttribute(ATTR_SPRINT, sprint);
            return "fragments :: sprintRow(sprint=${sprint})";
        } catch (Exception e) {
            return "";
        }
    }

    @DeleteMapping("/sprints/{id}")
    public org.springframework.http.ResponseEntity<String> deleteSprint(@PathVariable Long id) {
        boardService.deleteSprint(id);
        return org.springframework.http.ResponseEntity.ok("");
    }

    // Helper to allow returning View name for success is messy with mixed return
    // types.

    @GetMapping("/statuses")
    public String statuses(Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute(ATTR_CURRENT_PROJECT);

        Map<Long, List<Status>> projectStatuses = new java.util.HashMap<>();
        if (currentProject != null) {
            projectStatuses.put(currentProject.getId(), boardService.getStatusesForProject(currentProject));
            model.addAttribute(ATTR_PROJECTS, List.of(currentProject));
        } else {
            model.addAttribute(ATTR_PROJECTS, new ArrayList<>());
        }
        model.addAttribute("projectStatuses", projectStatuses);

        return VIEW_STATUSES;
    }

    @GetMapping("/statuses/form")
    public String getStatusForm(@RequestParam(required = false) Long id, Model model) {
        Status status = new Status();
        if (id != null) {
            status = boardService.getStatus(id);
        }

        model.addAttribute(ATTR_STATUS, status);
        model.addAttribute(ATTR_PROJECTS, boardService.getAllProjects());
        return "fragments :: statusForm(status=${status}, projects=${projects})";
    }

    @PostMapping("/statuses")
    public Object createStatus(@RequestParam("statusName") String name, Model model) {
        try {
            com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                    .getAttribute(ATTR_CURRENT_PROJECT);
            Status status = boardService.createStatus(name, currentProject);
            model.addAttribute(ATTR_STATUS, status);
            return "fragments :: statusRowOob(status=${status})";
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    // types.
    // Better approach: Use ExceptionHandler or just return ResponseEntity for both.
    // But rendering the template to string manually requires template engine
    // injection.
    // Let's try a different strategy:
    // If we change return type to Object.

    // REVISIT: Simplest path - Catch exception, if error return
    // ResponseEntity.badRequest logic.
    // If success, return String (view name). Spring handles Object return types
    // fine.

    @PutMapping("/statuses/{id}")
    public Object updateStatus(@PathVariable Long id, @RequestParam("statusName") String name, Model model) {
        try {
            Status status = boardService.updateStatus(id, name);
            model.addAttribute(ATTR_STATUS, status);
            return "fragments :: statusRow(status=${status})";
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/statuses/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<String> deleteStatus(@PathVariable Long id) {
        try {
            boardService.deleteStatus(id);
            return org.springframework.http.ResponseEntity.ok("");
        } catch (IllegalStateException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/statuses/reorder")
    @ResponseBody
    public String reorderStatuses(@RequestParam("itemIds") List<Long> itemIds) {
        boardService.updateStatusOrdering(itemIds);
        return "";
    }

    // --- Admin ---

    @GetMapping("/admin")
    public String adminPage(Model model, @AuthenticationPrincipal OAuth2User principal) {
        // Simple security check (better via SecurityConfig but this works for now)
        // GlobalControllerAdvice injects 'currentUser'
        AppUser user = boardService.getOrCreateUser(principal.getAttribute(EMAIL), principal.getAttribute("name"));
        if (!"ADMIN".equals(user.getRole())) {
            return REDIRECT_PREFIX;
        }

        // Admin sees all projects and their members
        model.addAttribute(ATTR_PROJECTS, boardService.getAllProjects());
        return VIEW_ADMIN;
    }

    @PostMapping("/admin/projects/{projectId}/members")
    public String addProjectMember(@PathVariable Long projectId, @RequestParam("memberEmail") String email,
            Model model, @AuthenticationPrincipal OAuth2User principal) {
        try {
            boardService.addProjectMember(projectId, email);
            return REDIRECT_PREFIX + VIEW_ADMIN;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Reload admin page with error
            boardService.getOrCreateUser(principal.getAttribute(EMAIL), principal.getAttribute("name"));
            model.addAttribute(ATTR_PROJECTS, boardService.getAllProjects());
            model.addAttribute("errorMessage", e.getMessage());
            return VIEW_ADMIN;
        }
    }

    @DeleteMapping("/admin/projects/{projectId}/members/{uid}")
    @ResponseBody
    public String removeProjectMember(@PathVariable Long projectId, @PathVariable Long uid) {
        boardService.removeProjectMember(projectId, uid);
        return "";
    }
}
