import time

from fastapi.testclient import TestClient

from app.main import app

c = TestClient(app)

# settings + decks
s = c.get("/api/settings").json()
assert "model" in s and "mock" in s
print("settings OK:", s["model"], "| mock:", s["mock"])

decks = c.get("/api/decks").json()
assert len(decks) >= 1
print("decks OK:", [(d["id"], d["name"], d["cards"]) for d in decks])

# create deck
deck = c.post("/api/decks", json={"name": "Live Test Deck"}).json()
deck_id = deck["id"]
print("created deck:", deck_id)

# generate (background job)
r = c.post(
    f"/api/decks/{deck_id}/generate",
    json={
        "source_text": (
            "Mitochondria produce ATP via cellular respiration. Helicase unwinds the DNA double helix. "
            "Ribosomes synthesize proteins. The Golgi apparatus packages proteins. The nucleus stores chromatin."
        ),
        "flashcards": True,
        "mcqs": True,
        "num_cards": 5,
        "num_mcqs": 2,
        "tags": ["Biology"],
    },
)
assert r.status_code == 200, r.text
job_id = r.json()["job_id"]
print("job started:", job_id)

job = None
for _ in range(120):
    job = c.get(f"/api/jobs/{job_id}").json()
    if job["status"] in ("done", "error"):
        break
    time.sleep(2)
assert job["status"] == "done", job
assert job["cards"] == 5 and job["mcqs"] == 2, job
print("job done:", job["cards"], "cards,", job["mcqs"], "mcqs")

deck = c.get(f"/api/decks/{deck_id}").json()
assert len(deck["cards"]) == 5 and len(deck["mcqs"]) == 2
print("deck contents OK")
card_id = deck["cards"][0]["id"]
mcq_id = deck["mcqs"][0]["id"]

# export
r = c.get(f"/api/decks/{deck_id}/export")
assert r.status_code == 200 and r.content[:4] == b"PK\x03\x04"
print("apkg export OK:", len(r.content), "bytes")

# review (SM-2)
r = c.get(f"/api/decks/{deck_id}/review")
assert r.status_code == 200 and r.json()["count"] == 5
r = c.post("/api/review", json={"deck_id": deck_id, "card_id": card_id, "quality": 5})
assert r.json()["interval"] == 1
r = c.post("/api/review", json={"deck_id": deck_id, "card_id": card_id, "quality": 4})
assert r.json()["interval"] == 6
deck = c.get(f"/api/decks/{deck_id}").json()
due_cards = [x for x in deck["cards"] if x["id"] == card_id]
assert due_cards[0]["interval"] == 6 and due_cards[0]["reps"] == 2
print("SM-2 review OK (interval 6, reps 2)")

# quiz
r = c.post(f"/api/decks/{deck_id}/quiz/answer", json={"mcq_id": mcq_id, "choice": 0})
assert r.status_code == 200 and "correct" in r.json()
r = c.post(f"/api/decks/{deck_id}/quiz/answer", json={"mcq_id": mcq_id, "choice": 1})
assert r.json()["correct"] in (True, False)
print("quiz answer OK:", r.json()["correct"], "|", r.json()["explanation"][:40])
deck = c.get(f"/api/decks/{deck_id}").json()
answered = [m for m in deck["mcqs"] if m["id"] == mcq_id][0]
assert answered["answered"] == 2
print("quiz stats OK: answered=2")

# edit + delete card
r = c.patch(f"/api/decks/{deck_id}/cards/{card_id}", json={"back": "ATP (edited)"})
assert r.json()["back"] == "ATP (edited)"
r = c.delete(f"/api/decks/{deck_id}/cards/{card_id}")
assert r.status_code == 200
print("edit + delete card OK")

# delete mcq
r = c.delete(f"/api/decks/{deck_id}/mcqs/{mcq_id}")
assert r.status_code == 200
print("delete mcq OK")

# persistence check: deck still exists after the earlier writes
assert c.get(f"/api/decks/{deck_id}").status_code == 200
print("persistence OK")

# cleanup
c.delete(f"/api/decks/{deck_id}")
print("ALL LIVE TESTS PASSED")
