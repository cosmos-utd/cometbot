from datetime import date

import pytest
from fastapi.testclient import TestClient

import main
from main import ExtractionError, ModelDeadline

client = TestClient(main.app)


@pytest.fixture
def fake_model(monkeypatch):
    calls = []

    def install(result):
        def fake(syllabus, today):
            calls.append((syllabus, today))
            if isinstance(result, Exception):
                raise result
            return result

        monkeypatch.setattr(main, "call_model", fake)
        return calls

    return install


def test_accepts_the_request_the_java_server_sends(fake_model):
    # Must match com.cometbot.dto.ExtractRequest's JSON (see ExtractRequestContractTest).
    calls = fake_model([ModelDeadline(title="HW 1", type="assignment", date="2026-09-10")])

    resp = client.post(
        "/extract",
        json={"syllabus": "HW 1 due Sep 10", "guild_id": "123", "today": "2026-08-20"},
    )

    assert resp.status_code == 200
    assert resp.json() == {"items": [{"title": "HW 1", "type": "assignment", "date": "2026-09-10"}]}
    assert calls == [("HW 1 due Sep 10", date(2026, 8, 20))]


def test_drops_invalid_dates_blank_titles_and_duplicates(fake_model):
    fake_model([
        ModelDeadline(title="Quiz 1", type="quiz", date="2026-09-01"),
        ModelDeadline(title="quiz 1 ", type="quiz", date="2026-09-01"),
        ModelDeadline(title="Quiz 2", type="quiz", date="2026-02-30"),
        ModelDeadline(title="Final", type="final", date="Dec 10"),
        ModelDeadline(title="  ", type="other", date="2026-09-02"),
    ])

    resp = client.post("/extract", json={"syllabus": "x"})

    assert resp.json()["items"] == [{"title": "Quiz 1", "type": "quiz", "date": "2026-09-01"}]


def test_rejects_empty_syllabus():
    resp = client.post("/extract", json={"syllabus": "   "})
    assert resp.status_code == 400


def test_rejects_oversized_syllabus_instead_of_truncating(monkeypatch):
    monkeypatch.setattr(main, "MAX_SYLLABUS_CHARS", 10)
    resp = client.post("/extract", json={"syllabus": "x" * 11})
    assert resp.status_code == 413


def test_model_failure_is_a_502(fake_model):
    fake_model(ExtractionError("the model declined to process this syllabus"))
    resp = client.post("/extract", json={"syllabus": "x"})
    assert resp.status_code == 502
    assert "declined" in resp.json()["detail"]


def test_health():
    assert client.get("/health").json() == {"status": "up"}
