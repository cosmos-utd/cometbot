import json
import os
import re

import boto3
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="CometBot AI")

MODEL_ID = os.environ.get("BEDROCK_MODEL_ID", "amazon.titan-text-express-v1")
REGION = os.environ.get("AWS_REGION", "us-east-2")

PROMPT = """You are a syllabus deadline parser. From the syllabus text below, extract every assignment, quiz, test, exam, and final that has a concrete due date. Return ONLY a JSON array with no other text. Each element must be exactly: {{"title": "...", "type": "assignment|quiz|test|exam|final", "date": "YYYY-MM-DD"}}. If a due date is missing or unclear, skip it. If nothing is found, return [].

SYLLABUS:
{syllabus}"""


class ExtractRequest(BaseModel):
    syllabus: str
    guild_id: str


class ExtractItem(BaseModel):
    title: str
    type: str
    date: str


class ExtractResponse(BaseModel):
    items: list[ExtractItem]


bedrock = boto3.client("bedrock-runtime", region_name=REGION)


def _clean(text: str) -> str:
    text = text.strip()
    text = re.sub(r"^```(?:json)?\s*", "", text)
    text = re.sub(r"\s*```$", "", text)
    return text


def _parse_items(text: str) -> list[ExtractItem]:
    clean = _clean(text)
    try:
        data = json.loads(clean)
    except json.JSONDecodeError:
        match = re.search(r"\[.*\]", clean, re.DOTALL)
        if not match:
            return []
        try:
            data = json.loads(match.group(0))
        except json.JSONDecodeError:
            return []

    if not isinstance(data, list):
        return []

    items = []
    for entry in data:
        if not isinstance(entry, dict):
            continue
        title = str(entry.get("title", "")).strip()
        date = str(entry.get("date", "")).strip()
        if not title or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
            continue
        items.append(
            ExtractItem(
                title=title,
                type=str(entry.get("type", "assignment")).strip().lower() or "assignment",
                date=date,
            )
        )
    return items


@app.post("/extract", response_model=ExtractResponse)
def extract(req: ExtractRequest):
    if not req.syllabus.strip():
        raise HTTPException(status_code=400, detail="syllabus is required")

    try:
        response = bedrock.invoke_model(
            modelId=MODEL_ID,
            contentType="application/json",
            accept="application/json",
            body=json.dumps(
                {
                    "inputText": PROMPT.format(syllabus=req.syllabus[:20000]),
                    "textGenerationConfig": {
                        "maxTokenCount": 2000,
                        "temperature": 0.0,
                    },
                }
            ).encode("utf-8"),
        )
        payload = json.loads(response["body"].read().decode("utf-8"))
        text = payload.get("results", [{}])[0].get("outputText", "[]")
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Bedrock error: {exc}") from exc

    return ExtractResponse(items=_parse_items(text))


@app.get("/health")
def health():
    return {"status": "up"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("AI_PORT", 8000)))