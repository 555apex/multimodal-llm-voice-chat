#!/usr/bin/env python3
"""Download a pinned public Hugging Face snapshot with resume and SHA-256 checks."""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


USER_AGENT = "dgx-spark-model-serving/1.0"
CHUNK_SIZE = 8 * 1024 * 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--target", required=True, type=Path)
    parser.add_argument("--endpoint", default=os.environ.get("HF_ENDPOINT", "https://hf-mirror.com"))
    parser.add_argument("--exclude-prefix", action="append", default=[])
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def api_manifest(endpoint: str, repo: str, revision: str) -> dict[str, object]:
    repo_path = "/".join(urllib.parse.quote(part, safe="") for part in repo.split("/"))
    revision_path = urllib.parse.quote(revision, safe="")
    url = f"{endpoint.rstrip('/')}/api/models/{repo_path}/revision/{revision_path}?blobs=true"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        payload = json.load(response)
    resolved = payload.get("sha")
    if resolved != revision:
        raise RuntimeError(f"revision mismatch: requested {revision}, mirror returned {resolved}")
    return payload


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(CHUNK_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def expected_sha(sibling: dict[str, object]) -> str | None:
    lfs = sibling.get("lfs") or {}
    oid = (lfs.get("sha256") or lfs.get("oid")) if isinstance(lfs, dict) else None
    if not isinstance(oid, str):
        return None
    digest = oid.removeprefix("sha256:")
    if len(digest) != 64 or any(char not in "0123456789abcdef" for char in digest.lower()):
        raise ValueError("invalid LFS SHA-256 in repository metadata")
    return digest.lower()


def download_one(
    endpoint: str,
    repo: str,
    revision: str,
    target_root: Path,
    sibling: dict[str, object],
) -> dict[str, object]:
    name = str(sibling["rfilename"])
    size_value = sibling.get("size")
    if size_value is None and isinstance(sibling.get("lfs"), dict):
        size_value = sibling["lfs"].get("size")
    expected_size = int(size_value) if size_value is not None else None
    digest = expected_sha(sibling)
    target = target_root / name
    partial = target.with_name(target.name + ".part")
    target.parent.mkdir(parents=True, exist_ok=True)

    if target.exists() and (expected_size is None or target.stat().st_size == expected_size):
        if digest is None or sha256_file(target) == digest:
            print(f"verified existing {name}", flush=True)
            return {"name": name, "size": target.stat().st_size, "sha256": digest, "status": "existing"}
        target.rename(partial)

    quoted_name = urllib.parse.quote(name, safe="/")
    quoted_repo = "/".join(urllib.parse.quote(part, safe="") for part in repo.split("/"))
    url = f"{endpoint.rstrip('/')}/{quoted_repo}/resolve/{revision}/{quoted_name}?download=true"

    for attempt in range(1, 13):
        offset = partial.stat().st_size if partial.exists() else 0
        if partial.exists() and expected_size is not None and offset == expected_size:
            actual_digest = sha256_file(partial) if digest else None
            if digest is None or actual_digest == digest:
                os.replace(partial, target)
                print(f"completed resumed {name} ({offset} bytes)", flush=True)
                return {"name": name, "size": offset, "sha256": digest, "status": "resumed"}
            partial.unlink()
            offset = 0
        headers = {"User-Agent": USER_AGENT}
        if offset:
            headers["Range"] = f"bytes={offset}-"
        request = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                append = offset > 0 and response.status == 206
                mode = "ab" if append else "wb"
                if offset and not append:
                    offset = 0
                with partial.open(mode) as handle:
                    shutil.copyfileobj(response, handle, length=CHUNK_SIZE)
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            if attempt == 12:
                raise RuntimeError(f"download failed for {name}: {type(exc).__name__}") from exc
            delay = min(60, 2**min(attempt, 5))
            print(f"retry {attempt}/12 {name} after {type(exc).__name__}; {delay}s", flush=True)
            time.sleep(delay)
            continue

        actual_size = partial.stat().st_size
        if expected_size is not None and actual_size != expected_size:
            if actual_size > expected_size:
                partial.unlink()
            if attempt == 12:
                raise RuntimeError(f"size mismatch for {name}: {actual_size} != {expected_size}")
            print(f"resume {name}: {actual_size}/{expected_size}", flush=True)
            continue
        actual_digest = sha256_file(partial) if digest else None
        if digest and actual_digest != digest:
            partial.unlink()
            if attempt == 12:
                raise RuntimeError(f"SHA-256 mismatch for {name}")
            print(f"checksum retry {attempt}/12 {name}", flush=True)
            continue
        os.replace(partial, target)
        print(f"downloaded {name} ({actual_size} bytes)", flush=True)
        return {"name": name, "size": actual_size, "sha256": digest, "status": "downloaded"}
    raise AssertionError("unreachable")


def main() -> None:
    args = parse_args()
    args.target.mkdir(parents=True, exist_ok=True)
    payload = api_manifest(args.endpoint, args.repo, args.revision)
    siblings = [
        item
        for item in payload.get("siblings") or []
        if not any(str(item.get("rfilename", "")).startswith(prefix) for prefix in args.exclude_prefix)
    ]
    print(
        f"snapshot {args.repo}@{args.revision}: {len(siblings)} files, "
        f"{sum(int(item.get('size') or (item.get('lfs') or {}).get('size') or 0) for item in siblings)} bytes, "
        f"workers={args.workers}",
        flush=True,
    )
    if args.dry_run:
        print("dry-run complete; no files downloaded", flush=True)
        return
    results: list[dict[str, object]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [
            pool.submit(download_one, args.endpoint, args.repo, args.revision, args.target, item)
            for item in siblings
        ]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
    manifest = {
        "repo_id": args.repo,
        "revision": args.revision,
        "endpoint": args.endpoint,
        "downloaded_at": datetime.now(timezone.utc).isoformat(),
        "excluded_prefixes": args.exclude_prefix,
        "files": sorted(results, key=lambda item: str(item["name"])),
        "total_bytes": sum(int(item["size"]) for item in results),
        "verified": True,
    }
    manifest_path = args.target / ".deployment-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"verified snapshot manifest: {manifest_path}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr, flush=True)
        raise
