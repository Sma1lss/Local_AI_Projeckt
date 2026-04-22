from fastapi.testclient import TestClient

from services.executor import app


def test_executor_factorial_python():
    client = TestClient(app)
    code = """
def fact(n):
    return 1 if n <= 1 else n * fact(n-1)

print(fact(5))
"""
    resp = client.post("/execute", json={"language": "python", "code": code, "tests": "", "stdin": ""})
    assert resp.status_code == 200
    data = resp.json()
    assert data["exit_code"] == 0
    assert "120" in data["stdout"]
