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

@Controller
public class BoardController {

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

    @GetMapping("/")
    public String index(Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute("currentProject");
        if (currentProject == null) {
            model.addAttribute("boardData", new ArrayList<>());
            return "index";
        }

        List<Sprint> activeSprints = boardService.getActiveSprintsForBoard(currentProject, java.time.LocalDate.now());

        // We will enrich boardData to include statuses for that sprint's project.
        List<Map<String, Object>> enrichedBoardData = new ArrayList<>();
        if (!activeSprints.isEmpty()) {
            for (Sprint sprint : activeSprints) {
                List<UserStory> stories = boardService.getStoriesForSprint(sprint.getId());
                Map<String, List<UserStory>> storiesByStatus = stories.stream()
                        .collect(Collectors.groupingBy(s -> s.getStatus().getName()));

                Map<String, Object> sprintData = new java.util.HashMap<>();
                sprintData.put("sprint", sprint);
                sprintData.put("storiesByStatus", storiesByStatus);
                // Add project statuses here
                sprintData.put("statuses", boardService.getStatusesForProject(sprint.getProject()));

                enrichedBoardData.add(sprintData);
            }
        }

        model.addAttribute("boardData", enrichedBoardData);
        return "index";
    }

    @GetMapping("/backlog")
    public String backlog(Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute("currentProject");
        List<UserStory> stories = boardService.getBacklogStories(currentProject);
        model.addAttribute("stories", stories);
        return "backlog";
    }

    @GetMapping("/sprints")
    public String sprints(Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute("currentProject");
        model.addAttribute("sprints", boardService.getSprintsForProject(currentProject));
        return "sprints";
    }

    @GetMapping("/sprints/form")
    public String getSprintForm(@RequestParam(required = false) Long id, Model model) {
        Sprint sprint = new Sprint();
        if (id != null) {
            sprint = boardService.getSprint(id);
        }
        model.addAttribute("sprint", sprint);
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
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false, defaultValue = "board") String view,
            Model model) {
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute("currentProject");
        UserStory story = boardService.createStory(title, description, assignee, sprintId, currentProject);
        model.addAttribute("story", story);

        String oobTarget = null;
        if (story.getSprint() != null) {
            oobTarget = "beforeend:#story-list-" + story.getSprint().getId() + "-" + story.getStatus().getId();
        } else if ("backlog".equals(view)) {
            oobTarget = "beforeend:#backlog-list";
        }

        model.addAttribute("oobTarget", oobTarget);

        if ("backlog".equals(view)) {
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
            @RequestParam(required = false, defaultValue = "board") String view,
            Model model) {
        UserStory story = new UserStory();
        if (id != null) {
            story = boardService.getStory(id);
        }
        com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                .getAttribute("currentProject");
        List<Sprint> activeSprints = boardService.getActiveSprintsForBoard(currentProject, java.time.LocalDate.now());

        model.addAttribute("story", story);
        model.addAttribute("view", view);
        model.addAttribute("activeSprints", activeSprints);
        return "fragments :: storyForm(story=${story}, activeSprints=${activeSprints}, view=${view}, projects=${projects})";
    }

    @PutMapping("/stories/{id}")
    public String updateStory(@PathVariable Long id,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String assignee,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false, defaultValue = "board") String view,
            Model model) {
        UserStory story = boardService.updateStoryDetails(id, title, description, assignee, sprintId);
        model.addAttribute("story", story);

        // No OOB for update currently, just replace in place
        // But we must satisfy the fragment signature
        model.addAttribute("oobTarget", null);

        if ("backlog".equals(view)) {
            return "fragments :: backlogItem(story=${story}, oobTarget=${oobTarget})";
        }
        return "fragments :: card(story=${story}, oobTarget=${oobTarget})";
    }

    @DeleteMapping("/stories/{id}")
    @ResponseBody
    public String deleteStory(@PathVariable Long id) {
        boardService.deleteStory(id);
        return "";
    }

    @GetMapping("/projects")
    public String projects(Model model) {
        model.addAttribute("projects", boardService.getAllProjects());
        return "projects";
    }

    @GetMapping("/projects/form")
    public String getProjectForm(@RequestParam(required = false) Long id, Model model) {
        com.antigravity.jira.model.Project project = new com.antigravity.jira.model.Project();
        if (id != null) {
            project = boardService.getProject(id);
        }
        model.addAttribute("project", project);
        return "fragments :: projectForm(project=${project})";
    }

    @PostMapping("/projects")
    public String createProject(@RequestParam String name,
            @RequestParam(required = false) String description,
            Model model) {
        com.antigravity.jira.model.Project project = boardService.createProject(name, description);
        model.addAttribute("project", project);
        return "fragments :: projectRow(project=${project})";
    }

    @PutMapping("/projects/{id}")
    public String updateProject(@PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            Model model) {
        com.antigravity.jira.model.Project project = boardService.updateProject(id, name, description);
        model.addAttribute("project", project);
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
    public String createSprint(@RequestParam String name,
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
                    .getAttribute("currentProject");
            boardService.createSprint(name, description, start, end, currentProject);
            // Return updated list
            model.addAttribute("sprints", boardService.getSprintsForProject(currentProject));
            // HTMX expects partials. We can return the list fragment.
            return "fragments :: sprintList(sprints=${sprints})";
        } catch (Exception e) {
            // Handle error - simplistic
            return "";
        }
    }

    @PutMapping("/sprints/{id}")
    public String updateSprint(@PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        try {
            java.time.LocalDate start = java.time.LocalDate.parse(startDate);
            java.time.LocalDate end = java.time.LocalDate.parse(endDate);
            boardService.updateSprint(id, name, description, start, end);

            Sprint sprint = boardService.getSprint(id);
            model.addAttribute("sprint", sprint);
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
                .getAttribute("currentProject");

        Map<Long, List<Status>> projectStatuses = new java.util.HashMap<>();
        if (currentProject != null) {
            projectStatuses.put(currentProject.getId(), boardService.getStatusesForProject(currentProject));
            model.addAttribute("projects", List.of(currentProject));
        } else {
            model.addAttribute("projects", new ArrayList<>());
        }
        model.addAttribute("projectStatuses", projectStatuses);

        return "statuses";
    }

    @GetMapping("/statuses/form")
    public String getStatusForm(@RequestParam(required = false) Long id, Model model) {
        Status status = new Status();
        if (id != null) {
            status = boardService.getStatus(id);
        }

        model.addAttribute("status", status);
        model.addAttribute("projects", boardService.getAllProjects());
        return "fragments :: statusForm(status=${status}, projects=${projects})";
    }

    @PostMapping("/statuses")
    public Object createStatus(@RequestParam String name, Model model) {
        try {
            com.antigravity.jira.model.Project currentProject = (com.antigravity.jira.model.Project) model
                    .getAttribute("currentProject");
            Status status = boardService.createStatus(name, currentProject);
            model.addAttribute("status", status);
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
    public Object updateStatus(@PathVariable Long id, @RequestParam String name, Model model) {
        try {
            Status status = boardService.updateStatus(id, name);
            model.addAttribute("status", status);
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
}
