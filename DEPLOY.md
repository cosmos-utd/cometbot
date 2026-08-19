# CometBot deployment (AWS)

Target: **1 EC2** running the Spring Boot app + FastAPI service, **1 RDS MySQL**, no secrets in files.

## 1. RDS (MySQL)

- Create a MySQL 8 RDS instance (Single DB, dev class is fine).
- Database name: `cometbot`. Note the endpoint, master user, password.
- Security group: allow **3306 only from the EC2 instance's security group** (not the internet).

## 2. EC2

- Launch an Ubuntu instance (t3.small+), attach an **IAM instance role** with `AmazonBedrockFullAccess`.
- Security group: allow **22** (SSH) and **8080** (API) from your IP. **8000 stays private.**

## 3. Install on EC2

```bash
# Java 21+
sudo apt update && sudo apt install -y openjdk-21-jre-headless python3-venv
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
ExecStart=/opt/cometbot/ai/.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000

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

Create `/etc/systemd/system/cometbot-server.service`:

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
Environment=AI_URL=http://localhost:8000
Environment=DISCORD_TOKEN=<bot-token>
Environment=DISCORD_OWNER_ID=<your-discord-user-id>
Environment=REMINDER_TZ=America/Chicago
ExecStart=/usr/bin/java -jar /opt/cometbot/server/target/cometbot-server-0.1.0.jar

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now cometbot-server
```

## 4. Verify

```bash
curl localhost:8080/actuator/health   # {"status":"UP"}
curl localhost:8000/health            # {"status":"up"}
```

## Local dev (no AWS)

```bash
cd server && mvn spring-boot:run          # H2 in-memory, REST-only unless DISCORD_TOKEN set
cd ai && python -m venv .venv             # FastAPI on :8000
.venv/Scripts/activate && pip install -r requirements.txt && python main.py
```

## Alternative: Docker Compose (prod-like MySQL locally)

```bash
docker compose up --build
```
Needs Docker; uses profile `prod` pointed at the compose `mysql` service.