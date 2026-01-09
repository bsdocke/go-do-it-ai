package com.antigravity.jira.repository;

import com.antigravity.jira.model.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    List<Sprint> findByActiveTrue();

    @Query("SELECT s FROM Sprint s WHERE s.active = true AND :date BETWEEN s.startDate AND s.endDate")
    List<Sprint> findActiveSprintForDate(@Param("date") LocalDate date);
}
