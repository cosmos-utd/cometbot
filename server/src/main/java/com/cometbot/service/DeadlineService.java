package com.cometbot.service;

import com.cometbot.dto.DeadlineRequest;
import com.cometbot.model.Deadline;
import com.cometbot.model.DeadlineTypes;
import com.cometbot.repo.DeadlineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class DeadlineService {

    private final DeadlineRepository deadlineRepository;
    private final Clock clock;

    public DeadlineService(DeadlineRepository deadlineRepository, Clock clock) {
        this.deadlineRepository = deadlineRepository;
        this.clock = clock;
    }

    public List<Deadline> list(String guildId) {
        return deadlineRepository.findByGuildIdOrderByDueDateAsc(guildId);
    }

    /** Deadlines due today or later. */
    public List<Deadline> listUpcoming(String guildId) {
        return deadlineRepository.findByGuildIdAndDueDateGreaterThanEqualOrderByDueDateAsc(
                guildId, LocalDate.now(clock));
    }

    @Transactional
    public Deadline create(String guildId, String title, String type, LocalDate dueDate, String actorId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        Deadline deadline = new Deadline(guildId, title.strip(), DeadlineTypes.require(type), dueDate);
        deadline.setManual(true);
        deadline.setCreatedBy(actorId);
        return deadlineRepository.save(deadline);
    }

    @Transactional
    public Deadline update(String guildId, Long id, DeadlineRequest request, String actorId) {
        Deadline deadline = getOwned(guildId, id);

        boolean changed = false;
        if (request.title() != null && !request.title().isBlank()) {
            deadline.setTitle(request.title().strip());
            changed = true;
        }
        if (request.type() != null && !request.type().isBlank()) {
            deadline.setDeadlineType(DeadlineTypes.require(request.type()));
            changed = true;
        }
        if (request.dueDate() != null) {
            deadline.setDueDate(request.dueDate());
            changed = true;
        }
        if (!changed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to update");
        }

        // An edited deadline belongs to whoever edited it: rescans must not overwrite it.
        deadline.setManual(true);
        deadline.setUpdatedBy(actorId);
        deadline.setNotified3Day(false);
        deadline.setNotifiedToday(false);
        return deadlineRepository.save(deadline);
    }

    @Transactional
    public void delete(String guildId, Long id) {
        Deadline deadline = getOwned(guildId, id);
        deadlineRepository.delete(deadline);
    }

    private Deadline getOwned(String guildId, Long id) {
        return deadlineRepository.findByIdAndGuildId(id, guildId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Deadline not found"));
    }
}
