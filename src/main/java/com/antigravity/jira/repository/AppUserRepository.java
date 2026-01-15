package com.antigravity.jira.repository;

import com.antigravity.jira.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    java.util.List<AppUser> findByEmail(String email);

    boolean existsByRole(String role);
}
