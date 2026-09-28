package com.cometbot;

import com.cometbot.dto.DeadlineRequest;
import com.cometbot.dto.ExtractItem;
import com.cometbot.dto.ExtractResponse;
import com.cometbot.model.Deadline;
import com.cometbot.repo.DeadlineRepository;
import com.cometbot.repo.SyllabusRepository;
import com.cometbot.service.AiService;
import com.cometbot.service.AiServiceException;
import com.cometbot.service.DeadlineService;
import com.cometbot.service.ReminderService;
import com.cometbot.service.SettingsService;
import com.cometbot.service.SyllabusService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** End-to-end through the services and a real (H2) database, with the AI service and Discord mocked. */
@SpringBootTest(properties = "reminder.interval-ms=3600000")
class SyllabusFlowIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("America/Chicago");
    static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    static final String GUILD = "guild-1";
    static final String CHANNEL = "chan-1";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(TODAY.atTime(9, 0).atZone(ZONE).toInstant(), ZONE);
        }
    }

    @MockitoBean
    AiService aiService;

    @MockitoBean
    JDA jda;

    @Autowired SyllabusService syllabusService;
    @Autowired DeadlineService deadlineService;
    @Autowired SettingsService settingsService;
    @Autowired ReminderService reminderService;
    @Autowired DeadlineRepository deadlineRepository;
    @Autowired SyllabusRepository syllabusRepository;

    final List<String> sent = new ArrayList<>();
    boolean discordDown;

    @BeforeEach
    void setUp() {
        deadlineRepository.deleteAll();
        syllabusRepository.deleteAll();
        settingsService.setReminderChannel(GUILD, CHANNEL);

        Guild guild = mock(Guild.class);
        TextChannel channel = mock(TextChannel.class);
        when(jda.getGuildById(GUILD)).thenReturn(guild);
        when(guild.getTextChannelById(CHANNEL)).thenReturn(channel);
        when(channel.canTalk()).thenReturn(true);
        when(channel.sendMessage(anyString())).thenAnswer(inv -> {
            MessageCreateAction action = mock(MessageCreateAction.class);
            when(action.complete()).thenAnswer(x -> {
                if (discordDown) {
                    throw new IllegalStateException("discord is down");
                }
                sent.add(inv.getArgument(0));
                return null;
            });
            return action;
        });
    }

    @Test
    void syllabusExtractionRemindersAndRescan() {
        aiReturns(
                item("Homework 1", "assignment", "2026-09-03"),
                item("Quiz 1", "quiz", "2026-09-01"),
                item("homework 1", "assignment", "2026-09-03"),   // duplicate
                item("Midterm", "exam", "sometime in October"),  // unusable date
                item("Project 1", "project", "2026-09-20"),      // unknown type
                item("Old Quiz", "quiz", "2026-08-01"));

        List<Deadline> extracted = syllabusService.setSyllabus(GUILD, "the syllabus");

        assertThat(extracted).extracting(Deadline::getTitle)
                .containsExactly("Old Quiz", "Quiz 1", "Homework 1", "Project 1");
        assertThat(byTitle().get("Project 1").getDeadlineType()).isEqualTo("other");
        assertThat(syllabusService.getSyllabus(GUILD).getContent()).isEqualTo("the syllabus");
        assertThat(deadlineService.listUpcoming(GUILD)).extracting(Deadline::getTitle)
                .doesNotContain("Old Quiz");

        deadlineService.create(GUILD, "Lab 1", null, LocalDate.of(2026, 9, 2), "user-1");

        // First reminder run: Quiz 1 is due today; Lab 1 and Homework 1 are inside the 3-day window.
        reminderService.checkDeadlines();
        assertThat(sent).hasSize(3);
        assertThat(sent).anyMatch(m -> m.contains("Due today") && m.contains("Quiz 1"));
        assertThat(sent).anyMatch(m -> m.contains("Lab 1") && m.contains("due tomorrow"));
        assertThat(sent).anyMatch(m -> m.contains("Homework 1") && m.contains("due in 2 days"));

        // Nothing new on the next run.
        sent.clear();
        reminderService.checkDeadlines();
        assertThat(sent).isEmpty();

        // Rescan: Quiz 1 disappears, Homework 1 keeps its reminder state, manual Lab 1 survives
        // and isn't duplicated by an AI item with the same title.
        Long homeworkId = byTitle().get("Homework 1").getId();
        aiReturns(
                item("Homework 1", "assignment", "2026-09-03"),
                item("Project 1", "assignment", "2026-09-20"),
                item("Lab 1", "assignment", "2026-09-02"));

        syllabusService.rescan(GUILD);

        Map<String, Deadline> after = byTitle();
        assertThat(after.get("Homework 1").getId()).isEqualTo(homeworkId);
        assertThat(after.get("Project 1").getDeadlineType()).isEqualTo("assignment");
        assertThat(after).containsOnlyKeys("Homework 1", "Project 1", "Lab 1");
        assertThat(after.get("Lab 1").isManual()).isTrue();
        assertThat(after.get("Homework 1").isNotified3Day()).isTrue();
        reminderService.checkDeadlines();
        assertThat(sent).isEmpty();
    }

    @Test
    void editedDeadlinesSurviveRescanWithoutDuplicates() {
        aiReturns(item("Homework 1", "assignment", "2026-09-10"));
        Deadline hw = syllabusService.setSyllabus(GUILD, "the syllabus").getFirst();

        deadlineService.update(GUILD, hw.getId(), new DeadlineRequest(null, null, LocalDate.of(2026, 9, 12)), "mod");
        syllabusService.rescan(GUILD);

        List<Deadline> all = deadlineService.list(GUILD);
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getDueDate()).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    void failedExtractionChangesNothing() {
        aiReturns(item("Homework 1", "assignment", "2026-09-10"));
        syllabusService.setSyllabus(GUILD, "first syllabus");

        when(aiService.extract(anyString(), anyString(), any()))
                .thenThrow(new AiServiceException("AI extraction failed: the model declined", null));

        assertThatThrownBy(() -> syllabusService.setSyllabus(GUILD, "second syllabus"))
                .isInstanceOf(AiServiceException.class);
        assertThat(syllabusService.getSyllabus(GUILD).getContent()).isEqualTo("first syllabus");
        assertThat(deadlineService.list(GUILD)).extracting(Deadline::getTitle).containsExactly("Homework 1");
    }

    @Test
    void failedSendIsRetriedOnTheNextRun() {
        deadlineService.create(GUILD, "Quiz 2", "quiz", TODAY, "mod");

        discordDown = true;
        reminderService.checkDeadlines();
        assertThat(deadlineService.list(GUILD).getFirst().isNotifiedToday()).isFalse();

        discordDown = false;
        reminderService.checkDeadlines();
        assertThat(sent).singleElement().asString().contains("Due today", "Quiz 2");
        assertThat(deadlineService.list(GUILD).getFirst().isNotifiedToday()).isTrue();
    }

    @Test
    void manualTypesAreValidated() {
        assertThatThrownBy(() -> deadlineService.create(GUILD, "Essay", "homework", TODAY, "mod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type must be one of");
    }

    private void aiReturns(ExtractItem... items) {
        when(aiService.extract(anyString(), eq(GUILD), eq(TODAY)))
                .thenReturn(new ExtractResponse(List.of(items)));
    }

    private static ExtractItem item(String title, String type, String date) {
        return new ExtractItem(title, type, date);
    }

    private Map<String, Deadline> byTitle() {
        return deadlineService.list(GUILD).stream()
                .collect(Collectors.toMap(Deadline::getTitle, Function.identity()));
    }
}
