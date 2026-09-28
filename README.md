# CometBot

A Discord bot for UT Dallas courses. A moderator uploads the course syllabus, Amazon Titan (on Bedrock) pulls
out every dated assignment, quiz, and exam, and the bot posts reminders before each one is due.

## How it works

```
Discord ──► server/ (Spring Boot + JDA) ──► ai/ (FastAPI) ──► Titan on Bedrock
                 │
                 └──► MySQL (H2 locally)
```

- **`server/`**: the Discord bot, a REST API, the database, and a scheduler that sends reminders.
- **`ai/`**: one endpoint, `POST /extract`, that turns syllabus text into `{title, type, date}` items using
  Amazon Titan Text Express. Syllabi are limited to 20,000 characters (Titan's context size).

Reminders go to the channel set with `!setchannel` (or the server's system channel): one once a deadline is within
3 days, and one on the due date. If the bot was offline, the advance reminder goes out on the next run instead of
being skipped.

## Discord commands

| Command | Who | What |
|---|---|---|
| `!deadlines` | everyone | List upcoming deadlines |
| `!help` | everyone | Show commands |
| `!setsyllabus` + attached PDF/.txt (or pasted text) | moderators | Store the syllabus and extract deadlines |
| `!scan` | moderators | Re-extract from the stored syllabus |
| `!adddeadline Title \| YYYY-MM-DD [\| type]` | moderators | Add a deadline by hand |
| `!editdeadline <id> <title\|type\|date> <value>` | moderators | Edit a deadline |
| `!deletedeadline <id>` | moderators | Delete a deadline |
| `!setchannel #channel` | moderators | Where reminders are posted |

Moderators are the server owner, anyone with Manage Server / Manage Channels / Manage Messages, and the bot owner
(`DISCORD_OWNER_ID`). Types are `assignment`, `quiz`, `test`, `exam`, `final`, `other`.

Deadlines you add or edit by hand are never overwritten by `!scan` or a new `!setsyllabus`.

## REST API

All endpoints are under `/api/guilds/{guildId}` and require an `X-API-Key` header when `API_KEY` is set.

| Method | Path | Body |
|---|---|---|
| `GET` | `/deadlines?upcoming=true` | |
| `POST` | `/deadlines` | `{"title", "dueDate": "YYYY-MM-DD", "type"}` |
| `PUT` | `/deadlines/{id}` | any of `title`, `dueDate`, `type` |
| `DELETE` | `/deadlines/{id}` | |
| `POST` | `/syllabus` | `{"content": "..."}` (max 20,000 characters) |
| `POST` | `/syllabus/file` | multipart `file` (PDF or .txt, max 10 MB) |
| `GET` | `/syllabus` | |
| `POST` | `/scan` | |
| `GET` / `PUT` | `/settings`, `/settings/channel` | `{"channelId"}` |

## Configuration

| Variable | Service | Default | Notes |
|---|---|---|---|
| `DISCORD_TOKEN` | server | empty | Empty runs REST-only |
| `DISCORD_OWNER_ID` | server | empty | Always allowed to run moderator commands |
| `API_KEY` | server | empty | Required on `/api/**` when set; always set it in production |
| `AI_URL` | server | `http://localhost:8000` | |
| `REMINDER_TZ` | server | `America/Chicago` | Defines "today" for reminders and extraction |
| `SPRING_PROFILES_ACTIVE` | server | `local` | `prod` uses MySQL (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSL_MODE`) |
| `AWS_REGION` | ai | `us-east-2` | |
| `BEDROCK_MODEL_ID` | ai | `amazon.titan-text-express-v1` | |
| `MAX_SYLLABUS_CHARS` | ai | `20000` | Keep in step with `app.syllabus.max-chars` on the server |

## Development

Requires Java 25, Maven, and Python 3.10+.

```bash
cd server && mvn test                    # unit + integration tests (H2, AI and Discord mocked)
cd ai && pip install -r requirements-dev.txt && pytest
```

Deployment: see [DEPLOY.md](DEPLOY.md).
