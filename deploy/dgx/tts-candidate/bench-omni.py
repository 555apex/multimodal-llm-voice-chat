#!/usr/bin/env python3
"""Short single/dual-user acceptance benchmark for a raw PCM Omni TTS API."""
from __future__ import annotations

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import json
import statistics
import time
import urllib.request


SAMPLES = [
    "当前G205道路通行正常，请继续关注设施运行状态。",
    "K123+456附近边坡位移为31.495毫米，利用率达到80%，请安排人员核查。",
    "2026年9月15日09:30，系统发现闽江特大桥设施预警，请及时确认并处置。",
    "交通态势分析显示，福州方向车流量正在增加；建议提前疏导，并关注后续变化。",
    "查询结果包含中文、English、括号（重点路段）、链接和多项数据，详细内容请查看页面。",
]


def synthesize(base_url: str, model: str, voice: str, text: str,
               reference_audio: str = "", reference_text: str = "") -> dict[str, float | int]:
    request_body = {
        "model": model,
        "input": text,
        "voice": voice,
        "language": "Chinese",
        "stream": True,
        "stream_format": "audio",
        "response_format": "pcm",
    }
    if reference_audio:
        request_body.update({
            "voice": "default",
            "ref_audio": reference_audio,
            "ref_text": reference_text,
        })
    payload = json.dumps(request_body).encode()
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/v1/audio/speech",
        data=payload,
        headers={"Content-Type": "application/json"},
    )
    started = time.monotonic()
    first = None
    size = 0
    arrivals: list[tuple[float, int]] = []
    with urllib.request.urlopen(request, timeout=180) as response:
        read_chunk = getattr(response, "read1", response.read)
        while True:
            chunk = read_chunk(8192)
            if not chunk:
                break
            arrived = time.monotonic()
            if first is None:
                first = arrived
            size += len(chunk)
            arrivals.append((arrived - started, size))
    ended = time.monotonic()
    if not size or size % 2:
        raise RuntimeError("invalid PCM response")
    duration = size / 2 / 24000
    playback_started = None
    playback_origin = 0.0
    maximum_starvation = 0.0
    for arrived, cumulative_bytes in arrivals:
        buffered_audio = cumulative_bytes / 2 / 24000
        if playback_started is None:
            if buffered_audio >= 0.7:
                playback_started = arrived
                playback_origin = arrived
            continue
        available_until = playback_origin + buffered_audio
        if arrived > available_until:
            starvation = arrived - available_until
            maximum_starvation = max(maximum_starvation, starvation)
            playback_origin += starvation
    return {
        "firstAudioSeconds": (first - started) if first else ended - started,
        "elapsedSeconds": ended - started,
        "audioSeconds": duration,
        "rtf": (ended - started) / duration,
        "maximumStarvationMs": maximum_starvation * 1000,
        "bytes": size,
    }


def percentile(values: list[float], quantile: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, int(len(ordered) * quantile + 0.999) - 1))]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:18092")
    parser.add_argument("--model", default="/models/Qwen--Qwen3-TTS-12Hz-1.7B-CustomVoice")
    parser.add_argument("--voice", default="serena")
    parser.add_argument("--ref-audio")
    parser.add_argument("--ref-text", default="")
    parser.add_argument("--single", type=int, default=10)
    parser.add_argument("--dual", type=int, default=10)
    parser.add_argument("--output", default="omni-benchmark.json")
    args = parser.parse_args()

    reference_audio = ""
    if args.ref_audio:
        with open(args.ref_audio, "rb") as handle:
            reference_audio = "data:audio/wav;base64," + base64.b64encode(handle.read()).decode()
        if not args.ref_text:
            parser.error("--ref-text is required with --ref-audio")

    single = [synthesize(args.url, args.model, args.voice, SAMPLES[index % len(SAMPLES)],
                         reference_audio, args.ref_text)
              for index in range(args.single)]
    dual: list[dict[str, float | int]] = []
    with ThreadPoolExecutor(max_workers=2) as pool:
        for index in range(args.dual):
            futures = [pool.submit(synthesize, args.url, args.model, args.voice,
                                   SAMPLES[(index * 2 + offset) % len(SAMPLES)],
                                   reference_audio, args.ref_text) for offset in range(2)]
            dual.extend(future.result() for future in futures)
    rows = single + dual
    report = {
        "createdAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "model": args.model,
        "voice": args.voice,
        "single": single,
        "dual": dual,
        "summary": {
            "requests": len(rows),
            "firstAudioP95Seconds": percentile([float(row["firstAudioSeconds"]) for row in rows], .95),
            "rtfMaximum": max(float(row["rtf"]) for row in rows),
            "rtfMean": statistics.mean(float(row["rtf"]) for row in rows),
            "maximumStarvationMs": max(float(row["maximumStarvationMs"]) for row in rows),
            "passed": percentile([float(row["firstAudioSeconds"]) for row in rows], .95) <= 3
                      and max(float(row["rtf"]) for row in rows) <= .8
                      and max(float(row["maximumStarvationMs"]) for row in rows) <= 300,
        },
    }
    with open(args.output, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(json.dumps(report["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
