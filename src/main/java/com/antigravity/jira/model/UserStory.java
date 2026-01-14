package com.antigravity.jira.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Entity
public class UserStory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Min(value = 0, message = "Points must be non-negative")
    @Max(value = 499, message = "Points must be less than 500")
    private Integer points;

    @Column(length = 2000)
    private String description;

    private String assignee;

    @ManyToOne
    @JoinColumn(name = "status_id")
    private Status status;

    @ManyToOne
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @OneToMany(mappedBy = "userStory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_story_id")
    private UserStory parentStory;

    @OneToMany(mappedBy = "parentStory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserStory> subTasks = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Sprint getSprint() {
        return sprint;
    }

    public void setSprint(Sprint sprint) {
        this.sprint = sprint;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setUserStory(this);
    }

    public void removeComment(Comment comment) {
        comments.remove(comment);
        comment.setUserStory(null);
    }

    public UserStory getParentStory() {
        return parentStory;
    }

    public void setParentStory(UserStory parentStory) {
        this.parentStory = parentStory;
    }

    public List<UserStory> getSubTasks() {
        return subTasks;
    }

    public void setSubTasks(List<UserStory> subTasks) {
        this.subTasks = subTasks;
    }

    public void addSubTask(UserStory subTask) {
        subTasks.add(subTask);
        subTask.setParentStory(this);
    }

    public void removeSubTask(UserStory subTask) {
        subTasks.remove(subTask);
        subTask.setParentStory(null);
    }
}
