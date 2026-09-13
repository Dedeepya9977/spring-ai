"""Standard-library local demo. No third-party Python dependencies."""
import base64
import json
import uuid
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:8080/api"


def request(path, body=None, user="developer", key=None):
    headers = {"Authorization": "Basic " + base64.b64encode(f"{user}:local-only".encode()).decode(),
               "Content-Type": "application/json"}
    if key:
        headers["Idempotency-Key"] = key
    req = Request(BASE + path, data=json.dumps(body or {}).encode(), headers=headers, method="POST")
    with urlopen(req, timeout=130) as response:
        return json.load(response)


if __name__ == "__main__":
    session = request("/sessions")
    key = "learn-" + str(uuid.uuid4())
    body = {"message": "Please request a credit for ORD-1001", "mode": "AGENT"}
    run = request(f"/sessions/{session['id']}/runs", body, key=key)
    print(json.dumps(run, indent=2))
    if run["status"] == "WAITING_APPROVAL":
        approved = input("Review the displayed proposal. Type approve to record this synthetic credit: ") == "approve"
        result = request(f"/runs/{run['id']}/decision",
                         {"approve": approved, "reason": "Manual review during the local learning exercise"}, user="reviewer")
        print(json.dumps(result, indent=2))
    replay = request(f"/sessions/{session['id']}/runs", body, key=key)
    assert replay["id"] == run["id"], "Idempotent replay changed the run ID"
    print("Replayed the same run:", replay["id"], replay["status"])
