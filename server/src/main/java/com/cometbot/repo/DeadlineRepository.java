package com.cometbot.repo;

import com.cometbot.model.Deadline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    List<Deadline> findByGuildIdOrderByDueDateAsc(String guildId);

    List<Deadline> findByGuildIdAndDueDateGreaterThanEqualOrderByDueDateAsc(String guildId, LocalDate from);

    List<Deadline> findByGuildIdAndManualFalse(String guildId);

    List<Deadline> findByGuildIdAndManualTrue(String guildId);

    Optional<Deadline> findByIdAndGuildId(Long id, String guildId);

    List<Deadline> findByDueDateAndNotifiedTodayFalse(LocalDate dueDate);

    List<Deadline> findByDueDateBetweenAndNotified3DayFalse(LocalDate from, LocalDate to);
}
