from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import requests


@dataclass
class Asset:
    name: str
    url: str
    target_path: str
    sha256: str = ""
    type: str = "model"


def load_manifest(path: Path) -> list[Asset]:
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    assets = []
    for item in data.get("assets", []):
        assets.append(
            Asset(
                name=item["name"],
                url=item["url"],
                target_path=item["target_path"],
                sha256=item.get("sha256", ""),
                type=item.get("type", "model"),
            )
        )
    return assets


def sha256sum(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _request_with_retry(session: requests.Session, url: str, headers: dict[str, str] | None = None, retries: int = 3):
    last_exc: Optional[Exception] = None
    for attempt in range(1, retries + 1):
        try:
            return session.get(url, stream=True, timeout=(15, 300), headers=headers)
        except Exception as exc:  # pragma: no cover - network instability path
            last_exc = exc
            wait_s = min(3 * attempt, 15)
            print(f"[retry] {url} attempt={attempt} wait={wait_s}s err={exc}", file=sys.stderr)
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
                if resp.status_code in (401, 403):
                    raise PermissionError(f"Access denied for {url}. Token/license may be required.")
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


def maybe_extract_llamacpp(base_dir: Path, zip_path: Path) -> None:
    if not zip_path.exists():
        return
    extract_root = base_dir / "tmp" / "llama_extract"
    target = base_dir / "llama.cpp"

    if extract_root.exists():
        shutil.rmtree(extract_root)
    extract_root.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(extract_root)

    candidates = list(extract_root.glob("llama.cpp-*"))
    if not candidates:
        raise RuntimeError("Could not find extracted llama.cpp directory")

    if target.exists():
        shutil.rmtree(target)
    shutil.move(str(candidates[0]), str(target))
    shutil.rmtree(extract_root)
    print(f"[ok] extracted llama.cpp -> {target}")


def should_skip_non_file_url(url: str) -> bool:
    return "huggingface.co" in url and "/resolve/" not in url


def main() -> int:
    parser = argparse.ArgumentParser(description="Resumable downloader for model assets")
    parser.add_argument("--manifest", type=Path, default=Path("config/models.json"))
    parser.add_argument("--base-dir", type=Path, default=Path("."))
    parser.add_argument("--only-type", type=str, default="")
    parser.add_argument("--name", type=str, default="")
    parser.add_argument("--extract-llamacpp", action="store_true")
    args = parser.parse_args()

    base_dir = args.base_dir.resolve()
    manifest = args.manifest.resolve()

    assets = load_manifest(manifest)

    if args.only_type:
        assets = [a for a in assets if a.type == args.only_type]
    if args.name:
        assets = [a for a in assets if a.name == args.name]

    if not assets:
        print("No assets selected")
        return 0

    failures = 0
    for asset in assets:
        dest = base_dir / asset.target_path
        print(f"[download] {asset.name} -> {dest}")

        if should_skip_non_file_url(asset.url):
            print(
                f"[skip] {asset.name}: non-file URL requires huggingface-cli snapshot ({asset.url})",
                file=sys.stderr,
            )
            continue

        try:
            download_file(asset.url, dest)
        except Exception as exc:
            failures += 1
            print(f"[error] {asset.name}: {exc}", file=sys.stderr)
            continue

        if asset.sha256:
            actual = sha256sum(dest)
            if actual.lower() != asset.sha256.lower():
                failures += 1
                print(f"[error] sha256 mismatch for {asset.name}: {actual}", file=sys.stderr)
            else:
                print(f"[ok] sha256 {asset.name}")

    if args.extract_llamacpp:
        zip_path = base_dir / "tmp" / "llama.cpp-master.zip"
        maybe_extract_llamacpp(base_dir, zip_path)

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
