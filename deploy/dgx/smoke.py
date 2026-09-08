#!/usr/bin/env python3
"""DGX Qwen and Road Agent smoke tests using only the standard library."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import mimetypes
import os
import sys
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


def configured_model_name() -> str:
    value = os.environ.get("ROADAGENT_MODEL_NAME")
    env_file = Path(__file__).with_name(".env")
    if not value and env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if line.startswith("ROADAGENT_MODEL_NAME="):
                value = line.split("=", 1)[1].strip().strip('"').strip("'")
    if not value:
        raise RuntimeError("set ROADAGENT_MODEL_NAME in deploy/dgx/.env or pass --model-name")
    return value


def model_content(result: dict) -> str:
    message = result["choices"][0]["message"]
    content = message.get("content") or ""
    if "<think>" in content or message.get("reasoning_content") or message.get("reasoning"):
        raise RuntimeError("unexpected thinking output for enable_thinking=false")
    return content


def qwen_payload(model_name: str, *, stream: bool = False, structured: bool = False) -> dict:
    payload = {
        "model": model_name,
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


def check_qwen(qwen_base: str, iterations: int, business_iterations: int, model_name: str) -> dict:
    models = request_json(f"{qwen_base}/models")
    ids = [item.get("id") for item in models.get("data", [])]
    if model_name not in ids:
        raise RuntimeError(f"Qwen model is not advertised: {ids}")

    started = time.monotonic()
    ordinary = request_json(f"{qwen_base}/chat/completions", qwen_payload(model_name))
    ordinary_content = model_content(ordinary)
    if "204" not in ordinary_content:
        raise RuntimeError(f"unexpected ordinary answer: {ordinary_content}")

    agent_like_payload = {
        "model": model_name,
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是福建公路应急交通Agent的意图规划器。只能选择"
                    "TRAFFIC_QUERY、EMERGENCY_DISPATCH、UNSUPPORTED之一。明确指定一条"
                    "业务范围时trafficScope必须使用给定枚举。用户询问福建省普通国省道"
                    "整体交通态势时，trafficScope必须为PROVINCE_OVERVIEW。必须输出json对象，字段包含"
                    "intent、trafficScope、originCity、destinationCity、routeCode、routeName、"
                    "selectedCities、analysisCity、city、areaName、roadName、direction、eventType、"
                    "location、severity、eventDescription、resourceTypes、clarification；数组字段"
                    "使用数组，其他不适用字段使用null。"
                ),
            },
            {"role": "user", "content": "福建省普通国省道目前整体交通态势如何？"},
        ],
        "temperature": 0.2,
        "stream": False,
        "chat_template_kwargs": {"enable_thinking": False},
        "response_format": {"type": "json_object"},
    }
    dispatch_payload = {
        "model": model_name,
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是福建公路应急调度需求分析器。必须输出严格JSON对象，仅包含"
                    "resourceRequirements和rescuePlan。resourceRequirements是数组，每项"
                    "仅包含resourceTypeCode、quantity、purpose；resourceTypeCode只能取"
                    "ROAD_RESCUE_TEAM或TRAFFIC_CONTROL_EQUIPMENT，quantity必须是大于0的"
                    "整数。rescuePlan必须是中文字符串，覆盖现场安全、交通组织、救援处置"
                    "和信息报送。"
                ),
            },
            {
                "role": "user",
                "content": (
                    "事件类型：崩塌（DT01）\n事件描述：莆田市乡道路涵结构坍塌，"
                    "双向车辆无法正常通过。\n可选资源类型：ROAD_RESCUE_TEAM、"
                    "TRAFFIC_CONTROL_EQUIPMENT。"
                ),
            },
        ],
        "temperature": 0.1,
        "stream": False,
        "chat_template_kwargs": {"enable_thinking": False},
        "response_format": {"type": "json_object"},
    }
    traffic_summary_payload = {
        "model": model_name,
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是福建普通国省干线交通态势研判助手。只能依据用户提供的事实。"
                    "必须输出严格JSON对象且只能包含summary、trend、trendForecast。"
                    "summary写3句、50至300字中文；trend只能为基本稳定；trendForecast"
                    "必须恰好1句并包含未来1至2小时、预计和基本稳定。"
                ),
            },
            {
                "role": "user",
                "content": (
                    "queryType=PROVINCE_OVERVIEW\n路线G104均速52km/h，状态10畅通；"
                    "路线G324均速38km/h，状态20轻度拥堵。"
                ),
            },
        ],
        "temperature": 0.1,
        "stream": False,
        "chat_template_kwargs": {"enable_thinking": False},
        "response_format": {"type": "json_object"},
    }
    for business_index in range(business_iterations):
        agent_like = request_json(f"{qwen_base}/chat/completions", agent_like_payload)
        intent = json.loads(model_content(agent_like))
        if (
            intent.get("intent") != "TRAFFIC_QUERY"
            or intent.get("trafficScope") != "PROVINCE_OVERVIEW"
        ):
            raise RuntimeError(f"invalid traffic intent response: {intent}")
        traffic_summary = request_json(
            f"{qwen_base}/chat/completions", traffic_summary_payload
        )
        summary = json.loads(model_content(traffic_summary))
        if not summary.get("summary") or summary.get("trend") != "基本稳定":
            raise RuntimeError(f"invalid traffic summary response: {summary}")
        dispatch = request_json(f"{qwen_base}/chat/completions", dispatch_payload)
        proposal = json.loads(model_content(dispatch))
        requirements = proposal.get("resourceRequirements")
        if not requirements or not isinstance(proposal.get("rescuePlan"), str):
            raise RuntimeError(f"invalid dispatch proposal response: {proposal}")
        for requirement in requirements:
            if requirement.get("resourceTypeCode") not in {
                "ROAD_RESCUE_TEAM",
                "TRAFFIC_CONTROL_EQUIPMENT",
            } or int(requirement.get("quantity", 0)) <= 0:
                raise RuntimeError(f"invalid resource requirement: {requirement}")
        if (business_index + 1) % 5 == 0:
            print(f"business JSON batches: {business_index + 1}/{business_iterations}", file=sys.stderr, flush=True)

    for model_index in range(iterations):
        result = request_json(f"{qwen_base}/chat/completions", qwen_payload(model_name, structured=True))
        content = model_content(result)
        parsed = json.loads(content)
        if str(parsed.get("answer")) != "204":
            raise RuntimeError(f"unexpected structured answer: {content}")
        if (model_index + 1) % 10 == 0:
            print(f"sequential JSON requests: {model_index + 1}/{iterations}", file=sys.stderr, flush=True)

    request = urllib.request.Request(
        f"{qwen_base}/chat/completions",
        data=json.dumps(qwen_payload(model_name, stream=True)).encode("utf-8"),
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
        "model": model_name,
        "ordinary_characters": len(ordinary_content),
        "traffic_intent_iterations": business_iterations,
        "traffic_summary_iterations": business_iterations,
        "resource_requirement_iterations": business_iterations,
        "dispatch_plan_iterations": business_iterations,
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
    with_workflow: bool,
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
        limit_request = urllib.request.Request(
            f"{app_base}/api/v1/speech/syntheses",
            data=json.dumps({"text": "路" * 501}, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json", "Accept": "*/*"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(limit_request, timeout=60):
                pass
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
        traffic_cases = [
            ("PROVINCE_OVERVIEW", {}, ("routeSummaries", "segments")),
            ("CAPACITY_OVERVIEW", {}, ("capacityRows",)),
            ("REGIONAL_TRAFFIC_OVERVIEW", {}, ("hubRows", "regionPressureRows", "routePressureRows")),
            ("VEHICLE_PATTERN_OVERVIEW", {"analysisCity": "福州市"}, ("vehicleStructureRows", "vehicleTimeFeatureRows", "vehicleDayTypeRows")),
        ]
        traffic_report: list[dict[str, object]] = []
        for query_type, fields, expected_lists in traffic_cases:
            traffic = request_json(
                f"{app_base}/api/v1/traffic/queries",
                {"queryType": query_type, **fields},
            )
            traffic_data = traffic.get("data") or {}
            if query_type == "REGIONAL_TRAFFIC_OVERVIEW" and "regionalPairRows" in traffic_data:
                # Current DGX release returns city pairs/channels; older builds
                # used hub/pressure rows. Validate the actual versioned contract.
                expected_lists = ("regionalPairRows", "regionalChannelRows")
            if (
                traffic_data.get("source") != "MYSQL"
                or traffic_data.get("queryType") != query_type
                or not traffic_data.get("summary")
                or not all(isinstance(traffic_data.get(name), list) for name in expected_lists)
            ):
                raise RuntimeError(f"invalid MySQL traffic response: {traffic}")
            traffic_report.append(
                {
                    "queryType": query_type,
                    "source": traffic_data.get("source"),
                    "rows": {name: len(traffic_data.get(name) or []) for name in expected_lists},
                    "summaryCharacters": len(traffic_data.get("summary", "")),
                }
            )
        result["traffic"] = traffic_report
    if with_workflow:
        workflow_report: dict[str, object] = {}
        for stage in ("LEVEL_1", "LEVEL_2", "LEVEL_3"):
            inbox = request_json(
                f"{app_base}/api/v1/emergency-workflows/inbox?stage={stage}"
            )
            inbox_data = inbox.get("data") or {}
            if not isinstance(inbox_data.get("counts"), dict):
                raise RuntimeError(f"invalid {stage} workflow inbox: {inbox}")
            workflow_report[stage] = {
                "hasItem": inbox_data.get("item") is not None,
                "counts": inbox_data.get("counts"),
            }
        history = request_json(
            f"{app_base}/api/v1/emergency-workflows/history?page=0&size=20"
        )
        history_data = history.get("data") or {}
        if not isinstance(history_data.get("items"), list):
            raise RuntimeError(f"invalid workflow history: {history}")
        workflow_report["history"] = {
            "items": len(history_data.get("items", [])),
            "total": history_data.get("total"),
        }
        result["workflow"] = workflow_report
    if with_agent:
        conversation_id = f"dgx-smoke-{uuid.uuid4()}"
        request = urllib.request.Request(
            f"{app_base}/api/v1/conversations/{conversation_id}/messages/stream",
            data=json.dumps(
                {"message": "福建省普通国省道目前整体交通态势如何？"},
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
    parser.add_argument("--qwen-base", default="http://127.0.0.1:8001/v1")
    parser.add_argument("--model-name")
    parser.add_argument("--model-only", action="store_true")
    parser.add_argument("--app-only", action="store_true")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--app-base", default="http://127.0.0.1:18080")
    parser.add_argument("--model-iterations", type=int, default=3)
    parser.add_argument("--business-structured-iterations", type=int, default=1)
    parser.add_argument("--with-tts", action="store_true")
    parser.add_argument("--tts-output", type=Path)
    parser.add_argument("--audio", type=Path)
    parser.add_argument("--with-traffic", action="store_true")
    parser.add_argument("--with-workflow", action="store_true")
    parser.add_argument("--with-agent", action="store_true")
    parser.add_argument("--tts-concurrency", type=int, default=0)
    parser.add_argument("--test-speech-limits", action="store_true")
    args = parser.parse_args()
    if args.model_only and args.app_only:
        parser.error("--model-only and --app-only cannot be combined")
    report = {}
    if not args.app_only:
        report["qwen"] = check_qwen(
            args.qwen_base.rstrip("/"),
            args.model_iterations,
            args.business_structured_iterations,
            args.model_name or configured_model_name(),
        )
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if not args.model_only:
        report["road_agent"] = check_road_agent(
            args.app_base.rstrip("/"),
            args.with_tts,
            args.audio,
            args.tts_output,
            args.with_traffic,
            args.with_workflow,
            args.with_agent,
            args.tts_concurrency,
            args.test_speech_limits,
        )
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
