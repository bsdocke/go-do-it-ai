package com.antigravity.jira.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import com.antigravity.jira.config.SecurityConfig;
import com.antigravity.jira.model.AppUser;
import com.antigravity.jira.model.Project;
import com.antigravity.jira.model.Sprint;
import com.antigravity.jira.model.Status;
import com.antigravity.jira.model.UserStory;
import com.antigravity.jira.service.BoardService;

@WebMvcTest(controllers = BoardController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@Import(GlobalControllerAdvice.class) // Import advice to ensure model attributes are populated
class BoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BoardService boardService;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private AppUser mockUser;
    private Project mockProject;
    private Status mockStatus;
    private Sprint mockSprint;
    private UserStory mockStory;

    @BeforeEach
    void setUp() {
        mockUser = new AppUser("test@example.com", "Test User", "USER");
        mockUser.setId(1L);

        mockProject = new Project("Test Project", "Description");
        mockProject.setId(10L);

        mockStatus = new Status();
        mockStatus.setId(100L);
        mockStatus.setName("To Do");
        mockStatus.setProject(mockProject);

        mockSprint = new Sprint();
        mockSprint.setId(200L);
        mockSprint.setName("Sprint 1");
        mockSprint.setProject(mockProject);

        mockStory = new UserStory();
        mockStory.setId(300L);
        mockStory.setTitle("Story 1");
        mockStory.setProject(mockProject);
        mockStory.setStatus(mockStatus);
        mockStory.setSprint(mockSprint); // Ensure sprint is set

        // Common mocks for GlobalControllerAdvice
        when(boardService.getOrCreateUser(anyString(), anyString())).thenReturn(mockUser);
        when(boardService.getProjectsForUser(any(AppUser.class))).thenReturn(Collections.singletonList(mockProject));
    }

    // --- View Tests ---

    @Test
    void testCurrentBoard() throws Exception {
        when(boardService.getActiveSprintsForBoard(eq(mockProject), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(mockSprint));
        when(boardService.getStoriesForSprint(mockSprint.getId())).thenReturn(Collections.singletonList(mockStory));

        mockMvc.perform(get("/current").with(oauth2Login().attributes(attrs -> {
            attrs.put("email", "test@example.com");
            attrs.put("name", "Test User");
        })))
                .andExpect(status().isOk())
                .andExpect(view().name("current"))
                .andExpect(model().attributeExists("boardData"));
    }

    @Test
    void testLanding() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/current"));
    }

    @Test
    void testLanding_Unauthenticated() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void testBacklog() throws Exception {
        when(boardService.getBacklogStories(mockProject)).thenReturn(Collections.singletonList(mockStory));

        mockMvc.perform(get("/backlog").with(oauth2Login().attributes(attrs -> {
            attrs.put("email", "test@example.com");
            attrs.put("name", "Test User");
        })))
                .andExpect(status().isOk())
                .andExpect(view().name("backlog"))
                .andExpect(model().attributeExists("stories"));
    }

    @Test
    void testSprintsView() throws Exception {
        when(boardService.getSprintsForProject(mockProject)).thenReturn(Collections.singletonList(mockSprint));

        mockMvc.perform(get("/sprints").with(oauth2Login().attributes(attrs -> {
            attrs.put("email", "test@example.com");
            attrs.put("name", "Test User");
        })))
                .andExpect(status().isOk())
                .andExpect(view().name("sprints"));
    }

    // --- Project Switching ---

    @Test
    void testSwitchProject() throws Exception {
        mockMvc.perform(put("/context/project")
                .param("projectId", "10")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Refresh", "true"));
    }

    // --- Story Tests ---

    @Test
    void testCreateStory() throws Exception {
        when(boardService.createStory(anyString(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(mockStory);

        mockMvc.perform(post("/stories")
                .param("title", "New Story")
                .param("description", "Desc")
                .param("assignee", "Me")
                .param("points", "3")
                .param("view", "board")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "test@example.com");
                    attrs.put("name", "Test User");
                })))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: cardOob(story=${story}, oobTarget=${oobTarget})"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/", "/css/**", "/js/**", "/images/**").permitAll() // Match production
                                                                                                 // config
                            .anyRequest().authenticated())
                    .oauth2Login(oauth2 -> oauth2
                            .defaultSuccessUrl("/current", true));
            return http.build();
        }
    }

    @Test
    void testCreateStory_Backlog() throws Exception {
        UserStory backlogStory = new UserStory();
        backlogStory.setId(400L);
        backlogStory.setProject(mockProject);
        backlogStory.setStatus(mockStatus); // Fix: Set status to avoid NPE in template
        // No sprint

        when(boardService.createStory(anyString(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(backlogStory);

        mockMvc.perform(post("/stories")
                .param("title", "New Story")
                .param("description", "Desc")
                .param("assignee", "Me")
                .param("points", "3")
                .param("view", "backlog")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "test@example.com");
                    attrs.put("name", "Test User");
                })))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: backlogItemOob(story=${story}, oobTarget=${oobTarget})"));
    }

    @Test
    void testMoveStory() throws Exception {
        mockMvc.perform(put("/stories/" + mockStory.getId() + "/move")
                .param("statusId", "101")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk());

        verify(boardService).updateStoryStatus(mockStory.getId(), 101L);
    }

    @Test
    void testUpdateStory() throws Exception {
        when(boardService.updateStoryDetails(eq(mockStory.getId()), anyString(), anyString(), anyString(), anyInt(),
                any()))
                .thenReturn(mockStory);

        mockMvc.perform(put("/stories/" + mockStory.getId())
                .param("title", "Updated")
                .param("description", "Desc")
                .param("assignee", "You")
                .param("points", "5")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: card(story=${story}, oobTarget=${oobTarget})"));
    }

    @Test
    void testDeleteStory() throws Exception {
        mockMvc.perform(delete("/stories/" + mockStory.getId())
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk());

        verify(boardService).deleteStory(mockStory.getId());
    }

    @Test
    void testGetStoryForm() throws Exception {
        when(boardService.getStory(mockStory.getId())).thenReturn(mockStory);

        mockMvc.perform(get("/stories/form")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "fragments :: storyForm(story=${story}, activeSprints=${activeSprints}, view=${view}, projects=${projects})"));
    }

    @Test
    void testGetStoryFormById() throws Exception {
        when(boardService.getStory(mockStory.getId())).thenReturn(mockStory);

        mockMvc.perform(get("/stories/form?id=" + mockStory.getId())
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "fragments :: storyForm(story=${story}, activeSprints=${activeSprints}, view=${view}, projects=${projects})"));

        verify(boardService).getStory(mockStory.getId());
    }

    @Test
    void testViewStory() throws Exception {
        when(boardService.getStory(mockStory.getId())).thenReturn(mockStory);

        mockMvc.perform(get("/story/" + mockStory.getId())
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("story_view"));
    }

    // --- Comment Tests ---

    @Test
    void testAddComment() throws Exception {
        when(boardService.getStory(mockStory.getId())).thenReturn(mockStory);

        mockMvc.perform(post("/stories/" + mockStory.getId() + "/comments")
                .param("text", "Nice work")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "test@example.com");
                    attrs.put("name", "Test User");
                })))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: commentList(story=${story})"));

        verify(boardService).commentOnStory(eq(mockStory.getId()), eq("Nice work"), any(AppUser.class));
    }

    @Test
    void testDeleteComment() throws Exception {
        mockMvc.perform(delete("/comments/50")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk());

        verify(boardService).deleteComment(50L);
    }

    // --- Subtask Tests ---

    @Test
    void testCreateSubTask() throws Exception {
        when(boardService.getStory(mockStory.getId())).thenReturn(mockStory);

        mockMvc.perform(post("/stories/" + mockStory.getId() + "/subtasks")
                .param("title", "Sub")
                .param("description", "Sub Desc")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "test@example.com");
                    attrs.put("name", "Test User");
                })))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: subTaskList(story=${story})"));

        verify(boardService).createSubTask(eq(mockStory.getId()), eq("Sub"), eq("Sub Desc"), any(AppUser.class));
    }

    // --- Project Admin Tests ---

    @Test
    void testCreateProject() throws Exception {
        when(boardService.createProject(anyString(), anyString(), any(AppUser.class))).thenReturn(mockProject);

        mockMvc.perform(post("/projects")
                .param("projectName", "New P")
                .param("description", "D")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "test@example.com");
                    attrs.put("name", "Test User");
                })))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: projectRow(project=${project})"));
    }

    @Test
    void testCreateProject_Error() throws Exception {
        when(boardService.createProject(anyString(), anyString(), any(AppUser.class)))
                .thenThrow(new IllegalStateException("Limit reached"));

        mockMvc.perform(post("/projects")
                .param("projectName", "New P")
                .param("description", "Limit Test") // Fix match anyString()
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "test@example.com");
                    attrs.put("name", "Test User");
                })))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Limit reached"));
    }

    @Test
    void testUpdateProject() throws Exception {
        when(boardService.updateProject(anyLong(), anyString(), anyString())).thenReturn(mockProject);

        mockMvc.perform(put("/projects/10")
                .param("projectName", "Updated P")
                .param("description", "D2")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: projectRow(project=${project})"));
    }

    @Test
    void testDeleteProject() throws Exception {
        mockMvc.perform(delete("/projects/10")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk());

        verify(boardService).deleteProject(10L);
    }

    // --- Sprint Management ---

    @Test
    void testCreateSprint() throws Exception {
        mockMvc.perform(post("/sprints")
                .param("sprintName", "S2")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-15")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "test@example.com");
                    attrs.put("name", "Test User");
                })))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: sprintList(sprints=${sprints})"));

        verify(boardService).createSprint(eq("S2"), any(), any(), any(), eq(mockProject));
    }

    @Test
    void testUpdateSprint() throws Exception {
        when(boardService.getSprint(200L)).thenReturn(mockSprint);

        mockMvc.perform(put("/sprints/200")
                .param("sprintName", "S2 Updated")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-15")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: sprintRow(sprint=${sprint})"));

        verify(boardService).updateSprint(eq(200L), eq("S2 Updated"), any(), any(), any());
    }

    @Test
    void testDeleteSprint() throws Exception {
        mockMvc.perform(delete("/sprints/200")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk());

        verify(boardService).deleteSprint(200L);
    }

    @Test
    void testSprintForm() throws Exception {
        when(boardService.getSprint(1L)).thenReturn(mockSprint);

        mockMvc.perform(get("/sprints/form")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: sprintForm(sprint=${sprint})"));

        mockMvc.perform(get("/sprints/form?id=1")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: sprintForm(sprint=${sprint})"));

        verify(boardService).getSprint(1L);
    }

    // --- Status Management ---

    @Test
    void testStatusesView() throws Exception {
        when(boardService.getStatusesForProject(mockProject)).thenReturn(Collections.singletonList(mockStatus));

        mockMvc.perform(get("/statuses").with(oauth2Login().attributes(attrs -> {
            attrs.put("email", "test@example.com");
            attrs.put("name", "Test User");
        })))
                .andExpect(status().isOk())
                .andExpect(view().name("statuses"));
    }

    @Test
    void testCreateStatus() throws Exception {
        when(boardService.createStatus(anyString(), any())).thenReturn(mockStatus);

        mockMvc.perform(post("/statuses")
                .param("statusName", "New Status")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "test@example.com");
                    attrs.put("name", "Test User");
                })))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: statusRowOob(status=${status})"));
    }

    @Test
    void testUpdateStatus() throws Exception {
        when(boardService.updateStatus(anyLong(), anyString())).thenReturn(mockStatus);

        mockMvc.perform(put("/statuses/100")
                .param("statusName", "Updated Status")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: statusRow(status=${status})"));
    }

    @Test
    void testStatusReorder() throws Exception {
        mockMvc.perform(post("/statuses/reorder")
                .param("itemIds", "100", "101")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk());

        verify(boardService).updateStatusOrdering(anyList());
    }

    @Test
    void testStatusDelete() throws Exception {
        mockMvc.perform(delete("/statuses/100")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk());

        verify(boardService).deleteStatus(100L);
    }

    @Test
    void testStatusDeleteInvalid() throws Exception {
        doThrow(IllegalStateException.class).when(boardService).deleteStatus(anyLong());
        mockMvc.perform(delete("/statuses/100")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isBadRequest());

        verify(boardService).deleteStatus(100L);
    }

    @Test
    void testGetStatusForm() throws Exception {
        when(boardService.getStatus(100L)).thenReturn(mockStatus);

        mockMvc.perform(get("/statuses/form")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments :: statusForm(status=${status}, projects=${projects})"));
    }

    // --- Admin ---

    @Test
    void testAdminPage_AccessDenied() throws Exception {
        // User is not ADMIN
        mockMvc.perform(get("/admin").with(oauth2Login().attributes(attrs -> {
            attrs.put("email", "test@example.com");
            attrs.put("name", "Test User");
        })))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testAdminPage_Success() throws Exception {
        AppUser admin = new AppUser("admin@example.com", "Admin", "ADMIN");
        when(boardService.getOrCreateUser("admin@example.com", "Admin")).thenReturn(admin);

        mockMvc.perform(get("/admin").with(oauth2Login().attributes(attrs -> {
            attrs.put("email", "admin@example.com");
            attrs.put("name", "Admin");
        })))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"));
    }

    @Test
    void testAddProjectMember() throws Exception {
        AppUser admin = new AppUser("admin@example.com", "Admin", "ADMIN");
        when(boardService.getOrCreateUser("admin@example.com", "Admin")).thenReturn(admin);

        mockMvc.perform(post("/admin/projects/10/members")
                .param("memberEmail", "new@example.com")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "admin@example.com");
                    attrs.put("name", "Admin");
                })))
                .andExpect(status().is3xxRedirection());

        verify(boardService).addProjectMember(10L, "new@example.com");
    }

    @Test
    void testAddProjectMemberInvalid() throws Exception {
        AppUser admin = new AppUser("admin@example.com", "Admin", "ADMIN");
        doThrow(IllegalArgumentException.class).when(boardService).addProjectMember(anyLong(), anyString());
        when(boardService.getOrCreateUser("admin@example.com", "Admin")).thenReturn(admin);
        mockMvc.perform(post("/admin/projects/10/members")
                .param("memberEmail", "new@example.com")
                .with(csrf())
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "admin@example.com");
                    attrs.put("name", "Admin");
                })))
                .andExpect(status().isOk());

    }

    @Test
    void testRemoveProjectMember() throws Exception {
        mockMvc.perform(delete("/admin/projects/10/members/99")
                .with(csrf())
                .with(oauth2Login()))
                .andExpect(status().isOk());

        verify(boardService).removeProjectMember(10L, 99L);
    }
}
