#!/usr/bin/env python3
"""Read-only native-v1 smoke test for an authorised Melodee deployment.

Credentials are supplied only through environment variables and are never printed.
The script does not refresh/rotate, mutate, download or persist anything.

Example:
  MELODEE_BASE_URL=https://music.example.com \
  MELODEE_USERNAME=test-user \
  MELODEE_PASSWORD='...' \
  python3 scripts/melodee_prod_smoke.py
"""
from __future__ import annotations

import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen

TIMEOUT = 20


def request(method: str, url: str, *, token: str | None = None, body: dict | None = None,
            headers: dict[str, str] | None = None) -> tuple[int, bytes, dict[str, str]]:
    data = None if body is None else json.dumps(body).encode()
    merged = {"Accept": "application/json", "User-Agent": "melodee-prod-smoke/1"}
    if token:
        merged["Authorization"] = f"Bearer {token}"
    if body is not None:
        merged["Content-Type"] = "application/json"
    if headers:
        merged.update(headers)
    req = Request(url, data=data, method=method, headers=merged)
    try:
        with urlopen(req, timeout=TIMEOUT) as response:
            return response.status, response.read(), dict(response.headers)
    except HTTPError as error:
        return error.code, error.read(), dict(error.headers)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def main() -> None:
    base = os.environ.get("MELODEE_BASE_URL", "").rstrip("/")
    username = os.environ.get("MELODEE_USERNAME", "")
    password = os.environ.get("MELODEE_PASSWORD", "")
    if not base or not username or not password:
        fail("set MELODEE_BASE_URL, MELODEE_USERNAME and MELODEE_PASSWORD")
    if not base.startswith("https://"):
        fail("MELODEE_BASE_URL must be an HTTPS origin")

    status, _, _ = request("GET", f"{base}/readyz")
    if status != 200:
        fail(f"/readyz returned {status}")
    print("PASS readyz")

    status, body, _ = request(
        "POST", f"{base}/api/v1/auth/authenticate",
        body={"userName": username, "password": password},
    )
    if status != 200:
        fail(f"authenticate returned {status}: {body[:160].decode(errors='replace')}")
    try:
        auth = json.loads(body)
        token = auth["token"]
        user = auth["user"]
    except (ValueError, KeyError):
        fail("authenticate response was not the expected JSON")
    if not isinstance(token, str) or not token:
        fail("authenticate response had no access token")
    print(f"PASS authenticate user={user.get('username', '?')} id={user.get('id', '?')}")

    status, body, _ = request("GET", f"{base}/api/v1/user/me", token=token)
    if status != 200:
        fail(f"/user/me returned {status}")
    print("PASS user/me")

    status, body, _ = request("GET", f"{base}/api/v1/albums?page=1&pageSize=5", token=token)
    if status != 200:
        fail(f"/albums returned {status}")
    albums = json.loads(body).get("data", [])
    if not albums:
        fail("account has no readable albums")
    album = albums[0]
    print(f"PASS albums count={len(albums)} first={album.get('name', '?')}")

    status, body, _ = request("GET", f"{base}/api/v1/albums/{album['id']}/songs", token=token)
    if status != 200:
        fail(f"/albums/{{id}}/songs returned {status}")
    songs = json.loads(body).get("data", [])
    if not songs:
        fail("first album has no readable songs")
    song = songs[0]
    print(f"PASS album songs count={len(songs)} first={song.get('title', '?')}")

    stream_path = song.get("streamUrl", "")
    stream_url = urljoin(base + "/", stream_path)
    status, _, headers = request("HEAD", stream_url, headers={"Range": "bytes=0-1023"})
    if status not in (200, 206):
        fail(f"stream HEAD returned {status}")
    status, payload, headers = request("GET", stream_url, headers={"Range": "bytes=0-1023"})
    if status not in (200, 206):
        fail(f"stream range GET returned {status}")
    content_type = headers.get("Content-Type", "")
    print(f"PASS stream range status={status} type={content_type} bytes={len(payload)}")

    print("PASS production auth/catalog/stream smoke test")


if __name__ == "__main__":
    try:
        main()
    except URLError as error:
        fail(f"network error: {error.reason}")
