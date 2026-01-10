package com.antigravity.jira.repository;

import com.antigravity.jira.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    java.util.List<com.antigravity.jira.model.AppUser> findByEmail(String email);

    boolean existsByRole(String role);
}
