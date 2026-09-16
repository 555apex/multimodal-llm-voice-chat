#!/usr/bin/env python3
"""Generate a small, repeatable local LLM load while benchmarking TTS."""
from __future__ import annotations

import argparse
import json
import time
import urllib.request


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:8001")
    parser.add_argument("--model", default="qwen3.6-35b-a3b-nvfp4")
    parser.add_argument("--requests", type=int, default=10)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    rows = []
    for index in range(args.requests):
        payload = json.dumps({
            "model": args.model,
            "messages": [{
                "role": "user",
                "content": "用约300字说明高速公路设施预警的核查、处置和闭环流程。",
            }],
            "max_tokens": 512,
            "temperature": 0.2,
            "chat_template_kwargs": {"enable_thinking": False},
        }).encode()
        request = urllib.request.Request(
            f"{args.url.rstrip('/')}/v1/chat/completions",
            data=payload,
            headers={"Content-Type": "application/json"},
        )
        started = time.monotonic()
        with urllib.request.urlopen(request, timeout=180) as response:
            result = json.load(response)
        rows.append({
            "index": index,
            "elapsedSeconds": time.monotonic() - started,
            "completionTokens": result.get("usage", {}).get("completion_tokens"),
        })

    report = {"model": args.model, "requests": rows}
    with open(args.output, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(json.dumps({
        "requests": len(rows),
        "maximumSeconds": max(row["elapsedSeconds"] for row in rows),
    }))


if __name__ == "__main__":
    main()
