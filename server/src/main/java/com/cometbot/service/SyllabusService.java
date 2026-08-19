package com.cometbot.service;

import com.cometbot.dto.ExtractItem;
import com.cometbot.dto.ExtractResponse;
import com.cometbot.model.Deadline;
import com.cometbot.model.Syllabus;
import com.cometbot.repo.DeadlineRepository;
import com.cometbot.repo.SyllabusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SyllabusService {

    private static final Logger log = LoggerFactory.getLogger(SyllabusService.class);
    private static final Set<String> VALID_TYPES = Set.of("assignment", "quiz", "test", "exam", "final", "other");

    private final SyllabusRepository syllabusRepository;
    private final DeadlineRepository deadlineRepository;
    private final AiService aiService;

    public SyllabusService(SyllabusRepository syllabusRepository,
                           DeadlineRepository deadlineRepository,
                           AiService aiService) {
        this.syllabusRepository = syllabusRepository;
        this.deadlineRepository = deadlineRepository;
        this.aiService = aiService;
    }

    @Transactional
    public List<Deadline> setSyllabus(String guildId, String content) {
        Syllabus syllabus = syllabusRepository.findById(guildId)
                .orElseGet(() -> new Syllabus(guildId));
        syllabus.setContent(content);
        syllabus.setUpdatedAt(LocalDateTime.now());
        syllabusRepository.save(syllabus);

        List<Deadline> deadlines = extractAndReplace(guildId, content);
        log.info("Saved syllabus + {} deadlines for guild {}", deadlines.size(), guildId);
        return deadlines;
    }

    @Transactional
    public List<Deadline> rescan(String guildId) {
        Syllabus syllabus = getSyllabus(guildId);
        List<Deadline> deadlines = extractAndReplace(guildId, syllabus.getContent());
        log.info("Rescanned {} deadlines for guild {}", deadlines.size(), guildId);
        return deadlines;
    }

    public Syllabus getSyllabus(String guildId) {
        return syllabusRepository.findById(guildId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No syllabus stored for this guild"));
    }

    private List<Deadline> extractAndReplace(String guildId, String content) {
        ExtractResponse response = aiService.extract(content, guildId);
        List<ExtractItem> items = response.items() == null ? List.of() : response.items();

        deadlineRepository.deleteByGuildId(guildId);

        List<Deadline> saved = new ArrayList<>();
        for (ExtractItem item : items) {
            String title = item.title() == null ? "" : item.title().trim();
            LocalDate dueDate = parseDate(item.date());
            if (title.isEmpty() || dueDate == null) {
                continue;
            }
            saved.add(deadlineRepository.save(
                    new Deadline(guildId, title, normalizeType(item.type()), dueDate)));
        }
        return saved;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeType(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase();
        return VALID_TYPES.contains(type) ? type : "other";
    }
}