import os
import re
from datetime import date
from functools import lru_cache
from typing import Literal

import anthropic
from anthropic import AnthropicBedrockMantle
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="CometBot AI")

MODEL_ID = os.environ.get("BEDROCK_MODEL_ID", "anthropic.claude-opus-5")
REGION = os.environ.get("AWS_REGION", "us-east-2")
MAX_SYLLABUS_CHARS = int(os.environ.get("MAX_SYLLABUS_CHARS", 300_000))

DeadlineType = Literal["assignment", "quiz", "test", "exam", "final", "other"]

SYSTEM_PROMPT = """You extract graded deadlines from a university course syllabus for a student reminder bot.

Include every assignment, homework, project, lab, quiz, test, exam, and final that has a concrete due date. Leave out items without a specific date (e.g. "TBA", "week 5"), office hours, holidays, and lecture topics.

For each item give:
- title: short, as the syllabus names it (e.g. "Homework 3", "Midterm 1")
- type: assignment, quiz, test, exam, final, or other
- date: the due date as YYYY-MM-DD

Syllabi often omit the year. Resolve it from the term named in the syllabus; if the term isn't stated, pick the year that puts the date in the current or upcoming academic term relative to today's date. If an item appears more than once, list it once."""


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
    type: DeadlineType
    date: str = Field(description="Due date as YYYY-MM-DD")


class ModelDeadlines(BaseModel):
    deadlines: list[ModelDeadline]


class ExtractionError(Exception):
    pass


@lru_cache(maxsize=1)
def _client() -> AnthropicBedrockMantle:
    # Credentials come from the default AWS chain (EC2 instance role in prod).
    return AnthropicBedrockMantle(aws_region=REGION)


def call_model(syllabus: str, today: date) -> list[ModelDeadline]:
    try:
        response = _client().messages.parse(
            model=MODEL_ID,
            max_tokens=16000,
            system=SYSTEM_PROMPT,
            messages=[
                {
                    "role": "user",
                    "content": f"Today's date: {today.isoformat()}\n\n<syllabus>\n{syllabus}\n</syllabus>",
                }
            ],
            output_format=ModelDeadlines,
        )
    except anthropic.APIStatusError as exc:
        raise ExtractionError(f"model returned HTTP {exc.status_code}") from exc
    except anthropic.APIConnectionError as exc:
        raise ExtractionError("could not reach the model") from exc

    if response.stop_reason == "refusal":
        raise ExtractionError("the model declined to process this syllabus")
    if response.stop_reason == "max_tokens":
        raise ExtractionError("the syllabus has too many deadlines to extract in one pass")
    if response.parsed_output is None:
        raise ExtractionError("the model returned no structured output")
    return response.parsed_output.deadlines


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
        items.append(ExtractItem(title=title, type=d.type, date=d.date))
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
