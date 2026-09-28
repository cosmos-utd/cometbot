package com.cometbot.controller;

import com.cometbot.dto.DeadlineDto;
import com.cometbot.dto.DeadlineRequest;
import com.cometbot.model.Deadline;
import com.cometbot.service.DeadlineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/guilds/{guildId}/deadlines")
public class DeadlineController {

    private final DeadlineService deadlineService;

    public DeadlineController(DeadlineService deadlineService) {
        this.deadlineService = deadlineService;
    }

    @GetMapping
    public List<DeadlineDto> list(@PathVariable String guildId,
                                  @RequestParam(defaultValue = "false") boolean upcoming) {
        List<Deadline> deadlines = upcoming ? deadlineService.listUpcoming(guildId) : deadlineService.list(guildId);
        return deadlines.stream().map(DeadlineDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeadlineDto create(@PathVariable String guildId,
                              @RequestParam(required = false) String actorId,
                              @Valid @RequestBody DeadlineRequest request) {
        Deadline deadline = deadlineService.create(
                guildId, request.title(), request.type(), request.dueDate(), actorId);
        return DeadlineDto.from(deadline);
    }

    @PutMapping("/{id}")
    public DeadlineDto update(@PathVariable String guildId,
                              @PathVariable Long id,
                              @RequestParam(required = false) String actorId,
                              @Valid @RequestBody DeadlineRequest request) {
        return DeadlineDto.from(deadlineService.update(guildId, id, request, actorId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String guildId, @PathVariable Long id) {
        deadlineService.delete(guildId, id);
    }
}
