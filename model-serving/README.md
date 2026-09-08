# DGX Spark model serving

This project deploys Open WebUI and two mutually exclusive vLLM services on `spark-8a8d`:

- Open WebUI: `http://spark-8a8d:12000`
- `daily`: selected by `.env`, Qwen3.8 FP8 or Qwen3.6 NVFP4, on `127.0.0.1:8001`
- `deep-reasoning`: `gpt-oss-120b` on `100.119.145.78:8002`

The model directories live in `/home/whtc/models` and are mounted read-only.
Only one heavy model may be running at a time.

Qwen3.8 deployment and model switching: see
[`Qwen3.8_DGX部署与模型切换指南.md`](../Qwen3.8_DGX部署与模型切换指南.md).
The model registry is `configs/models.yaml`; `scripts/model-stack switch qwen38|qwen36`
selects matching dense/MoE launch parameters and updates `.env` only after validation.
Use an SSH tunnel to reach Qwen's loopback API from Windows. Road Agent accesses
`http://qwen:8000/v1` through Docker and has no frontend model selector.

For the first Open WebUI installation and the exact expected output, follow
[`OPEN_WEBUI_RUNBOOK.md`](OPEN_WEBUI_RUNBOOK.md).

## Operations

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack status
scripts/model-stack webui status
scripts/model-stack switch daily
scripts/model-stack switch deep-reasoning
scripts/model-stack stop
scripts/model-stack logs qwen
scripts/model-stack logs gpt
```

Health checks:

```bash
scripts/healthcheck qwen 1200
scripts/healthcheck gpt 1800
scripts/healthcheck webui 300
```

Smoke tests:

```bash
python3 tests/smoke.py qwen
python3 tests/smoke.py gpt --reasoning-effort high
```

OpenAI-compatible base URLs:

- Qwen: `http://127.0.0.1:8001/v1` on DGX, or the same address through an SSH tunnel.
- GPT-OSS: `http://spark-8a8d:8002/v1` from another Tailscale device (unchanged).

Qwen is loopback-only on the host and available to Road Agent through the private
Docker network. No API key is configured. Do not expose its port publicly.

## Updating

Never overwrite a validated model revision. Download an updated revision into
a new variant directory and register it in `configs/models.yaml`. Run candidate
validation before `model-stack switch`; the controller applies the registered
launch parameters and updates `.env` after health and inference checks pass.
