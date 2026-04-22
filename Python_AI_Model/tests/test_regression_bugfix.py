from fastapi.testclient import TestClient

from services.executor import app


def test_regression_bugfix_style_execution():
    client = TestClient(app)
    buggy_fixed = """
def safe_div(a, b):
    if b == 0:
        return None
    return a / b

print(safe_div(10, 2))
print(safe_div(10, 0))
"""
    resp = client.post("/execute", json={"language": "python", "code": buggy_fixed, "tests": "", "stdin": ""})
    assert resp.status_code == 200
    data = resp.json()
    assert data["exit_code"] == 0
    assert "5.0" in data["stdout"]
    assert "None" in data["stdout"]
