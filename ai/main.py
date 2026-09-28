import json
import os
import re
from datetime import date
from functools import lru_cache

import boto3
from botocore.exceptions import BotoCoreError, ClientError
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="CometBot AI")

MODEL_ID = os.environ.get("BEDROCK_MODEL_ID", "amazon.titan-text-express-v1")
REGION = os.environ.get("AWS_REGION", "us-east-2")
# Titan Text Express reads ~8K tokens; longer syllabi are rejected instead of silently truncated.
MAX_SYLLABUS_CHARS = int(os.environ.get("MAX_SYLLABUS_CHARS", 20_000))

VALID_TYPES = {"assignment", "quiz", "test", "exam", "final", "other"}

PROMPT = """You are a syllabus deadline parser. Today's date is {today}. From the syllabus text below, extract every assignment, quiz, test, exam, and final that has a concrete due date. If a date has no year, use the year that places it in the term the syllabus describes (or the current/upcoming term relative to today). Return ONLY a JSON array with no other text. Each element must be exactly: {{"title": "...", "type": "assignment|quiz|test|exam|final|other", "date": "YYYY-MM-DD"}}. If a due date is missing or unclear, skip it. If nothing is found, return [].

SYLLABUS:
{syllabus}"""


class ExtractRequest(BaseModel):
    syllabus: str
    guild_id: str | None = None
    today: date | None = None


class ExtractItem(BaseModel):
    title: str
    type: str
    date: str


class ExtractResponse(BaseModel):
    items: list[ExtractItem]


class ModelDeadline(BaseModel):
    title: str
    type: str
    date: str


class ExtractionError(Exception):
    pass


@lru_cache(maxsize=1)
def _client():
    # Credentials come from the default AWS chain (EC2 instance role in prod).
    return boto3.client("bedrock-runtime", region_name=REGION)


def call_model(syllabus: str, today: date) -> list[ModelDeadline]:
    try:
        response = _client().invoke_model(
            modelId=MODEL_ID,
            contentType="application/json",
            accept="application/json",
            body=json.dumps(
                {
                    "inputText": PROMPT.format(today=today.isoformat(), syllabus=syllabus),
                    "textGenerationConfig": {"maxTokenCount": 3000, "temperature": 0.0},
                }
            ).encode("utf-8"),
        )
        payload = json.loads(response["body"].read().decode("utf-8"))
    except ClientError as exc:
        code = exc.response.get("Error", {}).get("Code", "error")
        raise ExtractionError(f"Bedrock returned {code}") from exc
    except (BotoCoreError, ValueError) as exc:
        raise ExtractionError("could not reach Bedrock") from exc

    result = (payload.get("results") or [{}])[0]
    if result.get("completionReason") == "LENGTH":
        raise ExtractionError("the syllabus has too many deadlines to extract in one pass")
    return parse_output(result.get("outputText", ""))


def parse_output(text: str) -> list[ModelDeadline]:
    """Titan has no JSON mode: pull the first JSON array out of its reply."""
    clean = text.strip()
    clean = re.sub(r"^```(?:json)?\s*", "", clean)
    clean = re.sub(r"\s*```$", "", clean)
    try:
        data = json.loads(clean)
    except json.JSONDecodeError:
        match = re.search(r"\[.*\]", clean, re.DOTALL)
        if not match:
            raise ExtractionError("the model's reply wasn't a JSON list of deadlines")
        try:
            data = json.loads(match.group(0))
        except json.JSONDecodeError as exc:
            raise ExtractionError("the model's reply wasn't a JSON list of deadlines") from exc

    if not isinstance(data, list):
        raise ExtractionError("the model's reply wasn't a JSON list of deadlines")

    deadlines = []
    for entry in data:
        if isinstance(entry, dict):
            deadlines.append(
                ModelDeadline(
                    title=str(entry.get("title", "")),
                    type=str(entry.get("type", "")),
                    date=str(entry.get("date", "")).strip(),
                )
            )
    return deadlines


def normalize(deadlines: list[ModelDeadline]) -> list[ExtractItem]:
    items: list[ExtractItem] = []
    seen: set[tuple[str, str]] = set()
    for d in deadlines:
        title = d.title.strip()
        if not title or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", d.date):
            continue
        try:
            date.fromisoformat(d.date)
        except ValueError:
            continue
        key = (title.lower(), d.date)
        if key in seen:
            continue
        seen.add(key)
        kind = d.type.strip().lower()
        items.append(ExtractItem(title=title, type=kind if kind in VALID_TYPES else "other", date=d.date))
    return items


@app.post("/extract", response_model=ExtractResponse)
def extract(req: ExtractRequest):
    syllabus = req.syllabus.strip()
    if not syllabus:
        raise HTTPException(status_code=400, detail="syllabus is required")
    if len(syllabus) > MAX_SYLLABUS_CHARS:
        raise HTTPException(
            status_code=413,
            detail=f"syllabus is {len(syllabus)} characters; the limit is {MAX_SYLLABUS_CHARS}",
        )

    try:
        deadlines = call_model(syllabus, req.today or date.today())
    except ExtractionError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return ExtractResponse(items=normalize(deadlines))


@app.get("/health")
def health():
    return {"status": "up"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("AI_PORT", 8000)))
