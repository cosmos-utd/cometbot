package com.cometbot.repo;

import com.cometbot.model.Deadline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    List<Deadline> findByGuildIdOrderByDueDateAsc(String guildId);

    Optional<Deadline> findByIdAndGuildId(Long id, String guildId);

    boolean existsByIdAndGuildId(Long id, String guildId);

    long deleteByGuildId(String guildId);

    List<Deadline> findByDueDateAndNotified3DayFalse(LocalDate dueDate);

    List<Deadline> findByDueDateAndNotifiedTodayFalse(LocalDate dueDate);
}