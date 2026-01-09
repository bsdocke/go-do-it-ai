package com.antigravity.jira.controller;

import com.antigravity.jira.model.Project;
import com.antigravity.jira.service.BoardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final BoardService boardService;

    public GlobalControllerAdvice(BoardService boardService) {
        this.boardService = boardService;
    }

    @ModelAttribute("allProjects")
    public List<Project> populateProjects() {
        return boardService.getAllProjects();
    }

    @ModelAttribute("currentProject")
    public Project populateCurrentProject(HttpSession session) {
        List<Project> allProjects = boardService.getAllProjects();
        if (allProjects.isEmpty()) {
            return null;
        }

        Long projectId = (Long) session.getAttribute("projectId");
        if (projectId != null) {
            Project project = boardService.getProject(projectId);
            if (project != null) {
                return project;
            }
        }

        // Default to first project if session is empty or invalid
        Project defaultProject = allProjects.get(0);
        session.setAttribute("projectId", defaultProject.getId());
        return defaultProject;
    }
}
