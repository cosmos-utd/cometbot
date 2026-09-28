package com.cometbot.discord;

import com.cometbot.dto.DeadlineRequest;
import com.cometbot.model.Deadline;
import com.cometbot.model.DeadlineTypes;
import com.cometbot.service.DeadlineService;
import com.cometbot.service.SettingsService;
import com.cometbot.service.SyllabusService;
import com.cometbot.service.SyllabusTextExtractor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy");
    private static final String TYPES = String.join("|", DeadlineTypes.ALL);

    private static final String HELP = """
            **CometBot commands**
            `!deadlines` — list upcoming deadlines
            `!help` — show this message

            **Moderators** (server owner, or Manage Server / Manage Channels / Manage Messages):
            `!setsyllabus` + attach a PDF or .txt — or `!setsyllabus <pasted text>` — load a syllabus and extract deadlines
            `!scan` — re-extract deadlines from the stored syllabus (keeps ones you added or edited)
            `!adddeadline Title | YYYY-MM-DD [| type]` — add a deadline by hand
            `!editdeadline <id> <title|type|date> <new value>`
            `!deletedeadline <id>`
            `!setchannel #channel` — where reminders are posted

            Types: %s""".formatted(String.join(", ", DeadlineTypes.ALL));

    private final SyllabusService syllabusService;
    private final DeadlineService deadlineService;
    private final SettingsService settingsService;
    private final String discordOwnerId;

    public CommandHandler(SyllabusService syllabusService,
                          DeadlineService deadlineService,
                          SettingsService settingsService,
                          @Value("${discord.owner-id:}") String discordOwnerId) {
        this.syllabusService = syllabusService;
        this.deadlineService = deadlineService;
        this.settingsService = settingsService;
        this.discordOwnerId = discordOwnerId;
    }

    public void handle(MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw().strip();
        if (content.isEmpty() || !content.startsWith("!")) {
            return;
        }

        String[] parts = content.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].strip() : "";

        try {
            switch (command) {
                case "!help" -> reply(event, HELP);
                case "!deadlines" -> handleDeadlines(event);
                case "!setsyllabus" -> ifModerator(event, () -> handleSetSyllabus(event, args));
                case "!scan" -> ifModerator(event, () -> handleScan(event));
                case "!adddeadline" -> ifModerator(event, () -> handleAddDeadline(event, args));
                case "!editdeadline" -> ifModerator(event, () -> handleEditDeadline(event, args));
                case "!deletedeadline" -> ifModerator(event, () -> handleDeleteDeadline(event, args));
                case "!setchannel" -> ifModerator(event, () -> handleSetChannel(event));
                default -> { }
            }
        } catch (Exception e) {
            log.warn("Command {} failed in guild {}: {}", command, event.getGuild().getId(), e.getMessage());
            String message = e instanceof ResponseStatusException rse && rse.getReason() != null
                    ? rse.getReason()
                    : e.getMessage();
            reply(event, "❌ " + message);
        }
    }

    // ---------- !setsyllabus ----------
    private void handleSetSyllabus(MessageReceivedEvent event, String text) throws Exception {
        String guildId = event.getGuild().getId();
        List<Message.Attachment> attachments = event.getMessage().getAttachments();

        if (!attachments.isEmpty()) {
            Message.Attachment file = attachments.getFirst();
            if (file.getSize() > SyllabusTextExtractor.MAX_FILE_BYTES) {
                reply(event, "❌ That file is larger than 10 MB.");
                return;
            }
            reply(event, "📥 Reading `" + file.getFileName() + "` and extracting deadlines...");
            byte[] bytes;
            try (InputStream in = file.getProxy().download().get(60, TimeUnit.SECONDS)) {
                bytes = in.readAllBytes();
            }
            reply(event, summary(syllabusService.setSyllabusFromFile(
                    guildId, file.getFileName(), file.getContentType(), bytes)));
            return;
        }

        if (text.isEmpty()) {
            reply(event, "❌ Attach your syllabus (PDF or .txt) to `!setsyllabus`, "
                    + "or paste the text after the command.");
            return;
        }
        reply(event, "📥 Saving syllabus and extracting deadlines...");
        reply(event, summary(syllabusService.setSyllabus(guildId, text)));
    }

    // ---------- !scan ----------
    private void handleScan(MessageReceivedEvent event) {
        reply(event, "🔍 Re-scanning the stored syllabus...");
        reply(event, summary(syllabusService.rescan(event.getGuild().getId())));
    }

    // ---------- !deadlines ----------
    private void handleDeadlines(MessageReceivedEvent event) {
        List<Deadline> deadlines = deadlineService.listUpcoming(event.getGuild().getId());
        if (deadlines.isEmpty()) {
            reply(event, "📭 No upcoming deadlines. A moderator can load them with `!setsyllabus`.");
            return;
        }
        StringBuilder sb = new StringBuilder("**Upcoming deadlines:**\n");
        deadlines.forEach(d -> sb.append(line(d)));
        reply(event, sb.toString());
    }

    // ---------- !adddeadline ----------
    private void handleAddDeadline(MessageReceivedEvent event, String args) {
        String[] segments = args.split("\\|");
        if (segments.length < 2) {
            reply(event, "❌ Usage: `!adddeadline Title | YYYY-MM-DD [| " + TYPES + "]`");
            return;
        }
        String title = segments[0].strip();
        LocalDate dueDate = parseDate(segments[1]);
        String type = segments.length > 2 ? segments[2] : null;
        if (title.isEmpty() || dueDate == null) {
            reply(event, "❌ Invalid title or date. Use `YYYY-MM-DD`.");
            return;
        }
        Deadline d = deadlineService.create(
                event.getGuild().getId(), title, type, dueDate, event.getAuthor().getId());
        reply(event, "✅ Added " + line(d));
    }

    // ---------- !editdeadline ----------
    private void handleEditDeadline(MessageReceivedEvent event, String args) {
        String[] tokens = args.split("\\s+", 3);
        if (tokens.length < 3) {
            reply(event, "❌ Usage: `!editdeadline <id> <title|type|date> <new value>`");
            return;
        }
        Long id = parseId(tokens[0]);
        String value = tokens[2].strip();
        if (id == null || value.isEmpty()) {
            reply(event, "❌ Invalid id or missing new value.");
            return;
        }

        DeadlineRequest request;
        switch (tokens[1].toLowerCase()) {
            case "title" -> request = new DeadlineRequest(value, null, null);
            case "type" -> request = new DeadlineRequest(null, value, null);
            case "date" -> {
                LocalDate date = parseDate(value);
                if (date == null) {
                    reply(event, "❌ Invalid date. Use `YYYY-MM-DD`.");
                    return;
                }
                request = new DeadlineRequest(null, null, date);
            }
            default -> {
                reply(event, "❌ Field must be `title`, `type`, or `date`.");
                return;
            }
        }

        Deadline updated = deadlineService.update(
                event.getGuild().getId(), id, request, event.getAuthor().getId());
        reply(event, "✅ Updated " + line(updated));
    }

    // ---------- !deletedeadline ----------
    private void handleDeleteDeadline(MessageReceivedEvent event, String args) {
        Long id = parseId(args);
        if (id == null) {
            reply(event, "❌ Usage: `!deletedeadline <id>`");
            return;
        }
        deadlineService.delete(event.getGuild().getId(), id);
        reply(event, "🗑️ Deleted deadline `#" + id + "`.");
    }

    // ---------- !setchannel ----------
    private void handleSetChannel(MessageReceivedEvent event) {
        GuildChannel channel = event.getMessage().getMentions().getChannels().stream().findFirst().orElse(null);
        if (channel == null) {
            reply(event, "❌ Mention a channel: `!setchannel #reminders`");
            return;
        }
        if (event.getGuild().getTextChannelById(channel.getId()) == null) {
            reply(event, "❌ Reminders can only be posted to a text channel.");
            return;
        }
        settingsService.setReminderChannel(event.getGuild().getId(), channel.getId());
        reply(event, "✅ Reminders will post to " + channel.getAsMention());
    }

    // ---------- helpers ----------
    private interface Action {
        void run() throws Exception;
    }

    private void ifModerator(MessageReceivedEvent event, Action action) throws Exception {
        if (!isModerator(event)) {
            reply(event, "❌ Only the server owner and moderators can do that.");
            return;
        }
        action.run();
    }

    private boolean isModerator(MessageReceivedEvent event) {
        if (!discordOwnerId.isBlank() && event.getAuthor().getId().equals(discordOwnerId)) {
            return true;
        }
        Member member = event.getMember();
        if (member == null) {
            return false;
        }
        return member.isOwner()
                || member.hasPermission(Permission.MANAGE_SERVER)
                || member.hasPermission(Permission.MANAGE_CHANNEL)
                || member.hasPermission(Permission.MESSAGE_MANAGE);
    }

    private String summary(List<Deadline> deadlines) {
        if (deadlines.isEmpty()) {
            return "✅ Saved syllabus. No new deadlines detected "
                    + "(check the text, or add them with `!adddeadline`).";
        }
        StringBuilder sb = new StringBuilder("✅ Saved syllabus — extracted **")
                .append(deadlines.size()).append("** deadlines:\n");
        deadlines.forEach(d -> sb.append(line(d)));
        return sb.toString();
    }

    private static String line(Deadline d) {
        return "`#" + d.getId() + "` " + d.getTitle() + " (" + d.getDeadlineType() + ") — "
                + d.getDueDate().format(DATE_FMT) + "\n";
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.strip());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseId(String raw) {
        try {
            return Long.parseLong(raw.strip().replaceFirst("^#", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private void reply(MessageReceivedEvent event, String text) {
        List<String> chunks = DiscordMessages.split(text, DiscordMessages.MAX_LENGTH);
        for (int i = 0; i < chunks.size(); i++) {
            var action = i == 0
                    ? event.getMessage().reply(chunks.get(i))
                    : event.getChannel().sendMessage(chunks.get(i));
            // complete() keeps multi-part replies in order.
            try {
                action.complete();
            } catch (Exception e) {
                log.warn("Failed to reply in channel {}: {}", event.getChannel().getId(), e.getMessage());
                return;
            }
        }
    }
}
