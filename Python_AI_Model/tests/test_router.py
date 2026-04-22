from fastapi.testclient import TestClient

from services.router import app


def test_router_code_classification():
    client = TestClient(app)
    resp = client.post("/route", json={"query": "fix this python bug in function"})
    assert resp.status_code == 200
    data = resp.json()
    assert "code" in data["route"]
