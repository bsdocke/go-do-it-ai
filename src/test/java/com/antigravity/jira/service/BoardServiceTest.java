package com.antigravity.jira.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.antigravity.jira.exception.UserStoryException;
import com.antigravity.jira.model.AppUser;
import com.antigravity.jira.model.Comment;
import com.antigravity.jira.model.Project;
import com.antigravity.jira.model.Sprint;
import com.antigravity.jira.model.Status;
import com.antigravity.jira.model.UserStory;
import com.antigravity.jira.repository.AppUserRepository;
import com.antigravity.jira.repository.CommentRepository;
import com.antigravity.jira.repository.ProjectRepository;
import com.antigravity.jira.repository.SprintRepository;
import com.antigravity.jira.repository.StatusRepository;
import com.antigravity.jira.repository.UserStoryRepository;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private StatusRepository statusRepository;
    @Mock
    private UserStoryRepository userStoryRepository;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private BoardService boardService;

    // --- User Sync and RBAC Tests ---

    @Test
    void testGetOrCreateUser_NewUser_FirstIsAdmin() {
        when(appUserRepository.findByEmail("admin@example.com")).thenReturn(Collections.emptyList());
        when(appUserRepository.count()).thenReturn(0L); // First user
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(i -> {
            AppUser u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        AppUser result = boardService.getOrCreateUser("admin@example.com", "Admin User");

        assertEquals("ADMIN", result.getRole());
        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    void testGetOrCreateUser_NewUser_SecondIsUser() {
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Collections.emptyList());
        when(appUserRepository.count()).thenReturn(1L); // Second user
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(i -> {
            AppUser u = i.getArgument(0);
            u.setId(2L);
            return u;
        });

        AppUser result = boardService.getOrCreateUser("user@example.com", "Regular User");

        assertEquals("USER", result.getRole());
    }

    @Test
    void testGetOrCreateUser_ExistingUser() {
        AppUser existing = new AppUser("existing@example.com", "Existing", "USER");
        existing.setId(10L);
        when(appUserRepository.findByEmail("existing@example.com")).thenReturn(Collections.singletonList(existing));

        AppUser result = boardService.getOrCreateUser("existing@example.com", "Existing");

        assertEquals(10L, result.getId());
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    // --- Project Management Tests ---

    @Test
    void testCreateProject_Admin_Success() {
        AppUser admin = new AppUser("admin@example.com", "Admin", "ADMIN");
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        Project result = boardService.createProject("New Project", "Desc", admin);

        assertNotNull(result);
        assertEquals("New Project", result.getName());
        assertTrue(result.getMembers().contains(admin));
    }

    @Test
    void testCreateProject_User_LimitReached() {
        AppUser user = new AppUser("user@example.com", "User", "USER");
        user.setId(1L);

        // Mock 5 existing projects for this user
        List<Project> existingProjects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Project p = new Project("P" + i, "");
            p.getMembers().add(user);
            existingProjects.add(p);
        }

        when(projectRepository.findAll(any(Sort.class))).thenReturn(existingProjects);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            boardService.createProject("Limit Breaker", "Desc", user);
        });

        assertTrue(ex.getMessage().contains("Project limit reached"));
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void testAddProjectMember_User_LimitReached() {
        AppUser user = new AppUser("user@example.com", "User", "USER");
        user.setId(1L);
        Project targetProject = new Project("Target", "Desc");
        targetProject.setId(100L);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(targetProject));
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Collections.singletonList(user));

        // Mock 5 existing projects
        List<Project> existingProjects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Project p = new Project("P" + i, "");
            p.getMembers().add(user);
            existingProjects.add(p);
        }
        when(projectRepository.findAll(any(Sort.class))).thenReturn(existingProjects);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            boardService.addProjectMember(100L, "user@example.com");
        });

        assertTrue(ex.getMessage().contains("maximum project limit"));
        assertFalse(targetProject.getMembers().contains(user));
    }

    // --- Status Management Tests ---

    @Test
    void testCreateStatus_Success() {
        Project project = new Project("P1", "");
        project.setId(1L);

        when(statusRepository.findByNameAndProject("New Status", project)).thenReturn(null);
        when(statusRepository.findByProjectOrderByPriorityAsc(project)).thenReturn(new ArrayList<>());
        when(statusRepository.save(any(Status.class))).thenAnswer(i -> i.getArgument(0));

        Status result = boardService.createStatus("New Status", project);

        assertEquals("New Status", result.getName());
        assertEquals(1, result.getPriority());
    }

    @Test
    void testCreateStatus_DuplicateName_ThrowsException() {
        Project project = new Project("P1", "");
        when(statusRepository.findByNameAndProject("Exists", project)).thenReturn(new Status());

        assertThrows(IllegalArgumentException.class, () -> {
            boardService.createStatus("Exists", project);
        });
    }

    @Test
    void testDeleteStatus_WithStories_ThrowsException() {
        Status status = new Status();
        status.setId(1L);
        when(statusRepository.findById(1L)).thenReturn(Optional.of(status));
        when(userStoryRepository.findByStatusAndParentStoryIsNullOrderByIdDesc(status))
                .thenReturn(Collections.singletonList(new UserStory()));

        assertThrows(IllegalStateException.class, () -> {
            boardService.deleteStatus(1L);
        });
        verify(statusRepository, never()).delete(any(Status.class));
    }

    // --- Story Management Tests ---

    @Test
    void testCreateStory_NoStatuses_ThrowsException() {
        Project project = new Project("P1", "");
        when(statusRepository.findByProjectOrderByPriorityAsc(project)).thenReturn(Collections.emptyList());

        assertThrows(UserStoryException.class, () -> {
            boardService.createStory("Title", "Desc", "A", 3, null, project);
        });
    }

    @Test
    void testCreateStory_Success() {
        Project project = new Project("P1", "");
        Status todo = new Status();
        todo.setName("To Do");

        when(statusRepository.findByProjectOrderByPriorityAsc(project)).thenReturn(Collections.singletonList(todo));
        when(userStoryRepository.save(any(UserStory.class))).thenAnswer(i -> i.getArgument(0));

        UserStory result = boardService.createStory("Title", "Desc", "User", 5, null, project);

        assertNotNull(result);
        assertEquals("Title", result.getTitle());
        assertEquals(todo, result.getStatus());
        assertEquals("User", result.getAssignee());
    }

    @Test
    void testCreateSubTask_Success() {
        Project project = new Project("P1", "");
        Status todo = new Status();
        AppUser user = new AppUser("u", "name", "USER");

        UserStory parent = new UserStory();
        parent.setId(10L);
        parent.setProject(project);

        when(userStoryRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(statusRepository.findByProjectOrderByPriorityAsc(project)).thenReturn(Collections.singletonList(todo));
        when(userStoryRepository.save(any(UserStory.class))).thenAnswer(i -> i.getArgument(0));

        UserStory sub = boardService.createSubTask(10L, "Sub", "Desc", user);

        assertNotNull(sub);
        assertEquals(project, sub.getProject());
        assertTrue(parent.getSubTasks().contains(sub));
    }

    // --- Sprint Management Tests ---

    @Test
    void getSprintsForProject() {
        Project project = new Project("P1", "");
        when(sprintRepository.findAll()).thenReturn(new ArrayList<>());

        List<Sprint> sprints = boardService.getSprintsForProject(project);

        assertEquals(0, sprints.size());
    }

    @Test
    void getSprintsForProjectNullInput() {
        Project project = null;
        List<Sprint> sprints = boardService.getSprintsForProject(project);

        assertEquals(0, sprints.size());
    }

    @Test
    void getActiveSprintsForBoard() {
        Project project = new Project("P1", "");
        List<Sprint> sprints = boardService.getActiveSprintsForBoard(project, LocalDate.now());

        assertEquals(0, sprints.size());
    }

    @Test
    void getActiveSprintsForBoardNullInput() {
        Project project = null;
        List<Sprint> sprints = boardService.getActiveSprintsForBoard(project, LocalDate.now());

        assertEquals(0, sprints.size());
    }

    @Test
    void testCreateSprint_ActiveLogic() {
        Project project = new Project("P1", "");
        LocalDate now = LocalDate.now();

        when(sprintRepository.save(any(Sprint.class))).thenAnswer(i -> i.getArgument(0));

        Sprint sprint = boardService.createSprint("S1", "Desc", now, now.plusDays(7), project);

        assertTrue(sprint.isActive(), "Sprint starting today should be active");

        Sprint futureSprint = boardService.createSprint("S2", "Desc", now.plusDays(1), now.plusDays(7), project);
        assertFalse(futureSprint.isActive(), "Future sprint should not be active");
    }

    @Test
    void testCommentOnStory() {
        AppUser user = new AppUser();
        UserStory story = new UserStory();
        story.setId(1L);

        when(userStoryRepository.findById(1L)).thenReturn(Optional.of(story));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));

        Comment c = boardService.commentOnStory(1L, "Hello", user);

        assertNotNull(c);
        assertEquals("Hello", c.getText());
        assertEquals(user, c.getCreatedBy());
    }
}
