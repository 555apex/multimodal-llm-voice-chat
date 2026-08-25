#!/usr/bin/env python3
"""DGX Qwen and Road Agent smoke tests using only the standard library."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import mimetypes
from pathlib import Path
import time
import uuid
import urllib.error
import urllib.request


def request_json(url: str, payload: dict | None = None, timeout: int = 600) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="GET" if payload is None else "POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.load(response)
    except urllib.error.HTTPError as exception:
        body = exception.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"HTTP {exception.code} from {url}: {body[:2000]}"
        ) from exception


def qwen_payload(*, stream: bool = False, structured: bool = False) -> dict:
    payload = {
        "model": "qwen3.6-35b-a3b-nvfp4",
        "messages": [
            {
                "role": "user",
                "content": "用中文输出 JSON，字段 answer 的值为 12 乘以 17 的结果。",
            }
        ],
        "temperature": 0.0,
        "max_tokens": 128,
        "stream": stream,
        "chat_template_kwargs": {"enable_thinking": False},
    }
    if structured:
        payload["response_format"] = {"type": "json_object"}
    return payload


def synthesize_speech(app_base: str, text: str) -> bytes:
    request = urllib.request.Request(
        f"{app_base}/api/v1/speech/syntheses",
        data=json.dumps({"text": text}, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "audio/mpeg"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=600) as response:
        speech = response.read()
        content_type = response.headers.get_content_type()
    if content_type != "audio/mpeg" or len(speech) < 1024:
        raise RuntimeError(f"invalid TTS response: {content_type}, {len(speech)} bytes")
    return speech


def check_qwen(qwen_base: str, iterations: int, business_iterations: int) -> dict:
    models = request_json(f"{qwen_base}/models")
    ids = [item.get("id") for item in models.get("data", [])]
    if "qwen3.6-35b-a3b-nvfp4" not in ids:
        raise RuntimeError(f"Qwen model is not advertised: {ids}")

    started = time.monotonic()
    ordinary = request_json(f"{qwen_base}/chat/completions", qwen_payload())
    ordinary_content = ordinary["choices"][0]["message"]["content"]
    if "204" not in ordinary_content:
        raise RuntimeError(f"unexpected ordinary answer: {ordinary_content}")

    agent_like_payload = {
        "model": "qwen3.6-35b-a3b-nvfp4",
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是福建公路应急交通Agent的意图规划器。只能选择"
                    "TRAFFIC_QUERY、EMERGENCY_DISPATCH、UNSUPPORTED之一。明确指定一条"
                    "道路时trafficScope必须为ROAD。必须输出json对象，字段包含intent、"
                    "trafficScope、city、roadName和clarification；不适用字段使用null。"
                ),
            },
            {"role": "user", "content": "查询福州五四路现在的路况。"},
        ],
        "temperature": 0.2,
        "stream": False,
        "chat_template_kwargs": {"enable_thinking": False},
        "response_format": {"type": "json_object"},
    }
    dispatch_payload = {
        "model": "qwen3.6-35b-a3b-nvfp4",
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是福建公路应急调度工单生成器。必须输出严格JSON对象，仅包含"
                    "suggestedResources和rescuePlan。suggestedResources是数组，每项包含"
                    "resourceType、resourceName、quantity、unit、purpose；quantity必须是"
                    "大于0的整数。rescuePlan覆盖现场安全、交通组织、救援处置和信息报送。"
                ),
            },
            {
                "role": "user",
                "content": (
                    "事件类型：崩塌（DT01）\n事件描述：莆田市乡道路涵结构坍塌，"
                    "双向车辆无法正常通过。"
                ),
            },
        ],
        "temperature": 0.1,
        "stream": False,
        "chat_template_kwargs": {"enable_thinking": False},
        "response_format": {"type": "json_object"},
    }
    for _ in range(business_iterations):
        agent_like = request_json(f"{qwen_base}/chat/completions", agent_like_payload)
        intent = json.loads(agent_like["choices"][0]["message"]["content"])
        if intent.get("intent") != "TRAFFIC_QUERY" or intent.get("trafficScope") != "ROAD":
            raise RuntimeError(f"invalid traffic intent response: {intent}")
        dispatch = request_json(f"{qwen_base}/chat/completions", dispatch_payload)
        proposal = json.loads(dispatch["choices"][0]["message"]["content"])
        if not proposal.get("suggestedResources") or not proposal.get("rescuePlan"):
            raise RuntimeError(f"invalid dispatch proposal response: {proposal}")

    for _ in range(iterations):
        result = request_json(f"{qwen_base}/chat/completions", qwen_payload(structured=True))
        content = result["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        if str(parsed.get("answer")) != "204":
            raise RuntimeError(f"unexpected structured answer: {content}")

    request = urllib.request.Request(
        f"{qwen_base}/chat/completions",
        data=json.dumps(qwen_payload(stream=True)).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    streamed: list[str] = []
    with urllib.request.urlopen(request, timeout=600) as response:
        for raw_line in response:
            line = raw_line.decode("utf-8").strip()
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if data == "[DONE]":
                break
            delta = json.loads(data).get("choices", [{}])[0].get("delta", {}).get("content")
            if delta:
                streamed.append(delta)
    if not streamed:
        raise RuntimeError("Qwen SSE returned no content")
    return {
        "model": "qwen3.6-35b-a3b-nvfp4",
        "ordinary_characters": len(ordinary_content),
        "business_structured_iterations": business_iterations,
        "structured_iterations": iterations,
        "seconds": round(time.monotonic() - started, 2),
        "stream_characters": len("".join(streamed)),
    }


def check_road_agent(
    app_base: str,
    with_tts: bool,
    audio: Path | None,
    tts_output: Path | None,
    with_traffic: bool,
    with_agent: bool,
    tts_concurrency: int,
    test_speech_limits: bool,
) -> dict:
    capabilities = request_json(f"{app_base}/api/v1/speech/capabilities")
    request_json(f"{app_base}/api/v1/emergency-events/pending/next")
    result = {"speech_capabilities": capabilities.get("data")}
    if with_tts:
        speech = synthesize_speech(app_base, "当前道路通行平稳。")
        result["tts_bytes"] = len(speech)
        if tts_output is not None:
            tts_output.parent.mkdir(parents=True, exist_ok=True)
            tts_output.write_bytes(speech)
            result["tts_output"] = str(tts_output)
    if tts_concurrency > 0:
        started = time.monotonic()
        with ThreadPoolExecutor(max_workers=tts_concurrency) as executor:
            speeches = list(
                executor.map(
                    lambda index: synthesize_speech(
                        app_base, f"并发排队测试第{index + 1}条，道路通行正常。"
                    ),
                    range(tts_concurrency),
                )
            )
        result["tts_queue"] = {
            "requests": tts_concurrency,
            "bytes": [len(item) for item in speeches],
            "seconds": round(time.monotonic() - started, 2),
        }
    if test_speech_limits:
        try:
            synthesize_speech(app_base, "路" * 501)
        except urllib.error.HTTPError as exception:
            if exception.code not in (400, 413):
                raise RuntimeError(
                    f"unexpected long TTS status: {exception.code}"
                ) from exception
            result["tts_long_text_status"] = exception.code
        else:
            raise RuntimeError("TTS accepted text longer than the configured limit")
    if audio is not None:
        content_type = mimetypes.guess_type(audio.name)[0] or "audio/wav"
        boundary = f"road-agent-{uuid.uuid4().hex}"
        payload = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="audio"; filename="{audio.name}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode("utf-8") + audio.read_bytes() + (
            f"\r\n--{boundary}\r\n"
            'Content-Disposition: form-data; name="durationMs"\r\n\r\n'
            "10000\r\n"
            f"--{boundary}--\r\n"
        ).encode("ascii")
        request = urllib.request.Request(
            f"{app_base}/api/v1/speech/transcriptions",
            data=payload,
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=600) as response:
            transcription = json.load(response)
        result["asr"] = transcription.get("data")
    if with_traffic:
        traffic = request_json(
            f"{app_base}/api/v1/traffic/queries",
            {"areaCode": "350100", "roadName": "五四路"},
        )
        traffic_data = traffic.get("data") or {}
        if traffic_data.get("source") != "AMAP" or not traffic_data.get("segments"):
            raise RuntimeError(f"invalid Amap traffic response: {traffic}")
        result["traffic"] = {
            "roadName": traffic_data.get("roadName"),
            "source": traffic_data.get("source"),
            "segmentCount": len(traffic_data.get("segments", [])),
            "summary": traffic_data.get("summary"),
        }
    if with_agent:
        conversation_id = f"dgx-smoke-{uuid.uuid4()}"
        request = urllib.request.Request(
            f"{app_base}/api/v1/conversations/{conversation_id}/messages/stream",
            data=json.dumps(
                {"message": "查询福州五四路现在的路况。"},
                ensure_ascii=False,
            ).encode("utf-8"),
            headers={"Content-Type": "application/json", "Accept": "text/event-stream"},
            method="POST",
        )
        event_name: str | None = None
        event_names: list[str] = []
        event_details: list[dict[str, object]] = []
        with urllib.request.urlopen(request, timeout=600) as response:
            for raw_line in response:
                line = raw_line.decode("utf-8").strip()
                if line.startswith("event:"):
                    event_name = line[6:].strip()
                elif line.startswith("data:") and event_name:
                    event_names.append(event_name)
                    raw_data = line[5:].strip()
                    try:
                        parsed_data: object = json.loads(raw_data)
                    except json.JSONDecodeError:
                        parsed_data = raw_data
                    event_details.append({"event": event_name, "data": parsed_data})
                    event_name = None
        if "result.traffic" not in event_names or "answer.delta" not in event_names:
            raise RuntimeError(
                f"Agent SSE did not complete traffic workflow: {event_details}"
            )
        result["agent_sse_events"] = event_names
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--qwen-base", default="http://100.119.145.78:8001/v1")
    parser.add_argument("--app-base", default="http://127.0.0.1:18080")
    parser.add_argument("--model-iterations", type=int, default=3)
    parser.add_argument("--business-structured-iterations", type=int, default=1)
    parser.add_argument("--with-tts", action="store_true")
    parser.add_argument("--tts-output", type=Path)
    parser.add_argument("--audio", type=Path)
    parser.add_argument("--with-traffic", action="store_true")
    parser.add_argument("--with-agent", action="store_true")
    parser.add_argument("--tts-concurrency", type=int, default=0)
    parser.add_argument("--test-speech-limits", action="store_true")
    args = parser.parse_args()
    report = {
        "qwen": check_qwen(
            args.qwen_base.rstrip("/"),
            args.model_iterations,
            args.business_structured_iterations,
        ),
        "road_agent": check_road_agent(
            args.app_base.rstrip("/"),
            args.with_tts,
            args.audio,
            args.tts_output,
            args.with_traffic,
            args.with_agent,
            args.tts_concurrency,
            args.test_speech_limits,
        ),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
