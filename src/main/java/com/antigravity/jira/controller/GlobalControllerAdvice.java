package com.antigravity.jira.controller;

import com.antigravity.jira.model.AppUser;
import com.antigravity.jira.model.Project;
import com.antigravity.jira.service.BoardService;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final BoardService boardService;

    public GlobalControllerAdvice(BoardService boardService) {
        this.boardService = boardService;
    }

    @ModelAttribute("currentUser")
    public AppUser populateUser(
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return null;
        }
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        return boardService.getOrCreateUser(email, name);
    }

    @ModelAttribute("allProjects")
    public List<Project> populateProjects(@ModelAttribute("currentUser") AppUser user) {
        if (user == null) {
            return java.util.Collections.emptyList();
        }
        return boardService.getProjectsForUser(user);
    }

    @ModelAttribute("currentProject")
    public Project populateCurrentProject(HttpSession session, @ModelAttribute("allProjects") List<Project> projects) {
        if (projects.isEmpty()) {
            return null;
        }

        Long projectId = (Long) session.getAttribute("projectId");
        if (projectId != null) {
            // Validate the user still has access to this project
            Project project = projects.stream().filter(p -> p.getId().equals(projectId)).findFirst().orElse(null);
            if (project != null) {
                return project;
            }
        }

        // Default to first project if session is empty or invalid
        Project defaultProject = projects.get(0);
        session.setAttribute("projectId", defaultProject.getId());
        return defaultProject;
    }
}
