package com.cometbot.service;

import com.cometbot.dto.ExtractItem;
import com.cometbot.model.Deadline;
import com.cometbot.model.DeadlineTypes;
import com.cometbot.model.Syllabus;
import com.cometbot.repo.DeadlineRepository;
import com.cometbot.repo.SyllabusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SyllabusService {

    private static final Logger log = LoggerFactory.getLogger(SyllabusService.class);

    private final SyllabusRepository syllabusRepository;
    private final DeadlineRepository deadlineRepository;
    private final AiService aiService;
    private final SyllabusTextExtractor textExtractor;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int maxChars;

    public SyllabusService(SyllabusRepository syllabusRepository,
                           DeadlineRepository deadlineRepository,
                           AiService aiService,
                           SyllabusTextExtractor textExtractor,
                           TransactionTemplate tx,
                           Clock clock,
                           @Value("${app.syllabus.max-chars:300000}") int maxChars) {
        this.syllabusRepository = syllabusRepository;
        this.deadlineRepository = deadlineRepository;
        this.aiService = aiService;
        this.textExtractor = textExtractor;
        this.tx = tx;
        this.clock = clock;
        this.maxChars = maxChars;
    }

    /**
     * Stores the syllabus and replaces this guild's AI-extracted deadlines.
     * Manually added deadlines are kept. Nothing is saved if extraction fails.
     */
    public List<Deadline> setSyllabus(String guildId, String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("syllabus content is required");
        }
        if (text.length() > maxChars) {
            throw new IllegalArgumentException(
                    "syllabus is " + text.length() + " characters; the limit is " + maxChars);
        }

        // The AI call can take a while: keep it outside the DB transaction.
        List<ExtractItem> items = extract(guildId, text);

        List<Deadline> deadlines = tx.execute(status -> {
            Syllabus syllabus = syllabusRepository.findById(guildId)
                    .orElseGet(() -> new Syllabus(guildId));
            syllabus.setContent(text);
            syllabus.setUpdatedAt(LocalDateTime.now(clock));
            syllabusRepository.save(syllabus);
            return replaceAiDeadlines(guildId, items);
        });
        log.info("Saved syllabus + {} deadlines for guild {}", deadlines.size(), guildId);
        return deadlines;
    }

    public List<Deadline> setSyllabusFromFile(String guildId, String filename, String contentType, byte[] bytes) {
        return setSyllabus(guildId, textExtractor.extract(filename, contentType, bytes));
    }

    public List<Deadline> rescan(String guildId) {
        String content = getSyllabus(guildId).getContent();
        List<ExtractItem> items = extract(guildId, content);
        List<Deadline> deadlines = tx.execute(status -> replaceAiDeadlines(guildId, items));
        log.info("Rescanned {} deadlines for guild {}", deadlines.size(), guildId);
        return deadlines;
    }

    public Syllabus getSyllabus(String guildId) {
        return syllabusRepository.findById(guildId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No syllabus stored for this guild"));
    }

    private List<ExtractItem> extract(String guildId, String content) {
        var response = aiService.extract(content, guildId, LocalDate.now(clock));
        return response.items() == null ? List.of() : response.items();
    }

    private List<Deadline> replaceAiDeadlines(String guildId, List<ExtractItem> items) {
        // Deadlines that survive the rescan keep their row, so their #id and reminder state stay the same.
        List<Deadline> old = deadlineRepository.findByGuildIdAndManualFalse(guildId);
        Map<String, Deadline> previous = new HashMap<>();
        for (Deadline d : old) {
            previous.putIfAbsent(key(d.getTitle(), d.getDueDate()), d);
        }

        // A manual deadline with the same title (added by hand, or an edited AI one) wins.
        Set<String> manualTitles = new HashSet<>();
        for (Deadline d : deadlineRepository.findByGuildIdAndManualTrue(guildId)) {
            manualTitles.add(d.getTitle().strip().toLowerCase(Locale.ROOT));
        }

        Map<String, Deadline> saved = new HashMap<>();
        for (ExtractItem item : items) {
            String title = item.title() == null ? "" : item.title().strip();
            LocalDate dueDate = parseDate(item.date());
            if (title.isEmpty() || dueDate == null) {
                continue;
            }
            String key = key(title, dueDate);
            if (saved.containsKey(key) || manualTitles.contains(title.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String type = DeadlineTypes.normalize(item.type());
            Deadline d = previous.remove(key);
            if (d == null) {
                d = new Deadline(guildId, truncate(title, 255), type, dueDate);
            } else {
                d.setDeadlineType(type);
            }
            saved.put(key, deadlineRepository.save(d));
        }
        // Whatever the new extraction no longer mentions is removed.
        List<Deadline> kept = List.copyOf(saved.values());
        deadlineRepository.deleteAll(old.stream().filter(d -> !kept.contains(d)).toList());

        List<Deadline> result = new ArrayList<>(saved.values());
        result.sort(Comparator.comparing(Deadline::getDueDate).thenComparing(Deadline::getTitle));
        return result;
    }

    private static String key(String title, LocalDate date) {
        return title.strip().toLowerCase(Locale.ROOT) + "|" + date;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.strip());
        } catch (Exception e) {
            return null;
        }
    }
}
