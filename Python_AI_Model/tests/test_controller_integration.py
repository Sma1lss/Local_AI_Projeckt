from fastapi.testclient import TestClient

import services.controller as controller


def test_controller_integration_mocked(monkeypatch):
    def fake_post(url, payload):
        if url.endswith('/route'):
            return {"route": ["design", "reasoning"], "confidence": 0.9}
        if url.endswith('/search'):
            return {
                "passages": [
                    {"doc_id": "d1", "score": 0.9, "snippet": "Use microservices and clear contracts."},
                    {"doc_id": "d2", "score": 0.8, "snippet": "Track latency and failure budgets."},
                ]
            }
        if url.endswith('/infer'):
            return {
                "text": "Architecture answer: use router, retriever, executor, aggregator with SLOs.",
                "latency_ms": 50,
                "logprob": 0.3,
            }
        if url.endswith('/execute'):
            return {"exit_code": 0, "stdout": "ok", "stderr": "", "runtime": 0.1}
        return {}

    monkeypatch.setattr(controller, "_post", fake_post)

    client = TestClient(controller.app)
    resp = client.post('/ask', json={"query": "design local inference architecture", "mode": "normal", "top_k": 3})
    assert resp.status_code == 200
    data = resp.json()
    assert "answer" in data
    assert "used_models" in data
    assert len(data["provenance"]) >= 1
