# CosyVoice3 DGX candidate (not enabled in production)

Pinned upstream source: `QwenAudio/CosyVoice@074ca6dc9e80a2f424f1f74b48bdd7d3fea531cc`.
Pinned weights: `FunAudioLLM/Fun-CosyVoice3-0.5B-2512@29e01c4e8d000f4bcd70751be16fa94bf3d85a18`.
Matcha-TTS submodule: `dd9105b34bf2be2230f4aa1e4769fb586a3c824e`.

These scripts prepare an isolated candidate under
`/home/whtc/workspace/projects/road-agent-dgx/.releases/speech-20260910`.
Run `prepare-cosy.py`, `prepare-cosy-runtime.py`, `build-cosy.py`, then
`bench-cosy.py` with Python 3 on DGX. They do not switch production services.
The baseline image tag must already exist from the release backup.
Network access is needed only for preparation/build. The benchmark disables
container networking, mounts weights read-only, caps memory at 16 GiB, and loads
one TTS model. Leave the existing Qwen3.6 service running; do not load a second LLM.

The candidate uses the verified NVIDIA Torch 2.8/CUDA 13 runtime, CPU ONNX Runtime
for voice-reference processing, Torch FP16 generation, and a cached reference
voice from the upstream `asset/zero_shot_prompt.wav`. It is not Serena.
The build script pins added dependency versions and records the image in Docker.
Source and weights retain their upstream licenses.

The first benchmark is an early rejection check, not the full release gate.
First PCM chunk is not browser audible-start time. An RTF above 0.8 rejects this
candidate before a model switch; passing it would still require the full 20-run
single/dual test, cancellation/cache-isolation tests, AudioWorklet measurements,
and listening review. Results are in `cosy-results`; production continues using
Qwen3-TTS CustomVoice/Serena until all gates pass.
