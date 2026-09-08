#!/usr/bin/env python3
"""Small OpenAI-compatible generation test for the active profile."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import time
import urllib.request


SERVICES = {
    "qwen": ("http://127.0.0.1:8001/v1/chat/completions", None),
    "gpt": ("http://100.119.145.78:8002/v1/chat/completions", "gpt-oss-120b"),
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("service", choices=SERVICES)
    parser.add_argument("--reasoning-effort", choices=("low", "medium", "high"), default="medium")
    args = parser.parse_args()
    url, model = SERVICES[args.service]
    if args.service == "qwen":
        sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
        from model_control import resolve
        model = resolve()['name']
    payload = {
        "model": model,
        "messages": [
            {
                "role": "user",
                "content": "用中文回答：12乘以17是多少？只需给出结果和一句简短解释。",
            }
        ],
        "max_tokens": 256,
        "temperature": 0.0,
    }
    if args.service == "gpt":
        payload["reasoning_effort"] = args.reasoning_effort
    else:
        payload["chat_template_kwargs"] = {"enable_thinking": False}
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.monotonic()
    with urllib.request.urlopen(request, timeout=600) as response:
        result = json.load(response)
    elapsed = time.monotonic() - started
    choice = result["choices"][0]["message"]
    print(json.dumps({"model": result.get("model"), "seconds": round(elapsed, 2), "message": choice}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
