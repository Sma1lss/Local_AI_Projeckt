from __future__ import annotations

import json
import os
import shutil
import time
import zipfile
from pathlib import Path
from typing import Optional

import requests


def _request_with_retry(session: requests.Session, url: str, headers: dict[str, str] | None = None, retries: int = 3):
    last_exc: Optional[Exception] = None
    for attempt in range(1, retries + 1):
        try:
            return session.get(url, stream=True, timeout=(15, 300), headers=headers)
        except Exception as exc:
            last_exc = exc
            wait_s = min(3 * attempt, 15)
            print(f"[retry] {url} attempt={attempt} wait={wait_s}s err={exc}")
            time.sleep(wait_s)
    raise RuntimeError(f"Failed to request {url}: {last_exc}")


def download_file(url: str, dest: Path, max_attempts: int = 20, chunk_size: int = 256 * 1024) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)

    attempt = 0
    while True:
        attempt += 1
        session = requests.Session()
        resume_from = dest.stat().st_size if dest.exists() else 0
        headers: dict[str, str] = {"Accept-Encoding": "identity"}
        if resume_from > 0:
            headers["Range"] = f"bytes={resume_from}-"

        try:
            with _request_with_retry(session, url, headers=headers, retries=3) as resp:
                if resp.status_code >= 400:
                    raise RuntimeError(f"HTTP {resp.status_code} for {url}")

                mode = "ab" if (resume_from > 0 and resp.status_code == 206) else "wb"
                if mode == "wb" and resume_from > 0:
                    print(f"[warn] server does not support resume, restarting {dest.name}")
                    resume_from = 0

                downloaded = resume_from
                last_report = downloaded

                with dest.open(mode) as f:
                    for chunk in resp.iter_content(chunk_size=chunk_size):
                        if not chunk:
                            continue
                        f.write(chunk)
                        downloaded += len(chunk)
                        if downloaded - last_report >= 20 * 1024 * 1024:
                            print(f"[progress] {dest.name}: {downloaded / (1024*1024):.1f} MB")
                            last_report = downloaded

            return
        except Exception as exc:
            if attempt >= max_attempts:
                raise
            wait_s = min(5 * attempt, 30)
            print(f"[retry] download error: {exc}. attempt={attempt}/{max_attempts} wait={wait_s}s")
            time.sleep(wait_s)


def pick_asset(assets: list[dict]) -> dict:
    candidates = [a for a in assets if a.get("name", "").lower().endswith(".zip")]
    if not candidates:
        raise RuntimeError("No zip assets found in release")

    def score(name: str) -> int:
        n = name.lower()
        s = 0
        if "win" in n:
            s += 3
        if "x64" in n or "x86_64" in n or "amd64" in n:
            s += 3
        if "cpu" in n:
            s += 2
        if "cuda" in n:
            s -= 1
        if "bin" in n or "release" in n:
            s += 1
        return s

    candidates.sort(key=lambda a: score(a.get("name", "")), reverse=True)
    return candidates[0]


def extract_and_stage(zip_path: Path, base_dir: Path) -> Path:
    extract_root = base_dir / "tmp" / "llama_release_extract"
    if extract_root.exists():
        shutil.rmtree(extract_root)
    extract_root.mkdir(parents=True, exist_ok=True)

    if not zipfile.is_zipfile(zip_path):
        raise RuntimeError("Downloaded file is not a valid zip")

    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(extract_root)

    exe_candidates = list(extract_root.rglob("llama-cli.exe"))
    if not exe_candidates:
        exe_candidates = list(extract_root.rglob("main.exe"))
    if not exe_candidates:
        raise RuntimeError("Could not find llama-cli.exe or main.exe in release archive")

    exe_path = exe_candidates[0]
    src_dir = exe_path.parent

    dest_dir = base_dir / "llama.cpp" / "build" / "bin"
    dest_dir.mkdir(parents=True, exist_ok=True)

    for item in src_dir.iterdir():
        if item.is_file():
            shutil.copy2(item, dest_dir / item.name)

    print(f"[ok] staged binaries -> {dest_dir}")
    return dest_dir


def main() -> int:
    base_dir = Path(os.getenv("BASE_DIR", ".")).resolve()
    api = "https://api.github.com/repos/ggerganov/llama.cpp/releases/latest"

    print("[info] fetching release metadata")
    resp = requests.get(api, timeout=30)
    resp.raise_for_status()
    data = resp.json()

    asset = pick_asset(data.get("assets", []))
    name = asset.get("name")
    url = asset.get("browser_download_url")
    if not url:
        raise RuntimeError("Missing browser_download_url in release asset")

    zip_path = base_dir / "tmp" / name

    # download with validation loop
    for attempt in range(1, 6):
        print(f"[download] {name} (attempt {attempt})")
        if zip_path.exists():
            zip_path.unlink()
        download_file(url, zip_path)
        if zipfile.is_zipfile(zip_path):
            break
        print("[warn] downloaded file invalid zip, retrying")
    else:
        raise RuntimeError("Failed to download a valid release zip after retries")

    extract_and_stage(zip_path, base_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
