package com.cometbot.service;

import com.cometbot.dto.DeadlineRequest;
import com.cometbot.model.Deadline;
import com.cometbot.repo.DeadlineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class DeadlineService {

    private final DeadlineRepository deadlineRepository;

    public DeadlineService(DeadlineRepository deadlineRepository) {
        this.deadlineRepository = deadlineRepository;
    }

    public List<Deadline> list(String guildId) {
        return deadlineRepository.findByGuildIdOrderByDueDateAsc(guildId);
    }

    @Transactional
    public Deadline create(String guildId, String title, String type, LocalDate dueDate, String actorId) {
        Deadline deadline = new Deadline(guildId, title, type, dueDate);
        deadline.setCreatedBy(actorId);
        return deadlineRepository.save(deadline);
    }

    @Transactional
    public Deadline update(String guildId, Long id, DeadlineRequest request, String actorId) {
        Deadline deadline = getOwned(guildId, id);

        boolean changed = false;
        if (request.title() != null && !request.title().isBlank()) {
            deadline.setTitle(request.title().trim());
            changed = true;
        }
        if (request.type() != null && !request.type().isBlank()) {
            deadline.setDeadlineType(request.type().trim().toLowerCase());
            changed = true;
        }
        if (request.dueDate() != null) {
            deadline.setDueDate(request.dueDate());
            changed = true;
        }
        if (!changed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to update");
        }

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