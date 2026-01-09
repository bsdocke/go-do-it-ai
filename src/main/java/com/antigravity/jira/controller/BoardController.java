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

    @GetMapping("/")
    public String index(Model model) {
        List<Status> statuses = boardService.getAllStatuses();
        List<Sprint> activeSprints = boardService.getActiveSprintsForBoard(java.time.LocalDate.now());

        List<Map<String, Object>> boardData = new ArrayList<>();

        if (activeSprints.isEmpty()) {
            // Optional: Put dummy data or handle in view?
            // View handles empty boardData by showing "No Active Sprint"
        } else {
            for (Sprint sprint : activeSprints) {
                List<UserStory> stories = boardService.getStoriesForSprint(sprint.getId());
                Map<String, List<UserStory>> storiesByStatus = stories.stream()
                        .collect(Collectors.groupingBy(s -> s.getStatus().getName()));

                Map<String, Object> sprintData = new java.util.HashMap<>();
                sprintData.put("sprint", sprint);
                sprintData.put("storiesByStatus", storiesByStatus);
                boardData.add(sprintData);
            }
        }

        model.addAttribute("statuses", statuses);
        model.addAttribute("boardData", boardData);
        return "index";
    }

    @GetMapping("/backlog")
    public String backlog(Model model) {
        List<UserStory> stories = boardService.getBacklogStories();
        model.addAttribute("stories", stories);
        return "backlog";
    }

    @GetMapping("/sprints")
    public String sprints(Model model) {
        model.addAttribute("sprints", boardService.getAllSprints());
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
                end = start.plusDays(14);
            }
        } catch (Exception e) {
            start = java.time.LocalDate.now();
            end = start.plusDays(14);
        }

        Sprint sprint = boardService.createSprint(name, description, start, end);
        model.addAttribute("sprint", sprint);
        return "fragments :: sprintRow(sprint=${sprint})";
    }

    @PutMapping("/sprints/{id}")
    public String updateSprint(@PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String startDate,
            @RequestParam String endDate,
            Model model) {
        Sprint sprint = boardService.updateSprint(id, name, description, java.time.LocalDate.parse(startDate),
                java.time.LocalDate.parse(endDate));
        model.addAttribute("sprint", sprint);
        return "fragments :: sprintRow(sprint=${sprint})";
    }

    @DeleteMapping("/sprints/{id}")
    public String deleteSprint(@PathVariable Long id, Model model) {
        boardService.deleteSprint(id);
        model.addAttribute("sprints", boardService.getAllSprints());
        return "fragments :: sprintList(sprints=${sprints})";
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
        UserStory story = boardService.createStory(title, description, assignee, sprintId);
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
        model.addAttribute("story", story);
        model.addAttribute("view", view);
        model.addAttribute("activeSprints", boardService.getActiveSprints());
        return "fragments :: storyForm(story=${story})";
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
}
