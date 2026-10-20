# LLM 单服务探针

在新机运行；实际模型名替换为 site.env 中 LLM_MODEL_NAME，curl 需存在于 qwen 镜像。

```bash
bash resources/scripts/site.sh exec qwen curl -fsS \
 -H 'Content-Type: application/json' \
 -d '{"model":"填写实际模型名","messages":[{"role":"user","content":"仅回答12加17的结果"}],"temperature":0,"max_tokens":128,"chat_template_kwargs":{"enable_thinking":false}}' \
 http://127.0.0.1:8000/v1/chat/completions
```

通过：返回 29，无 thinking 文本。把请求增加 `"stream":true` 并用 `curl -N` 检查连续分片和结束标记；再增加 `"response_format":{"type":"json_object"}`，提示改为“只返回JSON，字段answer为12加17的结果”，验证可解析JSON。三类均通过后，使用项目应急方案生成样本检查更复杂结构。保留模型 revision、完整请求、输出长度和时延，供 DGX 同负载比较。
