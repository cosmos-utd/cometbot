# CometBot deployment (AWS)

Target: **1 EC2** running the Spring Boot app + FastAPI service, **1 RDS MySQL**, no secrets in files.

## 0. Discord

- In the [Discord developer portal](https://discord.com/developers/applications), open your bot and enable the
  **Message Content** privileged intent (commands are read from message text).
- Invite the bot with the **Send Messages**, **Read Message History** and **View Channels** permissions.

## 1. RDS (MySQL)

- Create a MySQL 8 RDS instance (Single DB, dev class is fine).
- Database name: `cometbot`. Note the endpoint, master user, password.
- Security group: allow **3306 only from the EC2 instance's security group** (not the internet).
- Connections use TLS (`DB_SSL_MODE=REQUIRED`). For certificate verification, import the RDS CA bundle into a
  Java truststore and set `DB_SSL_MODE=VERIFY_IDENTITY`.

## 2. Bedrock

- In the Bedrock console for `us-east-2`, open **Model access** and confirm **Titan Text G1 - Express**
  (`amazon.titan-text-express-v1`) is enabled, and check Bedrock's model lifecycle page that it isn't
  marked legacy/end-of-life in your region.
- Titan reads about 8K tokens, so syllabi over 20,000 characters are rejected with a clear error
  (`MAX_SYLLABUS_CHARS` / `app.syllabus.max-chars`).

## 3. EC2

- Launch an Ubuntu 24.04 instance (t3.small+), attach an **IAM instance role** allowed to call
  `bedrock:InvokeModel` on the Titan model (e.g. `AmazonBedrockFullAccess`).
- Security group: allow **22** (SSH) and **8080** (API) from your IP. **8000 stays private.**

## 4. Install on EC2

```bash
# Java 25 (Eclipse Temurin), Maven, Python venv
sudo apt update && sudo apt install -y wget apt-transport-https gpg maven python3-venv
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
echo "deb https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install -y temurin-25-jdk
java -version   # must report 25
```

### FastAPI service

```bash
git clone https://github.com/cosmos-utd/cometbot.git /opt/cometbot
cd /opt/cometbot/ai
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
```

Create `/etc/systemd/system/cometbot-ai.service`:

```ini
[Unit]
Description=CometBot AI (FastAPI)
After=network.target

[Service]
WorkingDirectory=/opt/cometbot/ai
Environment=AWS_REGION=us-east-2
Environment=BEDROCK_MODEL_ID=amazon.titan-text-express-v1
ExecStart=/opt/cometbot/ai/.venv/bin/uvicorn main:app --host 127.0.0.1 --port 8000
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now cometbot-ai
```

### Spring Boot service

```bash
cd /opt/cometbot/server
mvn -q package -DskipTests
```

Create `/etc/systemd/system/cometbot-server.service` (keep this file readable by root only, since it holds secrets:
`sudo chmod 600`):

```ini
[Unit]
Description=CometBot Server
After=network.target cometbot-ai.service

[Service]
WorkingDirectory=/opt/cometbot/server
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DB_HOST=<rds-endpoint>
Environment=DB_PORT=3306
Environment=DB_NAME=cometbot
Environment=DB_USER=<db-user>
Environment=DB_PASSWORD=<db-password>
Environment=DB_SSL_MODE=REQUIRED
Environment=AI_URL=http://localhost:8000
Environment=API_KEY=<long-random-string>
Environment=DISCORD_TOKEN=<bot-token>
Environment=DISCORD_OWNER_ID=<your-discord-user-id>
Environment=REMINDER_TZ=America/Chicago
ExecStart=/usr/bin/java -jar /opt/cometbot/server/target/cometbot-server-0.1.0.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now cometbot-server
```

Generate the API key with e.g. `openssl rand -hex 32`. REST calls must send it as `X-API-Key`.

## 5. Verify

```bash
curl localhost:8080/actuator/health   # {"status":"UP"}
curl localhost:8000/health            # {"status":"up"}
curl -H "X-API-Key: $API_KEY" localhost:8080/api/guilds/<guild-id>/deadlines
```

In Discord, run `!help`, then `!setsyllabus` with a syllabus PDF attached.

## Updating

```bash
cd /opt/cometbot && git pull
ai/.venv/bin/pip install -r ai/requirements.txt && sudo systemctl restart cometbot-ai
(cd server && mvn -q package -DskipTests) && sudo systemctl restart cometbot-server
```

The schema is managed by Hibernate (`ddl-auto=update`), which adds new columns automatically.

## Local dev (no AWS database)

```bash
# Server: H2 in-memory DB, REST-only unless DISCORD_TOKEN is set
cd server && mvn spring-boot:run

# AI service on :8000 (Python 3.10+, needs AWS credentials that can call Bedrock)
cd ai && python3 -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt && python main.py
```

## Alternative: Docker Compose (prod-like MySQL locally)

```bash
cp .env.example .env   # fill in DISCORD_TOKEN, AWS credentials, API_KEY
docker compose up --build
```

Uses profile `prod` pointed at the compose `mysql` service.
